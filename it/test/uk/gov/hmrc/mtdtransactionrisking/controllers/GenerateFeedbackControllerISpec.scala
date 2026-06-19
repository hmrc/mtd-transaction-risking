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
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsJson, contentAsString, defaultAwaitTimeout, headers, status}
import uk.gov.hmrc.http.{ForbiddenException, UnauthorizedException}
import uk.gov.hmrc.http.SessionKeys.authToken
import uk.gov.hmrc.mtdtransactionrisking.stubs.{AuthStub, CommonTestData, InsightsRiskStub}
import uk.gov.hmrc.mtdtransactionrisking.support.IntegrationBaseSpec
import uk.gov.hmrc.mtdtransactionrisking.v1.controllers.GenerateFeedbackController
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.InsightsResponse

import scala.concurrent.Future


class GenerateFeedbackControllerISpec extends IntegrationBaseSpec:

  "GenerateFeedbackController" when :

    "POST /feedback/:vrn" should :

      "return 200 with risk response and correlation ID header" when :
        "a valid VRN is provided and cip-risk responds successfully" in new Test:
          override def setupStubs(): StubMapping = {
            AuthStub.successfulAuthWith(vrn)
            InsightsRiskStub.successResponse(vrn)
          }

          val response: Future[Result] = request(vrn)
          status(response) shouldBe OK
          headers(response).get("X-CorrelationId") shouldBe defined
          contentAsJson(response).as[InsightsResponse].insights.strategicRisk.riskScore shouldBe CommonTestData.setRiskScore

      "return 401" when :
        "user has no authorisation in their request" in new Test:
          override def setupStubs(): StubMapping = InsightsRiskStub.successResponse(vrn)

          val response: Future[Result] = requestWithoutAuth(vrn)

          status(response) shouldBe 401
          contentAsString(response) shouldBe
            "Failed to authorise request uk.gov.hmrc.auth.core.MissingBearerToken: Bearer token not supplied"

        "auth response has not contain required information" in new Test:
          override def setupStubs(): StubMapping = AuthStub.successfulAuthWithNoUserId()

          val response: Future[Result] = request(vrn)
          val exception: UnauthorizedException = intercept[UnauthorizedException](status(response))

          exception.responseCode shouldBe 401
          exception.message shouldBe "Unable to retrieve required auth values"

      "return 403" when :
        "user has no VRN" in new Test:
          override def setupStubs(): StubMapping = {
            AuthStub.successfulAuthWithNoEnrolments()
          }

          val response: Future[Result] = request(vrn)
          val exception: ForbiddenException = intercept[ForbiddenException](status(response))

          exception.responseCode shouldBe 403
          exception.message shouldBe "User has no MTD VAT enrolment"

        "user's VRN doesn't match request" in new Test:
          override def setupStubs(): StubMapping = {
            AuthStub.successfulAuthWith("not-the-vrn")
          }

          val response: Future[Result] = request(vrn)
          val exception: ForbiddenException = intercept[ForbiddenException](status(response))

          exception.responseCode shouldBe 403
          exception.message shouldBe "User VRN (not-the-vrn) does not match the requested VRN (123456789)"

      "return 500" when :
        "cip-risk returns 500" in new Test:
          override def setupStubs(): StubMapping = {
            AuthStub.successfulAuthWith(vrn)
            InsightsRiskStub.serverErrorResponse()
          }

          val response: Future[Result] = request(vrn)
          status(response) shouldBe INTERNAL_SERVER_ERROR

        "cip-risk returns 503" in new Test:
          override def setupStubs(): StubMapping = {
            AuthStub.successfulAuthWith(vrn)
            InsightsRiskStub.serviceUnavailableResponse()
          }

          val response: Future[Result] = request(vrn)
          status(response) shouldBe INTERNAL_SERVER_ERROR

        "cip-risk returns malformed JSON" in new Test:
          override def setupStubs(): StubMapping = {
            AuthStub.successfulAuthWith(vrn)
            InsightsRiskStub.malformedJsonResponse()
          }

          val response: Future[Result] = request(vrn)
          status(response) shouldBe INTERNAL_SERVER_ERROR

  private trait Test:

    def vrn: String = CommonTestData.simpleVrn

    def setupStubs(): StubMapping

    def request(vrn: String): Future[Result] =
      setupStubs()
      app.injector.instanceOf[GenerateFeedbackController].generateFeedback(vrn)(
        FakeRequest("POST", s"/feedback/$vrn")
          .withSession(authToken -> vrn)
            .withHeaders(
              "Authorization"-> "Bearer abc123",
              "Accept"       -> "application/vnd.hmrc.1.0+json",
              "Content-Type" -> "application/json"
            )
      )

    def requestWithoutAuth(vrn: String): Future[Result] =
      setupStubs()
      app.injector.instanceOf[GenerateFeedbackController].generateFeedback(vrn)(
        FakeRequest("POST", s"/feedback/$vrn")
          .withHeaders(
            "Accept"       -> "application/vnd.hmrc.1.0+json",
            "Content-Type" -> "application/json"
          )
      )