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

import play.api.http.Status.BAD_REQUEST
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.mocks.connectors.MockVatApiConnector
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.{BadRequestError, ErrorWrapper}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.outcomes.ResponseWrapper
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.Obligation

import scala.concurrent.Future

class VatApiServiceSpec extends UnitSpec, MockVatApiConnector:

  implicit val hc: HeaderCarrier            = HeaderCarrier()
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
      |    { "code": "VAT_TOTAL_VALUE", "message": "totalVatDue should be equal to vatDueSales + vatDueAcquisitions", "path": "/totalVatDue" }
      |  ]
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

  trait Test:
    val service = new VatApiService(mockVatApiConnector)

  "VatApiService.validate" when:

    "the connector returns a matched obligation" should:
      "pass the response through unchanged" in new Test:
        MockVatApiConnector
          .validate(vrn, validReturnBody)
          .returns(Future.successful(Right(ResponseWrapper(correlationId, obligation))))

        await(service.validate(vrn, validReturnBody)) shouldBe Right(ResponseWrapper(correlationId, obligation))

    "the connector returns a relayed validation error" should:
      "pass the ErrorWrapper through unchanged" in new Test:
        private val errorWrapper =
          ErrorWrapper(correlationId, BadRequestError, rawBody = Some(validationErrorBody), rawStatus = Some(BAD_REQUEST))

        MockVatApiConnector
          .validate(vrn, validReturnBody)
          .returns(Future.successful(Left(errorWrapper)))

        await(service.validate(vrn, validReturnBody)) shouldBe Left(errorWrapper)