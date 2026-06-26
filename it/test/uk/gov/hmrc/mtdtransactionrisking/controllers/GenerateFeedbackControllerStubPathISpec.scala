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
import play.api.libs.json.{JsArray, Json}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.http.SessionKeys.authToken
import uk.gov.hmrc.mtdtransactionrisking.stubs.{AuthStub, CommonTestData, FeedbackStub}
import uk.gov.hmrc.mtdtransactionrisking.support.IntegrationBaseSpec
import uk.gov.hmrc.mtdtransactionrisking.v1.controllers.GenerateFeedbackController

import scala.concurrent.Future

class GenerateFeedbackControllerStubPathISpec extends IntegrationBaseSpec:

  override def servicesConfig: Map[String, Any] =
    super.servicesConfig ++ Map(
      "microservice.services.feedback-stub.host"       -> mockHost,
      "microservice.services.feedback-stub.port"       -> mockPort,
      "microservice.services.feedback-stub.submit-url" -> "/feedback"
    )

  "GenerateFeedbackController" when:

    "POST /feedback/:vrn with feedback-stub configured" should:

      "return 200 with single feedback response" when:
        "no Gov-Test-Scenario header is sent" in new Test:
          override def setupStubs(): StubMapping =
            AuthStub.successfulAuthWith(vrn)
            FeedbackStub.successResponse()

          val response: Future[Result] = request(vrn)
          status(response) shouldBe OK
          headers(response).get("X-CorrelationId") shouldBe defined
          (contentAsJson(response) \ "reportId").asOpt[String]            shouldBe defined
          (contentAsJson(response) \ "correlationId").asOpt[String]       shouldBe defined
          (contentAsJson(response) \ "englishFeedback").as[JsArray].value should not be empty
          (contentAsJson(response) \ "welshFeedback").as[JsArray].value   should not be empty

      "return 200 with empty feedback arrays" when:
        "feedback-stub returns NO_FEEDBACK response" in new Test:
          override def setupStubs(): StubMapping =
            AuthStub.successfulAuthWith(vrn)
            FeedbackStub.noFeedbackResponse()

          val response: Future[Result] = request(vrn)
          status(response) shouldBe OK
          (contentAsJson(response) \ "englishFeedback").as[JsArray].value shouldBe empty
          (contentAsJson(response) \ "welshFeedback").as[JsArray].value   shouldBe empty

      "return 500" when:
        "feedback-stub returns 500" in new Test:
          override def setupStubs(): StubMapping =
            AuthStub.successfulAuthWith(vrn)
            FeedbackStub.serverErrorResponse()

          val response: Future[Result] = request(vrn)
          status(response) shouldBe INTERNAL_SERVER_ERROR

  private trait Test:

    def vrn: String = CommonTestData.simpleVrn

    def setupStubs(): StubMapping

    private val validBody = Json.obj(
      "periodKey"                    -> "18AD",
      "vatDueSales"                  -> 100.00,
      "vatDueAcquisitions"           -> 100.00,
      "totalVatDue"                  -> 200.00,
      "vatReclaimedCurrPeriod"       -> 100.00,
      "netVatDue"                    -> 100.00,
      "totalValueSalesExVAT"         -> 1000,
      "totalValuePurchasesExVAT"     -> 1000,
      "totalValueGoodsSuppliedExVAT" -> 1000,
      "totalAcquisitionsExVAT"       -> 1000
    )

    def request(vrn: String): Future[Result] =
      setupStubs()
      app.injector.instanceOf[GenerateFeedbackController].generateFeedback(vrn)(
        FakeRequest("POST", s"/feedback/$vrn")
          .withSession(authToken -> vrn)
          .withHeaders(
            "Authorization" -> "Bearer abc123",
            "Accept"        -> "application/vnd.hmrc.1.0+json",
            "Content-Type"  -> "application/json"
          )
          .withJsonBody(validBody)
      )