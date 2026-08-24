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

import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec

class ReportResponseSpec extends UnitSpec:

  private val reportJson: JsValue = Json.parse(
    """
      |{
      |  "links": [],
      |  "version": 2,
      |  "moduleId": "rbfConceptHub",
      |  "stepId": "execute",
      |  "executionState": "completed",
      |  "outputs": [
      |    { "name": "createdDttm", "value": "2026-04-01T09:35:15.094Z" },
      |    { "name": "vrn", "value": "123456789" },
      |    { "name": "correlationId", "value": "E9F65715BBC9222477B27074804BBDD5C73CDE62F84D8B00CFD05B883534AF3D" },
      |    { "name": "feedbackId", "value": "f2fb30e5-4ab6-4a29-b3c1-c7264259ff1c" },
      |    { "name": "responseCode", "value": "201" },
      |    { "name": "responseMessage", "value": "Feedback generated successfully" },
      |    {
      |      "name": "englishActions",
      |      "value": [
      |        { "metadata": [ { "itemNumber": "1" }, { "message": "m" }, { "path": "p" } ] },
      |        { "data": [ [ "1", "m", "p" ] ] }
      |      ]
      |    },
      |    {
      |      "name": "welshActions",
      |      "value": [
      |        { "metadata": [ { "itemNumber": "1" }, { "message": "n" }, { "path": "p" } ] },
      |        { "data": [ [ "1", "n", "p" ] ] }
      |      ]
      |    }
      |  ]
      |}
      |""".stripMargin
  )

  private val report: ReportResponse = reportJson.as[ReportResponse]

  "RdsAssessmentReport" when:

    "read from a SAS response" should:

      "expose the response code as an int" in:
        report.responseCode shouldBe Some(201)

      "expose the response message" in:
        report.responseMessage shouldBe Some("Feedback generated successfully")

      "expose the feedback id" in:
        report.feedbackId shouldBe Some("f2fb30e5-4ab6-4a29-b3c1-c7264259ff1c")

      "expose the RDS correlation id" in:
        report.rdsCorrelationId shouldBe Some("E9F65715BBC9222477B27074804BBDD5C73CDE62F84D8B00CFD05B883534AF3D")

      "expose the english and welsh action grids" in:
        report.englishActions should have size 2
        report.welshActions should have size 2

      "ignore the SAS envelope fields the model does not use" in:
        report.outputs should have size 8

    "an output is absent" should:

      "return None rather than failing" in:
        val withoutFeedbackId = ReportResponse(Seq(ReportOutput("responseCode", Json.toJson("201"))))

        withoutFeedbackId.feedbackId shouldBe None
        withoutFeedbackId.rdsCorrelationId shouldBe None
        withoutFeedbackId.responseMessage shouldBe None

      "return no action grids" in:
        ReportResponse(Seq.empty).englishActions shouldBe empty

    "the response code is not a numeric string" should:
      "return None" in:
        ReportResponse(Seq(ReportOutput("responseCode", Json.toJson("not-a-number")))).responseCode shouldBe None
