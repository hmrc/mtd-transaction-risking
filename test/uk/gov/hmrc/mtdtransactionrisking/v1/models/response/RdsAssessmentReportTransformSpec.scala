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

import play.api.libs.json.{JsArray, JsValue, Json}
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec

class RdsAssessmentReportTransformSpec extends UnitSpec:

  private val feedbackId    = "f2fb30e5-4ab6-4a29-b3c1-c7264259ff1c"
  private val correlationId = "E9F65715BBC9222477B27074804BBDD5C73CDE62F84D8B00CFD05B883534AF3D"

  /** An action output as returned. a metadata block naming the columns then the data rows. */
  private def actionGrids(rows: JsValue*): JsValue = Json.arr(
    Json.obj(
      "metadata" -> Json.arr(
        Json.obj("itemNumber" -> "1"),
        Json.obj("message"    -> "m"),
        Json.obj("action"     -> "a"),
        Json.obj("title"      -> "t"),
        Json.arr(Json.obj("linkTitle" -> "lt"), Json.obj("linkUrl" -> "lu")),
        Json.obj("path" -> "p")
      )),
    Json.obj("data" -> JsArray(rows))
  )

  private val completeRow: JsValue = Json.arr(
    "1",
    "Please review your VAT return figures.",
    "Check your sales records for the period.",
    "VAT Return Query",
    Json.arr(Json.obj("linkTitle" -> "VAT guidance"), Json.obj("linkUrl" -> "https://www.gov.uk/vat-returns")),
    "vatDueSales"
  )

  private def reportWith(outputs: (String, JsValue)*): RdsAssessmentReport =
    RdsAssessmentReport(outputs.map((name, value) => RdsOutput(name, value)))

  private val fullReport: RdsAssessmentReport = reportWith(
    "feedbackId"     -> Json.toJson(feedbackId),
    "correlationId"  -> Json.toJson(correlationId),
    "englishActions" -> actionGrids(completeRow),
    "welshActions"   -> actionGrids(completeRow)
  )

  "toFeedbackResponse" when:

    "the report is complete" should:

      "map the feedback and correlation ids" in:
        val result = RdsAssessmentReportTransform.toFeedbackResponse(fullReport).value

        result.reportId shouldBe feedbackId
        result.correlationId shouldBe correlationId

      "map each data row to a feedback message" in:
        val message = RdsAssessmentReportTransform.toFeedbackResponse(fullReport).value.englishFeedback.head

        message shouldBe FeedbackMessage(
          itemNumber = "1",
          title      = "VAT Return Query",
          body       = "Please review your VAT return figures.",
          action     = Some("Check your sales records for the period."),
          links      = Some(List(FeedbackLink("VAT guidance", "https://www.gov.uk/vat-returns"))),
          path       = "vatDueSales"
        )

      "map the welsh grid as well as the english" in:
        val result = RdsAssessmentReportTransform.toFeedbackResponse(fullReport).value

        result.englishFeedback should have size 1
        result.welshFeedback should have size 1

      "map every row in the grid" in:
        val report = reportWith(
          "feedbackId"     -> Json.toJson(feedbackId),
          "correlationId"  -> Json.toJson(correlationId),
          "englishActions" -> actionGrids(completeRow, completeRow),
          "welshActions"   -> actionGrids()
        )

        RdsAssessmentReportTransform.toFeedbackResponse(report).value.englishFeedback should have size 2

    "the columns are in a different order" should:
      "still map each value to the right field, since columns are located by name" in:
        val reordered = Json.arr(
          Json.obj(
            "metadata" -> Json.arr(
              Json.obj("path"       -> "p"),
              Json.obj("title"      -> "t"),
              Json.obj("itemNumber" -> "1"),
              Json.obj("message"    -> "m")
            )),
          Json.obj("data" -> Json.arr(Json.arr("vatDueSales", "VAT Return Query", "1", "Please review.")))
        )

        val report = reportWith(
          "feedbackId"     -> Json.toJson(feedbackId),
          "correlationId"  -> Json.toJson(correlationId),
          "englishActions" -> reordered,
          "welshActions"   -> Json.arr()
        )

        RdsAssessmentReportTransform.toFeedbackResponse(report).value.englishFeedback.head shouldBe FeedbackMessage(
          itemNumber = "1",
          title      = "VAT Return Query",
          body       = "Please review.",
          action     = None,
          links      = None,
          path       = "vatDueSales"
        )

    "a row is missing its optional columns" should:
      "map the message without an action or links" in:
        val minimal = Json.arr(
          Json.obj(
            "metadata" -> Json.arr(
              Json.obj("itemNumber" -> "1"),
              Json.obj("message"    -> "m"),
              Json.obj("title"      -> "t"),
              Json.obj("path"       -> "p")
            )),
          Json.obj("data" -> Json.arr(Json.arr("1", "Please review.", "VAT Return Query", "vatDueSales")))
        )

        val report = reportWith(
          "feedbackId"     -> Json.toJson(feedbackId),
          "correlationId"  -> Json.toJson(correlationId),
          "englishActions" -> minimal,
          "welshActions"   -> Json.arr()
        )

        val message = RdsAssessmentReportTransform.toFeedbackResponse(report).value.englishFeedback.head

        message.action shouldBe None
        message.links shouldBe None

    "a row is missing a mandatory column" should:
      "drop that message rather than failing the whole report" in:
        val withoutTitle = Json.arr(
          Json.obj(
            "metadata" -> Json.arr(Json.obj("itemNumber" -> "1"), Json.obj("message" -> "m"), Json.obj("path" -> "p"))),
          Json.obj("data" -> Json.arr(Json.arr("1", "Please review.", "vatDueSales")))
        )

        val report = reportWith(
          "feedbackId"     -> Json.toJson(feedbackId),
          "correlationId"  -> Json.toJson(correlationId),
          "englishActions" -> withoutTitle,
          "welshActions"   -> Json.arr()
        )

        RdsAssessmentReportTransform.toFeedbackResponse(report).value.englishFeedback shouldBe empty

    "the report has no feedback id" should:
      "return None" in:
        val report = reportWith("correlationId" -> Json.toJson(correlationId))

        RdsAssessmentReportTransform.toFeedbackResponse(report) shouldBe None

    "the report has no correlation id" should:
      "return None" in:
        val report = reportWith("feedbackId" -> Json.toJson(feedbackId))

        RdsAssessmentReportTransform.toFeedbackResponse(report) shouldBe None

    "the report has no action grids" should:
      "return a response with empty feedback" in:
        val report = reportWith(
          "feedbackId"    -> Json.toJson(feedbackId),
          "correlationId" -> Json.toJson(correlationId)
        )

        val result = RdsAssessmentReportTransform.toFeedbackResponse(report).value

        result.englishFeedback shouldBe empty
        result.welshFeedback shouldBe empty
