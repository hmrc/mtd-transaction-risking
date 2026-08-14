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
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.{DownstreamError, ErrorWrapper}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.outcomes.ResponseWrapper
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.InsightsRequest
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.{Insights, InsightsResponse, StrategicRisk}

class InsightsConnectorSpec extends ConnectorSpec, BeforeAndAfterAll, Injecting, MockAppConfig:

  def port: Int = wireMockServer.port()
  override def beforeAll(): Unit = wireMockServer.start()
  override def afterAll(): Unit = wireMockServer.stop()

  val httpClient: HttpClientV2 = app.injector.instanceOf[HttpClientV2]
  
  private val vrn = "123456789"
  private val urlPattern = urlPathMatching("/check/insights")
  
  private val request = InsightsRequest(vrn)
  private val successResponseJson: JsValue = Json.parse(
    """
      |{
      |  "insights": {
      |    "strategicRisk": {
      |      "riskScore": 12.33,
      |      "riskCorrelationId": "123e4567-e89b-12d3-a456-426614174000",
      |      "reasons": [
      |        "VRN '123456789' is 1 hops from something risky. The average VRN is 2.51 hops from something risky."
      |      ]
      |    }
      |  }
      |}
      |""".stripMargin
  )
  private val expectedResponse: InsightsResponse =
    InsightsResponse(
      Insights(
        StrategicRisk(
          riskScore = 12.33,
          riskCorrelationId = CorrelationId("123e4567-e89b-12d3-a456-426614174000"),
          reasons = Seq("VRN '123456789' is 1 hops from something risky. The average VRN is 2.51 hops from something risky.")
        )
      )
    )
  private val malformedResponseJson: JsValue = Json.parse("""{ "unexpected": "shape" }""")

  class Test:
    MockedAppConfig.insightsProxyServiceBaseUrl.returns(s"http://localhost:$port/check/insights").anyNumberOfTimes()
    MockedAppConfig.appName.returns("mtd-transaction-risking").anyNumberOfTimes()

    val connector = new InsightsConnector(httpClient, mockAppConfig)

    def stubInsights(body: Option[String], status: Int): StubMapping =
      wireMockServer.stubFor:
        val resp = body.foldLeft(aResponse().withStatus(status)): (r, b) =>
          r.withBody(b).withHeader("Content-Type", "application/json")
        post(urlPattern).willReturn(resp)

    def stubFault(): StubMapping =
      wireMockServer.stubFor(post(urlPattern).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)))

  "InsightsConnector.getRiskInsights" when:

    "the proxy responds 200 with a valid body" should:
      "return Right with the parsed insights response" in new Test:
        stubInsights(Some(successResponseJson.toString), OK)
        await(connector.getRiskInsights(request)) shouldBe Right(ResponseWrapper(correlationId, expectedResponse))

    "the proxy responds 200 with a body missing reasons" should :
      "return Left(DownstreamError)" in new Test:
        val missingReasons: JsValue = Json.parse(
          """{"insights":{"strategicRisk":{"riskScore":12.33,"riskCorrelationId":"123e4567-e89b-12d3-a456-426614174000"}}}"""
        )
        stubInsights(Some(missingReasons.toString), OK)
        await(connector.getRiskInsights(request)) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

    "the proxy responds 200 with a malformed body" should:
      "return Left(DownstreamError)" in new Test:
        stubInsights(Some(malformedResponseJson.toString), OK)
        await(connector.getRiskInsights(request)) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

    "the proxy responds with an error status" should:
      Seq(BAD_REQUEST, NOT_FOUND, INTERNAL_SERVER_ERROR, SERVICE_UNAVAILABLE).foreach: status =>
        s"return Left(DownstreamError) on $status" in new Test:
          stubInsights(None, status)
          await(connector.getRiskInsights(request)) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

    "the connection faults" should:
      "return Left(DownstreamError) via recover" in new Test:
        stubFault()
        await(connector.getRiskInsights(request)) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))