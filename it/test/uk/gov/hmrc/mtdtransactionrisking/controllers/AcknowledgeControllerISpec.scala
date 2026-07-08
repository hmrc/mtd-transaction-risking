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

import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.libs.ws.{EmptyBody, writeableOf_WsBody, WSResponse}
import play.api.test.Helpers.*
import uk.gov.hmrc.mtdtransactionrisking.stubs.{AcknowledgeStub, AuthStub, CommonTestData}
import uk.gov.hmrc.mtdtransactionrisking.support.IntegrationBaseSpec

class AcknowledgeControllerISpec extends IntegrationBaseSpec:

  override def servicesConfig: Map[String, Any] =
    super.servicesConfig ++ Map(
      "microservice.services.acknowledge-stub.host"       -> mockHost,
      "microservice.services.acknowledge-stub.port"       -> mockPort,
      "microservice.services.acknowledge-stub.submit-url" -> "/acknowledge"
    )

  private val vrn               = CommonTestData.simpleVrn
  private val reportId          = "f2fb30e5-4ab6-4a29-b3c1-c00000000001"
  private val requestCorrId     = "c75f40a6-a3df-4429-a697-471eeec46435"
  private val presentedDateTime = "2026-06-09T10:30:00Z"

  private def uri: String =
    s"/acknowledge/$vrn/$reportId/$requestCorrId?presentedDateTime=$presentedDateTime"

  "POST /acknowledge/:vrn/:reportId/:correlationId" when:

    "the downstream acknowledges successfully" should:
      "return 204 with a correlation header" in new Test:
        override def setupStubs(): StubMapping =
          AuthStub.successfulAuthWith(vrn)
          AcknowledgeStub.successResponse()

        val response: WSResponse = await(buildRequest(uri).post(EmptyBody))
        response.status shouldBe NO_CONTENT
        response.header("X-CorrelationId") shouldBe defined

    "the downstream returns a report ID format error" should:
      "return 400 with FORMAT_REPORT_ID" in new Test:
        override def setupStubs(): StubMapping =
          AuthStub.successfulAuthWith(vrn)
          AcknowledgeStub.reportIdInvalidResponse()

        val response: WSResponse = await(buildRequest(uri).post(EmptyBody))
        response.status shouldBe BAD_REQUEST
        (document(response) \ "code").as[String] shouldBe "FORMAT_REPORT_ID"

    "the downstream returns a correlation ID mismatch" should:
      "return 403 with CORRELATION_ID" in new Test:
        override def setupStubs(): StubMapping =
          AuthStub.successfulAuthWith(vrn)
          AcknowledgeStub.correlationIdMismatchResponse()

        val response: WSResponse = await(buildRequest(uri).post(EmptyBody))
        response.status shouldBe FORBIDDEN
        (document(response) \ "code").as[String] shouldBe "CORRELATION_ID"

    "the downstream cannot find the report" should:
      "return 404 with MATCHING_RESOURCE_NOT_FOUND" in new Test:
        override def setupStubs(): StubMapping =
          AuthStub.successfulAuthWith(vrn)
          AcknowledgeStub.notFoundResponse()

        val response: WSResponse = await(buildRequest(uri).post(EmptyBody))
        response.status shouldBe NOT_FOUND
        (document(response) \ "code").as[String] shouldBe "MATCHING_RESOURCE_NOT_FOUND"

    "the user has no MTD VAT enrolment" should:
      "return 403" in new Test:
        override def setupStubs(): StubMapping = AuthStub.successfulAuthWithNoEnrolments()

        val response: WSResponse = await(buildRequest(uri).post(EmptyBody))
        response.status shouldBe FORBIDDEN

    "auth returns no internal id" should:
      "return 401" in new Test:
        override def setupStubs(): StubMapping = AuthStub.successfulAuthWithNoUserId()

        val response: WSResponse = await(buildRequest(uri).post(EmptyBody))
        response.status shouldBe UNAUTHORIZED

    "the downstream connection faults" should :
      "return 500 INTERNAL_SERVER_ERROR" in new Test:
        override def setupStubs(): StubMapping =
          AuthStub.successfulAuthWith(vrn)
          AcknowledgeStub.connectionFaultResponse()

        val response: WSResponse = await(buildRequest(uri).post(EmptyBody))
        response.status shouldBe INTERNAL_SERVER_ERROR
        (document(response) \ "code").as[String] shouldBe "INTERNAL_SERVER_ERROR"

  private trait Test:
    def setupStubs(): StubMapping
    setupStubs()