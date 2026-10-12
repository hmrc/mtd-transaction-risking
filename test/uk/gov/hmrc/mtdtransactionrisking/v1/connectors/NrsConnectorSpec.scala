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

package uk.gov.hmrc.mtdtransactionrisking.v1.connectors

import com.github.tomakehurst.wiremock.client.WireMock.*
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import play.api.libs.json.Json
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.mtdtransactionrisking.support.{ConnectorSpec, MockAppConfig}
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.nrs.{Metadata, NrsSubmission, NrsSubmissionResult, SearchKeys}

class NrsConnectorSpec
  extends ConnectorSpec
    with MockAppConfig
    with BeforeAndAfterAll
    with BeforeAndAfterEach:

  private val path =
    "/nrs-orchestrator/submission"

  private val apiKey =
    "nrs-test-api-key"

  private val correlationId =
    CorrelationId("bd6c0972-34e7-11f1-9715-f35b3eb40b52")

  private val submission =
    NrsSubmission(
      payload = "eyJwZXJpb2RLZXkiOiJBQjEyIn0=",
      metadata = Metadata(
        businessId = "vata",
        notableEvent = "vata-request-feedback",
        payloadContentType = "application/json",
        payloadSha256Checksum = "checksum",
        userSubmissionTimestamp = "2026-09-24T10:15:30Z",
        identityData = Json.obj(
          "internalId" -> "internal-id"
        ),
        userAuthToken = "Bearer vendor-token",
        headerData = Json.obj(
          "Accept" -> "application/vnd.hmrc.1.0+json"
        ),
        searchKeys = SearchKeys(
          vrn = "123456789",
          reportId = "a1e8057e-fbbc-47a8-a8b4-78d9f015c253"
        )
      )
    )

  override protected def beforeAll(): Unit =
    super.beforeAll()
    wireMockServer.start()

  override protected def afterAll(): Unit =
    try wireMockServer.stop()
    finally super.afterAll()

  override protected def beforeEach(): Unit =
    super.beforeEach()
    wireMockServer.resetAll()

  private trait Test:
    MockedAppConfig.nrsSubmissionUrl
      .returns(
        s"http://localhost:${wireMockServer.port()}$path"
      )
      .anyNumberOfTimes()

    MockedAppConfig.nrsApiKey
      .returns(apiKey)
      .anyNumberOfTimes()

    val httpClient: HttpClientV2 =
      app.injector.instanceOf[HttpClientV2]

    val connector =
      new NrsConnector(
        httpClient = httpClient,
        appConfig = mockAppConfig
      )

  "NrsConnector.submit" should:

    "send the supplied correlation ID unchanged and accept HTTP 202" in new Test:
      val submissionJson =
        Json.stringify(
          Json.toJson(submission)
        )

      wireMockServer.stubFor(
        post(urlPathEqualTo(path))
          .withHeader(
            "X-API-Key",
            equalTo(apiKey)
          )
          .withHeader(
            "X-Correlation-Id",
            equalTo(correlationId.value)
          )
          .withHeader(
            "Content-Type",
            containing("application/json")
          )
          .withRequestBody(
            equalToJson(submissionJson)
          )
          .willReturn(
            aResponse()
              .withStatus(202)
          )
      )

      await(
        connector.submit(submission, correlationId)
      ) shouldBe NrsSubmissionResult.Success

      wireMockServer.verify(
        postRequestedFor(urlPathEqualTo(path))
          .withHeader(
            "X-API-Key",
            equalTo(apiKey)
          )
          .withHeader(
            "X-Correlation-Id",
            equalTo(correlationId.value)
          )
          .withHeader(
            "Content-Type",
            containing("application/json")
          )
          .withRequestBody(
            equalToJson(submissionJson)
          )
      )

    "classify HTTP 429 as retryable" in new Test:
      wireMockServer.stubFor(
        post(urlPathEqualTo(path))
          .willReturn(
            aResponse()
              .withStatus(429)
          )
      )

      await(
        connector.submit(submission, correlationId)
      ) shouldBe NrsSubmissionResult.RetryableFailure

    "classify HTTP 499 as retryable" in new Test:
      wireMockServer.stubFor(
        post(urlPathEqualTo(path))
          .willReturn(
            aResponse()
              .withStatus(499)
          )
      )

      await(
        connector.submit(submission, correlationId)
      ) shouldBe NrsSubmissionResult.RetryableFailure

    Seq(500, 503, 599).foreach { status =>
      s"classify HTTP $status as retryable" in new Test:
        wireMockServer.stubFor(
          post(urlPathEqualTo(path))
            .willReturn(
              aResponse()
                .withStatus(status)
            )
        )

        await(
          connector.submit(submission, correlationId)
        ) shouldBe NrsSubmissionResult.RetryableFailure
    }

    "classify HTTP 422 as permanent" in new Test:
      wireMockServer.stubFor(
        post(urlPathEqualTo(path))
          .willReturn(
            aResponse()
              .withStatus(422)
          )
      )

      await(
        connector.submit(submission, correlationId)
      ) shouldBe NrsSubmissionResult.PermanentFailure

    Seq(400, 401, 403, 404).foreach { status =>
      s"classify HTTP $status as permanent" in new Test:
        wireMockServer.stubFor(
          post(urlPathEqualTo(path))
            .willReturn(
              aResponse()
                .withStatus(status)
            )
        )

        await(
          connector.submit(submission, correlationId)
        ) shouldBe NrsSubmissionResult.PermanentFailure
    }

    "reuse the supplied correlation ID for repeated attempts" in new Test:
      wireMockServer.stubFor(
        post(urlPathEqualTo(path))
          .willReturn(
            aResponse()
              .withStatus(503)
          )
      )

      await(
        connector.submit(submission, correlationId)
      ) shouldBe NrsSubmissionResult.RetryableFailure

      await(
        connector.submit(submission, correlationId)
      ) shouldBe NrsSubmissionResult.RetryableFailure

      wireMockServer.verify(
        2,
        postRequestedFor(urlPathEqualTo(path))
          .withHeader(
            "X-Correlation-Id",
            equalTo(correlationId.value)
          )
          .withRequestBody(
            equalToJson(Json.stringify(Json.toJson(submission)))
          )
      )
