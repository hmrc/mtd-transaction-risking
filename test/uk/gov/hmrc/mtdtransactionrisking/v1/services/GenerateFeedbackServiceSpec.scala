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
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.mocks.connectors.{MockFeedbackConnector, MockInsightsConnector, MockVatApiConnector}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.{DownstreamError, ErrorWrapper}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.outcomes.ResponseWrapper
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.InsightsRequest
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class GenerateFeedbackServiceSpec extends UnitSpec, MockVatApiConnector, MockInsightsConnector, MockFeedbackConnector:
  
  implicit val hc: HeaderCarrier            = HeaderCarrier()
  implicit val correlationId: CorrelationId = CorrelationId("test-correlation-id")

  private val vrn             = "123456789"
  private val insightsRequest = InsightsRequest(vrn)

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
      start     = "2026-01-01",
      end       = "2026-03-31",
      due       = "2026-05-07",
      status    = "O",
      received  = None
    )

  private val insightsResponse: InsightsResponse =
    InsightsResponse(
      Insights(
        StrategicRisk(
          riskCorrelationId = CorrelationId("123e4567-e89b-12d3-a456-426614174000"),
          riskScore         = 12.46,
          reasons           = Seq("VRN '123456789' is 1 hops from something risky. The average VRN is 2.51 hops from something risky.")
        )
      )
    )

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
      correlationId = "a1e8057e-fbbc-47a8-a8b4-78d9f015c253"
    )

  trait Test:
    val service = new GenerateFeedbackService(
      mockVatApiConnector,
      mockInsightsConnector,
      mockFeedbackConnector
    )

  "generateFeedback" when:

    "vat-api returns an obligation and insights responds successfully" should:
      "return the insights response" in new Test:
        MockVatApiConnector
          .validate(vrn, validReturnBody)
          .returns(Future.successful(Right(ResponseWrapper(correlationId, obligation))))

        MockInsightsConnector
          .getRiskInsights(insightsRequest)
          .returns(Future.successful(Right(ResponseWrapper(correlationId, insightsResponse))))

        await(service.generateFeedback(vrn, validReturnBody)) shouldBe Right(ResponseWrapper(correlationId, insightsResponse))

    "vat-api returns an error" should:
      "pass the ErrorWrapper through without calling insights" in new Test:
        // No MockInsightsConnector expectation — insights must not be called
        MockVatApiConnector
          .validate(vrn, validReturnBody)
          .returns(Future.successful(Left(ErrorWrapper(correlationId, DownstreamError))))

        await(service.generateFeedback(vrn, validReturnBody)) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

    "insights returns an error" should:
      "pass the ErrorWrapper through" in new Test:
        MockVatApiConnector
          .validate(vrn, validReturnBody)
          .returns(Future.successful(Right(ResponseWrapper(correlationId, obligation))))

        MockInsightsConnector
          .getRiskInsights(insightsRequest)
          .returns(Future.successful(Left(ErrorWrapper(correlationId, DownstreamError))))

        await(service.generateFeedback(vrn, validReturnBody)) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

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