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
import play.api.http.HeaderNames
import play.api.http.Status.*


object AuthStub:

  val headers: (String, String) =
    HeaderNames.COOKIE -> SessionCookieBaker.bakeSessionCookie(Map("authToken"-> "mock-bearer-token"), Some("en"))

  def successfulAuthWith(vrn: String): StubMapping =
    stubFor(
      post(urlMatching("/auth/authorise"))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(
              s"""|{
                  |  "internalId": "12345-credId",
                  |  "allEnrolments": [
                  |    {
                  |      "key": "HMRC-MTD-VAT",
                  |      "identifiers": [
                  |        {
                  |          "key": "VRN",
                  |          "value": "$vrn"
                  |        }
                  |      ],
                  |      "state": "Activated"
                  |    }
                  |  ]
                  |}""".stripMargin
            )
        )
    )

  def successfulAuthWithNoEnrolments(): StubMapping =
    stubFor(
      post(urlMatching("/auth/authorise"))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(
              s"""|{
                  |  "internalId": "12345-credId",
                  |  "allEnrolments": []
                  |}""".stripMargin
            )
        )
    )

  def successfulAuthWithNoUserId(): StubMapping =
    stubFor(
      post(urlMatching("/auth/authorise"))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(
              s"""|{
                  |  "allEnrolments": []
                  |}""".stripMargin
            )
        )
    )
