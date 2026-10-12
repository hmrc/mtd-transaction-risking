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

import play.api.libs.json.{JsNull, JsValue, Json}
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec

class ReportResponseSpec extends UnitSpec:

  private val feedbackId = "ec413ab2-5f07-4d54-989a-5d630231b443"

  private val rdsResponseJson: JsValue = Json.parse(
    s"""
       |{
       |  "links": [],
       |  "version": 2,
       |  "moduleId": "HMRC_ASSIST_VAT_FINSUB_FEEDBACK",
       |  "stepId": "execute",
       |  "executionState": "completed",
       |  "metadata": { "module_id": "HMRC_ASSIST_VAT_FINSUB_FEEDBACK", "step_id": "execute" },
       |  "outputs": [
       |    { "name": "correlationId", "value": null },
       |    { "name": "createdDttm", "value": "2026-10-09T19:00:19.246" },
       |    {
       |      "name": "englishActions",
       |      "value": [
       |        { "metadata": [ { "ITEMNUMBER": "string" }, { "MESSAGE": "string" }, { "ACTION": "string" }, { "TITLE": "string" },
       |                        { "LINKTITLE": "string" }, { "LINKURL": "string" }, { "PATH": "string" } ] },
       |        { "data": [ [ "0", "message", "action", "title", "link title", "link url", "0" ] ] }
       |      ]
       |    },
       |    { "name": "feedbackId", "value": "$feedbackId" },
       |    { "name": "vrn", "value": "888852841" },
       |    {
       |      "name": "welshActions",
       |      "value": [
       |        { "metadata": [ { "ITEMNUMBER": "string" }, { "MESSAGE": "string" }, { "ACTION": "string" }, { "TITLE": "string" },
       |                        { "LINKTITLE": "string" }, { "LINKURL": "string" }, { "PATH": "string" } ] },
       |        { "data": [ [ "0", "neges", "gweithred", "teitl", "teitl dolen", "url dolen", "0" ] ] }
       |      ]
       |    },
       |    { "name": "rt_Record_Contacts_Outer", "value": "f9a5a9a7-09a2-a94b-a492-54c9fc91d5d2" }
       |  ]
       |}
       |""".stripMargin
  )

  private val report: ReportResponse = rdsResponseJson.as[ReportResponse]

  private def reportWith(outputs: (String, JsValue)*): ReportResponse =
    ReportResponse(outputs.map((name, value) => ReportOutput(name, value)))

  "ReportResponse" when:

    "read from the response RDS returns" should:

      "parse every output" in:
        report.outputs.map(_.name) should contain allOf ("createdDttm", "vrn", "rt_Record_Contacts_Outer")

      "expose the feedback id" in:
        report.feedbackId shouldBe Some(feedbackId)

      "treat a null correlation id as absent" in:
        report.rdsCorrelationId shouldBe None

      "have no response code as RDS does not send one" in:
        report.responseCode shouldBe None
        report.responseMessage shouldBe None

      "expose the english and welsh action grids as metadata and data blocks" in:
        report.englishActions should have size 2
        report.welshActions should have size 2

        report.englishActions.flatMap(_.metadata).flatten should have size 7
        report.englishActions.flatMap(_.data).flatten should have size 1

    "the correlation id is populated" should:
      "expose it" in:
        reportWith("correlationId" -> Json.toJson("E9F65715BBC9")).rdsCorrelationId shouldBe Some("E9F65715BBC9")

    "a response code is sent" should:

      "read it from a string" in:
        reportWith("responseCode" -> Json.toJson("201")).responseCode shouldBe Some(201)

      "read it from a number" in:
        reportWith("responseCode" -> Json.toJson(201)).responseCode shouldBe Some(201)

      "return None when it is not numeric" in:
        reportWith("responseCode" -> Json.toJson("not-a-number")).responseCode shouldBe None

      "return None when it is null" in:
        reportWith("responseCode" -> JsNull).responseCode shouldBe None

      "expose the response message alongside it" in:
        reportWith("responseMessage" -> Json.toJson("Feedback generated")).responseMessage shouldBe Some("Feedback generated")

    "output names differ in case" should:
      "match them case-insensitively" in:
        val capitalised = reportWith(
          "FeedbackId" -> Json.toJson(feedbackId),
          "EnglishActions" -> Json.arr(Json.obj("metadata" -> Json.arr()), Json.obj("data" -> Json.arr())),
          "WelshActions" -> Json.arr(Json.obj("metadata" -> Json.arr()), Json.obj("data" -> Json.arr()))
        )

        capitalised.feedbackId shouldBe Some(feedbackId)
        capitalised.englishActions should have size 2
        capitalised.welshActions should have size 2

    "an output is absent" should:

      "return None rather than failing" in :
        val report = ReportResponse(Seq.empty)

        report.feedbackId shouldBe None
        report.rdsCorrelationId shouldBe None
        report.responseCode shouldBe None
        report.responseMessage shouldBe None

      "return no action grids" in:
        val report = ReportResponse(Seq.empty)

        report.englishActions shouldBe empty
        report.welshActions shouldBe empty

    "an action output is not a grid" should:
      "return no action grids rather than failing" in:
        reportWith("englishActions" -> Json.toJson("not-a-grid")).englishActions shouldBe empty

    "an output has no value key" should:
      "fail to parse, so a malformed response is reported rather than hidden" in:
        Json.parse("""{ "outputs": [ { "name": "feedbackId" } ] }""").validate[ReportResponse].isError shouldBe true
