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

import play.api.libs.json.{JsObject, JsValue}

/** Converts the RDS action into the feedback shape returned to vendors.
 *
 * An action output holds a metadata block naming the columns and a data block of rows, where each
 * row's values line up positionally with those columns. Pairing the two gives a named field for
 * each value.
 */
object RdsAssessmentReportTransform:

  private val linksColumn = "links"

  def toFeedbackResponse(report: RdsAssessmentReport): Option[FeedbackResponse] =
    for
      feedbackId    <- report.feedbackId
      correlationId <- report.rdsCorrelationId
    yield FeedbackResponse(
      reportId        = feedbackId,
      englishFeedback = toMessages(report.englishActions),
      welshFeedback   = toMessages(report.welshActions),
      correlationId   = correlationId
    )

  private def toMessages(grids: Seq[RdsActionGrid]): List[FeedbackMessage] =
    val columns = grids.flatMap(_.metadata).headOption.map(columnNames).getOrElse(Seq.empty)
    val rows    = grids.flatMap(_.data).flatten

    rows.map(row => columns.zip(row).toMap).flatMap(toMessage).toList

  /** Each metadata entry is a single-key object naming its column, except the links column which
   * is an array and so has no name of its own.
   */
  private def columnNames(metadata: Seq[JsValue]): Seq[String] =
    metadata.map {
      case column: JsObject => column.keys.headOption.getOrElse("")
      case _                => linksColumn
    }

  private def toMessage(fields: Map[String, JsValue]): Option[FeedbackMessage] =

    def string(name: String): Option[String] = fields.get(name).flatMap(_.asOpt[String])

    for
      itemNumber <- string("itemNumber")
      body       <- string("message")
      title      <- string("title")
      path       <- string("path")
    yield FeedbackMessage(
      itemNumber = itemNumber,
      title      = title,
      body       = body,
      action     = string("action"),
      links      = fields.get(linksColumn).flatMap(toLinks),
      path       = path
    )

  private def toLinks(value: JsValue): Option[List[FeedbackLink]] =
    value.asOpt[Seq[JsValue]].flatMap { entries =>
      for
        title <- entries.flatMap(entry => (entry \ "linkTitle").asOpt[String]).headOption
        url   <- entries.flatMap(entry => (entry \ "linkUrl").asOpt[String]).headOption
      yield List(FeedbackLink(title, url))
    }