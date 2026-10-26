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

import com.github.tomakehurst.wiremock.client.WireMock.{equalTo, matchingJsonPath, postRequestedFor, urlPathMatching}
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.{WSResponse, writeableOf_JsValue, writeableOf_String}
import play.api.test.Helpers.*
import uk.gov.hmrc.mtdtransactionrisking.stubs.*
import uk.gov.hmrc.mtdtransactionrisking.support.IntegrationBaseSpec
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.{FeedbackLink, FeedbackMessage, FeedbackResponse}

import scala.concurrent.duration.*

class GenerateFeedbackControllerISpec extends IntegrationBaseSpec:

  private val vrn = CommonTestData.simpleVrn
  private val periodKey = "AB12"
  private val fromDate = "2020-01-01"
  private val toDate = "2020-03-31"
  private val rdsReportId = "f2fb30e5-4ab6-4a29-b3c1-c7264259ff1c"
  private val rdsCorrelationId = "E9F65715BBC9222477B27074804BBDD5C73CDE62F84D8B00CFD05B883534AF3D"

  private val authPath = "/auth/authorise"
  private val vatApiValidatePath = "/internal/validate/.*"
  private val rdsGeneratePath = "/rds/assessments/generate"
  private val rsdPath = "/rsd/receive-and-store"
  private val nrsPath = "/nrs-orchestrator/submission"

  private val validVatReturn: JsValue = Json.obj(
    "periodKey" -> periodKey,
    "vatDueSales" -> 100.00,
    "vatDueAcquisitions" -> 100.00,
    "totalVatDue" -> 200.00,
    "vatReclaimedCurrPeriod" -> 100.00,
    "netVatDue" -> 100.00,
    "totalValueSalesExVAT" -> 500,
    "totalValuePurchasesExVAT" -> 500,
    "totalValueGoodsSuppliedExVAT" -> 500,
    "totalAcquisitionsExVAT" -> 500
  )

  private val acceptHeader = "Accept" -> "application/vnd.hmrc.1.0+json"
  private val authHeader = "Authorization" -> "Bearer abc123"

  "POST /feedback/:vrn" when:

    "every downstream responds successfully" should:

      "return 200 with the feedback from RDS" in:
        stubFullJourney()

        val response = feedback()

        response.status shouldBe OK
        response.header("X-CorrelationId") shouldBe defined

        val result = response.json.as[FeedbackResponse]

        result.reportId shouldBe rdsReportId
        result.correlationId shouldBe Some(rdsCorrelationId)
        result.englishFeedback should have size 1
        result.welshFeedback should have size 1

        result.englishFeedback.head shouldBe FeedbackMessage(
          itemNumber = "1",
          title = "VAT Return Query",
          body = "Please review your VAT return figures.",
          action = Some("Check your sales records."),
          links = Some(List(FeedbackLink("VAT guidance", "https://www.gov.uk/vat-returns"))),
          path = Some("vatDueSales")
        )

        result.welshFeedback.head.links shouldBe Some(List(FeedbackLink("Canllawiau TAW", "https://www.gov.uk/ffurflenni-taw")))

      "send the period key and obligation dates to RDS" in:
        stubFullJourney()

        feedback().status shouldBe OK

        wireMockServer.verify(
          postRequestedFor(urlPathMatching(rdsGeneratePath))
            .withRequestBody(matchingJsonPath("$.inputs[?(@.name == 'periodKey')].value", equalTo(periodKey)))
            .withRequestBody(matchingJsonPath("$.inputs[?(@.name == 'startDate')].value", equalTo(fromDate)))
            .withRequestBody(matchingJsonPath("$.inputs[?(@.name == 'endDate')].value", equalTo(toDate)))
        )

      "store the interaction in RSD with the obligation dates and message paths" in:
        stubFullJourney()

        feedback().status shouldBe OK

        // The RSD call is made in the background after the response
        eventually {
          wireMockServer.verify(
            postRequestedFor(urlPathMatching(rsdPath))
              .withRequestBody(matchingJsonPath("$.eventName", equalTo("generate-report")))
              .withRequestBody(matchingJsonPath("$.metadata[0].vrn", equalTo(vrn)))
              .withRequestBody(matchingJsonPath("$.metadata[0].start", equalTo(fromDate)))
              .withRequestBody(matchingJsonPath("$.metadata[0].end", equalTo(toDate)))
              .withRequestBody(matchingJsonPath("$.payload.reportId", equalTo(rdsReportId)))
              .withRequestBody(matchingJsonPath("$.payload.messages[0].englishActions.path", equalTo("vatDueSales")))
              .withRequestBody(matchingJsonPath("$.payload.messages[0].welshActions.path", equalTo("vatDueSales")))
          )
        }

      "submit the request-feedback and generate-report events to NRS" in:
        stubFullJourney()

        feedback().status shouldBe OK

        // The NRS calls are made in the background after the response
        eventually {
          Seq("vata-request-feedback", "vata-generate-report").foreach { event =>
            wireMockServer.verify(
              postRequestedFor(urlPathMatching(nrsPath))
                .withRequestBody(matchingJsonPath("$.metadata.notableEvent", equalTo(event)))
                .withRequestBody(matchingJsonPath("$.metadata.searchKeys.vrn", equalTo(vrn)))
                .withRequestBody(matchingJsonPath("$.metadata.searchKeys.reportId", equalTo(rdsReportId)))
            )
          }
        }

    "RDS returns the no-feedback item 0" should:

      "return 200 with item 0, and no correlationId or path" in:
        stubUntilRds()
        RdsStub.noFeedback()

        val response = feedback()

        response.status shouldBe OK
        response.header("X-CorrelationId") shouldBe defined
        (response.json \ "correlationId").toOption shouldBe None

        Seq("englishFeedback", "welshFeedback").foreach { language =>
          (response.json \ language).as[Seq[JsValue]] should have size 1
          (response.json \ language \ 0 \ "itemNumber").as[String] shouldBe "0"
          (response.json \ language \ 0 \ "path").toOption shouldBe None
        }

      "not store an interaction in RSD or submit to NRS" in:
        stubUntilRds()
        RdsStub.noFeedback()
        InteractionStub.stores()
        NrsStub.accepts()

        feedback().status shouldBe OK

        assertNeverCalled(rsdPath)
        assertNeverCalled(nrsPath)

    "the user is an agent" should:

      "send the ARN to RDS" in:
        val arn = "LARN0085901"

        AuthStub.successfulAuthWithAgent(vrn, arn)
        VatApiStub.validationPasses(periodKey, fromDate, toDate)
        InsightsRiskStub.successResponse(vrn)
        RdsStub.reportGenerated()
        InteractionStub.stores()
        NrsStub.accepts()

        feedback().status shouldBe OK

        wireMockServer.verify(
          postRequestedFor(urlPathMatching(rdsGeneratePath))
            .withRequestBody(matchingJsonPath("$.inputs[?(@.name == 'agentReferenceNumber')].value", equalTo(arn)))
        )

    "the user is an organisation" should:

      "submit NRS evidence with the organisation's identity" in:
        AuthStub.successfulAuthWithOrganisation(vrn)
        VatApiStub.validationPasses(periodKey, fromDate, toDate)
        InsightsRiskStub.successResponse(vrn)
        RdsStub.reportGenerated()
        InteractionStub.stores()
        NrsStub.accepts()

        feedback().status shouldBe OK

        eventually {
          wireMockServer.verify(
            2,
            postRequestedFor(urlPathMatching(nrsPath))
              .withRequestBody(matchingJsonPath("$.metadata.identityData.affinityGroup", equalTo("Organisation")))
          )
        }

    "RSD or NRS fail after the report is generated" should:

      "return 200 and still submit to NRS when RSD is unavailable" in:
        stubUntilRds()
        RdsStub.reportGenerated()
        InteractionStub.unavailable()
        NrsStub.accepts()

        feedback().status shouldBe OK

        eventually {
          wireMockServer.verify(postRequestedFor(urlPathMatching(rsdPath)))
          wireMockServer.verify(2, postRequestedFor(urlPathMatching(nrsPath)))
        }

      "return 200 and still store in RSD when NRS is unavailable" in:
        stubUntilRds()
        RdsStub.reportGenerated()
        InteractionStub.stores()
        NrsStub.unavailable()

        feedback().status shouldBe OK

        eventually {
          wireMockServer.verify(postRequestedFor(urlPathMatching(rsdPath)))
          wireMockServer.verify(2, postRequestedFor(urlPathMatching(nrsPath)))
        }

    "the request is invalid" should:

      "return 400 VRN_INVALID without calling auth or any downstream" in:
        val response = feedback(vrn = "Bad-vrn-1234")

        assertError(response, BAD_REQUEST, "VRN_INVALID")
        assertNotCalled(authPath, vatApiValidatePath, rdsGeneratePath, rsdPath, nrsPath)

      "return 400 for malformed JSON without calling auth or any downstream" in:
        val response = await(
          buildRequest(s"/feedback/$vrn")
            .withHttpHeaders(acceptHeader, authHeader, "Content-Type" -> "application/json")
            .post("""{"periodKey":""")
        )

        // The body is parsed before the auth action runs
        response.status shouldBe BAD_REQUEST
        assertNotCalled(authPath, vatApiValidatePath, rdsGeneratePath, rsdPath, nrsPath)

    "vat-api rejects the VAT return" should:

      "return 400 INVALID_REQUEST and not call RDS" in:
        AuthStub.successfulAuthWith(vrn)
        VatApiStub.validationFails()

        assertError(feedback(), BAD_REQUEST, "INVALID_REQUEST")
        assertNotCalled(rdsGeneratePath, rsdPath, nrsPath)

      "return 400 TAX_PERIOD_NOT_ENDED and not call RDS" in:
        AuthStub.successfulAuthWith(vrn)
        VatApiStub.taxPeriodNotEnded()

        assertError(feedback(), BAD_REQUEST, "TAX_PERIOD_NOT_ENDED")
        assertNotCalled(rdsGeneratePath, rsdPath, nrsPath)

      "return 403 CLIENT_OR_AGENT_NOT_AUTHORISED and not call RDS" in:
        AuthStub.successfulAuthWith(vrn)
        VatApiStub.clientOrAgentNotAuthorised()

        assertError(feedback(), FORBIDDEN, "CLIENT_OR_AGENT_NOT_AUTHORISED")
        assertNotCalled(rdsGeneratePath, rsdPath, nrsPath)

    // Status only for now: VATAuthAction returns plain text. Assert the JSON codes
    // (INVALID_CREDENTIALS / CLIENT_OR_AGENT_NOT_AUTHORISED) once it returns MtdErrors.
    "the user is not authorised" should:

      "return 401 when the request has no Authorization header" in:
        val response = await(
          buildRequest(s"/feedback/$vrn")
            .withHttpHeaders(acceptHeader)
            .post(validVatReturn)
        )

        response.status shouldBe UNAUTHORIZED
        assertNotCalled(vatApiValidatePath, rdsGeneratePath)

      "return 401 when auth does not return the required values" in:
        AuthStub.successfulAuthWithNoUserId()

        feedback().status shouldBe UNAUTHORIZED
        assertNotCalled(vatApiValidatePath, rdsGeneratePath)

      "return 403 when the user has no MTD VAT enrolment" in:
        AuthStub.successfulAuthWithNoEnrolments()

        feedback().status shouldBe FORBIDDEN
        assertNotCalled(vatApiValidatePath, rdsGeneratePath)

      "return 403 when the user's VRN does not match the one requested" in:
        AuthStub.successfulAuthWith("999999999")

        feedback().status shouldBe FORBIDDEN
        assertNotCalled(vatApiValidatePath, rdsGeneratePath)

    "a downstream service returns an error or an unusable response" should:

      "return 500 when vat-api returns 200 with a body that is not an obligation" in:
        AuthStub.successfulAuthWith(vrn)
        VatApiStub.malformedObligation()

        assertError(feedback(), INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR")
        assertNotCalled(rdsGeneratePath, rsdPath, nrsPath)

      "return 500 when insights-proxy returns 500" in:
        AuthStub.successfulAuthWith(vrn)
        VatApiStub.validationPasses(periodKey, fromDate, toDate)
        InsightsRiskStub.serverErrorResponse()

        assertError(feedback(), INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR")
        assertNotCalled(rdsGeneratePath, rsdPath, nrsPath)

      "return 500 when insights-proxy returns malformed JSON" in:
        AuthStub.successfulAuthWith(vrn)
        VatApiStub.validationPasses(periodKey, fromDate, toDate)
        InsightsRiskStub.malformedJsonResponse()

        assertError(feedback(), INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR")
        assertNotCalled(rdsGeneratePath, rsdPath, nrsPath)

      "return 500 when RDS rejects the report request with 400" in:
        stubUntilRds()
        RdsStub.badRequest()

        assertError(feedback(), INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR")
        assertNotCalled(rsdPath, nrsPath)

      "return 500 when RDS returns 201 with a malformed report" in:
        stubUntilRds()
        RdsStub.malformedReport()

        assertError(feedback(), INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR")
        assertNotCalled(rsdPath, nrsPath)

      "return 500 when RDS returns 201 with a responseCode other than 201" in:
        stubUntilRds()
        RdsStub.reportResponds(responseCode = 500)

        assertError(feedback(), INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR")
        assertNotCalled(rsdPath, nrsPath)

      "return 500 when RDS returns a report with no feedbackId" in:
        stubUntilRds()
        RdsStub.reportWithoutFeedbackId()

        assertError(feedback(), INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR")
        assertNotCalled(rsdPath, nrsPath)

    "a downstream service is unavailable" should:

      "return 503 when vat-api's obligations lookup is unavailable" in:
        AuthStub.successfulAuthWith(vrn)
        VatApiStub.serviceUnavailable()

        // vat-api's body is relayed as it is so only the status is from us
        feedback().status shouldBe SERVICE_UNAVAILABLE
        assertNotCalled(rdsGeneratePath, rsdPath, nrsPath)

      "return 503 when insights-proxy is unavailable" in:
        AuthStub.successfulAuthWith(vrn)
        VatApiStub.validationPasses(periodKey, fromDate, toDate)
        InsightsRiskStub.serviceUnavailableResponse()

        assertError(feedback(), SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE")
        assertNotCalled(rdsGeneratePath, rsdPath, nrsPath)

      "return 503 when RDS is unavailable" in:
        stubUntilRds()
        RdsStub.unavailable()

        assertError(feedback(), SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE")
        assertNotCalled(rsdPath, nrsPath)

  private def feedback(vrn: String = vrn, body: JsValue = validVatReturn): WSResponse =
    await(
      buildRequest(s"/feedback/$vrn")
        .withHttpHeaders(acceptHeader, authHeader)
        .post(body)
    )

  private def stubFullJourney(): Unit =
    stubUntilRds()
    RdsStub.reportGenerated()
    InteractionStub.stores()
    NrsStub.accepts()

  // Auth, vat-api and insights succeed, so each test only sets up what happens at RDS and after
  private def stubUntilRds(): Unit =
    AuthStub.successfulAuthWith(vrn)
    VatApiStub.validationPasses(periodKey, fromDate, toDate)
    InsightsRiskStub.successResponse(vrn)

  private def assertError(response: WSResponse, status: Int, code: String): Unit =
    withClue(s"Response body: ${response.body}. ") {
      response.status shouldBe status
      (response.json \ "code").as[String] shouldBe code
    }

  // For paths that are called before the response is returned, or not at all once the journey has stopped
  private def assertNotCalled(paths: String*): Unit =
    paths.foreach(path => wireMockServer.verify(0, postRequestedFor(urlPathMatching(path))))

  // RSD and NRS are called in the background after the response, so watch for a short time rather than checking once
  private def assertNeverCalled(path: String, within: FiniteDuration = 2.seconds): Unit =
    val deadline = System.nanoTime() + within.toNanos
    while System.nanoTime() < deadline do
      wireMockServer.verify(0, postRequestedFor(urlPathMatching(path)))
      Thread.sleep(100)
