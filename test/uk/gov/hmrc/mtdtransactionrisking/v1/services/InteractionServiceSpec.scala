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
import uk.gov.hmrc.mtdtransactionrisking.v1.mocks.connectors.MockInteractionConnector
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.{DownstreamError, ErrorWrapper}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.outcomes.ResponseWrapper
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.Interaction
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.{FeedbackLink, FeedbackMessage, FeedbackResponse, Obligation}
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing

import scala.concurrent.ExecutionContext.Implicits.global
import java.time.{Clock, Instant, ZoneOffset}
import scala.concurrent.{ExecutionContext, Future}

class InteractionServiceSpec extends UnitSpec, MockInteractionConnector, LogCapturing:

  implicit val hc: HeaderCarrier            = HeaderCarrier()
  implicit val correlationId: CorrelationId = CorrelationId("test-correlation-id")

  private val vrn = "123456789"
  private val now = Instant.parse("2026-08-13T09:00:00Z")
  private val clock = Clock.fixed(now, ZoneOffset.UTC)

  private val obligation = Obligation(
    periodKey = "AB12",
    start     = "2026-01-01",
    end       = "2026-03-31",
    due       = "2026-05-07",
    status    = "O",
    received  = None
  )

  private val vendorBody: JsValue = Json.parse(
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

  private val englishMessage = FeedbackMessage(
    itemNumber = "1",
    title      = "VAT title",
    body       = "VAT body",
    action     = Some("VAT action"),
    links      = Some(List(FeedbackLink("VAT", "https://www.gov.uk/vat"))),
    path       = "/guidance"
  )

  private val welshMessage = englishMessage.copy(title = "Teitl TAW", body = "Corff TAW")

  private val feedback = FeedbackResponse(
    reportId        = "f2fb30e5-4ab6-4a29-b3c1-c00000000001",
    englishFeedback = List(englishMessage),
    welshFeedback   = List(welshMessage),
    correlationId   = "E9F65715BBC9222477B27074804BBDD5C73CDE62F84D8B00CFD05B883534AF3D"
  )

  private val expectedInteraction: Interaction =
    Interaction.from(feedback, obligation, vrn, vendorBody, now)

  trait Test:
    val service = new InteractionService(mockInteractionConnector, clock)

  "store" when:

    "the connector accepts the interaction" should:
      "send it built from the feedback, obligation and vendor body" in new Test:
        MockInteractionConnector
          .store(expectedInteraction)
          .returns(Future.successful(Right(ResponseWrapper(correlationId, ()))))

        service.store(feedback, obligation, vrn, vendorBody)

    "the connector returns an error" should :
      "not propagate it to the caller" in new Test:
        MockInteractionConnector
          .store(expectedInteraction)
          .returns(Future.successful(Left(ErrorWrapper(correlationId, DownstreamError))))

        noException should be thrownBy service.store(feedback, obligation, vrn, vendorBody)