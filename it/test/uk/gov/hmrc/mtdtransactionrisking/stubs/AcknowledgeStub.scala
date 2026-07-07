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
import play.api.libs.json.Json
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.AcknowledgeRequest

import java.time.OffsetDateTime

object AcknowledgeStub:

  private val acknowledgeUrl = "/acknowledge"

  private def expectedRequestBody(
                                   vrn: String,
                                   reportId: String,
                                   correlationId: String,
                                   presentedDateTime: String
                                 ): String =
    Json.toJson(
      AcknowledgeRequest(
        vrn = vrn,
        reportId = reportId,
        correlationId = correlationId,
        presentedDateTime = OffsetDateTime.parse(presentedDateTime)
      )
    ).toString

  def successResponse(vrn: String, reportId: String, correlationId: String, presentedDateTime: String): StubMapping =
    stubFor(
      post(urlEqualTo(acknowledgeUrl))
        .withRequestBody(equalToJson(expectedRequestBody(vrn, reportId, correlationId, presentedDateTime)))
        .willReturn(
          aResponse()
            .withStatus(204)
            .withHeader("Content-Type", "application/json")
            .withBody(Json.obj().toString)
        )
    )