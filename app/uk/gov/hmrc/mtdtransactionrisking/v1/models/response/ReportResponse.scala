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

/** RDS report.
  *
  * Results are a flat list of name/value outputs rather than a structured object. An HTTP 201 means the module executed and produced a report
  *
  * Feedback messages arrive as grids: a metadata block naming the columns, then data rows whose values line up positionally with them. 
  *
  * {{{
  * {
  *   "outputs": [
  *     { "name": "correlationId", "value": null },
  *     { "name": "createdDttm",   "value": "2026-10-09T19:00:19.246" },
  *     { "name": "feedbackId",    "value": "ec413ab2-5f07-..." },
  *     { "name": "vrn",           "value": "123456789" },
  *     { "name": "englishActions", "value": [
  *         { "metadata": [ {"ITEMNUMBER": "string"}, {"MESSAGE": "string"}, {"ACTION": "string"}, {"TITLE": "string"},
  *                         {"LINKTITLE": "string"}, {"LINKURL": "string"}, {"PATH": "string"} ] },
  *         { "data":     [ [ "1", "Please review your figures.", "Check your records.", "VAT Return Query",
  *                           "VAT guidance", "https://www.gov.uk/...", "vatDueSales" ] ] }
  *     ]},
  *     { "name": "welshActions", "value": [ ... ] },
  *     { "name": "rt_Record_Contacts_Outer", "value": "f9a5a9a7-..." }
  *   ]
  * }
  * }}}
  */
final case class ReportResponse(outputs: Seq[ReportOutput]):

  // Accepts "201" or 201, should RDS start sending it
  def responseCode: Option[Int] =
    valueOf("responseCode").flatMap(value => value.asOpt[Int].orElse(value.asOpt[String].flatMap(_.toIntOption)))

  def responseMessage: Option[String] = valueOf("responseMessage").flatMap(_.asOpt[String])
  def feedbackId: Option[String] = valueOf("feedbackId").flatMap(_.asOpt[String])
  def rdsCorrelationId: Option[String] = valueOf("correlationId").flatMap(_.asOpt[String]) // null becomes None

  def englishActions: Seq[ActionGrid] = actionGrids("englishActions")

  private def actionGrids(name: String): Seq[ActionGrid] =
    valueOf(name).flatMap(_.asOpt[Seq[ActionGrid]]).getOrElse(Seq.empty)

  private def valueOf(name: String): Option[JsValue] =
    outputs.find(_.name.equalsIgnoreCase(name)).map(_.value)

  def welshActions: Seq[ActionGrid] = actionGrids("welshActions")

object ReportResponse:
  given reads: Reads[ReportResponse] = Json.reads[ReportResponse]

final case class ReportOutput(name: String, value: JsValue)

object ReportOutput:
  given reads: Reads[ReportOutput] = Json.reads[ReportOutput]

final case class ActionGrid(metadata: Option[Seq[JsValue]], data: Option[Seq[Seq[JsValue]]])

object ActionGrid:
  given reads: Reads[ActionGrid] = Json.reads[ActionGrid]
