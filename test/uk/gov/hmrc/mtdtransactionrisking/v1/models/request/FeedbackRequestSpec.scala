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

package uk.gov.hmrc.mtdtransactionrisking.v1.models.request

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, JsSuccess, Json}

class FeedbackRequestSpec extends AnyWordSpec with Matchers:

  val validJson: JsObject = Json.obj(
    "periodKey" -> "18AD",
    "vatDueSales" -> 100.00,
    "vatDueAcquisitions" -> 100.00,
    "totalVatDue" -> 200.00,
    "vatReclaimedCurrPeriod" -> 100.00,
    "netVatDue" -> 100.00,
    "totalValueSalesExVAT" -> 1000,
    "totalValuePurchasesExVAT" -> 1000,
    "totalValueGoodsSuppliedExVAT" -> 1000,
    "totalAcquisitionsExVAT" -> 1000
  )

  val expectedModel = FeedbackRequest(
    periodKey = "18AD",
    vatDueSales = 100.00,
    vatDueAcquisitions = 100.00,
    totalVatDue = 200.00,
    vatReclaimedCurrPeriod = 100.00,
    netVatDue = 100.00,
    totalValueSalesExVAT = 1000,
    totalValuePurchasesExVAT = 1000,
    totalValueGoodsSuppliedExVAT = 1000,
    totalAcquisitionsExVAT = 1000
  )

  "FeedbackRequest" when {

    "reading from valid JSON" should {
      "deserialise correctly" in {
        validJson.validate[FeedbackRequest] shouldBe JsSuccess(expectedModel)
      }
    }

    "reading from JSON with a missing field" should {
      "return a JsError" in {
        val json = validJson.as[play.api.libs.json.JsObject] - "periodKey"
        val result = json.validate[FeedbackRequest]
        result.isError shouldBe true
      }
    }

    "reading from JSON with a wrong type" should {
      "return a JsError" in {
        val json = validJson.as[play.api.libs.json.JsObject] ++
          Json.obj("vatDueSales" -> "notANumber")
        val result = json.validate[FeedbackRequest]
        result.isError shouldBe true
      }
    }
  }
