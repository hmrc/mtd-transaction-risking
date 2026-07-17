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
import play.api.http.Status.{BAD_REQUEST, FORBIDDEN, INTERNAL_SERVER_ERROR, OK, UNAUTHORIZED}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsJson, contentAsString, defaultAwaitTimeout, headers, status}
import uk.gov.hmrc.http.SessionKeys.authToken
import uk.gov.hmrc.mtdtransactionrisking.stubs.{AuthStub, CommonTestData, InsightsRiskStub, VatApiStub}
import uk.gov.hmrc.mtdtransactionrisking.support.IntegrationBaseSpec
import uk.gov.hmrc.mtdtransactionrisking.v1.controllers.GenerateFeedbackController
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.InsightsResponse

import scala.concurrent.Future


class GenerateFeedbackControllerISpec extends IntegrationBaseSpec:
  
  "GenerateFeedbackController" when :

    "POST /assist/:vrn" should :

      "return 200 with risk response and correlation ID header" when :
        "a valid VRN is provided, validation passes and insights-proxy responds successfully" in new Test:
          override def setupStubs(): StubMapping = {
            AuthStub.successfulAuthWith(vrn)
            VatApiStub.validationPasses()
            InsightsRiskStub.successResponse(vrn)
          }

          val response: Future[Result] = request(vrn)
          status(response) shouldBe OK
          headers(response).get("X-CorrelationId") shouldBe defined
          contentAsJson(response).as[InsightsResponse].insights.strategicRisk.riskScore shouldBe CommonTestData.setRiskScore

      "return 400" when :

        "user has an invalid VRN in their request" in new Test:
          override def setupStubs(): StubMapping = InsightsRiskStub.successResponse(vrn)

          val response: Future[Result] = request("Bad-vrn-1234")

          status(response) shouldBe BAD_REQUEST
          contentAsString(response) should include("VRN_INVALID")
          contentAsString(response) should include("The provided VRN is invalid")

        "the VAT return fails vat-api validation" in new Test:
          override def setupStubs(): StubMapping = {
            AuthStub.successfulAuthWith(vrn)
            VatApiStub.validationFails()
          }

          val response: Future[Result] = request(vrn)
          status(response) shouldBe BAD_REQUEST
          (contentAsJson(response) \ "code").as[String] shouldBe "INVALID_REQUEST"

      "return 401" when :

        "user has no authorisation in their request" in new Test:
          override def setupStubs(): StubMapping = InsightsRiskStub.successResponse(vrn)

          val response: Future[Result] = requestWithoutAuth(vrn)

          status(response) shouldBe UNAUTHORIZED
          contentAsString(response) should include("Failed to authorise request")
          contentAsString(response) should include("MissingBearerToken")

        "auth response does not contain required information" in new Test:
          override def setupStubs(): StubMapping = AuthStub.successfulAuthWithNoUserId()

          val response: Future[Result] = request(vrn)

          status(response) shouldBe UNAUTHORIZED
          contentAsString(response) shouldBe "Unable to retrieve required auth values"

      "return 403" when :

        "user has no VRN" in new Test:
          override def setupStubs(): StubMapping = AuthStub.successfulAuthWithNoEnrolments()

          val response: Future[Result] = request(vrn)

          status(response) shouldBe FORBIDDEN
          contentAsString(response) shouldBe "User has no MTD VAT enrolment"

        "user's VRN doesn't match request" in new Test:
          override def setupStubs(): StubMapping = AuthStub.successfulAuthWith("not-the-vrn")

          val response: Future[Result] = request(vrn)

          status(response) shouldBe FORBIDDEN
          contentAsString(response) shouldBe "User VRN does not match requested VRN"

      "return 500" when :
        "validation passes but insights-proxy returns 500" in new Test:
          override def setupStubs(): StubMapping = {
            AuthStub.successfulAuthWith(vrn)
            VatApiStub.validationPasses()
            InsightsRiskStub.serverErrorResponse()
          }

          val response: Future[Result] = request(vrn)
          status(response) shouldBe INTERNAL_SERVER_ERROR

        "validation passes but insights-proxy returns 503" in new Test:
          override def setupStubs(): StubMapping = {
            AuthStub.successfulAuthWith(vrn)
            VatApiStub.validationPasses()
            InsightsRiskStub.serviceUnavailableResponse()
          }

          val response: Future[Result] = request(vrn)
          status(response) shouldBe INTERNAL_SERVER_ERROR

        "validation passes but insights-proxy returns malformed JSON" in new Test:
          override def setupStubs(): StubMapping = {
            AuthStub.successfulAuthWith(vrn)
            VatApiStub.validationPasses()
            InsightsRiskStub.malformedJsonResponse()
          }

          val response: Future[Result] = request(vrn)
          status(response) shouldBe INTERNAL_SERVER_ERROR

        "vat-api validation returns an unexpected 500" in new Test:
          override def setupStubs(): StubMapping = {
            AuthStub.successfulAuthWith(vrn)
            VatApiStub.validationServerError()
          }

          val response: Future[Result] = request(vrn)
          status(response) shouldBe INTERNAL_SERVER_ERROR

  private trait Test:

    def vrn: String = CommonTestData.simpleVrn

    val validVatReturnBody: JsValue = Json.parse(
      """
        |{
        |  "periodKey": "AB12",
        |  "vatDueSales": 100.00,
        |  "vatDueAcquisitions": 100.00,
        |  "totalVatDue": 200.00,
        |  "vatReclaimedCurrPeriod": 100.00,
        |  "netVatDue": 100.00,
        |  "totalValueSalesExVAT": 500,
        |  "totalValuePurchasesExVAT": 500,
        |  "totalValueGoodsSuppliedExVAT": 500,
        |  "totalAcquisitionsExVAT": 500
        |}
        |""".stripMargin
    )

    def setupStubs(): StubMapping

    def request(vrn: String): Future[Result] =
      setupStubs()
      app.injector.instanceOf[GenerateFeedbackController].generateFeedback(vrn)(
        FakeRequest("POST", s"/assist/$vrn")
          .withSession(authToken -> vrn)
          .withHeaders(
            "Authorization" -> "Bearer abc123",
            "Accept" -> "application/vnd.hmrc.1.0+json",
            "Content-Type" -> "application/json"
          )
          .withBody(validVatReturnBody)
      )

    def requestWithoutAuth(vrn: String): Future[Result] =
      setupStubs()
      app.injector.instanceOf[GenerateFeedbackController].generateFeedback(vrn)(
        FakeRequest("POST", s"/assist/$vrn")
          .withHeaders(
            "Accept" -> "application/vnd.hmrc.1.0+json",
            "Content-Type" -> "application/json"
          )
          .withBody(validVatReturnBody)
      )