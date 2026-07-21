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

import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.VatApiConnector
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.{BadRequestError, ErrorWrapper}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.outcomes.ResponseWrapper

import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

class VatApiServiceSpec extends AnyWordSpec, Matchers, MockitoSugar:

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val correlationId: CorrelationId = CorrelationId("test-correlation-id")

  private val vrn: String = "123456789"

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

  private def await[T](f: Future[T]): T = Await.result(f, 5.seconds)

  class Test:
    val mockConnector: VatApiConnector = mock[VatApiConnector]
    val service = new VatApiService(mockConnector)

  "VatApiService" when:

    "validate is called and the return is valid" must:
      "return Right(()) — validation passed" in new Test:
        when(mockConnector.validate(eqTo(vrn), eqTo(validReturnBody))(any(), any()))
          .thenReturn(Future.successful(Right(ResponseWrapper(correlationId, ()))))

        val result: ServiceOutcome[Unit] = await(service.validate(vrn, validReturnBody))
        result shouldBe Right(ResponseWrapper(correlationId, ()))

    "validate is called and the return fails validation" must:
      "return Left with the relayed error wrapper" in new Test:
        val errorWrapper = ErrorWrapper(correlationId, BadRequestError, rawBody = Some(validationErrorBody), rawStatus = Some(400))

        when(mockConnector.validate(eqTo(vrn), eqTo(validReturnBody))(any(), any()))
          .thenReturn(Future.successful(Left(errorWrapper)))

        val result: ServiceOutcome[Unit] = await(service.validate(vrn, validReturnBody))
        result shouldBe Left(errorWrapper)
