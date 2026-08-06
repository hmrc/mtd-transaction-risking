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
import play.api.libs.json.Json

object VatApiStub:

  private val validateUrl = urlPathMatching("/internal/validate/.*")
  private def obligationsUrl(vrn: String) =
    s"/internal/organisations/vat/$vrn/obligations?status=O"

  def validationPasses(periodKey: String = "AB12", fromDate: String = "2020-01-01", toDate: String = "2020-03-31"): StubMapping =
    stubFor(
      post(validateUrl).willReturn(
        aResponse()
          .withStatus(OK)
          .withHeader("Content-Type", "application/json")
          .withBody(
            Json.obj(
              "status" -> "O",
              "start" -> fromDate,
              "end" -> toDate,
              "due" -> toDate,
              "periodKey" -> periodKey
            ).toString()
          )
      )
    )

  def validationFails(): StubMapping =
    stubFor(
      post(validateUrl).willReturn(
        aResponse()
          .withStatus(BAD_REQUEST)
          .withHeader("Content-Type", "application/json")
          .withBody(
            """{"code":"INVALID_REQUEST","message":"Invalid request","errors":[{"code":"VAT_TOTAL_VALUE","message":"...","path":"/totalVatDue"}]}"""
          )
      )
    )

  def validationServiceUnavailable(): StubMapping =
    stubFor(post(validateUrl).willReturn(aResponse().withStatus(SERVICE_UNAVAILABLE)))

  def obligationsSuccessResponse(vrn: String, periodKey: String, fromDate: String, toDate: String): StubMapping =
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
                    "start" -> fromDate,
                    "end" -> toDate,
                    "due" -> toDate,
                    "periodKey" -> periodKey
                  )
                )
              ).toString()
            )
        )
    )

  def obligationsMalformedSuccessResponse(vrn: String): StubMapping =
    stubFor(get(urlEqualTo(obligationsUrl(vrn))).willReturn(aResponse().withStatus(OK).withHeader("Content-Type", "application/json").withBody("""{"unexpected":"shape"}""")))

  def obligationsServiceUnavailable(vrn: String): StubMapping =
    stubFor(get(urlEqualTo(obligationsUrl(vrn))).willReturn(aResponse().withStatus(SERVICE_UNAVAILABLE)))
