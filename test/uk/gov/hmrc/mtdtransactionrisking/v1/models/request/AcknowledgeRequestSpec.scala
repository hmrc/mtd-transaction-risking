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
import play.api.libs.json.Json

class AcknowledgeRequestSpec extends AnyWordSpec with Matchers:

  private val request = AcknowledgeRequest(
    vrn = "123456789",
    reportId = "f2fb30e5-4ab6-4a29-b3c1-c00000000001",
    correlationId = "c75f40a6-a3df-4429-a697-471eeec46435",
    presentedDateTime = "2026-06-09T10:30:00Z"
  )

  private val expectedJson = Json.obj(
    "inputs" -> Json.arr(
      Json.obj("name" -> "correlationId", "value" -> "c75f40a6-a3df-4429-a697-471eeec46435"),
      Json.obj("name" -> "feedbackId", "value" -> "f2fb30e5-4ab6-4a29-b3c1-c00000000001"),
      Json.obj("name" -> "vrn", "value" -> "123456789"),
      Json.obj("name" -> "presentedDateTime", "value" -> "2026-06-09T10:30:00Z")
    )
  )

  "AcknowledgeRequest" when {
    "serialising to JSON" should {
      "match the expected RDS payload structure" in {
        Json.toJson(request) shouldBe expectedJson
      }
    }

    "validating the generated JSON" should {
      "contain the required input names" in {
        val json = Json.toJson(request)

        (json \ "inputs").as[List[Map[String, String]]] should contain allOf (
          Map("name" -> "correlationId", "value" -> "c75f40a6-a3df-4429-a697-471eeec46435"),
          Map("name" -> "feedbackId", "value" -> "f2fb30e5-4ab6-4a29-b3c1-c00000000001"),
          Map("name" -> "vrn", "value" -> "123456789"),
          Map("name" -> "presentedDateTime", "value" -> "2026-06-09T10:30:00Z")
        )
      }
    }
  }
