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
import com.github.tomakehurst.wiremock.http.Fault
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.http.Status.*
import play.api.libs.json.Json

object AcknowledgeStub:

  private val acknowledgeUrl = urlEqualTo("/acknowledge")

  def successResponse(): StubMapping =
    stubFor(
      post(acknowledgeUrl)
        .willReturn(aResponse().withStatus(NO_CONTENT))
    )

  def reportIdInvalidResponse(): StubMapping =
    stubFor(
      post(acknowledgeUrl)
        .willReturn(
          aResponse()
            .withStatus(BAD_REQUEST)
            .withHeader("Content-Type", "application/json")
            .withBody(Json.obj("code" -> "FORMAT_REPORT_ID", "message" -> "The provided Report ID is invalid").toString)
        )
    )

  def correlationIdMismatchResponse(): StubMapping =
    stubFor(
      post(acknowledgeUrl)
        .willReturn(
          aResponse()
            .withStatus(FORBIDDEN)
            .withHeader("Content-Type", "application/json")
            .withBody(Json.obj("code" -> "CORRELATION_ID", "message" -> "The Correlation ID is not the expected value for this report").toString)
        )
    )

  def notFoundResponse(): StubMapping =
    stubFor(
      post(acknowledgeUrl)
        .willReturn(
          aResponse()
            .withStatus(NOT_FOUND)
            .withHeader("Content-Type", "application/json")
            .withBody(Json.obj("code" -> "MATCHING_RESOURCE_NOT_FOUND", "message" -> "Matching resource not found").toString)
        )
    )

  def connectionFaultResponse(): StubMapping =
    stubFor(
      post(acknowledgeUrl)
        .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
    )