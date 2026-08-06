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
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.{Obligation, ObligationsResponse}
import uk.gov.hmrc.mtdtransactionrisking.v1.services.ServiceOutcome

class VatApiConnectorSpec extends ConnectorSpec, BeforeAndAfterAll, Injecting, MockAppConfig:

  def port: Int = wireMockServer.port()

  val httpClient: HttpClientV2 = app.injector.instanceOf[HttpClientV2]

  private val vrn = "123456789"
  private val urlPattern = urlPathMatching("/internal/validate/.*")
  private val obligationsUrl = urlEqualTo(s"/internal/organisations/vat/$vrn/obligations?status=O")

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

  private val matchedObligation: Obligation =
    Obligation(
      status = "O",
      start = "2026-01-01",
      end = "2026-03-31",
      due = "2026-05-07",
      periodKey = "AB12"
    )

  private val matchedObligationJson: JsValue = Json.toJson(matchedObligation)

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

  class Test:
    val bearerToken = "Bearer vendor-token"

    implicit val headerCarrier: HeaderCarrier =
      HeaderCarrier(authorization = Some(Authorization(bearerToken)))

    MockedAppConfig.vatApiBaseUrl(s"http://localhost:$port/internal")
    MockedAppConfig.appName("mtd-transaction-risking")

    val connector = new VatApiConnector(httpClient, mockAppConfig)

    def stubValidate(body: Option[String], status: Int): StubMapping =
      wireMockServer.stubFor:
        val resp = body.foldLeft(aResponse().withStatus(status)): (r, b) =>
          r.withBody(b).withHeader("Content-Type", "application/json")
        post(urlPattern).willReturn(resp)

    def stubFault(): StubMapping =
      wireMockServer.stubFor(post(urlPattern).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)))

    def stubObligations(body: Option[String], status: Int): StubMapping =
      wireMockServer.stubFor:
        val resp = body.foldLeft(aResponse().withStatus(status)): (r, b) =>
          r.withBody(b).withHeader("Content-Type", "application/json")
        get(obligationsUrl).willReturn(resp)

    def stubObligationsFault(): StubMapping =
      wireMockServer.stubFor(get(obligationsUrl).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)))

  override def beforeAll(): Unit = wireMockServer.start()
  override def afterAll(): Unit = wireMockServer.stop()

  "VatApiConnector.validate" when:

    "the return is valid" must:
      "return Right(obligation) on 200" in new Test:
        stubValidate(Some(matchedObligationJson.toString), OK)
        await(connector.validate(vrn, validBody)) shouldBe Right(ResponseWrapper(correlationId, matchedObligation))

      "return DownstreamError when 200 body is malformed" in new Test:
        val malformedBody: JsObject = Json.obj("unexpected" -> "shape")
        stubValidate(Some(malformedBody.toString), OK)

        val result: ServiceOutcome[Obligation] = await(connector.validate(vrn, validBody))
        result.left.value.statusCode shouldBe OK
        result.left.value.rawBody shouldBe Some(malformedBody)

    "the return fails validation" must:
      "relay the error body and status on 400" in new Test:
        stubValidate(Some(validationErrorBody.toString), BAD_REQUEST)

        val result: ServiceOutcome[Obligation] = await(connector.validate(vrn, validBody))

        result.left.value.statusCode shouldBe BAD_REQUEST
        result.left.value.rawBody shouldBe Some(validationErrorBody)

    "the downstream returns an error status" must:
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

      "fall back to DownstreamError body when the error body is not JSON" in new Test:
        stubValidate(Some("not json at all ever"), BAD_REQUEST)

        val result: ServiceOutcome[Obligation] = await(connector.validate(vrn, validBody))

        result.left.value.statusCode shouldBe BAD_REQUEST
        result.left.value.rawBody shouldBe Some(DownstreamError.asJson)

    "the connection faults" must:
      "return a DownstreamError via recover" in new Test:
        stubFault()

        await(connector.validate(vrn, validBody)) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

    "called with a bearer token" must:
      "forward the Authorization header to vat-api" in new Test:
        stubValidate(Some(matchedObligationJson.toString), OK)

        await(connector.validate(vrn, validBody)(using headerCarrier, correlationId))
        wireMockServer.verify(
          postRequestedFor(urlPattern).withHeader("Authorization", matching("(?i)^Bearer\\s+.+$"))
        )

    "post" must:
      "send the VAT return body as JSON" in new Test:
        stubValidate(Some(matchedObligationJson.toString), OK)

        await(connector.validate(vrn, validBody))
        wireMockServer.verify(
          postRequestedFor(urlPattern)
            .withRequestBody(equalToJson(validBody.toString, true, false))
            .withHeader("X-CorrelationId", equalTo(correlationId.value))
        )

  "VatApiConnector.getObligations" when:
    "vat-api returns 200 with valid obligations response" must:
      "return parsed obligations" in new Test:
        val obligationsJson: JsObject = Json.obj("obligations" -> Json.arr(matchedObligationJson))
        stubObligations(Some(obligationsJson.toString), OK)

        await(connector.getObligations(vrn)) shouldBe
          Right(ResponseWrapper(correlationId, ObligationsResponse(Seq(matchedObligation))))

    "vat-api returns 200 with malformed obligations body" must:
      "return DownstreamError with raw body" in new Test:
        val malformedBody: JsObject = Json.obj("unexpected" -> "shape")
        stubObligations(Some(malformedBody.toString), OK)

        val result: ServiceOutcome[ObligationsResponse] = await(connector.getObligations(vrn))
        result.left.value.statusCode shouldBe OK
        result.left.value.error shouldBe DownstreamError
        result.left.value.rawBody shouldBe Some(malformedBody)

    "vat-api returns non-200 status" must:
      Seq(BAD_REQUEST, INTERNAL_SERVER_ERROR, SERVICE_UNAVAILABLE).foreach: downstreamStatus =>
        s"return DownstreamError with raw status/body on $downstreamStatus" in new Test:
          val body: JsObject = Json.obj("code" -> s"DOWNSTREAM_$downstreamStatus")
          stubObligations(Some(body.toString), downstreamStatus)

          val result: ServiceOutcome[ObligationsResponse] = await(connector.getObligations(vrn))
          result.left.value.statusCode shouldBe downstreamStatus
          result.left.value.rawBody shouldBe Some(body)

      "fall back to DownstreamError body when the error body is not JSON" in new Test:
        stubObligations(Some("not json at all ever"), BAD_REQUEST)

        val result: ServiceOutcome[ObligationsResponse] = await(connector.getObligations(vrn))
        result.left.value.statusCode shouldBe BAD_REQUEST
        result.left.value.rawBody shouldBe Some(DownstreamError.asJson)

    "the connection faults" must:
      "return DownstreamError via recover" in new Test:
        stubObligationsFault()
        await(connector.getObligations(vrn)) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

    "called with a bearer token" must:
      "forward the Authorization header to vat-api obligations" in new Test:
        val obligationsJson: JsObject = Json.obj("obligations" -> Json.arr(matchedObligationJson))
        stubObligations(Some(obligationsJson.toString), OK)

        await(connector.getObligations(vrn)(using headerCarrier, correlationId))
        wireMockServer.verify(
          getRequestedFor(urlPathEqualTo(s"/internal/organisations/vat/$vrn/obligations")).withHeader("Authorization", matching("(?i)^Bearer\\s+.+$"))
        )

    "get" must:
      "request open obligations using status=O query param and correlation id header" in new Test:
        val obligationsJson: JsObject = Json.obj("obligations" -> Json.arr(matchedObligationJson))
        stubObligations(Some(obligationsJson.toString), OK)

        await(connector.getObligations(vrn))
        wireMockServer.verify(
          getRequestedFor(urlPathEqualTo(s"/internal/organisations/vat/$vrn/obligations"))
            .withQueryParam("status", equalTo("O"))
            .withHeader("X-CorrelationId", equalTo(correlationId.value))
            .withHeader("Accept", equalTo("application/vnd.hmrc.1.0+json"))
        )
