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

import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec

class AcknowledgeRequestSpec extends UnitSpec:

  private val request =
    AcknowledgeRequest(
      vrn = "123456789",
      reportId = "f2fb30e5-4ab6-4a29-b3c1-c00000000001",
      correlationId = "E9F65715BBC9222477B27074804BBDD5C73CDE62F84D8B00CFD05B883534AF3D",
      presentedDateTime = "2026-06-09T10:30:00Z"
    )

  private val expectedJson =
    Json.obj(
      "vrn" -> "123456789",
      "reportId" -> "f2fb30e5-4ab6-4a29-b3c1-c00000000001",
      "correlationId" -> "E9F65715BBC9222477B27074804BBDD5C73CDE62F84D8B00CFD05B883534AF3D",
      "presentedDateTime" -> "2026-06-09T10:30:00Z"
    )

  "AcknowledgeRequest JSON format" should:

    "serialize all fields with their expected names and values" in:
      Json.toJson(request) shouldBe expectedJson

    "deserialize all required fields" in:
      expectedJson.as[AcknowledgeRequest] shouldBe request

    "round-trip through JSON" in:
      Json.toJson(Json.toJson(request).as[AcknowledgeRequest]) shouldBe expectedJson

    "reject JSON when a required field is missing" in:
      val missingPresentedDateTime =
        expectedJson.as[JsObject] - "presentedDateTime"

      Json.fromJson[AcknowledgeRequest](missingPresentedDateTime).isError shouldBe true

    "reject JSON when a field has the wrong type" in:
      val invalidVrn =
        expectedJson.as[JsObject] + ("vrn" -> Json.toJson(123456789))

      Json.fromJson[AcknowledgeRequest](invalidVrn).isError shouldBe true
