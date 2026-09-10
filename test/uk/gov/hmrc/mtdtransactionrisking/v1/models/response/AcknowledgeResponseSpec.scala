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

package uk.gov.hmrc.mtdtransactionrisking.v1.models.response

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsSuccess, Json}

class AcknowledgeResponseSpec extends AnyWordSpec with Matchers:

  private val response = AcknowledgeResponse(
    vrn = Some("123456789"),
    feedbackId = Some("f2fb30e5-4ab6-4a29-b3c1-c00000000001"),
    createdDttm = Some("2026-06-09T10:30:00Z"),
    responseCode = Some(202),
    responseMessage = Some("Accepted")
  )

  "AcknowledgeResponse" when {
    "serialising to JSON" should {
      "produce the RDS acknowledge response fields" in {
        val json = Json.toJson(response)

        (json \ "vrn").as[String] shouldBe "123456789"
        (json \ "feedbackId").as[String] shouldBe "f2fb30e5-4ab6-4a29-b3c1-c00000000001"
        (json \ "createdDttm").as[String] shouldBe "2026-06-09T10:30:00Z"
        (json \ "responseCode").as[Int] shouldBe 202
        (json \ "responseMessage").as[String] shouldBe "Accepted"
      }
    }

    "deserialising from JSON" should {
      "round-trip correctly" in {
        val json = Json.toJson(response)
        val result = json.validate[AcknowledgeResponse]

        result shouldBe JsSuccess(response)
      }
    }
  }

  "AcknowledgeResponseWrapper" when {
    "serialising to JSON" should {
      "wrap the output object correctly" in {
        val wrapper = AcknowledgeResponseWrapper(output = response)
        val json = Json.toJson(wrapper)

        (json \ "output" \ "responseCode").as[Int] shouldBe 202
        (json \ "output" \ "responseMessage").as[String] shouldBe "Accepted"
      }
    }

    "deserialising from JSON" should {
      "round-trip correctly" in {
        val wrapper = AcknowledgeResponseWrapper(output = response)
        val json = Json.toJson(wrapper)
        val result = json.validate[AcknowledgeResponseWrapper]

        result shouldBe JsSuccess(wrapper)
      }
    }
  }
