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

import play.api.libs.json.{JsValue, Json, Reads}

/** RDS report. An HTTP 201 means the module executed and produced a report
  *
  * Feedback messages arrive as grids: a metadata block naming the columns, then data rows whose values line up positionally with them.
  *
  * {{{
  * {
  *   "outputs": [
  *     { "name": "correlationId", "value": null },
  *     { "name": "createdDttm",   "value": "2026-10-09T19:00:19.246" },
  *     { "name": "feedbackId",    "value": "bc3877bc-5490-4775-9849-cdd566a9aa3c" },
  *     { "name": "vrn",           "value": "123456789" },
  *     { "name": "englishActions", "value": [
  *         { "metadata": [ {"ITEMNUMBER": "string"}, {"MESSAGE": "string"}, {"ACTION": "string"}, {"TITLE": "string"},
  *                         {"LINKTITLE": "string"}, {"LINKURL": "string"}, {"PATH": "string"} ] },
  *         { "data":     [ [ "1", "Please review your figures.", "Check your records.", "VAT Return Query",
  *                           "VAT guidance", "https://www.gov.uk", "vatDueSales" ] ] }
  *     ]},
  *     { "name": "welshActions", "value": [ ... ] },
  *     { "name": "rt_Record_Contacts_Outer", "value": "..." }
  *   ]
  * }
  * }}}
  */
final case class ReportResponse(outputs: Seq[RdsOutput]) extends RdsOutputs:

  def rdsCorrelationId: Option[String] = stringOf("correlationId")
  def englishActions: Seq[ActionGrid] = actionGrids("englishActions")
  def welshActions: Seq[ActionGrid] = actionGrids("welshActions")

  private def actionGrids(name: String): Seq[ActionGrid] =
    valueOf(name).flatMap(_.asOpt[Seq[ActionGrid]]).getOrElse(Seq.empty)

object ReportResponse:
  given reads: Reads[ReportResponse] = Json.reads[ReportResponse]

final case class ActionGrid(metadata: Option[Seq[JsValue]], data: Option[Seq[Seq[JsValue]]])

object ActionGrid:
  given reads: Reads[ActionGrid] = Json.reads[ActionGrid]
