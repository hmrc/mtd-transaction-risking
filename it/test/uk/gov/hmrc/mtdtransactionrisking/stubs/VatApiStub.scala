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
import play.api.http.Status.{BAD_REQUEST, OK, SERVICE_UNAVAILABLE}

/** Stubs vat-api's internal validate endpoint, which validates a VAT return and returns the open
 * obligation matching its period key.
 */
object VatApiStub:

  private val validateUrl = urlPathMatching("/internal/validate/.*")

  /** Validation passed and an obligation was matched — 200 with the obligation. */
  def validationPasses(periodKey: String = "AB12", fromDate: String = "2020-01-01", toDate: String = "2020-03-31"): StubMapping =
    stubFor(
      post(validateUrl).willReturn(
        aResponse()
          .withStatus(OK)
          .withHeader("Content-Type", "application/json")
          .withBody(
            s"""
               |{
               |  "periodKey": "$periodKey",
               |  "start": "$fromDate",
               |  "end": "$toDate",
               |  "due": "$toDate",
               |  "status": "O"
               |}
               |""".stripMargin
          )
      )
    )

  /** A 200 whose body doesn't parse as an obligation. */
  def malformedObligation(): StubMapping =
    stubFor(
      post(validateUrl).willReturn(
        aResponse()
          .withStatus(OK)
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "unexpected": "shape" }""")
      )
    )

  /** The VAT return failed validation — 400 with the field errors. */
  def validationFails(): StubMapping =
    stubFor(
      post(validateUrl).willReturn(
        aResponse()
          .withStatus(BAD_REQUEST)
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
              |{
              |  "code": "INVALID_REQUEST",
              |  "message": "Invalid request",
              |  "errors": [
              |    {
              |      "code": "VAT_TOTAL_VALUE",
              |      "message": "totalVatDue should be equal to vatDueSales + vatDueAcquisitions",
              |      "path": "/totalVatDue"
              |    }
              |  ]
              |}
              |""".stripMargin
          )
      )
    )

  /** The period key matched an obligation, but that period has not yet ended — 400. */
  def taxPeriodNotEnded(): StubMapping =
    stubFor(
      post(validateUrl).willReturn(
        aResponse()
          .withStatus(BAD_REQUEST)
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
              |{
              |  "code": "TAX_PERIOD_NOT_ENDED",
              |  "message": "The remote endpoint has indicated that the submission is for a tax period that has not ended"
              |}
              |""".stripMargin
          )
      )
    )

  /** vat-api's obligations lookup failed — 503. */
  def serviceUnavailable(): StubMapping =
    stubFor(
      post(validateUrl).willReturn(
        aResponse()
          .withStatus(SERVICE_UNAVAILABLE)
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
              |{
              |  "code": "SERVICE_UNAVAILABLE",
              |  "message": "Internal server error"
              |}
              |""".stripMargin
          )
      )
    )