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
import uk.gov.hmrc.mtdtransactionrisking.support.{ConnectorSpec, MockAppConfig}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.auth.RdsAuthCredentials
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.{
  AcknowledgementValidationFailedError,
  DownstreamError,
  ErrorWrapper,
  ServiceUnavailableError
}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.outcomes.ResponseWrapper
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.*
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.{FeedbackLink, FeedbackResponse, RdsAcknowledgeResponse}
import uk.gov.hmrc.mtdtransactionrisking.v1.services.ServiceOutcome

import scala.concurrent.Future

class RdsConnectorSpec extends ConnectorSpec, BeforeAndAfterAll, Injecting, MockAppConfig:

  val httpClient: HttpClientV2 = app.injector.instanceOf[HttpClientV2]
  private val vrn = "123456789"

  private val reportPath = "/microanalyticScore/modules/HMRC_ASSIST_VAT_FINSUB_FEEDBACK/steps/execute"
  private val reportUrlPattern = urlPathMatching(reportPath)
  private val acknowledgePath = "/microanalyticScore/modules/HMRC_ASSIST_VAT_FINSUB_FEEDBACK_ACK/steps/execute"
  private val acknowledgeUrlPattern = urlPathMatching(acknowledgePath)

  private val feedbackId = "f2fb30e5-4ab6-4a29-b3c1-c7264259ff1c"
  private val rdsCorrelationId = "E9F65715BBC9222477B27074804BBDD5C73CDE62F84D8B00CFD05B883534AF3D"
  private val credentials = RdsAuthCredentials("a-bearer-token", "bearer", 14399)

  private val rdsRequest: ReportRequest = ReportRequest(
    fixedId = "2dd537bc-4244-4ebf-bac9-96321be13cdc",
    vrn = vrn,
    periodKey = "AB12",
    startDate = "2026-01-01",
    endDate = "2026-03-31",
    customerType = "T",
    agentReferenceNumber = None,
    fraudRiskReportScore = BigDecimal("4.7"),
    fraudRiskReportReasons = Seq("VRN 123456789 is 3.7 hops away from something risky."),
    fraudPreventionHeaders = Seq(FraudPreventionHeader("Gov-Client-Timezone", "UTC+00:00")),
    vatDueSales = BigDecimal("100.00"),
    vatDueAcquisitions = BigDecimal("100.00"),
    totalVatDue = BigDecimal("200.00"),
    vatReclaimedCurrPeriod = BigDecimal("100.00"),
    netVatDue = BigDecimal("100.00"),
    totalValueSalesExVAT = BigDecimal(500),
    totalValuePurchasesExVAT = BigDecimal(400),
    totalValueGoodsSuppliedExVAT = BigDecimal(300),
    totalAcquisitionsExVAT = BigDecimal(200)
  )

  private val acknowledgeRequest: AcknowledgeRequest = AcknowledgeRequest(
    vrn = vrn,
    reportId = feedbackId,
    correlationId = rdsCorrelationId,
    presentedDateTime = "2026-09-03T10:00:00Z"
  )

  // A report with no feedbackId so the transform cannot build a response
  private val reportWithoutFeedbackId: JsValue = Json.parse(
    """
      |{
      |  "outputs": [
      |    { "name": "correlationId", "value": null }
      |  ]
      |}
      |""".stripMargin
  )

  private val acknowledgeWithoutResponseCode: JsValue = Json.parse(
    s"""
       |{
       |  "output": {
       |    "vrn": "$vrn",
       |    "feedbackId": "$feedbackId",
       |    "createdDttm": "2026-09-03T10:00:00Z",
       |    "responseMessage": "Missing response code"
       |  }
       |}
       |""".stripMargin
  )

  def port: Int = wireMockServer.port()

  override def beforeAll(): Unit = wireMockServer.start()

  override def afterAll(): Unit = wireMockServer.stop()

  private def acknowledgeJson(responseCode: Option[Int] = Some(202), responseMessage: Option[String] = Some("Accepted")): JsValue =
    Json.parse(
      s"""
         |{
         |  "output": {
         |    "vrn": "$vrn",
         |    "feedbackId": "$feedbackId",
         |    "createdDttm": "2026-09-03T10:00:00Z",
         |    "responseCode": ${responseCode.map(_.toString).getOrElse("null")},
         |    "responseMessage": ${responseMessage.map(m => s""""$m"""").getOrElse("null")}
         |  }
         |}
         |""".stripMargin
    )

  private def reportJson(responseCode: Option[String] = None, correlationId: Option[String] = None): JsValue =
    val optionalOutputs =
      responseCode.map(code => s"""{ "name": "responseCode", "value": "$code" },""").getOrElse("")

    val correlationIdValue = correlationId.map(id => s""""$id"""").getOrElse("null")

    val columns =
      """{ "metadata": [ { "ITEMNUMBER": "string" }, { "MESSAGE": "string" }, { "ACTION": "string" }, { "TITLE": "string" },
        |                { "LINKTITLE": "string" }, { "LINKURL": "string" }, { "PATH": "string" } ] }""".stripMargin

    Json.parse(
      s"""
         |{
         |  "links": [],
         |  "version": 2,
         |  "moduleId": "HMRC_ASSIST_VAT_FINSUB_FEEDBACK",
         |  "stepId": "execute",
         |  "executionState": "completed",
         |  "outputs": [
         |    $optionalOutputs
         |    { "name": "correlationId", "value": $correlationIdValue },
         |    { "name": "createdDttm", "value": "2026-10-09T19:00:19.246" },
         |    {
         |      "name": "englishActions",
         |      "value": [
         |        $columns,
         |        { "data": [ [ "1", "Please review your VAT return figures.", "Check your sales records.", "VAT Return Query",
         |                      "VAT guidance", "https://www.gov.uk/vat-returns", "vatDueSales" ] ] }
         |      ]
         |    },
         |    { "name": "feedbackId", "value": "$feedbackId" },
         |    { "name": "vrn", "value": "$vrn" },
         |    {
         |      "name": "welshActions",
         |      "value": [
         |        $columns,
         |        { "data": [ [ "1", "Adolygwch eich ffigurau.", "Gwiriwch eich cofnodion.", "Ymholiad Ffurflen TAW",
         |                      "Canllawiau TAW", "https://www.gov.uk/ffurflenni-taw", "vatDueSales" ] ] }
         |      ]
         |    },
         |    { "name": "rt_Record_Contacts_Outer", "value": "f9a5a9a7-09a2-a94b-a492-54c9fc91d5d2" }
         |  ]
         |}
         |""".stripMargin
    )

  class Test:
    MockedAppConfig.rdsSubmitUrl.returns(s"http://localhost:$port$reportPath").anyNumberOfTimes()
    MockedAppConfig.rdsAcknowledgeUrl.returns(s"http://localhost:$port$acknowledgePath").anyNumberOfTimes()
    MockedAppConfig.appName.returns("mtd-transaction-risking").anyNumberOfTimes()
    MockedAppConfig.rdsLogPayloads.returns(false).anyNumberOfTimes()

    val connector = new RdsConnector(httpClient, mockAppConfig)

    // Report stub helpers
    def stubReport(body: Option[String], status: Int): StubMapping =
      wireMockServer.stubFor:
        val resp = body.foldLeft(aResponse().withStatus(status)): (r, b) =>
          r.withBody(b).withHeader("Content-Type", "application/json")
        post(reportUrlPattern).willReturn(resp)

    def stubReportFault(): StubMapping =
      wireMockServer.stubFor(post(reportUrlPattern).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)))

    def generateReport(credentials: Option[RdsAuthCredentials] = Some(credentials)): Future[ServiceOutcome[FeedbackResponse]] =
      connector.generateReport(vrn, rdsRequest, credentials)

    // Acknowledge stub helpers
    def stubAcknowledge(body: Option[String], status: Int): StubMapping =
      wireMockServer.stubFor:
        val resp = body.foldLeft(aResponse().withStatus(status)): (r, b) =>
          r.withBody(b).withHeader("Content-Type", "application/json")
        post(acknowledgeUrlPattern).willReturn(resp)

    def stubAcknowledgeFault(): StubMapping =
      wireMockServer.stubFor(post(acknowledgeUrlPattern).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)))

    def acknowledge(credentials: Option[RdsAuthCredentials] = Some(credentials)): Future[ServiceOutcome[RdsAcknowledgeResponse]] =
      connector.acknowledge(acknowledgeRequest, credentials)

  "RdsConnector.acknowledge" when:

    "RDS accepts the acknowledgement" should:
      "return the acknowledge response when the inner response code is 202" in new Test:
        stubAcknowledge(Some(acknowledgeJson(responseCode = Some(202)).toString), CREATED)

        await(acknowledge()) shouldBe Right(
          ResponseWrapper(
            correlationId,
            RdsAcknowledgeResponse(
              vrn = Some(vrn),
              feedbackId = Some(feedbackId),
              createdDttm = Some("2026-09-03T10:00:00Z"),
              responseCode = Some(202),
              responseMessage = Some("Accepted")
            )
          )
        )

    "RDS rejects the acknowledgement" should:
      "return AcknowledgementValidationFailedError when the inner response code is 401" in new Test:
        stubAcknowledge(Some(acknowledgeJson(responseCode = Some(401), responseMessage = Some("Unauthorised")).toString), CREATED)

        await(acknowledge()) shouldBe Left(ErrorWrapper(correlationId, AcknowledgementValidationFailedError))

    "the response cannot be interpreted" should:

      "return DownstreamError when the inner response code is unexpected" in new Test:
        stubAcknowledge(Some(acknowledgeJson(responseCode = Some(500), responseMessage = Some("Unexpected")).toString), CREATED)

        await(acknowledge()) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

      "return DownstreamError when the response has no response code" in new Test:
        stubAcknowledge(Some(acknowledgeWithoutResponseCode.toString), CREATED)

        await(acknowledge()) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

      "return DownstreamError when the body is malformed" in new Test:
        val malformed: JsObject = Json.obj("unexpected" -> "shape")

        stubAcknowledge(Some(malformed.toString), CREATED)

        await(acknowledge()) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

    "RDS is unreachable" should:
      Seq(NOT_FOUND, REQUEST_TIMEOUT, SERVICE_UNAVAILABLE).foreach: status =>
        s"return ServiceUnavailableError on outer HTTP status $status" in new Test:
          stubAcknowledge(None, status)

          await(acknowledge()) shouldBe Left(ErrorWrapper(correlationId, ServiceUnavailableError))

    "RDS returns an unexpected HTTP status" should:
      "return DownstreamError" in new Test:
        stubAcknowledge(None, OK)

        await(acknowledge()) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

    "the connection faults" should:
      "return DownstreamError via recover" in new Test:
        stubAcknowledgeFault()

        await(acknowledge()) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

    "making the request" should:

      "send the bearer token when credentials are supplied" in new Test:
        stubAcknowledge(Some(acknowledgeJson().toString), CREATED)

        await(acknowledge())

        wireMockServer.verify(postRequestedFor(acknowledgeUrlPattern).withHeader("Authorization", equalTo("Bearer a-bearer-token")))

      "send no authorization header when no credentials are supplied" in new Test:
        stubAcknowledge(Some(acknowledgeJson().toString), CREATED)

        await(acknowledge(credentials = None))

        wireMockServer.verify(postRequestedFor(acknowledgeUrlPattern).withoutHeader("Authorization"))

      "send the acknowledge request as JSON" in new Test:
        stubAcknowledge(Some(acknowledgeJson().toString), CREATED)

        await(acknowledge())

        wireMockServer.verify(
          postRequestedFor(acknowledgeUrlPattern)
            .withRequestBody(equalToJson(Json.toJson(RdsAcknowledgeRequest.from(acknowledgeRequest)).toString, true, false))
        )

      "send the correlation id and user agent" in new Test:
        stubAcknowledge(Some(acknowledgeJson().toString), CREATED)

        await(acknowledge())

        wireMockServer.verify(
          postRequestedFor(acknowledgeUrlPattern)
            .withHeader("X-CorrelationId", equalTo(correlationId.value))
            .withHeader("User-Agent", equalTo("mtd-transaction-risking"))
        )

  "RdsConnector.generateReport" when:

    "RDS returns a report in its current shape with no response code and a null correlation id" should:

      "return the transformed feedback" in new Test:
        stubReport(Some(reportJson().toString), CREATED)

        private val feedback = await(generateReport()).value.responseData

        feedback.reportId shouldBe feedbackId
        feedback.correlationId shouldBe None
        feedback.englishFeedback should have size 1
        feedback.welshFeedback should have size 1

      "build links from the separate link title and url columns" in new Test:
        stubReport(Some(reportJson().toString), CREATED)

        private val feedback = await(generateReport()).value.responseData

        feedback.englishFeedback.head.links shouldBe Some(List(FeedbackLink("VAT guidance", "https://www.gov.uk/vat-returns")))

    "the report includes a correlation id" should:
      "pass it through" in new Test:
        stubReport(Some(reportJson(correlationId = Some(rdsCorrelationId)).toString), CREATED)

        await(generateReport()).value.responseData.correlationId shouldBe Some(rdsCorrelationId)

    "the report includes a response code" should:

      "succeed when it is 201" in new Test:
        stubReport(Some(reportJson(responseCode = Some("201")).toString), CREATED)

        await(generateReport()).value.responseData.reportId shouldBe feedbackId

      "return DownstreamError when it is anything else" in new Test:
        stubReport(Some(reportJson(responseCode = Some("500")).toString), CREATED)

        await(generateReport()) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

    "the report cannot be used" should:

      "return DownstreamError when the body is not a report" in new Test:
        stubReport(Some("""{"unexpected":"shape"}"""), CREATED)

        await(generateReport()) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

      "return DownstreamError when the report has no feedback id" in new Test:
        stubReport(Some(reportWithoutFeedbackId.toString), CREATED)

        await(generateReport()) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

    "RDS rejects the request" should:
      "return DownstreamError on 400" in new Test:
        stubReport(Some("""{"errorCode":0,"details":["The value of the field \"vrn\" must not be empty or missing."]}"""), BAD_REQUEST)

        await(generateReport()) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

    "RDS is unreachable" should:
      Seq(NOT_FOUND, REQUEST_TIMEOUT, SERVICE_UNAVAILABLE).foreach: status =>
        s"return ServiceUnavailableError on $status" in new Test:
          stubReport(None, status)

          await(generateReport()) shouldBe Left(ErrorWrapper(correlationId, ServiceUnavailableError))

    "RDS returns an unexpected HTTP status" should:
      "return DownstreamError" in new Test:
        stubReport(None, OK)

        await(generateReport()) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

    "the connection faults" should:
      "return DownstreamError via recover" in new Test:
        stubReportFault()

        await(generateReport()) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

    "making the request" should:

      "send the bearer token when credentials are supplied" in new Test:
        stubReport(Some(reportJson().toString), CREATED)

        await(generateReport())

        wireMockServer.verify(postRequestedFor(reportUrlPattern).withHeader("Authorization", equalTo("Bearer a-bearer-token")))

      "send no authorization header when no credentials are supplied" in new Test:
        stubReport(Some(reportJson().toString), CREATED)

        await(generateReport(credentials = None))

        wireMockServer.verify(postRequestedFor(reportUrlPattern).withoutHeader("Authorization"))

      "send the report request in the MAS inputs envelope" in new Test:
        stubReport(Some(reportJson().toString), CREATED)

        await(generateReport())

        wireMockServer.verify(postRequestedFor(reportUrlPattern).withRequestBody(equalToJson(Json.toJson(rdsRequest).toString, true, false)))

      "send the correlation id and user agent" in new Test:
        stubReport(Some(reportJson().toString), CREATED)

        await(generateReport())

        wireMockServer.verify(
          postRequestedFor(reportUrlPattern)
            .withHeader("X-CorrelationId", equalTo(correlationId.value))
            .withHeader("User-Agent", equalTo("mtd-transaction-risking")))
