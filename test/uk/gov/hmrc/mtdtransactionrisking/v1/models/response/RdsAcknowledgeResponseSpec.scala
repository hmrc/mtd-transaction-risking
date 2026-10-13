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

import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec

class RdsAcknowledgeResponseSpec extends UnitSpec:

  private def acknowledgeJson(outputs: (String, JsValue)*): JsValue =
    Json.obj(
      "moduleId"       -> "HMRC_ASSIST_VAT_FINSUB_FEEDBACK_ACK",
      "executionState" -> "completed",
      "outputs"        -> outputs.map((name, value) => Json.obj("name" -> name, "value" -> value))
    )

  private val qaRejection: JsValue = acknowledgeJson(
    "VRN"                      -> Json.toJson("888852841"),
    "createdDttm"              -> Json.toJson("2026-10-12T14:25:44.688"),
    "feedbackId"               -> Json.toJson("bc3877bc-5490-4775-9849-cdd566a9aa3c"),
    "responseCode"             -> Json.parse("401.0"),
    "responseMessage"          -> Json.toJson("Acknowledgement validation failed"),
    "rt_Record_Contacts_Outer" -> Json.toJson("bb07e500-8a7e-0a48-b3b0-8f639f4bf413")
  )

  "RdsAcknowledgeResponse" when:

    "reading the response RDS returned in QA" should:
      val response = qaRejection.as[RdsAcknowledgeResponse]

      "read VRN case-insensitively" in:
        response.vrn shouldBe Some("888852841")

      "read the feedback id and created time" in:
        response.feedbackId shouldBe Some("bc3877bc-5490-4775-9849-cdd566a9aa3c")
        response.createdDttm