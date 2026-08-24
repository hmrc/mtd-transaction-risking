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
import uk.gov.hmrc.mtdtransactionrisking.v1.models.auth.RdsAuthCredentials
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.{DownstreamError, ErrorWrapper, ServiceUnavailableError}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.{FraudPreventionHeader, ReportRequest}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.FeedbackResponse
import uk.gov.hmrc.mtdtransactionrisking.v1.services.ServiceOutcome

import scala.concurrent.Future

class RdsConnectorSpec extends ConnectorSpec, BeforeAndAfterAll, Injecting, MockAppConfig:

  val httpClient: HttpClientV2 = app.injector.instanceOf[HttpClientV2]
  private val vrn        = "123456789"
  private val reportPath = "/microanalyticScore/modules/HMRC_ASSIST_VAT_FINSUB_FEEDBACK/steps/execute"
  private val urlPattern = urlPathMatching(reportPath)

  private val feedbackId = "f2fb30e5-4ab6-4a29-b3c1-c7264259ff1c"
  private val rdsCorrelationId = "E9F65715BBC9222477B27074804BBDD5C73CDE62F84D8B00CFD05B883534AF3D"
  private val credentials = RdsAuthCredentials("a-bearer-token", "bearer", 14399)

  private val rdsRequest: ReportRequest = ReportRequest(
    fixedId = "2dd537bc-4244-4ebf-bac9-96321be13cdc",
    periodKey = "AB12",
    startDate = "2026-01-01",
    endDate = "2026-03-31",
    customerType = "T",
    agentReferenceNumber = None,
    fraudRiskReportScore = 4.7,
    fraudRiskReportReasons = Seq("VRN 123456789 is 3.7 hops away from something risky."),
    fraudPreventionHeaders = Seq(FraudPreventionHeader("gov-client-timezone", "UTC+00:00")),
    vatDueSales = BigDecimal("100.00"),
    vatDueAcquisitions = BigDecimal("100.00"),
    vatDueTotal = BigDecimal("200.00"),
    vatReclaimedCurrPeriod = BigDecimal("100.00"),
    vatDueNet = BigDecimal("100.00"),
    totalValueSalesExVAT = BigDecimal(500),
    totalValuePurchasesExVAT = BigDecimal(400),
    totalValueGoodsSuppliedExVAT = BigDecimal(300),
    totalAllAcquisitionsExVAT = BigDecimal(200)
  )
  /** A report with no feedbackId, so the transform cannot build a response. */
  private val reportWithoutFeedbackId: JsValue = Json.parse(
    s"""
       |{
       |  "outputs": [
       |    { "name": "correlationId", "value": "$rdsCorrelationId" },
       |    { "name": "responseCode", "value": "201" }
       |  ]
       |}
       |""".stripMargin
  )
  private val reportWithoutResponseCode: JsValue = Json.parse(
    s"""
       |{
       |  "outputs": [
       |    { "name": "correlationId", "value": "$rdsCorrelationId" },
       |    { "name": "feedbackId", "value": "$feedbackId" }
       |  ]
       |}
       |""".stripMargin
  )

  def port: Int = wireMockServer.port()

  override def beforeAll(): Unit = wireMockServer.start()

  override def afterAll(): Unit = wireMockServer.stop()

  /** A report as SAS returns it, with the inner response code and one message per language. */
  private def reportJson(responseCode: String = "201"): JsValue = Json.parse(
    s"""
       |{
       |  "links": [],
       |  "version": 2,
       |  "moduleId": "rbfConceptHub",
       |  "stepId": "execute",
       |  "executionState": "completed",
       |  "outputs": [
       |    { "name": "correlationId", "value": "$rdsCorrelationId" },
       |    { "name": "feedbackId", "value": "$feedbackId" },
       |    { "name": "responseCode", "value": "$responseCode" },
       |    { "name": "responseMessage", "value": "Feedback generated successfully" },
       |    {
       |      "name": "englishActions",
       |      "value": [
       |        { "metadata": [ { "itemNumber": "1" }, { "message": "m" }, { "action": "a" }, { "title": "t" },
       |                        [ { "linkTitle": "lt" }, { "linkUrl": "lu" } ], { "path": "p" } ] },
       |        { "data": [ [ "1", "Please review your VAT return figures.", "Check your sales records.",
       |                      "VAT Return Query",
       |                      [ { "linkTitle": "VAT guidance" }, { "linkUrl": "https://www.gov.uk/vat-returns" } ],
       |                      "vatDueSales" ] ] }
       |      ]
       |    },
       |    {
       |      "name": "welshActions",
       |      "value": [
       |        { "metadata": [ { "itemNumber": "1" }, { "message": "m" }, { "action": "a" }, { "title": "t" },
       |                        [ { "linkTitle": "lt" }, { "linkUrl": "lu" } ], { "path": "p" } ] },
       |        { "data": [ [ "1", "Adolygwch eich ffigurau.", "Gwiriwch eich cofnodion.",
       |                      "Ymholiad Ffurflen TAW",
       |                      [ { "linkTitle": "Canllawiau TAW" }, { "linkUrl": "https://www.gov.uk/ffurflenni-taw" } ],
       |                      "vatDueSales" ] ] }
       |      ]
       |    }
       |  ]
       |}
       |""".stripMargin
  )

  class Test:
    MockedAppConfig.rdsSubmitUrl.returns(s"http://localhost:$port$reportPath").anyNumberOfTimes()
    MockedAppConfig.appName.returns("mtd-transaction-risking").anyNumberOfTimes()

    val connector = new RdsConnector(httpClient, mockAppConfig)

    def stubReport(body: Option[String], status: Int): StubMapping =
      wireMockServer.stubFor:
        val resp = body.foldLeft(aResponse().withStatus(status)): (r, b) =>
          r.withBody(b).withHeader("Content-Type", "application/json")
        post(urlPattern).willReturn(resp)

    def stubFault(): StubMapping =
      wireMockServer.stubFor(post(urlPattern).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)))

    def generateReport(credentials: Option[RdsAuthCredentials] = Some(credentials)): Future[ServiceOutcome[FeedbackResponse]] =
      connector.generateReport(vrn, rdsRequest, credentials)

  "RdsConnector.generateReport" when:

    "RDS returns a report" should:

      "return the transformed feedback when the inner response code is 201" in new Test:
        stubReport(Some(reportJson().toString), CREATED)

        private val feedback = await(generateReport()).value.responseData

        feedback.reportId shouldBe feedbackId
        feedback.correlationId shouldBe rdsCorrelationId
        feedback.englishFeedback should have size 1
        feedback.welshFeedback should have size 1

      "return DownstreamError when the body is not a report" in new Test:
        stubReport(Some("""{"unexpected":"shape"}"""), CREATED)

        await(generateReport()) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

      "return DownstreamError when the report has no response code" in new Test:
        stubReport(Some(reportWithoutResponseCode.toString), CREATED)

        await(generateReport()) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

      "return DownstreamError when the inner response code is unexpected" in new Test:
        stubReport(Some(reportJson(responseCode = "500").toString), CREATED)

        await(generateReport()) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

      "return DownstreamError when the report cannot be transformed" in new Test:
        stubReport(Some(reportWithoutFeedbackId.toString), CREATED)

        await(generateReport()) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

    "RDS rejects the request" should:
      "return DownstreamError on 400" in new Test:
        stubReport(None, BAD_REQUEST)

        await(generateReport()) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

    "RDS is unreachable" should:
      Seq(NOT_FOUND, REQUEST_TIMEOUT, SERVICE_UNAVAILABLE).foreach: status =>
        s"return ServiceUnavailableError on $status" in new Test:
          stubReport(None, status)

          await(generateReport()) shouldBe Left(ErrorWrapper(correlationId, ServiceUnavailableError))

    "RDS returns an unexpected status" should:
      "return DownstreamError" in new Test:
        stubReport(None, OK)

        await(generateReport()) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

    "the connection faults" should:
      "return DownstreamError via recover" in new Test:
        stubFault()

        await(generateReport()) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

    "making the request" should:

      "send the bearer token when credentials are supplied" in new Test:
        stubReport(Some(reportJson().toString), CREATED)

        await(generateReport())

        wireMockServer.verify(postRequestedFor(urlPattern).withHeader("Authorization", equalTo("Bearer a-bearer-token")))

      "send no authorization header when no credentials are supplied" in new Test:
        stubReport(Some(reportJson().toString), CREATED)

        await(generateReport(credentials = None))

        wireMockServer.verify(postRequestedFor(urlPattern).withoutHeader("Authorization"))

      "send the report request as JSON" in new Test:
        stubReport(Some(reportJson().toString), CREATED)

        await(generateReport())

        wireMockServer.verify(postRequestedFor(urlPattern).withRequestBody(equalToJson(Json.toJson(rdsRequest).toString, true, false)))

      "send the correlation id and user agent" in new Test:
        stubReport(Some(reportJson().toString), CREATED)

        await(generateReport())

        wireMockServer.verify(
          postRequestedFor(urlPattern)
            .withHeader("X-CorrelationId", equalTo(correlationId.value))
            .withHeader("User-Agent", equalTo("mtd-transaction-risking")))
