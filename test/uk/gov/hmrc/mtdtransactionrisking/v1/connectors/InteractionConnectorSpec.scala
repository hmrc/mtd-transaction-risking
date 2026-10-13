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
import com.github.tomakehurst.wiremock.http.Fault
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import play.api.libs.json.Json
import play.api.test.Injecting
import uk.gov.hmrc.http.{BadGatewayException, GatewayTimeoutException}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.mtdtransactionrisking.support.{ConnectorSpec, MockAppConfig}
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.models.auth.InteractionCredentials
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.{Interaction, InteractionMetadata, InteractionPayload}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.rsd.RsdSubmissionResult
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.rsd.RsdSubmissionResult.*

import java.net.http.HttpTimeoutException
import java.net.{ConnectException, SocketTimeoutException, UnknownHostException}
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLException

class InteractionConnectorSpec
  extends ConnectorSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with Injecting
    with MockAppConfig:

  private val storePath =
    "/rsd/receive-and-store"

  private val urlPattern =
    urlPathMatching(storePath)

  private val clientId =
    "stub-client-id"

  private val clientSecret =
    "stub-client-secret"

  private val correlationId =
    CorrelationId("bd6c0972-34e7-11f1-9715-f35b3eb40b52")

  private val expectedBasicAuth =
    "Basic " + Base64.getEncoder.encodeToString(
      s"$clientId:$clientSecret".getBytes(UTF_8)
    )

  private val interaction =
    Interaction(
      serviceRegime = "vat-assist",
      eventName = "generate-report",
      feedbackId = "f2fb30e5-4ab6-4a29-b3c1-c00000000001",
      eventTimestamp = "2026-08-13T09:00:00Z",
      metadata = Seq(
        InteractionMetadata(
          vrn = "123456789",
          start = Some("2026-01-01"),
          end = Some("2026-03-31"),
          additionalProperties = Json.obj(
            "periodKey" -> "AB12"
          )
        )
      ),
      payload = InteractionPayload(
        reportId = "f2fb30e5-4ab6-4a29-b3c1-c00000000001",
        messages = Some(Seq.empty)
      )
    )

  private trait Test:
    MockedAppConfig.interactionsBaseUrl
      .returns(
        s"http://localhost:${wireMockServer.port()}$storePath"
      )
      .anyNumberOfTimes()

    MockedAppConfig.interactionCredentials
      .returns(
        InteractionCredentials(
          clientId = clientId,
          clientSecret = clientSecret
        )
      )
      .anyNumberOfTimes()

    val httpClient: HttpClientV2 =
      app.injector.instanceOf[HttpClientV2]

    val connector =
      new InteractionConnector(
        httpClient = httpClient,
        appConfig = mockAppConfig
      )

    def stubStore(status: Int): StubMapping =
      wireMockServer.stubFor(
        post(urlPattern).willReturn(
          aResponse().withStatus(status)
        )
      )

    def stubConnectionReset(): StubMapping =
      wireMockServer.stubFor(
        post(urlPattern).willReturn(
          aResponse().withFault(
            Fault.CONNECTION_RESET_BY_PEER
          )
        )
      )

  override protected def beforeAll(): Unit =
    super.beforeAll()
    wireMockServer.start()

  override protected def beforeEach(): Unit =
    super.beforeEach()
    wireMockServer.resetAll()

  override protected def afterAll(): Unit =
    try wireMockServer.stop()
    finally super.afterAll()

  "InteractionConnector.store" should:

    "return Success on HTTP 204" in new Test:
      stubStore(204)

      await(
        connector.store(interaction, correlationId)
      ) shouldBe Success

    "return PermanentFailure on HTTP 400" in new Test:
      stubStore(400)

      await(
        connector.store(interaction, correlationId)
      ) shouldBe PermanentFailure

    "return RetryableFailure on HTTP 499" in new Test:
      stubStore(499)

      await(
        connector.store(interaction, correlationId)
      ) shouldBe RetryableFailure

    Seq(500, 503, 599).foreach { status =>
      s"return RetryableFailure on HTTP $status" in new Test:
        stubStore(status)

        await(
          connector.store(interaction, correlationId)
        ) shouldBe RetryableFailure
    }

    Seq(401, 403, 404, 408, 409, 422, 429).foreach { status =>
      s"return PermanentFailure on non-ticket HTTP status $status" in new Test:
        stubStore(status)

        await(
          connector.store(interaction, correlationId)
        ) shouldBe PermanentFailure
    }

    "return RetryableFailure on a transient connection reset" in new Test:
      stubConnectionReset()

      await(
        connector.store(interaction, correlationId)
      ) shouldBe RetryableFailure

    "send the existing Basic authentication credentials" in new Test:
      stubStore(204)

      await(
        connector.store(interaction, correlationId)
      ) shouldBe Success

      wireMockServer.verify(
        postRequestedFor(urlPattern)
          .withHeader(
            "Authorization",
            equalTo(expectedBasicAuth)
          )
      )

    "send the supplied CorrelationId header unchanged" in new Test:
      stubStore(204)

      await(
        connector.store(interaction, correlationId)
      ) shouldBe Success

      wireMockServer.verify(
        postRequestedFor(urlPattern)
          .withHeader(
            "CorrelationId",
            equalTo(correlationId.value)
          )
      )

    "send JSON content type and accept any response type" in new Test:
      stubStore(204)

      await(
        connector.store(interaction, correlationId)
      ) shouldBe Success

      wireMockServer.verify(
        postRequestedFor(urlPattern)
          .withHeader(
            "Content-Type",
            containing("application/json")
          )
          .withHeader(
            "Accept",
            equalTo("*/*")
          )
      )

    "send the original interaction as the request body" in new Test:
      stubStore(204)

      await(
        connector.store(interaction, correlationId)
      ) shouldBe Success

      wireMockServer.verify(
        postRequestedFor(urlPattern)
          .withRequestBody(
            equalToJson(
              Json.stringify(Json.toJson(interaction)),
              true,
              false
            )
          )
      )

    "reuse the supplied CorrelationId unchanged on repeated delivery attempts" in new Test:
      stubStore(503)

      await(
        connector.store(interaction, correlationId)
      ) shouldBe RetryableFailure

      await(
        connector.store(interaction, correlationId)
      ) shouldBe RetryableFailure

      wireMockServer.verify(
        2,
        postRequestedFor(urlPattern)
          .withHeader(
            "CorrelationId",
            equalTo(correlationId.value)
          )
          .withRequestBody(
            equalToJson(
              Json.stringify(Json.toJson(interaction))
            )
          )
      )

  "InteractionConnector.classifyTransportFailure" should:

    "classify GatewayTimeoutException as retryable" in:
      InteractionConnector.classifyTransportFailure(
        new GatewayTimeoutException("RSD request timed out")
      ) shouldBe RetryableFailure

    "classify BadGatewayException as retryable" in:
      InteractionConnector.classifyTransportFailure(
        new BadGatewayException("RSD connection failed")
      ) shouldBe RetryableFailure

    "classify a nested TimeoutException as retryable" in:
      InteractionConnector.classifyTransportFailure(
        new RuntimeException(
          new TimeoutException("read timed out")
        )
      ) shouldBe RetryableFailure

    "classify a nested ConnectException as retryable" in:
      InteractionConnector.classifyTransportFailure(
        new RuntimeException(
          new ConnectException("connection refused")
        )
      ) shouldBe RetryableFailure

    "classify a nested UnknownHostException as retryable" in:
      InteractionConnector.classifyTransportFailure(
        new RuntimeException(
          new UnknownHostException("rsd.example")
        )
      ) shouldBe RetryableFailure

    "classify SSL/TLS failures as permanent" in:
      InteractionConnector.classifyTransportFailure(
        new RuntimeException(
          new SSLException("certificate validation failed")
        )
      ) shouldBe PermanentFailure

    "classify unknown failures as permanent" in:
      InteractionConnector.classifyTransportFailure(
        new IllegalStateException("invalid configuration")
      ) shouldBe PermanentFailure

    "classify a nested SocketTimeoutException as retryable" in :
      InteractionConnector.classifyTransportFailure(
        new RuntimeException(
          new SocketTimeoutException("socket read timed out")
        )
      ) shouldBe RetryableFailure

    "classify HttpTimeoutException as retryable" in:
      InteractionConnector.classifyTransportFailure(
        new HttpTimeoutException("request timed out")
      ) shouldBe RetryableFailure
