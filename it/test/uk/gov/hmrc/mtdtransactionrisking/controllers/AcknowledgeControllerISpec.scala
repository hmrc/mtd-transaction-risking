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
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.libs.ws.{EmptyBody, WSResponse, writeableOf_WsBody}
import play.api.test.Helpers.*
import uk.gov.hmrc.mtdtransactionrisking.stubs.*
import uk.gov.hmrc.mtdtransactionrisking.support.IntegrationBaseSpec

class AcknowledgeControllerISpec extends IntegrationBaseSpec:

  private val vrn = CommonTestData.simpleVrn
  private val arn = "LARN0085901"
  private val reportId = "f2fb30e5-4ab6-4a29-b3c1-c00000000001"
  private val requestCorrelationId = "E9F65715BBC9222477B27074804BBDD5C73CDE62F84D8B00CFD05B883534AF3D"
  private val presentedDateTime = "2026-06-09T10:30:00Z"

  private val rdsAcknowledgePath = "/rds/assessments/acknowledge"
  private val rsdPath = "/rsd/receive-and-store"
  private val nrsPath = "/nrs-orchestrator/submission"

  private val validUri: String =
    s"/acknowledge/$vrn/$reportId/$requestCorrelationId?presentedDateTime=$presentedDateTime"

  "POST /acknowledge/:vrn/:reportId/:correlationId" when:

    // ---------- Successful journeys ----------

    "every downstream responds successfully" should:

      "return 204, acknowledge the report in RDS, store it in RSD and submit it to NRS" in:
        stubFullJourney()

        acknowledge().status shouldBe NO_CONTENT

        // RSD and NRS are called in the background after the response
        eventually {
          wireMockServer.verify(postRequestedFor(urlPathMatching(rdsAcknowledgePath)))
          wireMockServer.verify(postRequestedFor(urlPathMatching(rsdPath)))
          wireMockServer.verify(
            postRequestedFor(urlPathMatching(nrsPath))
              .withRequestBody(matchingJsonPath("$.metadata.notableEvent", equalTo("vata-acknowledge-report")))
              .withRequestBody(matchingJsonPath("$.metadata.searchKeys.vrn", equalTo(vrn)))
              .withRequestBody(matchingJsonPath("$.metadata.searchKeys.reportId", equalTo(reportId)))
          )
        }

    "the user is an agent acting for their client" should:

      "return 204 and acknowledge the report in RDS" in:
        stubFullJourney(auth = AuthStub.successfulAuthWithAgent(arn))

        acknowledge().status shouldBe NO_CONTENT

        wireMockServer.verify(postRequestedFor(urlPathMatching(rdsAcknowledgePath)))

    "the user is an organisation" should:

      "return 204 store the report in RSD and submit the acknowledge-report event to NRS" in:
        stubFullJourney(auth = AuthStub.successfulAuthWithOrganisation(vrn))

        acknowledge().status shouldBe NO_CONTENT

        eventually {
          wireMockServer.verify(postRequestedFor(urlPathMatching(rdsAcknowledgePath)))
          wireMockServer.verify(postRequestedFor(urlPathMatching(rsdPath)))
          wireMockServer.verify(
            postRequestedFor(urlPathMatching(nrsPath))
              .withRequestBody(matchingJsonPath("$.metadata.notableEvent", equalTo("vata-acknowledge-report")))
          )
        }

    "RSD or NRS fails after RDS accepts the acknowledgement" should:

      "return 204 and submit to NRS when RSD is unavailable" in:
        AuthStub.successfulAuthWith(vrn)
        RdsStub.acknowledgeAccepted()
        InteractionStub.unavailable()
        NrsStub.accepts()

        acknowledge().status shouldBe NO_CONTENT

        eventually {
          wireMockServer.verify(
            postRequestedFor(urlPathMatching(nrsPath))
              .withRequestBody(matchingJsonPath("$.metadata.notableEvent", equalTo("vata-acknowledge-report")))
          )
        }

      "return 204 and store in RSD when NRS is unavailable" in:
        AuthStub.successfulAuthWith(vrn)
        RdsStub.acknowledgeAccepted()
        InteractionStub.stores()
        NrsStub.unavailable()

        acknowledge().status shouldBe NO_CONTENT

        eventually {
          wireMockServer.verify(postRequestedFor(urlPathMatching(rsdPath)))
          wireMockServer.verify(postRequestedFor(urlPathMatching(nrsPath)))
        }

    // ---------- Errors in order of the journey  ----------

    "the request is invalid" should:

      "return 400 REPORT_ID_INVALID when the report ID is not a UUId without calling any downstream" in:
        AuthStub.successfulAuthWith(vrn)

        val response = acknowledge(s"/acknowledge/$vrn/not-a-uuid/$requestCorrelationId?presentedDateTime=$presentedDateTime")

        assertError(response, BAD_REQUEST, "REPORT_ID_INVALID")
        assertNotCalled(rdsAcknowledgePath, rsdPath, nrsPath)

      "return 400 CORRELATION_ID_INVALID when the correlation ID is not 64 hex characters" in:
        AuthStub.successfulAuthWith(vrn)

        val response = acknowledge(s"/acknowledge/$vrn/$reportId/WrongCorrId?presentedDateTime=$presentedDateTime")

        assertError(response, BAD_REQUEST, "CORRELATION_ID_INVALID")
        assertNotCalled(rdsAcknowledgePath, rsdPath, nrsPath)

      "return 400 PRESENTED_DATE_TIME_INVALID when presentedDateTime is missing" in:
        AuthStub.successfulAuthWith(vrn)

        val response = acknowledge(s"/acknowledge/$vrn/$reportId/$requestCorrelationId")

        assertError(response, BAD_REQUEST, "PRESENTED_DATE_TIME_INVALID")
        assertNotCalled(rdsAcknowledgePath, rsdPath, nrsPath)

      "return 400 PRESENTED_DATE_TIME_INVALID when presentedDateTime is not a valid date-time" in:
        AuthStub.successfulAuthWith(vrn)

        val response = acknowledge(s"/acknowledge/$vrn/$reportId/$requestCorrelationId?presentedDateTime=09/06/2026")

        assertError(response, BAD_REQUEST, "PRESENTED_DATE_TIME_INVALID")
        assertNotCalled(rdsAcknowledgePath, rsdPath, nrsPath)

    "auth fails" should:

      "return 401 INVALID_CREDENTIALS when auth does not return an internal ID" in:
        AuthStub.successfulAuthWithNoUserId()

        assertError(acknowledge(), UNAUTHORIZED, "INVALID_CREDENTIALS")
        assertNotCalled(rdsAcknowledgePath, rsdPath, nrsPath)

      // Not enrolled for the VRN or an agent no relation with client
      "return 403 CLIENT_OR_AGENT_NOT_AUTHORISED when the user may not act for the VRN" in:
        AuthStub.insufficientEnrolments()

        assertError(acknowledge(), FORBIDDEN, "CLIENT_OR_AGENT_NOT_AUTHORISED")
        assertNotCalled(rdsAcknowledgePath, rsdPath, nrsPath)

    "RDS rejects the acknowledgement" should:

      "return 400 ACKNOWLEDGEMENT_VALIDATION_FAILED without storing in RSD or submitting to NRS" in:
        AuthStub.successfulAuthWith(vrn)
        RdsStub.acknowledgeRejected()
        InteractionStub.stores()

        assertError(acknowledge(), BAD_REQUEST, "ACKNOWLEDGEMENT_VALIDATION_FAILED")
        assertNotCalled(rsdPath, nrsPath)

    "RDS fails" should:

      "return 500 when RDS returns a malformed response without storing in RSD or submitting to NRS" in:
        AuthStub.successfulAuthWith(vrn)
        RdsStub.acknowledgeMalformedResponse()
        InteractionStub.stores()

        acknowledge().status shouldBe INTERNAL_SERVER_ERROR
        assertNotCalled(rsdPath, nrsPath)

      "return 503 when RDS is unavailable without storing in RSD or submitting to NRS" in:
        AuthStub.successfulAuthWith(vrn)
        RdsStub.acknowledgeUnavailable()
        InteractionStub.stores()

        acknowledge().status shouldBe SERVICE_UNAVAILABLE
        assertNotCalled(rsdPath, nrsPath)

  // ---------- Helpers ----------

  private def acknowledge(uri: String = validUri): WSResponse =
    await(buildRequest(uri).post(EmptyBody))

  private def stubFullJourney(auth: => StubMapping = AuthStub.successfulAuthWith(vrn)): Unit =
    auth
    RdsStub.acknowledgeAccepted()
    InteractionStub.stores()
    NrsStub.accepts()

  private def assertError(response: WSResponse, status: Int, code: String): Unit =
    withClue(s"Response body: ${response.body}. ") {
      response.status shouldBe status
      (response.json \ "code").as[String] shouldBe code
    }

  private def assertNotCalled(paths: String*): Unit =
    paths.foreach(path => wireMockServer.verify(0, postRequestedFor(urlPathMatching(path))))
