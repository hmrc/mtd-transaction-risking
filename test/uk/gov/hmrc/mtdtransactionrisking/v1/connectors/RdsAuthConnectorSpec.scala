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
import play.api.libs.json.{JsValue, Json}
import play.api.test.Injecting
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.mtdtransactionrisking.support.{ConnectorSpec, MockAppConfig}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.auth.{RdsAuthCredentials, RdsCredentials}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.{DownstreamError, ErrorWrapper}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.outcomes.ResponseWrapper

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

class RdsAuthConnectorSpec extends ConnectorSpec, BeforeAndAfterAll, Injecting, MockAppConfig:

  def port: Int = wireMockServer.port()

  val httpClient: HttpClientV2 = app.injector.instanceOf[HttpClientV2]

  private val authPath   = "/SASLogon/oauth/token"
  private val urlPattern = urlPathMatching(authPath)

  private val clientId     = "txr_api_user1"
  private val clientSecret = "a-client-secret"

  private val expectedBasicAuth =
    "Basic " + Base64.getEncoder.encodeToString(s"$clientId:$clientSecret".getBytes(UTF_8))

  private val tokenResponseJson: JsValue = Json.parse(
    """
      |{
      |  "access_token": "a-bearer-token",
      |  "token_type": "bearer",
      |  "expires_in": 14399,
      |  "scope": "txr_api",
      |  "jti": "30b2023212d547b185a012d2d29cb78b"
      |}
      |""".stripMargin
  )

  private val expectedCredentials = RdsAuthCredentials("a-bearer-token", "bearer", 14399)

  private val malformedResponseJson: JsValue = Json.parse("""{ "unexpected": "shape" }""")

  class Test:
    MockedAppConfig.rdsAuthUrl.returns(s"http://localhost:$port$authPath").anyNumberOfTimes()
    MockedAppConfig.rdsCredentials.returns(RdsCredentials(clientId, clientSecret)).anyNumberOfTimes()

    val connector = new RdsAuthConnector(httpClient, mockAppConfig)

    def stubToken(body: Option[String], status: Int): StubMapping =
      wireMockServer.stubFor:
        val resp = body.foldLeft(aResponse().withStatus(status)): (r, b) =>
          r.withBody(b).withHeader("Content-Type", "application/json")
        post(urlPattern).willReturn(resp)

    def stubFault(): StubMapping =
      wireMockServer.stubFor(post(urlPattern).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)))

  override def beforeAll(): Unit = wireMockServer.start()
  override def afterAll(): Unit  = wireMockServer.stop()

  "RdsAuthConnector.retrieveBearerToken" when:

    "SAS issues a token" should:

      "return the credentials on 200" in new Test:
        stubToken(Some(tokenResponseJson.toString), OK)

        await(connector.retrieveBearerToken()) shouldBe Right(ResponseWrapper(correlationId, expectedCredentials))

      "return the credentials on 202" in new Test:
        stubToken(Some(tokenResponseJson.toString), ACCEPTED)

        await(connector.retrieveBearerToken()) shouldBe Right(ResponseWrapper(correlationId, expectedCredentials))

      "return DownstreamError when the body is not a token response" in new Test:
        stubToken(Some(malformedResponseJson.toString), OK)

        await(connector.retrieveBearerToken()) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

    "SAS rejects the request" should:
      Seq(BAD_REQUEST, UNAUTHORIZED, FORBIDDEN, INTERNAL_SERVER_ERROR, SERVICE_UNAVAILABLE).foreach: status =>
        s"return DownstreamError on $status" in new Test:
          stubToken(None, status)

          await(connector.retrieveBearerToken()) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

    "the connection faults" should:
      "return DownstreamError via recover" in new Test:
        stubFault()

        await(connector.retrieveBearerToken()) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

    "making the request" should:

      "send the client credentials as basic auth" in new Test:
        stubToken(Some(tokenResponseJson.toString), OK)

        await(connector.retrieveBearerToken())

        wireMockServer.verify(postRequestedFor(urlPattern).withHeader("Authorization", equalTo(expectedBasicAuth)))

      "send the form-encoded content type and json accept headers" in new Test:
        stubToken(Some(tokenResponseJson.toString), OK)

        await(connector.retrieveBearerToken())

        wireMockServer.verify(
          postRequestedFor(urlPattern)
            .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
            .withHeader("Accept", equalTo("application/json")))

      "send the client credentials grant type as a form body" in new Test:
        stubToken(Some(tokenResponseJson.toString), OK)

        await(connector.retrieveBearerToken())

        wireMockServer.verify(postRequestedFor(urlPattern).withRequestBody(equalTo("grant_type=client_credentials")))