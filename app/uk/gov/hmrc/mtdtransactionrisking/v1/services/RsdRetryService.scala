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

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.workitem.WorkItem
import uk.gov.hmrc.mtdtransactionrisking.config.AppConfig
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.utils.Logging
import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.InteractionConnector
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.Interaction
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.rsd.{RsdInteractionWorkItem, RsdSubmissionResult}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.rsd.RsdSubmissionResult.*
import uk.gov.hmrc.mtdtransactionrisking.v1.repositories.RsdInteractionWorkItemStore

import javax.inject.{Inject, Provider, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class RsdRetryService @Inject()(
                                 appConfig: AppConfig,
                                 connector: InteractionConnector,
                                 repositoryProvider: Provider[RsdInteractionWorkItemStore],
                                 retryPolicy: RsdRetryPolicy
                               )(using ec: ExecutionContext)
  extends Logging:

  private def repository: RsdInteractionWorkItemStore =
    repositoryProvider.get()

  def submitInitial(
                     interaction: Interaction,
                     correlationId: CorrelationId
                   )(implicit hc: HeaderCarrier): Unit =

    connector
      .store(
        interaction = interaction,
        correlationId = correlationId
      )
      .flatMap {
        case Success =>
          Future.unit

        case RetryableFailure =>
          repository
            .enqueueRetryableFailure(
              interaction = interaction,
              correlationId = correlationId
            )
            .map { workItem =>
              logger.warn(
                s"${correlationId.value}::[RsdRetryService][submitInitial] " +
                  s"retryable RSD failure for ${interaction.eventName}, " +
                  s"feedbackId ${interaction.feedbackId}; " +
                  s"work item ${workItem.id} scheduled for ${workItem.availableAt}"
              )
              ()
            }

        case PermanentFailure =>
          repository
            .enqueuePermanentFailure(
              interaction = interaction,
              correlationId = correlationId
            )
            .map { workItem =>
              logger.warn(
                s"${correlationId.value}::[RsdRetryService][submitInitial] " +
                  s"permanent RSD failure for ${interaction.eventName}, " +
                  s"feedbackId ${interaction.feedbackId}; " +
                  s"work item ${workItem.id} retained"
              )
              ()
            }
      }
      .recover {
        case NonFatal(error) =>
          logger.error(
            s"${correlationId.value}::[RsdRetryService][submitInitial] " +
              s"failed to persist RSD delivery result for ${interaction.eventName}; " +
              "VAT Assist response is unaffected",
            error
          )
          ()
      }

  def processDueWorkItems(): Future[Unit] =

    def processNext(
                     processedCount: Int
                   ): Future[Unit] =
      if processedCount >= appConfig.rsdRetryBatchSize then
        logger.info(
          s"[RsdRetryService][processDueWorkItems] reached configured retry batch " +
            s"size ${appConfig.rsdRetryBatchSize}"
        )
        Future.unit
      else
        repository
          .pullDue()
          .flatMap {
            case None =>
              Future.unit

            case Some(workItem) =>
              processWorkItem(workItem)
                .flatMap(
                  _ => processNext(processedCount + 1)
                )
          }

    processNext(processedCount = 0)

  private def processWorkItem(
                               workItem: WorkItem[RsdInteractionWorkItem]
                             ): Future[Unit] =
    given HeaderCarrier = HeaderCarrier()
    connector
      .store(
        interaction = workItem.item.interaction,
        correlationId = workItem.item.correlationId
      )
      .recover {
        case NonFatal(error) =>
          logger.error(
            s"${workItem.item.correlationId.value}::[RsdRetryService][processWorkItem] " +
              s"unexpected RSD retry processing failure for work item ${workItem.id}; " +
              "treating as permanent",
            error
          )
          PermanentFailure
      }
      .flatMap {
        case Success =>
          repository
            .completeAndDelete(workItem.id)
            .map { deleted =>
              logger.info(
                s"${workItem.item.correlationId.value}::[RsdRetryService][processWorkItem] " +
                  s"RSD retry succeeded for work item ${workItem.id}; deleted=$deleted"
              )
              ()
            }

        case PermanentFailure =>
          repository
            .markPermanentFailure(workItem)
            .map { updated =>
              logger.warn(
                s"${workItem.item.correlationId.value}::[RsdRetryService][processWorkItem] " +
                  s"permanent RSD failure for work item ${workItem.id}; updated=$updated"
              )
              ()
            }

        case RetryableFailure =>
          val retryNumber =
            workItem.failureCount + 1

          repository
            .markRetryableFailure(workItem)
            .map { updated =>
              if retryPolicy.isExhaustedAfterFailure(retryNumber) then
                logger.warn(
                  s"${workItem.item.correlationId.value}::[RsdRetryService][processWorkItem] " +
                    s"RSD work item ${workItem.id} exhausted retry " +
                    s"$retryNumber/${retryPolicy.maxRetries}; updated=$updated"
                )
              else
                logger.warn(
                  s"${workItem.item.correlationId.value}::[RsdRetryService][processWorkItem] " +
                    s"RSD retry $retryNumber/${retryPolicy.maxRetries} failed for " +
                    s"work item ${workItem.id}; next retry scheduled; updated=$updated"
                )
              ()
            }
      }
