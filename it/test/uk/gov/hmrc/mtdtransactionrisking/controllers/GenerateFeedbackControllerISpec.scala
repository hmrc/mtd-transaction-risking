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

import com.github.tomakehurst.wiremock.client.WireMock.{postRequestedFor, urlPathMatching, matchingJsonPath, equalTo}
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.http.Status.*
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsJson, contentAsString, defaultAwaitTimeout, headers, status}
import uk.gov.hmrc.http.SessionKeys.authToken
import uk.gov.hmrc.mtdtransactionrisking.stubs.*
import uk.gov.hmrc.mtdtransactionrisking.support.IntegrationBaseSpec
import uk.gov.hmrc.mtdtransactionrisking.v1.controllers.GenerateFeedbackController
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.FeedbackResponse

import scala.concurrent.Future

class GenerateFeedbackControllerISpec extends IntegrationBaseSpec:

  "GenerateFeedbackController" when:

    "POST /feedback/:vrn" should:

      "return 200" when:

        "every downstream responds successfully" in new Test:
          override def setupStubs(): StubMapping =
            AuthStub.successfulAuthWith(vrn)
            VatApiStub.validationPasses(periodKey, fromDate, toDate)
            InsightsRiskStub.successResponse(vrn)
            RdsStub.reportGenerated()
            InteractionStub.stores()
            NrsStub.accepts()

          val response: Future[Result] = request(vrn)

          status(response) shouldBe OK
          headers(response).get("X-CorrelationId") shouldBe defined

          val feedback: FeedbackResponse = contentAsJson(response).as[FeedbackResponse]
          feedback.reportId shouldBe "f2fb30e5-4ab6-4a29-b3c1-c7264259ff1c"
          feedback.englishFeedback should have size 1
          feedback.welshFeedback should have size 1

          val txrReportId = headers(response).getOrElse(
            "X-CorrelationId",
            fail("Expected X-CorrelationId response header")
          )

          // Since rsd call is fire and forget this confirms the call was actually made
          eventually {
            wireMockServer.verify(postRequestedFor(urlPathMatching("/rsd/receive-and-store")))

            wireMockServer.verify(postRequestedFor(urlPathMatching("/nrs-orchestrator/submission"))
                .withRequestBody(matchingJsonPath("$.metadata.notableEvent", equalTo("vaa-request-feedback")))
                .withRequestBody(matchingJsonPath("$.metadata.searchKeys.vrn", equalTo(vrn)))
                .withRequestBody(matchingJsonPath("$.metadata.searchKeys.reportId", equalTo(txrReportId))))

            wireMockServer.verify(postRequestedFor(urlPathMatching("/nrs-orchestrator/submission"))
                .withRequestBody(matchingJsonPath("$.metadata.notableEvent", equalTo("vaa-report-generated")))
                .withRequestBody(matchingJsonPath("$.metadata.searchKeys.vrn", equalTo(vrn)))
                .withRequestBody(matchingJsonPath("$.metadata.searchKeys.reportId", equalTo(txrReportId))))
          }

        "RDS returns no-feedback messages without path" in new Test:
          override def setupStubs(): StubMapping =
            AuthStub.successfulAuthWith(vrn)
            VatApiStub.validationPasses(periodKey, fromDate, toDate)
            InsightsRiskStub.successResponse(vrn)
            RdsStub.noFeedbackWithoutPath()
            InteractionStub.stores()
            NrsStub.accepts()

          val response: Future[Result] = request(vrn)

          status(response) shouldBe OK
          headers(response).get("X-CorrelationId") shouldBe defined

          val json = contentAsJson(response)
          val feedback = json.as[FeedbackResponse]
          
          feedback.englishFeedback should have size 1
          feedback.welshFeedback should have size 1
          
          feedback.englishFeedback.head.itemNumber shouldBe "0"
          feedback.englishFeedback.head.path shouldBe None
          
          feedback.welshFeedback.head.itemNumber shouldBe "0"
          feedback.welshFeedback.head.path shouldBe None
          
          (json \ "englishFeedback" \ 0 \ "path").toOption shouldBe None
          (json \ "welshFeedback" \ 0 \ "path").toOption shouldBe None
          
          eventually {
            wireMockServer.verify(postRequestedFor(urlPathMatching("/rsd/receive-and-store")))
            wireMockServer.verify(0, postRequestedFor(urlPathMatching("/nrs-orchestrator/submission")))
          }

        "the interactions datastore is unavailable" in new Test:
          // Storage and NRS are both loose-coupled to the successful vendor response
          override def setupStubs(): StubMapping =
            AuthStub.successfulAuthWith(vrn)
            VatApiStub.validationPasses(periodKey, fromDate, toDate)
            InsightsRiskStub.successResponse(vrn)
            RdsStub.reportGenerated()
            InteractionStub.unavailable()
            NrsStub.accepts()

          val response: Future[Result] = request(vrn)

          status(response) shouldBe OK

          eventually {
            wireMockServer.verify(postRequestedFor(urlPathMatching("/rsd/receive-and-store")))
            wireMockServer.verify(2, postRequestedFor(urlPathMatching("/nrs-orchestrator/submission"))
            )
          }

        "NRS is unavailable after RDS returns actual feedback" in new Test:
          override def setupStubs(): StubMapping =
            AuthStub.successfulAuthWith(vrn)
            VatApiStub.validationPasses(periodKey, fromDate, toDate)
            InsightsRiskStub.successResponse(vrn)
            RdsStub.reportGenerated()
            InteractionStub.stores()
            NrsStub.unavailable()

          val response: Future[Result] = request(vrn)

          status(response) shouldBe OK

          eventually {
            /*
             * Both direct NRS submissions are attempted, but their 503
             * responses do not affect the successful VAT Assist response.
             */
            wireMockServer.verify(2, postRequestedFor(urlPathMatching("/nrs-orchestrator/submission")))
          }
          
        "Auth returns an Organisation identity-data response" in new Test:

          override def setupStubs(): StubMapping =
            AuthStub.successfulAuthWithOrganisation(vrn)
            VatApiStub.validationPasses(periodKey, fromDate, toDate)
            InsightsRiskStub.successResponse(vrn)
            RdsStub.reportGenerated()
            InteractionStub.stores()
            NrsStub.accepts()

          status(request(vrn)) shouldBe OK

          eventually {wireMockServer.verify(2, postRequestedFor(urlPathMatching("/nrs-orchestrator/submission")))
          }


        "Auth returns an Agent identity-data response and preserves the existing ARN journey" in new Test:

          val arn = "LARN0085901"

          override def setupStubs(): StubMapping =
            AuthStub.successfulAuthWithAgent(vrn, arn)
            VatApiStub.validationPasses(periodKey, fromDate, toDate)
            InsightsRiskStub.successResponse(vrn)
            RdsStub.reportGenerated()
            InteractionStub.stores()
            NrsStub.accepts()


          status(request(vrn)) shouldBe OK

          eventually {
            wireMockServer.verify(
              postRequestedFor(urlPathMatching("/rds/assessments/generate"))
                .withRequestBody(
                  matchingJsonPath(
                    "$.agentReferenceNumber",
                    equalTo(arn)
                  )
                )
            )

            wireMockServer.verify(
              2,
              postRequestedFor(urlPathMatching("/nrs-orchestrator/submission"))
            )
          }

      "return 400" when:

        "the VRN in the request is invalid" in new Test:
          override def setupStubs(): StubMapping = InsightsRiskStub.successResponse(vrn)

          val response: Future[Result] = request("Bad-vrn-1234")

          status(response) shouldBe BAD_REQUEST
          contentAsString(response) should include("VRN_INVALID")
          contentAsString(response) should include("The provided VRN is invalid")

        "vat-api fails with validation errors the VAT return" in new Test:
          override def setupStubs(): StubMapping =
            AuthStub.successfulAuthWith(vrn)
            VatApiStub.validationFails()

          val response: Future[Result] = request(vrn)

          status(response) shouldBe BAD_REQUEST
          (contentAsJson(response) \ "code").as[String] shouldBe "INVALID_REQUEST"

          wireMockServer.verify(
            0,
            postRequestedFor(urlPathMatching("/nrs-orchestrator/submission"))
          )

        "vat-api reports that the tax period has not ended" in new Test:
          override def setupStubs(): StubMapping =
            AuthStub.successfulAuthWith(vrn)
            VatApiStub.taxPeriodNotEnded()

          val response: Future[Result] = request(vrn)

          status(response) shouldBe BAD_REQUEST
          (contentAsJson(response) \ "code").as[String] shouldBe "TAX_PERIOD_NOT_ENDED"

          wireMockServer.verify(
            0,
            postRequestedFor(urlPathMatching("/nrs-orchestrator/submission"))
          )

      "return 401" when:

        "the request carries no authorisation" in new Test:
          override def setupStubs(): StubMapping = InsightsRiskStub.successResponse(vrn)

          val response: Future[Result] = requestWithoutAuth(vrn)

          status(response) shouldBe UNAUTHORIZED
          contentAsString(response) should include("Failed to authorise request")
          contentAsString(response) should include("MissingBearerToken")

        "the auth response is missing required values" in new Test:
          override def setupStubs(): StubMapping = AuthStub.successfulAuthWithNoUserId()

          val response: Future[Result] = request(vrn)

          status(response) shouldBe UNAUTHORIZED
          contentAsString(response) shouldBe "Unable to retrieve required auth values"

      "return 403" when:

        "the user has no MTD VAT enrolment" in new Test:
          override def setupStubs(): StubMapping = AuthStub.successfulAuthWithNoEnrolments()

          val response: Future[Result] = request(vrn)

          status(response) shouldBe FORBIDDEN
          contentAsString(response) shouldBe "User has no MTD VAT enrolment"

        "the user's VRN does not match the one requested" in new Test:
          override def setupStubs(): StubMapping = AuthStub.successfulAuthWith("not-the-vrn")

          val response: Future[Result] = request(vrn)

          status(response) shouldBe FORBIDDEN
          contentAsString(response) shouldBe "User VRN does not match requested VRN"

      "return 500" when:

        "vat-api returns 200 with a body that is not an obligation" in new Test:
          override def setupStubs(): StubMapping =
            AuthStub.successfulAuthWith(vrn)
            VatApiStub.malformedObligation()

          val response: Future[Result] = request(vrn)

          status(response) shouldBe INTERNAL_SERVER_ERROR

          wireMockServer.verify(
            0,
            postRequestedFor(urlPathMatching("/nrs-orchestrator/submission"))
          )

        "insights-proxy returns 500" in new Test:
          override def setupStubs(): StubMapping =
            AuthStub.successfulAuthWith(vrn)
            VatApiStub.validationPasses(periodKey, fromDate, toDate)
            InsightsRiskStub.serverErrorResponse()

          val response: Future[Result] = request(vrn)

          status(response) shouldBe INTERNAL_SERVER_ERROR

          wireMockServer.verify(
            0,
            postRequestedFor(urlPathMatching("/nrs-orchestrator/submission"))
          )

        "insights-proxy returns 503" in new Test:
          override def setupStubs(): StubMapping =
            AuthStub.successfulAuthWith(vrn)
            VatApiStub.validationPasses(periodKey, fromDate, toDate)
            InsightsRiskStub.serviceUnavailableResponse()

          val response: Future[Result] = request(vrn)

          status(response) shouldBe INTERNAL_SERVER_ERROR

          wireMockServer.verify(
            0,
            postRequestedFor(urlPathMatching("/nrs-orchestrator/submission"))
          )

        "insights-proxy returns malformed JSON" in new Test:
          override def setupStubs(): StubMapping =
            AuthStub.successfulAuthWith(vrn)
            VatApiStub.validationPasses(periodKey, fromDate, toDate)
            InsightsRiskStub.malformedJsonResponse()

          val response: Future[Result] = request(vrn)

          status(response) shouldBe INTERNAL_SERVER_ERROR

          wireMockServer.verify(
            0,
            postRequestedFor(urlPathMatching("/nrs-orchestrator/submission"))
          )

        "RDS returns 201 with a malformed report, and does not store an interaction" in new Test:
          override def setupStubs(): StubMapping =
            AuthStub.successfulAuthWith(vrn)
            VatApiStub.validationPasses(periodKey, fromDate, toDate)
            InsightsRiskStub.successResponse(vrn)
            RdsStub.malformedReport()

          status(request(vrn)) shouldBe INTERNAL_SERVER_ERROR

          wireMockServer.verify(0, postRequestedFor(urlPathMatching("/rsd/receive-and-store")))

          wireMockServer.verify(0, postRequestedFor(urlPathMatching("/nrs-orchestrator/submission")))

      "return 503" when:

        "vat-api's obligations lookup is unavailable" in new Test:
          override def setupStubs(): StubMapping =
            AuthStub.successfulAuthWith(vrn)
            VatApiStub.serviceUnavailable()

          val response: Future[Result] = request(vrn)

          status(response) shouldBe SERVICE_UNAVAILABLE

          wireMockServer.verify(0, postRequestedFor(urlPathMatching("/nrs-orchestrator/submission")))

        "RDS is unavailable, and does not store an interaction" in new Test:
          override def setupStubs(): StubMapping =
            AuthStub.successfulAuthWith(vrn)
            VatApiStub.validationPasses(periodKey, fromDate, toDate)
            InsightsRiskStub.successResponse(vrn)
            RdsStub.unavailable()

          status(request(vrn)) shouldBe SERVICE_UNAVAILABLE

          wireMockServer.verify(0, postRequestedFor(urlPathMatching("/rsd/receive-and-store")))
          wireMockServer.verify(0, postRequestedFor(urlPathMatching("/nrs-orchestrator/submission")))

  private trait Test:

    val periodKey: String = "AB12"
    val fromDate: String = "2020-01-01"
    val toDate: String = "2020-03-31"

    private val validVatReturnBody: JsValue = Json.parse(
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

    def vrn: String = CommonTestData.simpleVrn

    def setupStubs(): StubMapping

    def request(vrn: String): Future[Result] =
      setupStubs()
      app.injector
        .instanceOf[GenerateFeedbackController]
        .generateFeedback(vrn)(
          FakeRequest("POST", s"/feedback/$vrn")
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
      app.injector
        .instanceOf[GenerateFeedbackController]
        .generateFeedback(vrn)(
          FakeRequest("POST", s"/feedback/$vrn")
            .withHeaders(
              "Accept" -> "application/vnd.hmrc.1.0+json",
              "Content-Type" -> "application/json"
            )
            .withBody(validVatReturnBody)
        )
