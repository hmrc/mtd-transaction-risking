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
import play.api.Configuration
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mtdtransactionrisking.config.AppConfig
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.InteractionConnector
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.{Interaction, SubmissionResult}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.SubmissionResult.*
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.{FeedbackLink, FeedbackMessage, FeedbackResponse, Obligation}
import uk.gov.hmrc.mtdtransactionrisking.v1.repositories.RsdInteractionWorkItemStore

import java.time.{Clock, Instant, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

class InteractionServiceSpec extends UnitSpec:

  private val correlationId =
    CorrelationId("test-correlation-id")

  private given CorrelationId =
    correlationId

  private given HeaderCarrier =
    HeaderCarrier()

  private val vrn =
    "123456789"

  private val now =
    Instant.parse("2026-08-13T09:00:00Z")

  private val clock =
    Clock.fixed(now, ZoneOffset.UTC)

  private val obligation =
    Obligation(
      periodKey = "AB12",
      start = "2026-01-01",
      end = "2026-03-31",
      due = "2026-05-07",
      status = "O",
      received = None
    )

  private val vendorBody: JsValue =
    Json.parse(
      """
        |{
        |  "periodKey": "AB12",
        |  "vatDueSales": 100.00,
        |  "vatDueAcquisitions": 100.00,
        |  "totalVatDue": 200.00,
        |  "vatReclaimedCurrPeriod": 100.00,
        |  "netVatDue": 100.00,
        |  "totalValueSalesExVAT": 500,
        |  "totalValuePurchasesExVAT": 500,
        |  "totalAcquisitionsExVAT": 500
        |}
        |""".stripMargin
    )

  private val englishMessage =
    FeedbackMessage(
      itemNumber = "1",
      title = "VAT title",
      body = "VAT body",
      action = Some("VAT action"),
      links = Some(
        List(
          FeedbackLink(
            "VAT",
            "https://www.gov.uk/vat"
          )
        )
      ),
      path = Some("/guidance")
    )

  private val welshMessage =
    englishMessage.copy(
      title = "Teitl TAW",
      body = "Corff TAW"
    )

  private val feedback =
    FeedbackResponse(
      reportId = "f2fb30e5-4ab6-4a29-b3c1-c00000000001",
      englishFeedback = List(englishMessage),
      welshFeedback = List(welshMessage),
      correlationId = None
    )

  private val expectedInteraction =
    Interaction.forGeneratedReport(
      feedback = feedback,
      obligation = obligation,
      vrn = vrn,
      vendorBody = vendorBody,
      occurredAt = now
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

    val service =
      new InteractionService(
        appConfig = appConfig,
        connector = connector,
        repositoryProvider = repositoryProvider,
        retryPolicy = retryPolicy,
        clock = clock
      )

    def expectConnectorResult(
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
          Json.toJson(expectedInteraction),
          expectedInteraction.feedbackId,
          expectedInteraction.eventName,
          correlationId,
          *
        )
        .returning(result)

    def expectRepository(): Unit =
      (() => repositoryProvider.get())
        .expects()
        .returning(repository)

  "InteractionService.store" should:

    "build and submit the generated-report interaction using the TxR correlation ID" in new Test:
      expectConnectorResult(
        Future.successful(SubmissionResult.Success)
      )

      service.store(
        feedback = feedback,
        obligation = obligation,
        vrn = vrn,
        vendorBody = vendorBody
      )

    "not depend on the nullable downstream response-data correlation ID" in new Test:
      expectConnectorResult(
        Future.successful(SubmissionResult.Success)
      )

      noException shouldBe thrownBy {
        service.store(
          feedback = feedback,
          obligation = obligation,
          vrn = vrn,
          vendorBody = vendorBody
        )
      }

  "InteractionService initial submission" should:

    "enqueue a retryable failure when retries are enabled" in new Test:
      val enqueueAttempted =
        Promise[Unit]()

      expectConnectorResult(
        Future.successful(SubmissionResult.RetryableFailure)
      )

      expectRepository()

      (repository.enqueueRetryableFailure(_: Interaction, _: CorrelationId))
        .expects(expectedInteraction, correlationId)
        .onCall { (_, _) =>
          enqueueAttempted.trySuccess(())
          Future.failed(
            new RuntimeException("Mongo unavailable in test")
          )
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

      expectConnectorResult(
        Future.successful(SubmissionResult.PermanentFailure)
      )

      expectRepository()

      (repository.enqueuePermanentFailure(_: Interaction, _: CorrelationId))
        .expects(expectedInteraction, correlationId)
        .onCall { (_, _) =>
          enqueueAttempted.trySuccess(())
          Future.failed(
            new RuntimeException("Mongo unavailable in test")
          )
        }

      service.store(
        feedback = feedback,
        obligation = obligation,
        vrn = vrn,
        vendorBody = vendorBody
      )

      await(enqueueAttempted.future) shouldBe ()

    "treat an unexpected initial connector failure as retryable when retries are enabled" in new Test:
      val enqueueAttempted =
        Promise[Unit]()

      expectConnectorResult(
        Future.failed(
          new IllegalStateException("unexpected connector failure")
        )
      )

      expectRepository()

      (repository.enqueueRetryableFailure(_: Interaction, _: CorrelationId))
        .expects(expectedInteraction, correlationId)
        .onCall { (_, _) =>
          enqueueAttempted.trySuccess(())
          Future.failed(
            new RuntimeException("Mongo unavailable in test")
          )
        }

      service.store(
        feedback = feedback,
        obligation = obligation,
        vrn = vrn,
        vendorBody = vendorBody
      )

      await(enqueueAttempted.future) shouldBe ()

    "still make the initial call but not enqueue a retryable failure when retries are disabled" in new Test:
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
          Json.toJson(expectedInteraction),
          expectedInteraction.feedbackId,
          expectedInteraction.eventName,
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

    "still make the initial call but not retain a permanent failure when retries are disabled" in new Test:
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
          Json.toJson(expectedInteraction),
          expectedInteraction.feedbackId,
          expectedInteraction.eventName,
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

    "not access the retry repository when retries are disabled" in new Test:
      override protected def retryEnabled: Boolean =
        false

      (() => repositoryProvider.get())
        .expects()
        .never()

      await(service.processDueWorkItems()) shouldBe ()

    "finish when retries are enabled but there are no due work items" in new Test:
      expectRepository()

      (() => appConfig.rsdRetryBatchSize)
        .expects()
        .returning(100)

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(None))

      await(service.processDueWorkItems()) shouldBe ()
