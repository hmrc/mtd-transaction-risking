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

// Converts RDS action grids into the feedback shape returned to vendors.
// An action output holds a metadata block naming the columns and a data block of rows, where each row's values line up positionally with those columns.

object ReportResponseTransform:

  private val itemNumberColumn = "itemnumber"
  private val messageColumn = "message" // becomes FeedbackMessage.body
  private val actionColumn = "action"
  private val titleColumn = "title"
  private val linkTitleColumn = "linktitle"
  private val linkUrlColumn = "linkurl"
  private val pathColumn = "path"

  def toFeedbackResponse(report: ReportResponse): Option[FeedbackResponse] =
    report.feedbackId.map { feedbackId =>
      FeedbackResponse(
        reportId = feedbackId,
        englishFeedback = toMessages(report.englishActions),
        welshFeedback = toMessages(report.welshActions),
        // RDS currently returns null here
        correlationId = report.rdsCorrelationId
      )
    }

  private def toMessages(grids: Seq[ActionGrid]): List[FeedbackMessage] =
    val columns = grids.flatMap(_.metadata).headOption.map(columnNames).getOrElse(Seq.empty)
    val rows = grids.flatMap(_.data).flatten

    rows.map(row => columns.zip(row).toMap).flatMap(toMessage).toList

  private def columnNames(metadata: Seq[JsValue]): Seq[String] =
    metadata.map {
      case column: JsObject => column.keys.headOption.map(_.toLowerCase).getOrElse("")
      case _                => ""
    }

  private def toMessage(fields: Map[String, JsValue]): Option[FeedbackMessage] =

    def string(name: String): Option[String] = fields.get(name).flatMap(_.asOpt[String])
    def nonEmpty(name: String): Option[String] = string(name).filter(_.nonEmpty)

    for
      itemNumber <- string(itemNumberColumn)
      body <- string(messageColumn)
      title <- string(titleColumn)
    yield FeedbackMessage(
      itemNumber = itemNumber,
      title = title,
      body = body,
      action = nonEmpty(actionColumn),
      links = toLinks(nonEmpty(linkTitleColumn), nonEmpty(linkUrlColumn)),
      path = nonEmpty(pathColumn)
    )

  // RDS sends one link per item as two plain columns
  private def toLinks(title: Option[String], url: Option[String]): Option[List[FeedbackLink]] =
    for
      linkTitle <- title
      linkUrl <- url
    yield List(FeedbackLink(linkTitle, linkUrl))
