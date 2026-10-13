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

import play.api.libs.json.{Json, Reads}

/** RDS acknowledge. HTTP 201 means the module executed the business outcome is in responseCode.
  *
  * {{{
  * {
  *   "moduleId": "HMRC_ASSIST_VAT_FINSUB_FEEDBACK_ACK",
  *   "executionState": "completed",
  *   "outputs": [
  *     { "name": "VRN",                      "value": "888852841" },
  *     { "name": "createdDttm",              "value": "2026-10-12T14:25:44.688" },
  *     { "name": "feedbackId",               "value": "bc3877bc-5490-4775-9849-cdd566a9aa3c" },
  *     { "name": "responseCode",             "value": 401.0 },
  *     { "name": "responseMessage",          "value": "Acknowledgement validation failed" },
  *     { "name": "rt_Record_Contacts_Outer", "value": "..." }
  *   ]
  * }
  * }}}
  */
final case class RdsAcknowledgeResponse(outputs: Seq[RdsOutput]) extends RdsOutputs:
  def vrn: Option[String] = stringOf("vrn")

object RdsAcknowledgeResponse:
  given reads: Reads[RdsAcknowledgeResponse] = Json.reads[RdsAcknowledgeResponse]
