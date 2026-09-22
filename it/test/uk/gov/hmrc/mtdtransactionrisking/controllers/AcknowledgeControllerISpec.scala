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
import play.api.libs.ws.{EmptyBody, WSResponse, writeableOf_WsBody}
import play.api.test.Helpers.*
import uk.gov.hmrc.mtdtransactionrisking.stubs.{AuthStub, CommonTestData, InteractionStub, RdsStub}
import uk.gov.hmrc.mtdtransactionrisking.support.IntegrationBaseSpec

class AcknowledgeControllerISpec extends IntegrationBaseSpec:

  private val vrn = CommonTestData.simpleVrn
  private val reportId = "f2fb30e5-4ab6-4a29-b3c1-c00000000001"
  private val requestCorrelationId = "E9F65715BBC9222477B27074804BBDD5C73CDE62F84D8B00CFD05B883534AF3D"
  private val presentedDateTime = "2026-06-09T10:30:00Z"

  private def uri: String =
    s"/acknowledge/$vrn/$reportId/$requestCorrelationId?presentedDateTime=$presentedDateTime"

  "POST /acknowledge/:vrn/:reportId/:correlationId" when:
    
    "every downstream succeeds" should:

      "return 204 and store the interaction" in new Test:
        override def setupStubs(): StubMapping =
          AuthStub.successfulAuthWith(vrn)
          InteractionStub.stores()
          RdsStub.acknowledgeAccepted()

        val response: WSResponse = await(buildRequest(uri).post(EmptyBody))
        response.status shouldBe NO_CONTENT

        eventually {
          wireMockServer.verify(postRequestedFor(urlPathMatching("/rsd/receive-and-store")))
          wireMockServer.verify(postRequestedFor(urlPathMatching("/rds/assessments/acknowledge")))
        }

      "return 204 even when the interactions datastore is unavailable" in new Test:
        override def setupStubs(): StubMapping =
          AuthStub.successfulAuthWith(vrn)
          InteractionStub.unavailable()
          RdsStub.acknowledgeAccepted()

        val response: WSResponse = await(buildRequest(uri).post(EmptyBody))
        response.status shouldBe NO_CONTENT

      "return 204 and store the interaction when Auth returns an Organisation identity-data response" in new Test:

        override def setupStubs(): StubMapping =
          AuthStub.successfulAuthWithOrganisation(vrn)
          InteractionStub.stores()
          RdsStub.acknowledgeAccepted()

        val response: WSResponse = await(buildRequest(uri).post(EmptyBody))
        response.status shouldBe NO_CONTENT

        eventually {
          wireMockServer.verify(postRequestedFor(urlPathMatching("/rds/assessments/acknowledge")))
          wireMockServer.verify(postRequestedFor(urlPathMatching("/rsd/receive-and-store")))
        }  

    "the request fails validation" should:

      "return 400 when the report id is not a valid UUID" in new Test:
        override def setupStubs(): StubMapping = AuthStub.successfulAuthWith(vrn)

        val response: WSResponse =
          await(buildRequest(s"/acknowledge/$vrn/not-a-uuid/$requestCorrelationId?presentedDateTime=$presentedDateTime").post(EmptyBody))

        response.status shouldBe BAD_REQUEST
        (document(response) \ "code").as[String] shouldBe "REPORT_ID_INVALID"

      "return 400 when the correlation id is not 64 hex characters" in new Test:
        override def setupStubs(): StubMapping = AuthStub.successfulAuthWith(vrn)

        val response: WSResponse =
          await(buildRequest(s"/acknowledge/$vrn/$reportId/WrongCorrId?presentedDateTime=$presentedDateTime").post(EmptyBody))

        response.status shouldBe BAD_REQUEST
        (document(response) \ "code").as[String] shouldBe "CORRELATION_ID_INVALID"

      "return 400 when presentedDateTime is missing" in new Test:
        override def setupStubs(): StubMapping = AuthStub.successfulAuthWith(vrn)

        val response: WSResponse = await(buildRequest(s"/acknowledge/$vrn/$reportId/$requestCorrelationId").post(EmptyBody))

        response.status shouldBe BAD_REQUEST
        (document(response) \ "code").as[String] shouldBe "PRESENTED_DATE_TIME_INVALID"

      "return 400 when presentedDateTime is not a valid date-time" in new Test:
        override def setupStubs(): StubMapping = AuthStub.successfulAuthWith(vrn)

        val response: WSResponse =
          await(buildRequest(s"/acknowledge/$vrn/$reportId/$requestCorrelationId?presentedDateTime=09/06/2026").post(EmptyBody))

        response.status shouldBe BAD_REQUEST
        (document(response) \ "code").as[String] shouldBe "PRESENTED_DATE_TIME_INVALID"

      "not call RDS or the interaction store" in new Test:
        override def setupStubs(): StubMapping = AuthStub.successfulAuthWith(vrn)

        await(buildRequest(s"/acknowledge/$vrn/not-a-uuid/$requestCorrelationId?presentedDateTime=$presentedDateTime").post(EmptyBody))

        wireMockServer.verify(0, postRequestedFor(urlPathMatching("/rds/assessments/acknowledge")))
        wireMockServer.verify(0, postRequestedFor(urlPathMatching("/rsd/receive-and-store")))

    "RDS rejects or fails to process the acknowledgement" should:

      "return 503 and not store the interaction when RDS is unavailable" in new Test:
        override def setupStubs(): StubMapping =
          AuthStub.successfulAuthWith(vrn)
          InteractionStub.stores()
          RdsStub.acknowledgeUnavailable()

        val response: WSResponse = await(buildRequest(uri).post(EmptyBody))
        response.status shouldBe SERVICE_UNAVAILABLE

        wireMockServer.verify(0, postRequestedFor(urlPathMatching("/rsd/receive-and-store")))

      "return 500 and not store the interaction when RDS returns a malformed response" in new Test:
        override def setupStubs(): StubMapping =
          AuthStub.successfulAuthWith(vrn)
          InteractionStub.stores()
          RdsStub.acknowledgeMalformedResponse()

        val response: WSResponse = await(buildRequest(uri).post(EmptyBody))
        response.status shouldBe INTERNAL_SERVER_ERROR

        wireMockServer.verify(0, postRequestedFor(urlPathMatching("/rsd/receive-and-store")))

  private trait Test:
    def setupStubs(): StubMapping
    setupStubs()
