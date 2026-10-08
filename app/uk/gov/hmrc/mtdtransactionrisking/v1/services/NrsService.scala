/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.mtdtransactionrisking.v1.services

import play.api.libs.json.{JsObject, JsValue, Json}
import uk.gov.hmrc.mongo.workitem.WorkItem
import uk.gov.hmrc.mtdtransactionrisking.config.{AppConfig, FeatureSwitch, NrsSubmissionFeature}
import uk.gov.hmrc.mtdtransactionrisking.utils.Logging
import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.NrsConnector
import uk.gov.hmrc.mtdtransactionrisking.v1.models.auth.IdentityData
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.nrs.{Metadata, NotableEventType, NrsSubmission, NrsSubmissionResult, NrsSubmissionWorkItem, SearchKeys}
import uk.gov.hmrc.mtdtransactionrisking.v1.repositories.NrsSubmissionWorkItemStore

import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import javax.inject.{Inject, Provider, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class NrsService @Inject()(
                            appConfig: AppConfig,
                            connector: NrsConnector,
                            nrsSubmissionWorkItemRepositoryProvider: Provider[NrsSubmissionWorkItemStore],
                            retryPolicy: NrsRetryPolicy
                          )(implicit ec: ExecutionContext)
  extends Logging:

  private def nrsSubmissionWorkItemRepository: NrsSubmissionWorkItemStore =
    nrsSubmissionWorkItemRepositoryProvider.get()

  def submit(
              evidence: JsValue,
              vrn: String,
              reportId: String,
              submissionTimestamp: Instant,
              identityData: Option[IdentityData],
              userAuthToken: Option[String],
              requestHeaders: Seq[(String, String)],
              notableEventType: NotableEventType
            ): Unit =
    if !FeatureSwitch(appConfig.featureSwitch).isEnabled(NrsSubmissionFeature) then
      logger.debug(
        s"[NrsService][submit] NRS submission is disabled for " +
          s"${notableEventType.value}"
      )
    else
      buildNrsSubmission(
        evidence = evidence,
        vrn = vrn,
        reportId = reportId,
        submissionTimestamp = submissionTimestamp,
        identityData = identityData,
        userAuthToken = userAuthToken,
        requestHeaders = requestHeaders,
        notableEventType = notableEventType
      ) match
        case Left(reason) =>
          logger.warn(s"[NrsService][submit] NRS submission skipped: $reason")

        case Right(nrsSubmission) =>
          submitInitial(nrsSubmission)

  def processDueWorkItems(): Future[Unit] =

    def processNext(): Future[Unit] =
      nrsSubmissionWorkItemRepository.pullDue().flatMap {
        case None =>
          Future.unit

        case Some(workItem) =>
          processWorkItem(workItem)
            .flatMap(_ => processNext())
      }

    processNext()

  def buildNrsSubmission(
                          evidence: JsValue,
                          vrn: String,
                          reportId: String,
                          submissionTimestamp: Instant,
                          identityData: Option[IdentityData],
                          userAuthToken: Option[String],
                          requestHeaders: Seq[(String, String)],
                          notableEventType: NotableEventType
                        ): Either[String, NrsSubmission] =
    for
      authenticatedIdentityData <- identityData.toRight(
        "identity data is unavailable"
      )
      token <- userAuthToken.toRight(
        "original Authorization header is unavailable"
      )
      headers = headerData(requestHeaders)
    yield
      val evidenceBytes = Json.stringify(evidence).getBytes(UTF_8)

      NrsSubmission(
        payload = Base64.getEncoder.encodeToString(evidenceBytes),
        metadata = Metadata(
          businessId = "vata",
          notableEvent = notableEventType.value,
          payloadContentType = "application/json",
          payloadSha256Checksum = sha256(evidenceBytes),
          userSubmissionTimestamp = submissionTimestamp.toString,
          identityData = Json.toJson(authenticatedIdentityData),
          userAuthToken = token,
          headerData = headers,
          searchKeys = SearchKeys(
            vrn = vrn,
            reportId = reportId
          )
        )
      )

  private def submitInitial(
                             nrsSubmission: NrsSubmission
                           ): Unit =
    connector
      .submit(nrsSubmission)
      .flatMap {
        case NrsSubmissionResult.Success =>
          logger.info(
            s"[NrsService][submitInitial] NRS submission accepted for " +
              s"${nrsSubmission.metadata.notableEvent}"
          )
          Future.unit

        case NrsSubmissionResult.RetryableFailure =>
          nrsSubmissionWorkItemRepository
            .enqueueRetryableFailure(nrsSubmission)
            .map { workItem =>
              logger.warn(
                s"[NrsService][submitInitial] Retryable NRS failure for " +
                  s"${nrsSubmission.metadata.notableEvent}; " +
                  s"persisted work item ${workItem.id} for retry at " +
                  s"${workItem.availableAt}"
              )
              ()
            }

        case NrsSubmissionResult.PermanentFailure =>
          nrsSubmissionWorkItemRepository
            .enqueuePermanentFailure(nrsSubmission)
            .map { workItem =>
              logger.warn(
                s"[NrsService][submitInitial] Permanent NRS failure for " +
                  s"${nrsSubmission.metadata.notableEvent}; " +
                  s"persisted work item ${workItem.id} for 28-day retention"
              )
              ()
            }
      }
      .recover { case NonFatal(error) =>
        logger.error(
          s"[NrsService][submitInitial] Unexpected NRS submission or " +
            "retry-persistence failure; VAT Assist response is unaffected",
          error
        )
        ()
      }

  private def processWorkItem(
                               workItem: WorkItem[NrsSubmissionWorkItem]
                             ): Future[Unit] =
    connector.submit(workItem.item.nrsSubmission).flatMap {
      case NrsSubmissionResult.Success =>
        nrsSubmissionWorkItemRepository
          .completeAndDelete(workItem.id)
          .map { deleted =>
            logger.info(
              s"[NrsService][processDueWorkItems] Retry succeeded for " +
                s"NRS work item ${workItem.id}; deleted=$deleted"
            )
            ()
          }

      case NrsSubmissionResult.RetryableFailure =>
        val retryNumber = workItem.failureCount + 1

        nrsSubmissionWorkItemRepository
          .markRetryableFailure(workItem)
          .map { updated =>
            if retryPolicy.isExhaustedAfterFailure(retryNumber) then
              logger.warn(
                s"[NrsService][processDueWorkItems] NRS work item " +
                  s"${workItem.id} exhausted retry $retryNumber/" +
                  s"${retryPolicy.maxRetries}; retained for 28 days; " +
                  s"updated=$updated"
              )
            else
              logger.warn(
                s"[NrsService][processDueWorkItems] Retry $retryNumber/" +
                  s"${retryPolicy.maxRetries} failed for NRS work item " +
                  s"${workItem.id}; next retry after " +
                  s"${retryPolicy.delayForRetry(retryNumber + 1)}; " +
                  s"updated=$updated"
              )
            ()
          }

      case NrsSubmissionResult.PermanentFailure =>
        nrsSubmissionWorkItemRepository
          .markPermanentFailure(workItem)
          .map { updated =>
            logger.warn(
              s"[NrsService][processDueWorkItems] Permanent NRS failure for " +
                s"work item ${workItem.id}; retained for 28 days; " +
                s"updated=$updated"
            )
            ()
          }
    }

  private def headerData(
                          headers: Seq[(String, String)]
                        ): JsObject =
    Json.obj(
      headers.collect {
        case (name, value) if !name.equalsIgnoreCase("Authorization") =>
          name -> value
      }*
    )

  private def sha256(
                      bytes: Array[Byte]
                    ): String =
    MessageDigest
      .getInstance("SHA-256")
      .digest(bytes)
      .map("%02x".format(_))
      .mkString
