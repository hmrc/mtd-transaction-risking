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
import org.scalatest.BeforeAndAfterAll
import play.api.libs.json.Json
import play.api.test.Injecting
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.mtdtransactionrisking.support.{ConnectorSpec, MockAppConfig}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.auth.InteractionCredentials
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.{DownstreamError, ErrorWrapper}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.outcomes.ResponseWrapper
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.{Interaction, InteractionMetadata, InteractionPayload}

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

class InteractionConnectorSpec extends ConnectorSpec, BeforeAndAfterAll, Injecting, MockAppConfig:

  def port: Int = wireMockServer.port()

  val httpClient: HttpClientV2 = app.injector.instanceOf[HttpClientV2]

  private val storePath  = "/rsd/receive-and-store"
  private val urlPattern = urlPathMatching(storePath)

  private val clientId     = "stub-client-id"
  private val clientSecret = "stub-client-secret"

  private val expectedBasicAuth =
    "Basic " + Base64.getEncoder.encodeToString(s"$clientId:$clientSecret".getBytes(UTF_8))

  private val interaction: Interaction = Interaction(
    serviceRegime  = "vat-assist",
    eventName      = "generate-report",
    feedbackId     = "f2fb30e5-4ab6-4a29-b3c1-c00000000001",
    eventTimestamp = "2026-08-13T09:00:00Z",
    metadata       = Seq(InteractionMetadata(vrn = "123456789", start = Some("2026-01-01"), end = Some("2026-03-31"), additionalProperties = Json.obj())),
    payload        = InteractionPayload(reportId = "f2fb30e5-4ab6-4a29-b3c1-c00000000001", messages = Seq.empty)
  )

  class Test:
    MockedAppConfig.interactionsBaseUrl.returns(s"http://localhost:$port$storePath").anyNumberOfTimes()
    MockedAppConfig.interactionCredentials.returns(InteractionCredentials(clientId, clientSecret)).anyNumberOfTimes()

    val connector = new InteractionConnector(httpClient, mockAppConfig)

    def stubStore(status: Int, body: Option[String] = None): StubMapping =
      wireMockServer.stubFor:
        val resp = body.foldLeft(aResponse().withStatus(status)): (r, b) =>
          r.withBody(b).withHeader("Content-Type", "application/json")
        post(urlPattern).willReturn(resp)

    def stubFault(): StubMapping =
      wireMockServer.stubFor(post(urlPattern).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)))

  override def beforeAll(): Unit = wireMockServer.start()
  override def afterAll(): Unit  = wireMockServer.stop()

  "InteractionConnector.store" when:

    "the datastore accepts the interaction" should:
      "return Right(()) on 204" in new Test:
        stubStore(NO_CONTENT)

        await(connector.store(interaction)) shouldBe Right(ResponseWrapper(correlationId, ()))

    "the datastore rejects the request" should:
      Seq(BAD_REQUEST, UNAUTHORIZED, FORBIDDEN, INTERNAL_SERVER_ERROR, SERVICE_UNAVAILABLE).foreach: status =>
        s"return DownstreamError on $status" in new Test:
          stubStore(status, Some(Json.obj("code" -> "ERROR", "message" -> "failed").toString))

          await(connector.store(interaction)) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

    "the connection faults" should:
      "return DownstreamError via recover" in new Test:
        stubFault()

        await(connector.store(interaction)) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

    "making the request" should:

      "send the client credentials as basic auth" in new Test:
        stubStore(NO_CONTENT)

        await(connector.store(interaction))

        wireMockServer.verify(postRequestedFor(urlPattern).withHeader("Authorization", equalTo(expectedBasicAuth)))

      "send the correlation id" in new Test:
        stubStore(NO_CONTENT)

        await(connector.store(interaction))

        wireMockServer.verify(postRequestedFor(urlPattern).withHeader("CorrelationId", equalTo(correlationId.value)))

      "send json content type and accept any response type" in new Test:
        stubStore(NO_CONTENT)

        await(connector.store(interaction))

        wireMockServer.verify(
          postRequestedFor(urlPattern)
            .withHeader("Content-Type", containing("application/json"))
            .withHeader("Accept", equalTo("*/*")))

      "send the interaction as the request body" in new Test:
        stubStore(NO_CONTENT)

        await(connector.store(interaction))

        wireMockServer.verify(postRequestedFor(urlPattern).withRequestBody(equalToJson(Json.toJson(interaction).toString, true, false)))