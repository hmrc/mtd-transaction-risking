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
import play.api.libs.json.Format.GenericFormat
import play.api.libs.json.{JsObject, JsSuccess, Json}

class AcknowledgeRequestSpec extends AnyWordSpec with Matchers {
  val validJson: JsObject = Json.obj(
    "vrn"               -> "123456789",
    "reportId"          -> "123456aB-012A-345B-678C-012345678abc",
    "correlationId"     -> "a1e8057e-fbbc-47a8-a8b4-78d9f015c253",
    "presentedDateTime" -> "2023-06-01T12:00:00Z"
  )

  val expectedModel = AcknowledgeRequest(
    vrn               = "123456789",
    reportId          = "123456aB-012A-345B-678C-012345678abc",
    correlationId     = "a1e8057e-fbbc-47a8-a8b4-78d9f015c253",
    presentedDateTime = java.time.OffsetDateTime.parse("2023-06-01T12:00:00Z")
  )

  "AcknowledgeRequest" when {

    "reading from valid JSON" should {

      "deserialise correctly" in {
        validJson.validate[AcknowledgeRequest] shouldBe JsSuccess(expectedModel)
      }
    }
    
    

  }
}