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
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.utils.Logging
import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.NrsConnector
import uk.gov.hmrc.mtdtransactionrisking.v1.models.auth.IdentityData
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.SubmissionResult
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.nrs.*
import uk.gov.hmrc.mtdtransactionrisking.v1.repositories.NrsSubmissionWorkItemStore

import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import javax.inject.{Inject, Provider, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

@Singleton
class NrsService @Inject() (
    appConfig: AppConfig,
    connector: NrsConnector,
    nrsSubmissionWorkItemRepositoryProvider: Provider[NrsSubmissionWorkItemStore],
    retryPolicy: NrsRetryPolicy
)(implicit ec: ExecutionContext)
    extends Logging:

  // Logs are labelled by stage, so Kibana can filter first attempts from retries
  private val InitialStage = "submitInitial"
  private val RetryStage = "processWorkItem"

  // ---------- Public API ----------

  def submit(
      evidence: JsValue,
      vrn: String,
      reportId: String,
      submissionTimestamp: Instant,
      identityData: Option[IdentityData],
      userAuthToken: Option[String],
      requestHeaders: Seq[(String, String)],
      notableEventType: NotableEventType,
      correlationId: CorrelationId
  ): Unit =
    val prefix = logPrefix(correlationId, InitialStage)
    val event = notableEventType.value

    if !FeatureSwitch(appConfig.featureSwitch).isEnabled(NrsSubmissionFeature) then
      logger.info(s"$prefix NRS submission is disabled; skipping $event")
    else
      buildNrsSubmission(evidence, vrn, reportId, submissionTimestamp, identityData, userAuthToken, requestHeaders, notableEventType) match
        case Left(reason) =>
          logger.error(s"$prefix NRS submission skipped for $event: $reason")

        case Right(nrsSubmission) =>
          submitInitial(nrsSubmission, correlationId)

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
      authenticatedIdentityData <- identityData.toRight("identity data is unavailable")
      token <- userAuthToken.toRight("original Authorization header is unavailable")
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
          headerData = headerData(requestHeaders),
          searchKeys = SearchKeys(vrn = vrn, reportId = reportId)
        )
      )

  def processDueWorkItems(): Future[Unit] =

    def processNext(processedCount: Int): Future[Unit] =
      if processedCount >= appConfig.nrsRetryBatchSize then
        logger.info(s"[NrsService][processDueWorkItems] reached configured retry batch size ${appConfig.nrsRetryBatchSize}")
        Future.unit
      else
        repository.pullDue().flatMap {
          case None           => Future.unit
          case Some(workItem) => processWorkItem(workItem).flatMap(_ => processNext(processedCount + 1))
        }

    processNext(processedCount = 0)

  // ---------- First attempt ----------

  private def submitInitial(nrsSubmission: NrsSubmission, correlationId: CorrelationId): Unit =
    val prefix = logPrefix(correlationId, InitialStage)
    val event = nrsSubmission.metadata.notableEvent

    submitWithRecovery(nrsSubmission, correlationId, prefix)
      .flatMap {
        case SubmissionResult.Success =>
          Future.unit

        case SubmissionResult.RetryableFailure =>
          repository
            .enqueueRetryableFailure(submission = nrsSubmission, correlationId = correlationId)
            .map(workItem => logger.warn(s"$prefix retryable NRS failure for $event; work item ${workItem.id} scheduled for ${workItem.availableAt}"))

        case SubmissionResult.PermanentFailure =>
          repository
            .enqueuePermanentFailure(submission = nrsSubmission, correlationId = correlationId)
            .map(workItem =>
              logger.warn(s"$prefix permanent NRS failure for $event; work item ${workItem.id} retained for ${appConfig.nrsRetryRetention}"))
      }
      .recover { case NonFatal(error) =>
        logger.error(s"$prefix failed to persist NRS delivery result for $event; VAT Assist response is unaffected", error)
      }

  // ---------- Retries ----------

  private def processWorkItem(workItem: WorkItem[NrsSubmissionWorkItem]): Future[Unit] =
    val prefix = logPrefix(workItem.item.correlationId, RetryStage)

    Try(workItem.item.nrsSubmission) match
      case Failure(error) =>
        logger.error(s"$prefix could not decrypt or parse NRS work item ${workItem.id}; marking permanently failed", error)
        repository.markPermanentFailure(workItem).map(_ => ())

      case Success(nrsSubmission) =>
        submitWithRecovery(nrsSubmission, workItem.item.correlationId, prefix)
          .flatMap(result => recordResult(workItem, nrsSubmission.metadata.notableEvent, result, prefix))

  private def recordResult(workItem: WorkItem[NrsSubmissionWorkItem], event: String, result: SubmissionResult, prefix: String): Future[Unit] =
    result match
      case SubmissionResult.Success =>
        repository
          .completeAndDelete(workItem.id)
          .map(deleted => logger.info(s"$prefix NRS retry succeeded for $event, work item ${workItem.id}; deleted=$deleted"))

      case SubmissionResult.PermanentFailure =>
        repository
          .markPermanentFailure(workItem)
          .map(updated =>
            logger.warn(
              s"$prefix permanent NRS failure for $event, work item ${workItem.id}; retained for ${appConfig.nrsRetryRetention}; updated=$updated"))

      case SubmissionResult.RetryableFailure =>
        val retryNumber = workItem.failureCount + 1

        repository
          .markRetryableFailure(workItem)
          .map { updated =>
            if retryPolicy.isExhaustedAfterFailure(retryNumber) then
              logger.error(
                s"$prefix NRS work item ${workItem.id} for $event exhausted retry $retryNumber/${retryPolicy.maxRetries}; " +
                  s"retained for ${appConfig.nrsRetryRetention}; updated=$updated")
            else
              logger.warn(
                s"$prefix NRS retry $retryNumber/${retryPolicy.maxRetries} failed for $event, work item ${workItem.id}; next retry scheduled; updated=$updated")
          }

  // ---------- Shared ----------

  private def submitWithRecovery(nrsSubmission: NrsSubmission, correlationId: CorrelationId, prefix: String): Future[SubmissionResult] =
    connector
      .submit(nrsSubmission = nrsSubmission, correlationId = correlationId)
      .recover { case NonFatal(error) =>
        logger.warn(s"$prefix NRS submission failed unexpectedly for ${nrsSubmission.metadata.notableEvent}; treating as retryable", error)
        SubmissionResult.RetryableFailure
      }

  private def repository: NrsSubmissionWorkItemStore =
    nrsSubmissionWorkItemRepositoryProvider.get()

  private def logPrefix(correlationId: CorrelationId, stage: String): String =
    s"${correlationId.value}::[NrsService][$stage]"

  // ---------- Evidence helpers ----------

  private def headerData(headers: Seq[(String, String)]): JsObject =
    Json.obj(
      headers.collect {
        case (name, value) if !name.equalsIgnoreCase("Authorization") => name -> value
      }*
    )

  private def sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map("%02x".format(_)).mkString
