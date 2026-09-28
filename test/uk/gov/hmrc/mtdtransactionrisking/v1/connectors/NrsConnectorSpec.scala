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
import org.scalatest.BeforeAndAfterAll
import play.api.libs.json.Json
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.mtdtransactionrisking.support.{ConnectorSpec, MockAppConfig}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.nrs.{Metadata, NrsSubmission, SearchKeys}

class NrsConnectorSpec extends ConnectorSpec, MockAppConfig, BeforeAndAfterAll:

  private val path = "/nrs-orchestrator/submission"

  private val submission = NrsSubmission(
    payload = "eyJwZXJpb2RLZXkiOiJBQjEyIn0=",
    metadata = Metadata(
      businessId = "vaa",
      notableEvent = "vaa-request-feedback",
      payloadContentType = "application/json",
      payloadSha256Checksum = "checksum",
      userSubmissionTimestamp = "2026-09-24T10:15:30Z",
      identityData = Json.obj("internalId" -> "internal-id"),
      userAuthToken = "Bearer vendor-token",
      headerData = Json.obj("Accept" -> "application/vnd.hmrc.1.0+json"),
      searchKeys = SearchKeys(
        vrn = "123456789",
        reportId = "a1e8057e-fbbc-47a8-a8b4-78d9f015c253"
      )
    )
  )

  override def beforeAll(): Unit =
    wireMockServer.start()

  override def afterAll(): Unit =
    wireMockServer.stop()

  private trait Test:
    MockedAppConfig.nrsSubmissionUrl
      .returns(s"http://localhost:${wireMockServer.port()}$path")
      .anyNumberOfTimes()

    val httpClient: HttpClientV2 = app.injector.instanceOf[HttpClientV2]
    val connector = new NrsConnector(httpClient, mockAppConfig)

  "NrsConnector.submit" should :

    "send X-API-Key and a generated UUID X-Correlation-Id, and accept HTTP 202" in new Test:
      MockedAppConfig.nrsApiKey
        .returns("nrs-test-api-key")
        .anyNumberOfTimes()

      wireMockServer.resetAll()

      wireMockServer.stubFor(
        post(urlPathEqualTo(path))
          .withHeader("X-API-Key", equalTo("nrs-test-api-key"))
          .withHeader("X-Correlation-Id", matching("[0-9a-fA-F-]{36}"))
          .willReturn(aResponse().withStatus(202))
      )

      await(connector.submit(submission))

      wireMockServer.verify(
        postRequestedFor(urlPathEqualTo(path))
          .withHeader("X-API-Key", equalTo("nrs-test-api-key"))
          .withHeader("X-Correlation-Id", matching("[0-9a-fA-F-]{36}"))
      )

    "not fail the caller when NRS returns a non-202 response" in new Test:
      MockedAppConfig.nrsApiKey
        .returns("nrs-test-api-key")
        .anyNumberOfTimes()

      wireMockServer.resetAll()

      wireMockServer.stubFor(
        post(urlPathEqualTo(path))
          .willReturn(aResponse().withStatus(500))
      )

      noException shouldBe thrownBy(
        await(connector.submit(submission))
      )

      wireMockServer.verify(
        postRequestedFor(urlPathEqualTo(path))
      )
