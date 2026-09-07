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

package uk.gov.hmrc.mtdtransactionrisking.controllers
import com.github.tomakehurst.wiremock.client.WireMock.{postRequestedFor, urlPathMatching}
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest.concurrent.Eventually
import play.api.libs.ws.{EmptyBody, WSResponse, writeableOf_WsBody}
import play.api.test.Helpers.*
import uk.gov.hmrc.mtdtransactionrisking.stubs.{AuthStub, CommonTestData, InteractionStub}
import uk.gov.hmrc.mtdtransactionrisking.support.IntegrationBaseSpec

class AcknowledgeControllerISpec extends IntegrationBaseSpec:

  private val vrn = CommonTestData.simpleVrn
  private val reportId = "f2fb30e5-4ab6-4a29-b3c1-c00000000001"
  private val requestCorrelationId = "c75f40a6-a3df-4429-a697-471eeec46435"
  private val presentedDateTime = "2026-06-09T10:30:00Z"

  private def uri: String =
    s"/acknowledge/$vrn/$reportId/$requestCorrelationId?presentedDateTime=$presentedDateTime"

  "POST /acknowledge/:vrn/:reportId/:correlationId" should:

    "return 204 and store the interaction" in new Test:
      override def setupStubs(): StubMapping =
        AuthStub.successfulAuthWith(vrn)
        InteractionStub.stores()

      val response: WSResponse = await(buildRequest(uri).post(EmptyBody))
      println(s"xxxxxxx${response.body}")
      response.status shouldBe NO_CONTENT

      eventually {
        wireMockServer.verify(postRequestedFor(urlPathMatching("/rsd/receive-and-store")))
      }

    "return 204 even when the interactions datastore is unavailable" in new Test:
      override def setupStubs(): StubMapping =
        AuthStub.successfulAuthWith(vrn)
        InteractionStub.unavailable()

      val response: WSResponse = await(buildRequest(uri).post(EmptyBody))

      response.status shouldBe NO_CONTENT

  private trait Test:
    def setupStubs(): StubMapping
    setupStubs()
