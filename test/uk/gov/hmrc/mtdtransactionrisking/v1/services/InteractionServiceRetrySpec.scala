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
import com.typesafe.config.ConfigFactory
import org.bson.types.ObjectId
import org.scalamock.scalatest.MockFactory
import play.api.Configuration
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.crypto.{Crypted, PlainBytes, PlainContent, PlainText, SymmetricCryptoFactory}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem}
import uk.gov.hmrc.mtdtransactionrisking.config.{AppConfig, RsdCrypto}
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.InteractionConnector
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.{Interaction, InteractionMetadata, InteractionPayload, SubmissionResult}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.SubmissionResult.*
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.rsd.RsdInteractionWorkItem
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.{FeedbackMessage, FeedbackResponse, Obligation}
import uk.gov.hmrc.mtdtransactionrisking.v1.repositories.RsdInteractionWorkItemStore

import java.time.{Clock, Instant, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

class InteractionServiceRetrySpec extends UnitSpec, MockFactory:

  private val correlationId =
    CorrelationId("bd6c0972-34e7-11f1-9715-f35b3eb40b52")

  private given CorrelationId =
    correlationId

  private given HeaderCarrier =
    HeaderCarrier()

  private given RsdCrypto =
    new RsdCrypto:
      private val crypto =
        SymmetricCryptoFactory.aesGcmCrypto(
          "9EMrIXA5uyAUjKzd/uViY0JAH2UqIl+VFWuTug4a7Uc="
        )

      override def encrypt(plain: PlainContent): Crypted =
        crypto.encrypt(plain)

      override def decrypt(encrypted: Crypted): PlainText =
        crypto.decrypt(encrypted)

      override def decryptAsBytes(encrypted: Crypted): PlainBytes =
        crypto.decryptAsBytes(encrypted)

  private val now =
    Instant.parse("2026-10-12T10:00:00Z")

  private val clock =
    Clock.fixed(now, ZoneOffset.UTC)

  private val vrn =
    "123456789"

  private val vendorBody: JsValue =
    Json.parse(
      """
        |{
        |  "periodKey": "26A3",
        |  "vatDueSales": 100
        |}
        |""".stripMargin
    )

  private val obligation =
    Obligation(
      periodKey = "26A3",
      start = "2026-07-01",
      end = "2026-09-30",
      due = "2026-11-07",
      status = "O",
      received = None
    )

  private val englishMessage =
    FeedbackMessage(
      itemNumber = "1",
      title = "Feedback title",
      body = "Feedback body",
      action = None,
      links = None,
      path = None
    )

  private val welshMessage =
    englishMessage.copy(
      title = "Teitl adborth",
      body = "Corff adborth"
    )

  private val feedback =
    FeedbackResponse(
      reportId = "f2fb30e5-4ab6-4a29-b3c1-c00000000001",
      englishFeedback = List(englishMessage),
      welshFeedback = List(welshMessage),
      correlationId = None
    )

  private val initialInteraction =
    Interaction.forGeneratedReport(
      feedback = feedback,
      obligation = obligation,
      vrn = vrn,
      vendorBody = vendorBody,
      occurredAt = now
    )

  private val initialBody =
    Json.toJson(initialInteraction)

  private val retryInteraction =
    Interaction(
      serviceRegime = "vat-assist",
      eventName = "generate-report",
      feedbackId = "f2fb30e5-4ab6-4a29-b3c1-c00000000002",
      eventTimestamp = now.toString,
      metadata = Seq(
        InteractionMetadata(
          vrn = vrn,
          start = Some("2026-07-01"),
          end = Some("2026-09-30"),
          additionalProperties = Json.obj(
            "periodKey" -> "26A3"
          )
        )
      ),
      payload = InteractionPayload(
        reportId = "f2fb30e5-4ab6-4a29-b3c1-c00000000002",
        messages = None
      )
    )

  private val workItem =
    WorkItem(
      id = new ObjectId(),
      receivedAt = now,
      updatedAt = now,
      availableAt = now,
      status = ProcessingStatus.InProgress,
      failureCount = 0,
      item = RsdInteractionWorkItem(
        interaction = retryInteraction,
        correlationId = correlationId
      )
    )

  private def featureSwitch(
                             retryEnabled: Boolean
                           ): Option[Configuration] =
    Some(
      Configuration(
        ConfigFactory.parseString(
          s"rsd-retry.enabled = $retryEnabled"
        )
      )
    )

  private trait Test:
    protected def retryEnabled: Boolean =
      true

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

    (() => appConfig.featureSwitch)
      .expects()
      .returning(featureSwitch(retryEnabled))
      .anyNumberOfTimes()
    
    (() => appConfig.rsdRetryBatchSize)
      .expects()
      .returning(configuredBatchSize)
      .anyNumberOfTimes()

    val service =
      new InteractionService(
        appConfig = appConfig,
        connector = connector,
        repositoryProvider = repositoryProvider,
        retryPolicy = retryPolicy,
        clock = clock
      )

    def expectRepository(): Unit =
      (() => repositoryProvider.get())
        .expects()
        .returning(repository)
        .anyNumberOfTimes()

    def expectInitialStore(
                            result: Future[SubmissionResult]
                          ): Unit =
      (
        connector.store(
          _: JsValue,
          _: String,
          _: String,
          _: CorrelationId
        )(_: HeaderCarrier)
        )
        .expects(
          initialBody,
          initialInteraction.feedbackId,
          initialInteraction.eventName,
          correlationId,
          *
        )
        .returning(result)

    def expectRetryStore(
                          result: Future[SubmissionResult],
                          item: WorkItem[RsdInteractionWorkItem] = workItem
                        ): Unit =
      (
        connector.store(
          _: JsValue,
          _: String,
          _: String,
          _: CorrelationId
        )(_: HeaderCarrier)
        )
        .expects(
          item.item.body,
          item.item.feedbackId,
          item.item.eventName,
          item.item.correlationId,
          *
        )
        .returning(result)

  "InteractionService initial submission" should:

    "submit the interaction built from the feedback, obligation and vendor body" in new Test:
      expectInitialStore(
        Future.successful(SubmissionResult.Success)
      )

      service.store(
        feedback = feedback,
        obligation = obligation,
        vrn = vrn,
        vendorBody = vendorBody
      )

    "enqueue a retryable failure when retries are enabled" in new Test:
      val enqueueAttempted =
        Promise[Unit]()

      expectInitialStore(
        Future.successful(SubmissionResult.RetryableFailure)
      )
      expectRepository()

      (repository.enqueueRetryableFailure(_: Interaction, _: CorrelationId))
        .expects(initialInteraction, correlationId)
        .onCall { (_, _) =>
          enqueueAttempted.trySuccess(())
          Future.failed(new RuntimeException("Mongo unavailable in test"))
        }

      service.store(
        feedback = feedback,
        obligation = obligation,
        vrn = vrn,
        vendorBody = vendorBody
      )

      await(enqueueAttempted.future) shouldBe ()

    "enqueue a permanent failure when retries are enabled" in new Test:
      val enqueueAttempted =
        Promise[Unit]()

      expectInitialStore(
        Future.successful(SubmissionResult.PermanentFailure)
      )
      expectRepository()

      (repository.enqueuePermanentFailure(_: Interaction, _: CorrelationId))
        .expects(initialInteraction, correlationId)
        .onCall { (_, _) =>
          enqueueAttempted.trySuccess(())
          Future.failed(new RuntimeException("Mongo unavailable in test"))
        }

      service.store(
        feedback = feedback,
        obligation = obligation,
        vrn = vrn,
        vendorBody = vendorBody
      )

      await(enqueueAttempted.future) shouldBe ()

    "treat an unexpected initial connector exception as retryable when retries are enabled" in new Test:
      val enqueueAttempted =
        Promise[Unit]()

      expectInitialStore(
        Future.failed(
          new IllegalStateException("unexpected connector failure")
        )
      )
      expectRepository()

      (repository.enqueueRetryableFailure(_: Interaction, _: CorrelationId))
        .expects(initialInteraction, correlationId)
        .onCall { (_, _) =>
          enqueueAttempted.trySuccess(())
          Future.failed(new RuntimeException("Mongo unavailable in test"))
        }

      service.store(
        feedback = feedback,
        obligation = obligation,
        vrn = vrn,
        vendorBody = vendorBody
      )

      await(enqueueAttempted.future) shouldBe ()

    "still attempt the initial submission but not queue a retryable failure when retries are disabled" in new Test:
      override protected def retryEnabled: Boolean =
        false

      val connectorCalled =
        Promise[Unit]()

      (
        connector.store(
          _: JsValue,
          _: String,
          _: String,
          _: CorrelationId
        )(_: HeaderCarrier)
        )
        .expects(
          initialBody,
          initialInteraction.feedbackId,
          initialInteraction.eventName,
          correlationId,
          *
        )
        .onCall { (_, _, _, _, _) =>
          connectorCalled.trySuccess(())
          Future.successful(SubmissionResult.RetryableFailure)
        }

      service.store(
        feedback = feedback,
        obligation = obligation,
        vrn = vrn,
        vendorBody = vendorBody
      )

      await(connectorCalled.future) shouldBe ()

    "still attempt the initial submission but not retain a permanent failure when retries are disabled" in new Test:
      override protected def retryEnabled: Boolean =
        false

      val connectorCalled =
        Promise[Unit]()

      (
        connector.store(
          _: JsValue,
          _: String,
          _: String,
          _: CorrelationId
        )(_: HeaderCarrier)
        )
        .expects(
          initialBody,
          initialInteraction.feedbackId,
          initialInteraction.eventName,
          correlationId,
          *
        )
        .onCall { (_, _, _, _, _) =>
          connectorCalled.trySuccess(())
          Future.successful(SubmissionResult.PermanentFailure)
        }

      service.store(
        feedback = feedback,
        obligation = obligation,
        vrn = vrn,
        vendorBody = vendorBody
      )

      await(connectorCalled.future) shouldBe ()

  "InteractionService.processDueWorkItems" should:

    "not access the repository when retries are disabled" in new Test:
      override protected def retryEnabled: Boolean =
        false

      (() => repositoryProvider.get())
        .expects()
        .never()

      await(service.processDueWorkItems()) shouldBe ()

    "finish when retries are enabled but there are no due items" in new Test:
      expectRepository()

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(None))

      await(service.processDueWorkItems()) shouldBe ()

    "delete a work item after a successful retry" in new Test:
      expectRepository()

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(Some(workItem)))
        .once()

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(None))
        .once()

      expectRetryStore(
        Future.successful(SubmissionResult.Success)
      )

      (repository.completeAndDelete(_: ObjectId))
        .expects(workItem.id)
        .returning(Future.successful(true))

      await(service.processDueWorkItems()) shouldBe ()

    "mark a work item permanently failed after a permanent retry result" in new Test:
      expectRepository()

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(Some(workItem)))
        .once()

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(None))
        .once()

      expectRetryStore(
        Future.successful(SubmissionResult.PermanentFailure)
      )

      (repository.markPermanentFailure(_: WorkItem[RsdInteractionWorkItem]))
        .expects(workItem)
        .returning(Future.successful(true))

      await(service.processDueWorkItems()) shouldBe ()

    "reschedule a work item after a retryable result" in new Test:
      expectRepository()

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(Some(workItem)))
        .once()

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(None))
        .once()

      expectRetryStore(
        Future.successful(SubmissionResult.RetryableFailure)
      )

      (retryPolicy.isExhaustedAfterFailure(_: Int))
        .expects(1)
        .returning(false)

      (() => retryPolicy.maxRetries)
        .expects()
        .returning(10)

      (repository.markRetryableFailure(_: WorkItem[RsdInteractionWorkItem]))
        .expects(workItem)
        .returning(Future.successful(true))

      await(service.processDueWorkItems()) shouldBe ()

    "mark the tenth failed retry as exhausted" in new Test:
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

      expectRetryStore(
        result = Future.successful(SubmissionResult.RetryableFailure),
        item = retryTenWorkItem
      )

      (retryPolicy.isExhaustedAfterFailure(_: Int))
        .expects(10)
        .returning(true)

      (() => retryPolicy.maxRetries)
        .expects()
        .returning(10)

      (repository.markRetryableFailure(_: WorkItem[RsdInteractionWorkItem]))
        .expects(retryTenWorkItem)
        .returning(Future.successful(true))

      await(service.processDueWorkItems()) shouldBe ()

    "respect the configured work-item batch size" in new Test:
      override protected def configuredBatchSize: Int =
        1

      expectRepository()

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(Some(workItem)))
        .once()

      expectRetryStore(
        Future.successful(SubmissionResult.Success)
      )

      (repository.completeAndDelete(_: ObjectId))
        .expects(workItem.id)
        .returning(Future.successful(true))
      
      await(service.processDueWorkItems()) shouldBe ()
