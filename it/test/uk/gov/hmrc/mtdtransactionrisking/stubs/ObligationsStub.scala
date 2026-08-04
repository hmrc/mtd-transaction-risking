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
import play.api.libs.json.Json

object ObligationsStub:

  private def obligationsUrl(vrn: String) =
    s"/internal/organisations/vat/$vrn/obligations?status=O"

  def successResponse(vrn: String, periodKey: String, from: String, to: String): StubMapping =
    stubFor(
      get(urlEqualTo(obligationsUrl(vrn)))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withHeader("Content-Type", "application/json")
            .withBody(
              Json.obj(
                "obligations" -> Json.arr(
                  Json.obj(
                    "status" -> "O",
                    "start" -> from,
                    "end" -> to,
                    "due" -> to,
                    "periodKey" -> periodKey
                  )
                )
              ).toString()
            )
        )
    )

  def malformedSuccessResponse(vrn: String): StubMapping =
    stubFor(
      get(urlEqualTo(obligationsUrl(vrn)))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withHeader("Content-Type", "application/json")
            .withBody("""{"unexpected":"shape"}""")
        )
    )

  def singleFailureResponse(vrn: String): StubMapping =
    stubFor(
      get(urlEqualTo(obligationsUrl(vrn)))
        .willReturn(
          aResponse()
            .withStatus(BAD_REQUEST)
            .withHeader("Content-Type", "application/json")
            .withBody("""{"code":"INVALID_DATE_FROM","reason":"Invalid date"}""")
        )
    )

  def arrayFailureResponse(vrn: String): StubMapping =
    stubFor(
      get(urlEqualTo(obligationsUrl(vrn)))
        .willReturn(
          aResponse()
            .withStatus(BAD_REQUEST)
            .withHeader("Content-Type", "application/json")
            .withBody(
              """{"failures":[{"code":"INVALID_DATE_FROM","reason":"Invalid date"},{"code":"INVALID_STATUS","reason":"Invalid status"}]}"""
            )
        )
    )