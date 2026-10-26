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

package uk.gov.hmrc.mtdtransactionrisking.stubs

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.http.Status.*

object RdsStub:

  private val generateRdsUrl = urlPathMatching("/rds/assessments/generate")
  private val acknowledgeRdsUrl = urlPathMatching("/rds/assessments/acknowledge")

  private val actionColumns =
    """{ "metadata": [ { "ITEMNUMBER": "string" }, { "MESSAGE": "string" }, { "ACTION": "string" }, { "TITLE": "string" },
      |                { "LINKTITLE": "string" }, { "LINKURL": "string" }, { "PATH": "string" } ] }""".stripMargin

  def reportGenerated(): StubMapping =
    stubFor(post(generateRdsUrl).willReturn(created(report())))

  // The report with one feedback message
  private def report(responseCode: Option[Int] = None, includeFeedbackId: Boolean = true): String =
    val responseCodeOutput = responseCode.fold("")(code => s"""{ "name": "responseCode", "value": $code.0 },""")
    val feedbackIdOutput = if includeFeedbackId then """{ "name": "feedbackId", "value": "f2fb30e5-4ab6-4a29-b3c1-c7264259ff1c" },""" else ""

    s"""
       |{
       |  "moduleId": "HMRC_ASSIST_VAT_FINSUB_FEEDBACK",
       |  "executionState": "completed",
       |  "outputs": [
       |    { "name": "correlationId", "value": "E9F65715BBC9222477B27074804BBDD5C73CDE62F84D8B00CFD05B883534AF3D" },
       |    { "name": "createdDttm", "value": "2026-10-09T19:00:19.246" },
       |    $responseCodeOutput
       |    {
       |      "name": "englishActions",
       |      "value": [
       |        $actionColumns,
       |        { "data": [ [ "1", "Please review your VAT return figures.", "Check your sales records.", "VAT Return Query",
       |                      "VAT guidance", "https://www.gov.uk/vat-returns", "vatDueSales" ] ] }
       |      ]
       |    },
       |    $feedbackIdOutput
       |    { "name": "vrn", "value": "123456789" },
       |    {
       |      "name": "welshActions",
       |      "value": [
       |        $actionColumns,
       |        { "data": [ [ "1", "Adolygwch eich ffigurau.", "Gwiriwch eich cofnodion.", "Ymholiad Ffurflen TAW",
       |                      "Canllawiau TAW", "https://www.gov.uk/ffurflenni-taw", "vatDueSales" ] ] }
       |      ]
       |    },
       |    { "name": "rt_Record_Contacts_Outer", "value": "f9a5a9a7-09a2-a94b-a492-54c9fc91d5d2" }
       |  ]
       |}
       |""".stripMargin

  private def created(body: String) =
    aResponse()
      .withStatus(CREATED)
      .withHeader("Content-Type", "application/json")
      .withBody(body)

  // A 201 responseCode output says the report was not generated
  def reportResponds(responseCode: Int): StubMapping =
    stubFor(post(generateRdsUrl).willReturn(created(report(responseCode = Some(responseCode)))))

  // A 201 that parses as a report but has no feedbackId to return to the vendor
  def reportWithoutFeedbackId(): StubMapping =
    stubFor(post(generateRdsUrl).willReturn(created(report(includeFeedbackId = false))))

  // Item 0 "no feedback" report (PATH "0", which we drop)
  def noFeedback(): StubMapping =
    stubFor(
      post(generateRdsUrl).willReturn(
        created(
          s"""
             |{
             |  "moduleId": "HMRC_ASSIST_VAT_FINSUB_FEEDBACK",
             |  "executionState": "completed",
             |  "outputs": [
             |    { "name": "correlationId", "value": null },
             |    { "name": "createdDttm", "value": "2026-10-12T13:40:11.102" },
             |    {
             |      "name": "englishActions",
             |      "value": [
             |        $actionColumns,
             |        { "data": [ [ "0", "This is not a complete check of your return or confirmation that your return is accurate.",
             |                      "Consider any further checks before finalising your return.",
             |                      "HMRC Feedback: HMRC Assist has not returned any messages.",
             |                      "Find out more about HMRC Assist on GOV.UK", "https://www.gov.uk/hmrc-assist", "0" ] ] }
             |      ]
             |    },
             |    { "name": "feedbackId", "value": "f2fb30e5-4ab6-4a29-b3c1-c7264259ff1c" },
             |    { "name": "vrn", "value": "123456789" },
             |    {
             |      "name": "welshActions",
             |      "value": [
             |        $actionColumns,
             |        { "data": [ [ "0", "Nid yw hwn yn wiriad cyflawn o'ch Ffurflen Dreth.",
             |                      "Ystyriwch unrhyw wiriadau pellach.",
             |                      "Adborth CThEF: Nid yw HMRC Assist wedi dychwelyd unrhyw negeseuon.",
             |                      "Ewch i GOV.UK i gael rhagor o wybodaeth am HMRC Assist", "https://www.gov.uk/hmrc-assist", "0" ] ] }
             |      ]
             |    },
             |    { "name": "rt_Record_Contacts_Outer", "value": "f9a5a9a7-09a2-a94b-a492-54c9fc91d5d2" }
             |  ]
             |}
             |""".stripMargin
        )
      )
    )

  def badRequest(): StubMapping =
    stubFor(post(generateRdsUrl).willReturn(aResponse().withStatus(BAD_REQUEST)))

  def unavailable(): StubMapping =
    stubFor(post(generateRdsUrl).willReturn(aResponse().withStatus(SERVICE_UNAVAILABLE)))

  def malformedReport(): StubMapping =
    stubFor(post(generateRdsUrl).willReturn(created("""{"unexpected":"shape"}""")))

  def acknowledgeAccepted(): StubMapping = acknowledgeResponds(responseCode = 202, responseMessage = "Accepted")

  def acknowledgeRejected(): StubMapping = acknowledgeResponds(responseCode = 401, responseMessage = "Acknowledgement validation failed")

  private def acknowledgeResponds(responseCode: Int, responseMessage: String): StubMapping =
    stubFor(
      post(acknowledgeRdsUrl).willReturn(
        created(
          s"""
             |{
             |  "moduleId": "HMRC_ASSIST_VAT_FINSUB_FEEDBACK_ACK",
             |  "executionState": "completed",
             |  "outputs": [
             |    { "name": "VRN", "value": "123456789" },
             |    { "name": "createdDttm", "value": "2026-10-12T14:25:44.688" },
             |    { "name": "feedbackId", "value": "f2fb30e5-4ab6-4a29-b3c1-c00000000001" },
             |    { "name": "responseCode", "value": $responseCode.0 },
             |    { "name": "responseMessage", "value": "$responseMessage" },
             |    { "name": "rt_Record_Contacts_Outer", "value": "bb07e500-8a7e-0a48-b3b0-8f639f4bf413" }
             |  ]
             |}
             |""".stripMargin
        )
      )
    )

  def acknowledgeUnavailable(): StubMapping =
    stubFor(post(acknowledgeRdsUrl).willReturn(aResponse().withStatus(SERVICE_UNAVAILABLE)))

  def acknowledgeMalformedResponse(): StubMapping =
    stubFor(post(acknowledgeRdsUrl).willReturn(created("""{"unexpected":"shape"}""")))

  def acknowledgeBadRequest(): StubMapping =
    stubFor(post(acknowledgeRdsUrl).willReturn(aResponse().withStatus(BAD_REQUEST)))
