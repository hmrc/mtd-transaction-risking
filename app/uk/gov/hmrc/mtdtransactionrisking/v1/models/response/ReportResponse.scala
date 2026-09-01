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

/** The RDS report
  *
  * Results are a flat list of name/value outputs rather than a structured object, and the HTTP status only tells us the module executed. The decision
  * itself is in the `responseCode` output. Feedback messages arrive as grids: a metadata block naming the columns, then data rows values line up
  * positionally with them.
  *
  * {{{
  * {
  *   "outputs": [
  *     { "name": "correlationId",   "value": "E9F65715BBC922..." },
  *     { "name": "feedbackId",      "value": "f2fb30e5-4ab6-..." },
  *     { "name": "responseCode",    "value": "201" },
  *     { "name": "responseMessage", "value": "Feedback generated successfully" },
  *     { "name": "englishActions",  "value": [
  *         { "metadata": [ {"itemNumber": ""}, {"message": ""}, {"action": ""},
  *                         {"title": ""}, [{"linkTitle": ""}, {"linkUrl": ""}], {"path": ""} ] },
  *         { "data":     [ [ "1", "Please review your figures.", "Check your records.",
  *                           "VAT Return Query",
  *                           [{"linkTitle": "VAT guidance"}, {"linkUrl": "https://..."}],
  *                           "vatDueSales" ] ] }
  *     ]},
  *     { "name": "welshActions", "value": [ ... ] }
  *   ]
  * }
  * }}}
  */
final case class ReportResponse(outputs: Seq[ReportOutput]):

  def responseCode: Option[Int]        = valueOf("responseCode").flatMap(_.asOpt[String]).flatMap(_.toIntOption)
  def responseMessage: Option[String]  = valueOf("responseMessage").flatMap(_.asOpt[String])
  def feedbackId: Option[String]       = valueOf("feedbackId").flatMap(_.asOpt[String])
  def rdsCorrelationId: Option[String] = valueOf("correlationId").flatMap(_.asOpt[String])
  
  def englishActions: Seq[ActionGrid]  = actionGrids("englishActions")
  def welshActions: Seq[ActionGrid]    = actionGrids("welshActions")

  private def valueOf(name: String): Option[JsValue] = outputs.find(_.name == name).map(_.value)

  private def actionGrids(name: String): Seq[ActionGrid] =
    valueOf(name).flatMap(_.asOpt[Seq[ActionGrid]]).getOrElse(Seq.empty)

object ReportResponse:
  given reads: Reads[ReportResponse] = Json.reads[ReportResponse]

final case class ReportOutput(name: String, value: JsValue)

object ReportOutput:
  given reads: Reads[ReportOutput] = Json.reads[ReportOutput]

final case class ActionGrid(metadata: Option[Seq[JsValue]], data: Option[Seq[Seq[JsValue]]])

object ActionGrid:
  given reads: Reads[ActionGrid] = Json.reads[ActionGrid]
