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

  private val reportUrl = urlPathMatching("/microanalyticScore/modules/HMRC_ASSIST_VAT_FINSUB_FEEDBACK/steps/execute")

  /** A 201 carrying a report with one english and one welsh feedback message. */
  def reportGenerated(): StubMapping =
    stubFor(
      post(reportUrl).willReturn(
        aResponse()
          .withStatus(CREATED)
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
              |{
              |  "outputs": [
              |    { "name": "correlationId", "value": "E9F65715BBC9222477B27074804BBDD5C73CDE62F84D8B00CFD05B883534AF3D" },
              |    { "name": "feedbackId", "value": "f2fb30e5-4ab6-4a29-b3c1-c7264259ff1c" },
              |    { "name": "responseCode", "value": "201" },
              |    { "name": "responseMessage", "value": "Feedback generated successfully" },
              |    {
              |      "name": "englishActions",
              |      "value": [
              |        { "metadata": [ { "itemNumber": "1" }, { "message": "m" }, { "action": "a" }, { "title": "t" },
              |                        [ { "linkTitle": "lt" }, { "linkUrl": "lu" } ], { "path": "p" } ] },
              |        { "data": [ [ "1", "Please review your VAT return figures.", "Check your sales records.",
              |                      "VAT Return Query",
              |                      [ { "linkTitle": "VAT guidance" }, { "linkUrl": "https://www.gov.uk/vat-returns" } ],
              |                      "vatDueSales" ] ] }
              |      ]
              |    },
              |    {
              |      "name": "welshActions",
              |      "value": [
              |        { "metadata": [ { "itemNumber": "1" }, { "message": "m" }, { "action": "a" }, { "title": "t" },
              |                        [ { "linkTitle": "lt" }, { "linkUrl": "lu" } ], { "path": "p" } ] },
              |        { "data": [ [ "1", "Adolygwch eich ffigurau.", "Gwiriwch eich cofnodion.",
              |                      "Ymholiad Ffurflen TAW",
              |                      [ { "linkTitle": "Canllawiau TAW" }, { "linkUrl": "https://www.gov.uk/ffurflenni-taw" } ],
              |                      "vatDueSales" ] ] }
              |      ]
              |    }
              |  ]
              |}
              |""".stripMargin
          )
      )
    )

  def badRequest(): StubMapping =
    stubFor(post(reportUrl).willReturn(aResponse().withStatus(BAD_REQUEST)))

  def unavailable(): StubMapping =
    stubFor(post(reportUrl).willReturn(aResponse().withStatus(SERVICE_UNAVAILABLE)))

  def malformedReport(): StubMapping =
    stubFor(
      post(reportUrl).willReturn(
        aResponse()
          .withStatus(CREATED)
          .withHeader("Content-Type", "application/json")
          .withBody("""{"unexpected":"shape"}""")
      ))