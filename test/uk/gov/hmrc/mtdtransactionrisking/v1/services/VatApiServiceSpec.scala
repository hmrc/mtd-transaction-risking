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

import controllers.Execution.trampoline
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.when
import org.scalatest.EitherValues.convertLeftProjectionToValuable
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.{JsObject, JsValue, Json}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.VatApiConnector
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.{BadRequestError, DownstreamError, ErrorWrapper, ServiceUnavailableError, TaxPeriodNotEndedError}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.outcomes.ResponseWrapper
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.{Obligation, ObligationPeriod, ObligationsResponse}

import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}
import java.time.LocalDate
class VatApiServiceSpec extends AnyWordSpec, Matchers, MockitoSugar:

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val correlationId: CorrelationId = CorrelationId("test-correlation-id")

  private val vrn: String = "123456789"
  private val periodKey: String = "AB12"

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

  private val validationErrorBody: JsValue = Json.parse(
    """
      |{
      |  "code": "INVALID_REQUEST",
      |  "message": "Invalid request",
      |  "errors": [
      |    { "code": "VAT_TOTAL_VALUE", "message": "...", "path": "/totalVatDue" }
      |  ]
      |}
      |""".stripMargin
  )

  private val matchedObligation: Obligation =
    Obligation(
      status = "O",
      start = "2026-01-01",
      end = "2026-03-31",
      due = "2026-05-07",
      periodKey = "AB12"
    )
  private def await[T](f: Future[T]): T = Await.result(f, 5.seconds)

  class Test:
    val mockConnector: VatApiConnector = mock[VatApiConnector]
    val service = new VatApiService(mockConnector)

  "VatApiService" when:
    "validate is called and the return is valid" must:
      "relay the connector response" in new Test:
        when(mockConnector.validate(eqTo(vrn), eqTo(validReturnBody))(any(), any()))
          .thenReturn(Future.successful(Right(ResponseWrapper(correlationId, matchedObligation))))

        val result: ServiceOutcome[Obligation] = await(service.validate(vrn, validReturnBody))
        result shouldBe Right(ResponseWrapper(correlationId, matchedObligation))

    "getObligationPeriod is called with matching period key and past end date" must:
      "return Right(ObligationPeriod)" in new Test:
        val obligations = ObligationsResponse(Seq(matchedObligation.copy(end = "2020-03-31")))
        when(mockConnector.getObligations(eqTo(vrn))(any(), any()))
          .thenReturn(Future.successful(Right(ResponseWrapper(correlationId, obligations))))

        val result: ServiceOutcome[ObligationPeriod] = await(service.getObligationPeriod(periodKey, vrn, today = LocalDate.parse("2020-04-01")))
        result shouldBe Right(ResponseWrapper(correlationId, ObligationPeriod("2026-01-01", "2020-03-31")))

    "getObligationPeriod is called with matching period key and end date today/future" must:
      "return TAX_PERIOD_NOT_ENDED" in new Test:
        val obligations = ObligationsResponse(Seq(matchedObligation.copy(end = "2030-03-31")))
        when(mockConnector.getObligations(eqTo(vrn))(any(), any()))
          .thenReturn(Future.successful(Right(ResponseWrapper(correlationId, obligations))))

        val result: ServiceOutcome[ObligationPeriod] = await(service.getObligationPeriod(periodKey, vrn, today = LocalDate.parse("2030-03-31")))
        result shouldBe Left(ErrorWrapper(correlationId, TaxPeriodNotEndedError))

    "getObligationPeriod is called with no matching period key" must:
      "return DownstreamError" in new Test:
        val obligations = ObligationsResponse(Seq(matchedObligation.copy(periodKey = "ZZ99")))
        when(mockConnector.getObligations(eqTo(vrn))(any(), any()))
          .thenReturn(Future.successful(Right(ResponseWrapper(correlationId, obligations))))

        val result: ServiceOutcome[ObligationPeriod] = await(service.getObligationPeriod(periodKey, vrn, today = LocalDate.parse("2020-04-01")))
        result shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

    "getObligationPeriod connector error" must:
      "map to ServiceUnavailableError and preserve raw body" in new Test:
        val rawBody: JsObject = Json.obj("code" -> "ANY_DOWNSTREAM_ERROR")
        when(mockConnector.getObligations(eqTo(vrn))(any(), any()))
          .thenReturn(Future.successful(Left(ErrorWrapper(correlationId, DownstreamError, rawBody = Some(rawBody), rawStatus = Some(400)))))

        val result: ServiceOutcome[ObligationPeriod] = await(service.getObligationPeriod(periodKey, vrn))
        result shouldBe Left(ErrorWrapper(correlationId, ServiceUnavailableError))

    "validate is called and the return fails validation" must:
      "return Left with the relayed error wrapper" in new Test:
        val errorWrapper = ErrorWrapper(correlationId, BadRequestError, rawBody = Some(validationErrorBody), rawStatus = Some(400))

        when(mockConnector.validate(eqTo(vrn), eqTo(validReturnBody))(any(), any()))
          .thenReturn(Future.successful(Left(errorWrapper)))

        val result: ServiceOutcome[Obligation] = await(service.validate(vrn, validReturnBody))
        result shouldBe Left(errorWrapper)
