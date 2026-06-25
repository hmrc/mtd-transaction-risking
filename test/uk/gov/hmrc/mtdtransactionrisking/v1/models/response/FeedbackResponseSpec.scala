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

class FeedbackResponseSpec extends AnyWordSpec with Matchers {

  val link    = FeedbackLink(title = "VAT", url = "https://www.gov.uk/vat")
  val message = FeedbackMessage(
    itemNumber = "1",
    title      = "VAT title",
    body       = "VAT body",
    action     = Some("VAT action"),
    links      = Some(List(link)),
    path       = "/guidance"
  )
  val messageNoOptionals = FeedbackMessage(
    itemNumber = "2",
    title      = "VAT title",
    body       = "VAT body",
    action     = None,
    links      = None,
    path       = "/guidance"
  )

  val response = FeedbackResponse(
    reportId        = "f2fb30e5-4ab6-4a29-b3c1-c00000000001",
    englishFeedback = List(message),
    welshFeedback   = List(message),
    correlationId   = "a1e8057e-fbbc-47a8-a8b4-78d9f015c253"
  )

  "FeedbackResponse" when {

    "serialising to JSON" should {
      "produce the correct structure" in {
        val json = Json.toJson(response)
        (json \ "reportId").as[String]      shouldBe "f2fb30e5-4ab6-4a29-b3c1-c00000000001"
        (json \ "correlationId").as[String] shouldBe "a1e8057e-fbbc-47a8-a8b4-78d9f015c253"
        (json \ "englishFeedback").as[List[FeedbackMessage]] shouldBe List(message)
        (json \ "welshFeedback").as[List[FeedbackMessage]]   shouldBe List(message)
      }
    }

    "deserialising from JSON" should {
      "round-trip correctly" in {
        val json   = Json.toJson(response)
        val result = json.validate[FeedbackResponse]
        result shouldBe JsSuccess(response)
      }
    }
  }

  "FeedbackMessage" when {

    "optional fields are present" should {
      "serialise action and links" in {
        val json = Json.toJson(message)
        (json \ "action").asOpt[String]            shouldBe Some("VAT action")
        (json \ "links").asOpt[List[FeedbackLink]] shouldBe Some(List(link))
      }
    }

    "optional fields are absent" should {
      "omit action and links from JSON" in {
        val json = Json.toJson(messageNoOptionals)
        (json \ "action").asOpt[String] shouldBe None
        (json \ "links").isDefined      shouldBe false
      }
    }
  }

  "FeedbackLink" when {
    "serialising" should {
      "produce title and url fields" in {
        val json = Json.toJson(link)
        (json \ "title").as[String] shouldBe "VAT"
        (json \ "url").as[String]   shouldBe "https://www.gov.uk/vat"
      }
    }
  }
}
