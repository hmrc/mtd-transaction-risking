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

import com.google.inject.Provider
import org.bson.types.ObjectId
import org.scalamock.handlers.CallHandler
import org.scalamock.scalatest.MockFactory
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem}
import uk.gov.hmrc.mtdtransactionrisking.config.AppConfig
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.InteractionConnector
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.{Interaction, InteractionMetadata, InteractionPayload}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.rsd.{RsdInteractionWorkItem, RsdSubmissionResult}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.rsd.RsdSubmissionResult.*
import uk.gov.hmrc.mtdtransactionrisking.v1.repositories.RsdInteractionWorkItemStore

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

class RsdRetryServiceSpec extends UnitSpec, MockFactory:

  private val correlationId =
    CorrelationId("bd6c0972-34e7-11f1-9715-f35b3eb40b52")

  private val interaction =
    Interaction(
      serviceRegime = "vat-assist",
      eventName = "generate-report",
      feedbackId = "f2fb30e5-4ab6-4a29-b3c1-c00000000001",
      eventTimestamp = "2026-10-12T10:00:00Z",
      metadata = Seq(
        InteractionMetadata(
          vrn = "123456789",
          start = Some("2026-07-01"),
          end = Some("2026-09-30"),
          additionalProperties = Json.obj(
            "periodKey" -> "26A3"
          )
        )
      ),
      payload = InteractionPayload(
        reportId = "f2fb30e5-4ab6-4a29-b3c1-c00000000001",
        messages = None
      )
    )

  private val workItem =
    WorkItem(
      id = new ObjectId(),
      receivedAt = Instant.parse("2026-10-12T10:00:00Z"),
      updatedAt = Instant.parse("2026-10-12T10:00:00Z"),
      availableAt = Instant.parse("2026-10-12T10:05:00Z"),
      status = ProcessingStatus.InProgress,
      failureCount = 0,
      item = RsdInteractionWorkItem(
        interaction = interaction,
        correlationId = correlationId
      )
    )

  private trait Test:
    protected def configuredBatchSize: Int =
      100

    val appConfig: AppConfig =
      mock[AppConfig]

    val connector: InteractionConnector =
      mock[InteractionConnector]

    val repository: RsdInteractionWorkItemStore =
      mock[RsdInteractionWorkItemStore]

    val repositoryProvider: Provider[RsdInteractionWorkItemStore] =
      mock[Provider[RsdInteractionWorkItemStore]]

    val retryPolicy: RsdRetryPolicy =
      mock[RsdRetryPolicy]

    (() => appConfig.rsdRetryBatchSize)
      .expects()
      .returning(configuredBatchSize)
      .anyNumberOfTimes()

    (() => retryPolicy.maxRetries)
      .expects()
      .returning(10)
      .anyNumberOfTimes()

    val service: RsdRetryService =
      new RsdRetryService(
        appConfig = appConfig,
        connector = connector,
        repositoryProvider = repositoryProvider,
        retryPolicy = retryPolicy
      )

    def expectRepository(): Unit =
      (() => repositoryProvider.get())
        .expects()
        .returning(repository)
        .anyNumberOfTimes()

    def expectConnectorResult(
                               result: RsdSubmissionResult
                             ): CallHandler[Future[RsdSubmissionResult]] =
      (
        connector
          .store(_: Interaction, _: CorrelationId)(_: HeaderCarrier)
        )
        .expects(interaction, correlationId, *)
        .returning(Future.successful(result))

  "RsdRetryService.submitInitial" should :

    "not persist an item when the initial RSD submission succeeds" in new Test:
      expectConnectorResult(Success)

      service.submitInitial(
        interaction = interaction,
        correlationId = correlationId
      )

    "persist a permanently failed item after an initial permanent failure" in new Test:
      val persisted =
        Promise[Unit]()

      expectConnectorResult(PermanentFailure)

      expectRepository()

      (repository.enqueuePermanentFailure(_: Interaction, _: CorrelationId))
        .expects(interaction, correlationId)
        .onCall { (_, _) =>
          persisted.success(())
          Future.successful(workItem)
        }

      service.submitInitial(
        interaction = interaction,
        correlationId = correlationId
      )

      await(persisted.future) shouldBe()

    "persist a retry item after an initial retryable failure" in new Test:
      val persisted =
        Promise[Unit]()

      expectConnectorResult(RetryableFailure)

      expectRepository()

      (repository.enqueueRetryableFailure(_: Interaction, _: CorrelationId))
        .expects(interaction, correlationId)
        .onCall { (_, _) =>
          persisted.success(())
          Future.successful(workItem)
        }

      service.submitInitial(
        interaction = interaction,
        correlationId = correlationId
      )

      await(persisted.future) shouldBe()

    "not throw to the customer flow if persistence fails" in new Test:
      val persistenceAttempted =
        Promise[Unit]()

      expectConnectorResult(RetryableFailure)

      expectRepository()

      (repository.enqueueRetryableFailure(_: Interaction, _: CorrelationId))
        .expects(interaction, correlationId)
        .onCall { (_, _) =>
          persistenceAttempted.success(())
          Future.failed(
            new RuntimeException("Mongo unavailable")
          )
        }

      noException shouldBe thrownBy {
        service.submitInitial(
          interaction = interaction,
          correlationId = correlationId
        )
      }

      await(persistenceAttempted.future) shouldBe()

  "RsdRetryService.processDueWorkItems" should :

    "finish without calling the connector when there are no due items" in new Test:
      expectRepository()

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(None))

      await(
        service.processDueWorkItems()
      ) shouldBe()

    "delete an item after a successful retry" in new Test:
      expectRepository()

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(Some(workItem)))
        .once()

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(None))
        .once()

      expectConnectorResult(Success)

      (repository.completeAndDelete(_: ObjectId))
        .expects(workItem.id)
        .returning(Future.successful(true))

      await(
        service.processDueWorkItems()
      ) shouldBe()

    "reuse the stored Interaction and outbound CorrelationId on retry" in new Test:
      expectRepository()

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(Some(workItem)))
        .once()

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(None))
        .once()

      (
        connector
          .store(_: Interaction, _: CorrelationId)(_: HeaderCarrier)
        )
        .expects(
          workItem.item.interaction,
          workItem.item.correlationId,
          *
        )
        .returning(Future.successful(Success))

      (repository.completeAndDelete(_: ObjectId))
        .expects(workItem.id)
        .returning(Future.successful(true))

      await(
        service.processDueWorkItems()
      ) shouldBe()

    "mark an item permanently failed after a permanent retry result" in new Test:
      expectRepository()

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(Some(workItem)))
        .once()

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(None))
        .once()

      expectConnectorResult(PermanentFailure)

      (repository.markPermanentFailure(_: WorkItem[RsdInteractionWorkItem]))
        .expects(workItem)
        .returning(Future.successful(true))

      await(
        service.processDueWorkItems()
      ) shouldBe()

    "reschedule an item when retry one fails retryably" in new Test:
      expectRepository()

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(Some(workItem)))
        .once()

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(None))
        .once()

      expectConnectorResult(RetryableFailure)

      (retryPolicy.isExhaustedAfterFailure(_: Int))
        .expects(1)
        .returning(false)

      (repository.markRetryableFailure(_: WorkItem[RsdInteractionWorkItem]))
        .expects(workItem)
        .returning(Future.successful(true))

      await(
        service.processDueWorkItems()
      ) shouldBe()

    "mark retry ten permanently failed without scheduling retry eleven" in new Test:
      val retryTenWorkItem =
        workItem.copy(failureCount = 9)

      expectRepository()

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(Some(retryTenWorkItem)))
        .once()

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(None))
        .once()

      (
        connector
          .store(_: Interaction, _: CorrelationId)(_: HeaderCarrier)
        )
        .expects(
          retryTenWorkItem.item.interaction,
          retryTenWorkItem.item.correlationId,
          *
        )
        .returning(Future.successful(RetryableFailure))

      (retryPolicy.isExhaustedAfterFailure(_: Int))
        .expects(10)
        .returning(true)

      (repository.markRetryableFailure(_: WorkItem[RsdInteractionWorkItem]))
        .expects(retryTenWorkItem)
        .returning(Future.successful(true))

      await(
        service.processDueWorkItems()
      ) shouldBe()

    "respect the configured batch size" in new Test:
      override protected def configuredBatchSize: Int =
        1

      expectRepository()

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(Some(workItem)))
        .once()

      (
        connector
          .store(_: Interaction, _: CorrelationId)(_: HeaderCarrier)
        )
        .expects(
          workItem.item.interaction,
          workItem.item.correlationId,
          *
        )
        .returning(Future.successful(Success))

      (repository.completeAndDelete(_: ObjectId))
        .expects(workItem.id)
        .returning(Future.successful(true))

      await(
        service.processDueWorkItems()
      ) shouldBe ()
