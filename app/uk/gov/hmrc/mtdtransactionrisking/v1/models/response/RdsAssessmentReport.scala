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

/** RDS Results arrive as a flat list of name/value outputs; the HTTP status only tells us the module
 * executed, while the `responseCode` output carries the decision itself.
 */
final case class RdsAssessmentReport(outputs: Seq[RdsOutput]):

  private def valueOf(name: String): Option[JsValue] = outputs.find(_.name == name).map(_.value)

  def responseCode: Option[Int]        = valueOf("responseCode").flatMap(_.asOpt[String]).flatMap(_.toIntOption)
  def responseMessage: Option[String]  = valueOf("responseMessage").flatMap(_.asOpt[String])
  def feedbackId: Option[String]       = valueOf("feedbackId").flatMap(_.asOpt[String])
  def rdsCorrelationId: Option[String] = valueOf("correlationId").flatMap(_.asOpt[String])

  def englishActions: Seq[RdsActionGrid] = actionGrids("englishActions")
  def welshActions: Seq[RdsActionGrid]   = actionGrids("welshActions")

  private def actionGrids(name: String): Seq[RdsActionGrid] =
    valueOf(name).flatMap(_.asOpt[Seq[RdsActionGrid]]).getOrElse(Seq.empty)

object RdsAssessmentReport:
  given reads: Reads[RdsAssessmentReport] = Json.reads[RdsAssessmentReport]

final case class RdsOutput(name: String, value: JsValue)

object RdsOutput:
  given reads: Reads[RdsOutput] = Json.reads[RdsOutput]

final case class RdsActionGrid(metadata: Option[Seq[JsValue]], data: Option[Seq[Seq[JsValue]]])

object RdsActionGrid:
  given reads: Reads[RdsActionGrid] = Json.reads[RdsActionGrid]