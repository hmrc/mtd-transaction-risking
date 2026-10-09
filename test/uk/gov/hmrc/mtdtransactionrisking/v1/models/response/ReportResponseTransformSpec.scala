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

import play.api.libs.json.{JsArray, JsNull, JsValue, Json}
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec

class ReportResponseTransformSpec extends UnitSpec:

  private val feedbackId    = "f2fb30e5-4ab6-4a29-b3c1-c7264259ff1c"
  private val correlationId = "E9F65715BBC9222477B27074804BBDD5C73CDE62F84D8B00CFD05B883534AF3D"

  private val rdsColumns = Seq("ITEMNUMBER", "MESSAGE", "ACTION", "TITLE", "LINKTITLE", "LINKURL", "PATH")

  private def grid(columns: Seq[String], rows: JsValue*): JsValue = Json.arr(
    Json.obj("metadata" -> columns.map(column => Json.obj(column -> "string"))),
    Json.obj("data" -> JsArray(rows))
  )

  private def actionGrids(rows: JsValue*): JsValue = grid(rdsColumns, rows*)

  private val completeRow: JsValue = Json.arr(
    "1",
    "Please review your VAT return figures.",
    "Check your sales records for the period.",
    "VAT Return Query",
    "VAT guidance",
    "https://www.gov.uk/vat-returns",
    "vatDueSales"
  )

  private def reportWith(outputs: (String, JsValue)*): ReportResponse =
    ReportResponse(outputs.map((name, value) => ReportOutput(name, value)))

  private def reportWithEnglish(englishActions: JsValue): ReportResponse =
    reportWith(
      "feedbackId"     -> Json.toJson(feedbackId),
      "correlationId"  -> Json.toJson(correlationId),
      "englishActions" -> englishActions,
      "welshActions"   -> Json.arr()
    )

  private def englishMessages(englishActions: JsValue): List[FeedbackMessage] =
    ReportResponseTransform.toFeedbackResponse(reportWithEnglish(englishActions)).value.englishFeedback

  private val fullReport: ReportResponse = reportWith(
    "feedbackId"     -> Json.toJson(feedbackId),
    "correlationId"  -> Json.toJson(correlationId),
    "englishActions" -> actionGrids(completeRow),
    "welshActions"   -> actionGrids(completeRow)
  )

  "toFeedbackResponse" when:

    "the report is complete" should:

      "map the feedback and correlation ids" in:
        val result = ReportResponseTransform.toFeedbackResponse(fullReport).value

        result.reportId shouldBe feedbackId
        result.correlationId shouldBe Some(correlationId)

      "map each data row to a feedback message" in:
        val message = ReportResponseTransform.toFeedbackResponse(fullReport).value.englishFeedback.head

        message shouldBe FeedbackMessage(
          itemNumber = "1",
          title = "VAT Return Query",
          body = "Please review your VAT return figures.",
          action = Some("Check your sales records for the period."),
          links = Some(List(FeedbackLink("VAT guidance", "https://www.gov.uk/vat-returns"))),
          path = Some("vatDueSales")
        )

      "map the welsh grid as well as the english" in:
        val result = ReportResponseTransform.toFeedbackResponse(fullReport).value

        result.englishFeedback should have size 1
        result.welshFeedback should have size 1

      "map every row in the grid" in:
        englishMessages(actionGrids(completeRow, completeRow)) should have size 2

    "RDS returns the 'no feedback' report seen in QA" should:
      "map item 0 with its placeholder content" in:
        val noFeedbackRow = Json.arr(
          "0",
          "This is not a complete check of your return or confirmation that your return is accurate.",
          "Consider any further checks before finalising your return.",
          "HMRC Feedback: HMRC Assist has not returned any messages.",
          "Find out more about HMRC Assist on GOV.UK",
          "https://www.gov.uk/hmrc-assist",
          "0"
        )

        englishMessages(actionGrids(noFeedbackRow)) shouldBe List(
          FeedbackMessage(
            itemNumber = "0",
            title = "HMRC Feedback: HMRC Assist has not returned any messages.",
            body = "This is not a complete check of your return or confirmation that your return is accurate.",
            action = Some("Consider any further checks before finalising your return."),
            links = Some(List(FeedbackLink("Find out more about HMRC Assist on GOV.UK", "https://www.gov.uk/hmrc-assist"))),
            path = Some("0")
          ))

    "column names differ in case" should:
      "match them case-insensitively" in:
        val camelCaseColumns = Seq("itemNumber", "message", "action", "title", "linkTitle", "linkUrl", "path")

        englishMessages(grid(camelCaseColumns, completeRow)).head.itemNumber shouldBe "1"

    "the columns are in a different order" should:
      "still map each value to the right field, since columns are located by name" in:
        val reordered = grid(
          Seq("PATH", "TITLE", "ITEMNUMBER", "MESSAGE"),
          Json.arr("vatDueSales", "VAT Return Query", "1", "Please review.")
        )

        englishMessages(reordered).head shouldBe FeedbackMessage(
          itemNumber = "1",
          title = "VAT Return Query",
          body = "Please review.",
          action = None,
          links = None,
          path = Some("vatDueSales")
        )

    "a row's links" should:

      "be omitted when the link title is empty" in:
        val row = Json.arr("1", "Please review.", "Check.", "VAT Return Query", "", "https://www.gov.uk/vat-returns", "vatDueSales")

        englishMessages(actionGrids(row)).head.links shouldBe None

      "be omitted when the link url is empty" in:
        val row = Json.arr("1", "Please review.", "Check.", "VAT Return Query", "VAT guidance", "", "vatDueSales")

        englishMessages(actionGrids(row)).head.links shouldBe None

    "a row has an empty action" should:
      "map the action as absent" in:
        val row = Json.arr("1", "Please review.", "", "VAT Return Query", "", "", "vatDueSales")

        englishMessages(actionGrids(row)).head.action shouldBe None

    "a row is missing its optional columns" should:
      "map the message without an action or links" in:
        val minimal = grid(
          Seq("ITEMNUMBER", "MESSAGE", "TITLE", "PATH"),
          Json.arr("1", "Please review.", "VAT Return Query", "vatDueSales")
        )

        val message = englishMessages(minimal).head

        message.action shouldBe None
        message.links shouldBe None

    "a row is missing path" should:
      "retain the feedback message with no path" in:
        val noPath = grid(
          Seq("ITEMNUMBER", "MESSAGE", "TITLE"),
          Json.arr("0", "HMRC Assist has not returned any messages.", "HMRC feedback")
        )

        englishMessages(noPath).head shouldBe FeedbackMessage(
          itemNumber = "0",
          title = "HMRC feedback",
          body = "HMRC Assist has not returned any messages.",
          action = None,
          links = None,
          path = None
        )

    "a row has an empty path" should:
      "retain the feedback message without surfacing path" in:
        val row = Json.arr("0", "HMRC Assist has not returned any messages.", "", "HMRC feedback", "", "", "")

        englishMessages(actionGrids(row)).head.path shouldBe None

    "a row is missing a mandatory column" should:
      "drop that message rather than failing the whole report" in:
        val withoutTitle = grid(
          Seq("ITEMNUMBER", "MESSAGE", "PATH"),
          Json.arr("1", "Please review.", "vatDueSales")
        )

        englishMessages(withoutTitle) shouldBe empty

    "the report has no feedback id" should:
      "return None" in:
        val report = reportWith("correlationId" -> Json.toJson(correlationId))

        ReportResponseTransform.toFeedbackResponse(report) shouldBe None

    "the report's correlation id is null, as RDS currently returns it" should:
      "still produce a response, without a correlation id" in:
        val report = reportWith(
          "feedbackId"     -> Json.toJson(feedbackId),
          "correlationId"  -> JsNull,
          "englishActions" -> actionGrids(completeRow),
          "welshActions"   -> actionGrids(completeRow)
        )

        val result = ReportResponseTransform.toFeedbackResponse(report).value

        result.reportId shouldBe feedbackId
        result.correlationId shouldBe None
        result.englishFeedback should have size 1

    "the report has no correlation id output at all" should:
      "still produce a response, without a correlation id" in:
        val report = reportWith("feedbackId" -> Json.toJson(feedbackId))

        ReportResponseTransform.toFeedbackResponse(report).value.correlationId shouldBe None

    "the report has no action grids" should:
      "return a response with empty feedback" in:
        val report = reportWith(
          "feedbackId"    -> Json.toJson(feedbackId),
          "correlationId" -> Json.toJson(correlationId)
        )

        val result = ReportResponseTransform.toFeedbackResponse(report).value

        result.englishFeedback shouldBe empty
        result.welshFeedback shouldBe empty