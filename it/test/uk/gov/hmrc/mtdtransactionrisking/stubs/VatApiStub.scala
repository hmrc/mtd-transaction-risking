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

object VatApiStub:

  private val validateUrl = urlPathMatching("/internal/validate/.*")

  def validationPasses(): StubMapping =
    stubFor(post(validateUrl).willReturn(aResponse().withStatus(204)))

  def validationFails(): StubMapping =
    stubFor(
      post(validateUrl).willReturn(
        aResponse()
          .withStatus(400)
          .withHeader("Content-Type", "application/json")
          .withBody(
            """{"code":"INVALID_REQUEST","message":"Invalid request","errors":[{"code":"VAT_TOTAL_VALUE","message":"...","path":"/totalVatDue"}]}"""
          )
      )
    )

  def validationServerError(): StubMapping =
    stubFor(post(validateUrl).willReturn(aResponse().withStatus(500)))