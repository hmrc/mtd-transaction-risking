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
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.mocks.connectors.{MockFeedbackConnector, MockInsightsConnector, MockRdsConnector, MockVatApiConnector}
import uk.gov.hmrc.mtdtransactionrisking.v1.mocks.services.{MockInteractionService, MockRdsAuthService}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.auth.RdsAuthCredentials
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.{DownstreamError, ErrorWrapper, ServiceUnavailableError}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.outcomes.ResponseWrapper
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.InsightsRequest
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.*
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

class GenerateFeedbackServiceSpec
    extends UnitSpec,
      MockVatApiConnector,
      MockInsightsConnector,
      MockFeedbackConnector,
      MockRdsConnector,
      MockRdsAuthService,
      MockInteractionService:

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val correlationId: CorrelationId = CorrelationId("test-correlation-id")

  private val vrn = "123456789"
  private val insightsRequest = InsightsRequest(vrn)
  private val agentReferenceNumber = Some("LARN0085901")

  private val requestHeaders: Seq[(String, String)] = Seq(
    "Gov-Client-Timezone" -> "UTC+00:00",
    "Accept" -> "application/vnd.hmrc.1.0+json"
  )

  private val validReturnBody: JsValue = Json.parse(
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
      |  "totalValueGoodsSuppliedExVAT": 500,
      |  "totalAcquisitionsExVAT": 500
      |}
      |""".stripMargin
  )

  private val obligation: Obligation =
    Obligation(
      periodKey = "AB12",
      start = "2026-01-01",
      end = "2026-03-31",
      due = "2026-05-07",
      status = "O",
      received = None
    )

  private val insightsResponse: InsightsResponse =
    InsightsResponse(
      Insights(
        StrategicRisk(
          riskCorrelationId = CorrelationId("123e4567-e89b-12d3-a456-426614174000"),
          riskScore = 12.46,
          reasons = Seq("VRN '123456789' is 1 hops from something risky.")
        )
      )
    )

  private val credentials = RdsAuthCredentials("a-bearer-token", "bearer", 14399)

  private val feedbackResponse: FeedbackResponse =
    FeedbackResponse(
      reportId = "f2fb30e5-4ab6-4a29-b3c1-c00000000001",
      englishFeedback = List(
        FeedbackMessage(
          itemNumber = "1",
          title = "VAT title",
          body = "VAT body",
          action = Some("VAT action"),
          links = Some(List(FeedbackLink(title = "VAT", url = "https://www.gov.uk/vat"))),
          path = "/guidance"
        )),
      welshFeedback = List(
        FeedbackMessage(
          itemNumber = "1",
          title = "Teitl TAW",
          body = "Corff TAW",
          action = Some("Gweithred TAW"),
          links = Some(List(FeedbackLink(title = "TAW", url = "https://www.gov.uk/vat"))),
          path = "/guidance"
        )),
      correlationId = "E9F65715BBC9222477B27074804BBDD5C73CDE62F84D8B00CFD05B883534AF3D"
    )

  trait Test:
    val service = new GenerateFeedbackService(
      mockRdsAuthService,
      mockInteractionService,
      mockVatApiConnector,
      mockInsightsConnector,
      mockFeedbackConnector,
      mockRdsConnector
    )

    def vatApiReturnsObligation(): Unit =
      MockVatApiConnector
        .validate(vrn, validReturnBody)
        .returns(Future.successful(Right(ResponseWrapper(correlationId, obligation))))

    def insightsSucceed(): Unit =
      MockInsightsConnector
        .getRiskInsights(insightsRequest)
        .returns(Future.successful(Right(ResponseWrapper(correlationId, insightsResponse))))

    def authSucceeds(): Unit =
      MockRdsAuthService.bearerToken
        .returns(Future.successful(Right(ResponseWrapper(correlationId, Some(credentials)))))

    def interactionIsStored(): Unit =
      MockInteractionService
        .store(feedbackResponse, obligation, vrn, validReturnBody)
        .returns(())

    def generate(body: JsValue = validReturnBody): Future[ServiceOutcome[FeedbackResponse]] =
      service.generateFeedback(vrn, body, agentReferenceNumber, requestHeaders)

  "generateFeedback" when:

    "every downstream responds successfully" should:
      "return the feedback report and store the interaction" in new Test:
        vatApiReturnsObligation()
        insightsSucceed()
        authSucceeds()

        MockRdsConnector.generateReport(vrn).returns(Future.successful(Right(ResponseWrapper(correlationId, feedbackResponse))))

        interactionIsStored()

        await(generate()) shouldBe Right(ResponseWrapper(correlationId, feedbackResponse))

    "vat-api returns an error" should:
      "pass the ErrorWrapper through without calling insights or storing an interaction" in new Test:
        // No further expectations so nothing downstream of validation should be called
        MockVatApiConnector
          .validate(vrn, validReturnBody)
          .returns(Future.successful(Left(ErrorWrapper(correlationId, DownstreamError))))

        await(generate()) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

    "insights returns an error" should:
      "pass the ErrorWrapper through without calling RDS or storing an interaction" in new Test:
        vatApiReturnsObligation()

        MockInsightsConnector
          .getRiskInsights(insightsRequest)
          .returns(Future.successful(Left(ErrorWrapper(correlationId, DownstreamError))))

        await(generate()) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

    "the validated body is missing a mandatory VAT figure" should:
      "return DownstreamError without calling RDS or storing an interaction" in new Test:
        val incompleteBody: JsValue = validReturnBody.as[JsObject] - "vatDueSales"

        MockVatApiConnector
          .validate(vrn, incompleteBody)
          .returns(Future.successful(Right(ResponseWrapper(correlationId, obligation))))

        insightsSucceed()

        await(generate(incompleteBody)) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

    "the bearer token cannot be obtained" should:
      "pass the ErrorWrapper through without calling RDS or storing an interaction" in new Test:
        vatApiReturnsObligation()
        insightsSucceed()

        MockRdsAuthService.bearerToken.returns(Future.successful(Left(ErrorWrapper(correlationId, DownstreamError))))

        await(generate()) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

    "RDS returns an error" should:
      "pass the ErrorWrapper through without storing an interaction" in new Test:
        vatApiReturnsObligation()
        insightsSucceed()
        authSucceeds()

        MockRdsConnector.generateReport(vrn).returns(Future.successful(Left(ErrorWrapper(correlationId, ServiceUnavailableError))))

        await(generate()) shouldBe Left(ErrorWrapper(correlationId, ServiceUnavailableError))

  "requestStubFeedback" when:

    "the feedback stub responds successfully" should:
      "return the feedback response" in new Test:
        MockFeedbackConnector
          .requestFeedback(insightsRequest)
          .returns(Future.successful(Right(ResponseWrapper(correlationId, feedbackResponse))))

        await(service.requestStubFeedback(vrn)) shouldBe Right(ResponseWrapper(correlationId, feedbackResponse))

    "the feedback stub returns an error" should:
      "pass the ErrorWrapper through" in new Test:
        MockFeedbackConnector
          .requestFeedback(insightsRequest)
          .returns(Future.successful(Left(ErrorWrapper(correlationId, DownstreamError))))

        await(service.requestStubFeedback(vrn)) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))
