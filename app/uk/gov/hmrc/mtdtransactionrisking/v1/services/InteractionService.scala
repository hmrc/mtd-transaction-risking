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

import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.workitem.WorkItem
import uk.gov.hmrc.mtdtransactionrisking.config.{AppConfig, FeatureSwitch, RsdRetryFeature}
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.utils.Logging
import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.InteractionConnector
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.{AcknowledgeRequest, Interaction, SubmissionResult}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.rsd.RsdInteractionWorkItem
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.{FeedbackResponse, Obligation}
import uk.gov.hmrc.mtdtransactionrisking.v1.repositories.RsdInteractionWorkItemStore

import java.time.{Clock, Instant}
import javax.inject.{Inject, Provider, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal

@Singleton
class InteractionService @Inject() (
                                     appConfig: AppConfig,
                                     connector: InteractionConnector,
                                     repositoryProvider: Provider[RsdInteractionWorkItemStore],
                                     retryPolicy: RsdRetryPolicy,
                                     clock: Clock
                                   )(using ec: ExecutionContext)
  extends Logging:

  private lazy val retryEnabled =
    FeatureSwitch(appConfig.featureSwitch).isEnabled(RsdRetryFeature)

  private def repository: RsdInteractionWorkItemStore =
    repositoryProvider.get()

  def store(feedback: FeedbackResponse, obligation: Obligation, vrn: String, vendorBody: JsValue)(implicit hc: HeaderCarrier, correlationId: CorrelationId): Unit =
    send(Interaction.forGeneratedReport(feedback, obligation, vrn, vendorBody, Instant.now(clock)))

  def storeAcknowledgement(request: AcknowledgeRequest)(implicit hc: HeaderCarrier, correlationId: CorrelationId): Unit =
    send(Interaction.forAcknowledgement(request, Instant.now(clock)))

  private def send(interaction: Interaction)(implicit hc: HeaderCarrier, correlationId: CorrelationId): Unit =
    submitInitial(interaction, correlationId)

  private def submitInitial(
                             interaction: Interaction,
                             correlationId: CorrelationId
                           )(implicit hc: HeaderCarrier): Unit =
    storeWithRecovery(
      body = Json.toJson(interaction),
      feedbackId = interaction.feedbackId,
      eventName = interaction.eventName,
      correlationId = correlationId
    ).flatMap {
      case SubmissionResult.Success =>
        Future.unit

      // The switch controls retries/queueing, not the initial RSD delivery.
      case SubmissionResult.RetryableFailure if !retryEnabled =>
        logger.warn(
          s"${correlationId.value}::[InteractionService][submitInitial] " +
            s"RSD retry disabled; not queueing ${interaction.eventName}, " +
            s"feedbackId ${interaction.feedbackId}"
        )
        Future.unit

      case SubmissionResult.PermanentFailure if !retryEnabled =>
        logger.warn(
          s"${correlationId.value}::[InteractionService][submitInitial] " +
            s"RSD retry disabled; not retaining ${interaction.eventName}, " +
            s"feedbackId ${interaction.feedbackId}"
        )
        Future.unit

      case SubmissionResult.RetryableFailure =>
        repository
          .enqueueRetryableFailure(interaction, correlationId)
          .map { workItem =>
            logger.warn(
              s"${correlationId.value}::[InteractionService][submitInitial] " +
                s"retryable RSD failure for ${interaction.eventName}, " +
                s"feedbackId ${interaction.feedbackId}; work item ${workItem.id} " +
                s"scheduled for ${workItem.availableAt}"
            )
            ()
          }

      case SubmissionResult.PermanentFailure =>
        repository
          .enqueuePermanentFailure(interaction, correlationId)
          .map { workItem =>
            logger.warn(
              s"${correlationId.value}::[InteractionService][submitInitial] " +
                s"permanent RSD failure for ${interaction.eventName}, " +
                s"feedbackId ${interaction.feedbackId}; work item ${workItem.id} retained"
            )
            ()
          }
    }.recover {
      case NonFatal(error) =>
        logger.error(
          s"${correlationId.value}::[InteractionService][submitInitial] " +
            s"failed to persist RSD delivery result for ${interaction.eventName}; " +
            "VAT Assist response is unaffected",
          error
        )
        ()
    }

  private def storeWithRecovery(
                                 body: JsValue,
                                 feedbackId: String,
                                 eventName: String,
                                 correlationId: CorrelationId
                               )(implicit hc: HeaderCarrier): Future[SubmissionResult] =
    connector
      .store(
        body = body,
        feedbackId = feedbackId,
        eventName = eventName,
        correlationId = correlationId
      )
      .recover {
        case NonFatal(error) =>
          logger.warn(
            s"${correlationId.value}::[InteractionService] " +
              s"RSD submission failed unexpectedly for $eventName; treating as retryable",
            error
          )
          SubmissionResult.RetryableFailure
      }

  def processDueWorkItems(): Future[Unit] =
    if !retryEnabled then
      Future.unit
    else
      def processNext(processedCount: Int): Future[Unit] =
        if processedCount >= appConfig.rsdRetryBatchSize then
          logger.info(
            s"[InteractionService][processDueWorkItems] reached configured retry batch " +
              s"size ${appConfig.rsdRetryBatchSize}"
          )
          Future.unit
        else
          repository.pullDue().flatMap {
            case None =>
              Future.unit

            case Some(workItem) =>
              processWorkItem(workItem)
                .flatMap(_ => processNext(processedCount + 1))
          }

      processNext(processedCount = 0)

  private def processWorkItem(
                               workItem: WorkItem[RsdInteractionWorkItem]
                             ): Future[Unit] =
    Try(workItem.item.body) match
      case Failure(error) =>
        logger.error(
          s"${workItem.item.correlationId.value}::[InteractionService][processWorkItem] " +
            s"could not decode RSD work item ${workItem.id}; marking permanently failed",
          error
        )
        repository
          .markPermanentFailure(workItem)
          .map(_ => ())

      case Success(body) =>
        given HeaderCarrier = HeaderCarrier()

        storeWithRecovery(
          body = body,
          feedbackId = workItem.item.feedbackId,
          eventName = workItem.item.eventName,
          correlationId = workItem.item.correlationId
        ).flatMap(result => recordResult(workItem, result))

  private def recordResult(
                            workItem: WorkItem[RsdInteractionWorkItem],
                            result: SubmissionResult
                          ): Future[Unit] =
    val logPrefix =
      s"${workItem.item.correlationId.value}::[InteractionService][processWorkItem]"

    result match
      case SubmissionResult.Success =>
        repository
          .completeAndDelete(workItem.id)
          .map { deleted =>
            logger.info(
              s"$logPrefix RSD retry succeeded for work item ${workItem.id}; deleted=$deleted"
            )
            ()
          }

      case SubmissionResult.PermanentFailure =>
        repository
          .markPermanentFailure(workItem)
          .map { updated =>
            logger.warn(
              s"$logPrefix permanent RSD failure for work item ${workItem.id}; updated=$updated"
            )
            ()
          }

      case SubmissionResult.RetryableFailure =>
        val retryNumber =
          workItem.failureCount + 1

        repository
          .markRetryableFailure(workItem)
          .map { updated =>
            if retryPolicy.isExhaustedAfterFailure(retryNumber) then
              logger.warn(
                s"$logPrefix RSD work item ${workItem.id} exhausted retry " +
                  s"$retryNumber/${retryPolicy.maxRetries}; updated=$updated"
              )
            else
              logger.warn(
                s"$logPrefix RSD retry $retryNumber/${retryPolicy.maxRetries} failed for " +
                  s"work item ${workItem.id}; next retry scheduled; updated=$updated"
              )
            ()
          }
