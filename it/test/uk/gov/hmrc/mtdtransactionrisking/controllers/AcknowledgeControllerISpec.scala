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

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.http.Status.*
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsJson, contentAsString, defaultAwaitTimeout, headers, status}
import uk.gov.hmrc.http.SessionKeys.authToken
import uk.gov.hmrc.mtdtransactionrisking.stubs.{AcknowledgeStub, AuthStub, CommonTestData}
import uk.gov.hmrc.mtdtransactionrisking.support.IntegrationBaseSpec
import uk.gov.hmrc.mtdtransactionrisking.v1.controllers.AcknowledgeController
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.AcknowledgeRequest

import java.time.OffsetDateTime
import scala.concurrent.Future

class AcknowledgeControllerISpec extends IntegrationBaseSpec:

  override def servicesConfig: Map[String, Any] =
    super.servicesConfig ++ Map(
      "microservice.services.acknowledge-stub.host" -> mockHost,
      "microservice.services.acknowledge-stub.port" -> mockPort,
      "microservice.services.acknowledge-stub.submit-url" -> "/acknowledge"
    )

  override def beforeEach(): Unit =
    super.beforeEach()
    resetWireMock()

  "AcknowledgeController" when:

    "POST /acknowledge/:vrn/:reportId/:correlationId" should:

      "return 204" when:
        "a valid VRN is provided and acknowledge responds successfully" in new Test:
          override def setupStubs(): StubMapping =
            AuthStub.successfulAuthWith(vrn)
            AcknowledgeStub.successResponse(vrn, reportId, correlationId, presentedDateTime)

          val response: Future[Result] = request(vrn = vrn)
          status(response) shouldBe NO_CONTENT
          headers(response).get("X-CorrelationId") shouldBe Some(correlationId)
          contentAsString(response) shouldBe ""

      "return 400" when:
        "reportId format is invalid" in new Test:
          override def setupStubs(): StubMapping =
            AuthStub.successfulAuthWith(vrn)

          val response: Future[Result] = request(reportId = "bad-report-id")
          status(response) shouldBe BAD_REQUEST
          (contentAsJson(response) \ "code").as[String] shouldBe "FORMAT_RECEIPT_ID"
          headers(response).get("X-CorrelationId") shouldBe empty

        "correlationId format is invalid" in new Test:
          override def setupStubs(): StubMapping =
            AuthStub.successfulAuthWith(vrn)

          val response: Future[Result] = request(correlationId = "bad-correlation-id")
          status(response) shouldBe BAD_REQUEST
          (contentAsJson(response) \ "code").as[String] shouldBe "FORMAT_CORRELATION_ID"
          headers(response).get("X-CorrelationId") shouldBe empty

        "presentedDateTime is missing" in new Test:
          override def setupStubs(): StubMapping =
            AuthStub.successfulAuthWith(vrn)

          val response: Future[Result] = requestWithoutPresentDateTime()
          status(response) shouldBe BAD_REQUEST
          (contentAsJson(response) \ "code").as[String] shouldBe "FORMAT_DATETIME"
          headers(response).get("X-CorrelationId") shouldBe empty

      "return 404 with correlation header" when:
        "acknowledge upstream returns a pass-through client error" in new Test:
          override def setupStubs(): StubMapping =
            AuthStub.successfulAuthWith(vrn)
            stubAcknowledgeResponse(
              NOT_FOUND,
              Json.obj("code" -> "MATCHING_RESOURCE_NOT_FOUND", "message" -> "A matching resource was not found.").toString
            )

          val response: Future[Result] = request()
          status(response) shouldBe NOT_FOUND
          (contentAsJson(response) \ "code").as[String] shouldBe "MATCHING_RESOURCE_NOT_FOUND"
          headers(response).get("X-CorrelationId") shouldBe Some(correlationId)

      "return 500 with correlation header" when:
        "acknowledge upstream returns a non-pass-through error" in new Test:
          override def setupStubs(): StubMapping =
            AuthStub.successfulAuthWith(vrn)
            stubAcknowledgeResponse(
              INTERNAL_SERVER_ERROR,
              Json.obj("code" -> "INTERNAL_SERVER_ERROR", "message" -> "Upstream failed").toString
            )

          val response: Future[Result] = request()
          status(response) shouldBe INTERNAL_SERVER_ERROR
          (contentAsJson(response) \ "code").as[String] shouldBe "INTERNAL_SERVER_ERROR"
          headers(response).get("X-CorrelationId") shouldBe Some(correlationId)

  private trait Test:

    def vrn: String = CommonTestData.simpleVrn
    def reportId: String = "123456aB-012A-345B-678C-012345678abc"
    def correlationId: String = "a1e8057e-fbbc-47a8-a8b4-78d9f015c253"
    def presentedDateTime: String = "2023-06-01T12:00:00Z"

    def setupStubs(): StubMapping

    def request(
      vrn: String = this.vrn,
      reportId: String = this.reportId,
      correlationId: String = this.correlationId,
      presentedDateTime: String = this.presentedDateTime
    ): Future[Result] =
      setupStubs()
      app.injector.instanceOf[AcknowledgeController].acknowledgeReport(vrn, reportId, correlationId).apply(
        FakeRequest("POST", s"/acknowledge/$vrn/$reportId/$correlationId?presentedDateTime=$presentedDateTime")
          .withSession(authToken -> vrn)
          .withHeaders(
            "Authorization" -> "Bearer abc123",
            "Accept" -> "application/vnd.hmrc.1.0+json",
            "Content-Type" -> "application/json"
          )
      )

    def requestWithoutPresentDateTime(
      vrn: String = this.vrn,
      reportId: String = this.reportId,
      correlationId: String = this.correlationId
    ): Future[Result] =
      setupStubs()
      app.injector.instanceOf[AcknowledgeController].acknowledgeReport(vrn, reportId, correlationId).apply(
        FakeRequest("POST", s"/acknowledge/$vrn/$reportId/$correlationId")
          .withSession(authToken -> vrn)
          .withHeaders(
            "Authorization" -> "Bearer abc123",
            "Accept" -> "application/vnd.hmrc.1.0+json",
            "Content-Type" -> "application/json"
          )
      )

    def stubAcknowledgeResponse(statusCode: Int, body: String): StubMapping =
      stubFor(
        post(urlEqualTo("/acknowledge"))
          .withRequestBody(
            equalToJson(
              Json.toJson(
                AcknowledgeRequest(
                  vrn = vrn,
                  reportId = reportId,
                  correlationId = correlationId,
                  presentedDateTime = OffsetDateTime.parse(presentedDateTime)
                )
              ).toString
            )
          )
          .willReturn(
            aResponse()
              .withStatus(statusCode)
              .withHeader("Content-Type", "application/json")
              .withBody(body)
          )
      )