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

package uk.gov.hmrc.mtdtransactionrisking.v1.controllers.requestParsers

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors._
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.{FeedbackRawData, FeedbackRequest}

class FeedbackRequestParserSpec extends AnyWordSpec with Matchers {

  implicit val correlationId: String = "test-correlation-id"

  val parser = new FeedbackRequestParser

  val validVrn   = "123456789"
  val invalidVrn = "12345"

  val validBody: JsValue = Json.obj(
    "periodKey"                    -> "18AD",
    "vatDueSales"                  -> 100.00,
    "vatDueAcquisitions"           -> 100.00,
    "totalVatDue"                  -> 200.00,
    "vatReclaimedCurrPeriod"       -> 100.00,
    "netVatDue"                    -> 100.00,
    "totalValueSalesExVAT"         -> 1000,
    "totalValuePurchasesExVAT"     -> 1000,
    "totalValueGoodsSuppliedExVAT" -> 1000,
    "totalAcquisitionsExVAT"       -> 1000
  )

  val expectedRequest = FeedbackRequest(
    periodKey                    = "18AD",
    vatDueSales                  = 100.00,
    vatDueAcquisitions           = 100.00,
    totalVatDue                  = 200.00,
    vatReclaimedCurrPeriod       = 100.00,
    netVatDue                    = 100.00,
    totalValueSalesExVAT         = 1000,
    totalValuePurchasesExVAT     = 1000,
    totalValueGoodsSuppliedExVAT = 1000,
    totalAcquisitionsExVAT       = 1000
  )

  "FeedbackRequestParser" when {

    "given a valid VRN and valid body" should {
      "return a Right containing the parsed FeedbackRequest" in {
        val result = parser.parse(FeedbackRawData(validVrn, validBody))
        result shouldBe Right(expectedRequest)
      }
    }

    "given an invalid VRN" should {

      "return VrnFormatError for too short a VRN" in {
        val result = parser.parse(FeedbackRawData("12345", validBody))
        result shouldBe Left(ErrorWrapper(correlationId, VrnFormatError))
      }

      "return VrnFormatError for too long a VRN" in {
        val result = parser.parse(FeedbackRawData("1234567890", validBody))
        result shouldBe Left(ErrorWrapper(correlationId, VrnFormatError))
      }

      "return VrnFormatError for a VRN containing letters" in {
        val result = parser.parse(FeedbackRawData("12345678A", validBody))
        result shouldBe Left(ErrorWrapper(correlationId, VrnFormatError))
      }

      "return VrnFormatError for an empty VRN" in {
        val result = parser.parse(FeedbackRawData("", validBody))
        result shouldBe Left(ErrorWrapper(correlationId, VrnFormatError))
      }

      // VRN check short-circuits — body is not parsed
      "not attempt body parsing when VRN is invalid" in {
        val badBody = Json.obj("notAField" -> "garbage")
        val result  = parser.parse(FeedbackRawData(invalidVrn, badBody))
        result shouldBe Left(ErrorWrapper(correlationId, VrnFormatError))
      }
    }

    "given a valid VRN but invalid body" should {

      "return BadRequestError when body is empty" in {
        val result = parser.parse(FeedbackRawData(validVrn, Json.obj()))
        result shouldBe Left(ErrorWrapper(correlationId, BadRequestError))
      }

      "return BadRequestError when a required field is missing" in {
        val bodyMissingField = validBody.as[play.api.libs.json.JsObject] - "periodKey"
        val result           = parser.parse(FeedbackRawData(validVrn, bodyMissingField))
        result shouldBe Left(ErrorWrapper(correlationId, BadRequestError))
      }

      "return BadRequestError when a numeric field has wrong type" in {
        val badBody = validBody.as[play.api.libs.json.JsObject] ++
          Json.obj("vatDueSales" -> "notANumber")
        val result = parser.parse(FeedbackRawData(validVrn, badBody))
        result shouldBe Left(ErrorWrapper(correlationId, BadRequestError))
      }

      "return BadRequestError when body is not a JSON object" in {
        val result = parser.parse(FeedbackRawData(validVrn, Json.arr("a", "b")))
        result shouldBe Left(ErrorWrapper(correlationId, BadRequestError))
      }
    }
  }
}