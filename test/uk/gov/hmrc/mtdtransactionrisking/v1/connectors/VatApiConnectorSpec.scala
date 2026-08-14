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
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.test.Injecting
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{Authorization, HeaderCarrier}
import uk.gov.hmrc.mtdtransactionrisking.support.{ConnectorSpec, MockAppConfig}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.{DownstreamError, ErrorWrapper}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.outcomes.ResponseWrapper
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.Obligation
import uk.gov.hmrc.mtdtransactionrisking.v1.services.ServiceOutcome

class VatApiConnectorSpec extends ConnectorSpec, BeforeAndAfterAll, Injecting, MockAppConfig:

  def port: Int = wireMockServer.port()

  val httpClient: HttpClientV2 = app.injector.instanceOf[HttpClientV2]

  private val vrn        = "123456789"
  private val urlPattern = urlPathMatching("/internal/validate/.*")

  private val validBody: JsValue = Json.parse(
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

  private val obligationJson: JsValue = Json.parse(
    """
      |{
      |  "periodKey": "AB12",
      |  "start": "2026-01-01",
      |  "end": "2026-03-31",
      |  "due": "2026-05-07",
      |  "status": "O"
      |}
      |""".stripMargin
  )

  private val expectedObligation: Obligation =
    Obligation(
      periodKey = "AB12",
      start     = "2026-01-01",
      end       = "2026-03-31",
      due       = "2026-05-07",
      status    = "O",
      received  = None
    )

  private val validationErrorBody: JsValue = Json.parse(
    """
      |{
      |  "code": "INVALID_REQUEST",
      |  "message": "Invalid request",
      |  "errors": [
      |    { "code": "VAT_TOTAL_VALUE", "message": "totalVatDue should be equal to vatDueSales + vatDueAcquisitions", "path": "/totalVatDue" }
      |  ]
      |}
      |""".stripMargin
  )

  private val malformedObligationJson: JsValue = Json.parse("""{ "unexpected": "shape" }""")

  class Test:
    val bearerToken = "Bearer vendor-token"

    implicit val headerCarrier: HeaderCarrier =
      HeaderCarrier(authorization = Some(Authorization(bearerToken)))

    MockedAppConfig.vatApiBaseUrl.returns(s"http://localhost:$port/internal").anyNumberOfTimes()
    MockedAppConfig.appName.returns("mtd-transaction-risking").anyNumberOfTimes()

    val connector = new VatApiConnector(httpClient, mockAppConfig)

    def stubValidate(body: Option[String], status: Int): StubMapping =
      wireMockServer.stubFor:
        val resp = body.foldLeft(aResponse().withStatus(status)): (r, b) =>
          r.withBody(b).withHeader("Content-Type", "application/json")
        post(urlPattern).willReturn(resp)

    def stubFault(): StubMapping =
      wireMockServer.stubFor(post(urlPattern).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)))

  override def beforeAll(): Unit = wireMockServer.start()
  override def afterAll(): Unit  = wireMockServer.stop()

  "VatApiConnector.validate" when:

    "the return is valid and an obligation is matched" should:
      "return Right with the parsed obligation on 200" in new Test:
        stubValidate(Some(obligationJson.toString), OK)

        await(connector.validate(vrn, validBody)) shouldBe Right(ResponseWrapper(correlationId, expectedObligation))

      "return Left(DownstreamError) when the 200 body is malformed" in new Test:
        stubValidate(Some(malformedObligationJson.toString), OK)

        await(connector.validate(vrn, validBody)) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

    "the return fails validation" should:
      "relay the error body and status on 400" in new Test:
        stubValidate(Some(validationErrorBody.toString), BAD_REQUEST)

        val result: ServiceOutcome[Obligation] = await(connector.validate(vrn, validBody))

        result.left.value.statusCode shouldBe BAD_REQUEST
        result.left.value.rawBody shouldBe Some(validationErrorBody)

    "the downstream returns an error status" should:
      "relay a 500" in new Test:
        val body: JsObject = Json.obj("code" -> "INTERNAL_SERVER_ERROR", "message" -> "broken")

        stubValidate(Some(body.toString), INTERNAL_SERVER_ERROR)

        val result: ServiceOutcome[Obligation] = await(connector.validate(vrn, validBody))

        result.left.value.statusCode shouldBe INTERNAL_SERVER_ERROR
        result.left.value.rawBody shouldBe Some(body)

      "relay a 503" in new Test:
        val body: JsObject = Json.obj("code" -> "SERVICE_UNAVAILABLE", "message" -> "down")

        stubValidate(Some(body.toString), SERVICE_UNAVAILABLE)

        val result: ServiceOutcome[Obligation] = await(connector.validate(vrn, validBody))

        result.left.value.statusCode shouldBe SERVICE_UNAVAILABLE
        result.left.value.rawBody shouldBe Some(body)

      "fall back to the DownstreamError body when the error body is not JSON" in new Test:
        stubValidate(Some("not json at all ever"), BAD_REQUEST)

        val result: ServiceOutcome[Obligation] = await(connector.validate(vrn, validBody))

        result.left.value.statusCode shouldBe BAD_REQUEST
        result.left.value.rawBody shouldBe Some(DownstreamError.asJson)

    "the connection faults" should:
      "return a DownstreamError via recover" in new Test:
        stubFault()

        await(connector.validate(vrn, validBody)) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

    "called with a bearer token" should:
      "forward the Authorization header to vat-api" in new Test:
        stubValidate(Some(obligationJson.toString), OK)

        await(connector.validate(vrn, validBody)(using headerCarrier, correlationId))

        wireMockServer.verify(postRequestedFor(urlPattern).withHeader("Authorization", equalTo("Bearer vendor-token")))

    "posting" should:
      "send the VAT return body as JSON" in new Test:
        stubValidate(Some(obligationJson.toString), OK)

        await(connector.validate(vrn, validBody))

        wireMockServer.verify(postRequestedFor(urlPattern).withRequestBody(equalToJson(validBody.toString, true, false)))