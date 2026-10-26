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
import play.api.libs.json.*
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
  private val acknowledgeWithoutResponseCode: JsValue = acknowledgeJson(responseCode = None)

  private val reportColumns =
    """{ "metadata": [ { "ITEMNUMBER": "string" }, { "MESSAGE": "string" }, { "ACTION": "string" }, { "TITLE": "string" },
      |                { "LINKTITLE": "string" }, { "LINKURL": "string" }, { "PATH": "string" } ] }""".stripMargin

  def port: Int = wireMockServer.port()

  override def beforeAll(): Unit = wireMockServer.start()

  override def afterAll(): Unit = wireMockServer.stop()

  private def acknowledgeJson(
                               responseCode: Option[Int] = Some(ACCEPTED),
                               responseMessage: Option[String] = Some("Accepted")
                             ): JsValue =
    Json.obj(
      "moduleId" -> "HMRC_ASSIST_VAT_FINSUB_FEEDBACK_ACK",
      "executionState" -> "completed",
      "outputs" -> JsArray(
        Seq(
          Some(Json.obj("name" -> "VRN", "value" -> vrn)),
          Some(Json.obj("name" -> "createdDttm", "value" -> "2026-09-03T10:00:00")),
          Some(Json.obj("name" -> "feedbackId", "value" -> feedbackId)),
          responseCode.map(code => Json.obj("name" -> "responseCode", "value" -> JsNumber(code))),
          responseMessage.map(message => Json.obj("name" -> "responseMessage", "value" -> message))
        ).flatten
      )
    )

  // Report with real feedback (item 1). With no responseCode
  private def reportJson(responseCode: Option[Int] = None, correlationId: Option[String] = None): JsValue =
    val optionalOutputs =
      responseCode.map(code => s"""{ "name": "responseCode", "value": $code },""").getOrElse("")

    val correlationIdValue = correlationId.map(id => s""""$id"""").getOrElse("null")

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
         |        $reportColumns,
         |        { "data": [ [ "1", "Please review your VAT return figures.", "Check your sales records.", "VAT Return Query",
         |                      "VAT guidance", "https://www.gov.uk/vat-returns", "vatDueSales" ] ] }
         |      ]
         |    },
         |    { "name": "feedbackId", "value": "$feedbackId" },
         |    { "name": "vrn", "value": "$vrn" },
         |    {
         |      "name": "welshActions",
         |      "value": [
         |        $reportColumns,
         |        { "data": [ [ "1", "Adolygwch eich ffigurau.", "Gwiriwch eich cofnodion.", "Ymholiad Ffurflen TAW",
         |                      "Canllawiau TAW", "https://www.gov.uk/ffurflenni-taw", "vatDueSales" ] ] }
         |      ]
         |    },
         |    { "name": "rt_Record_Contacts_Outer", "value": "f9a5a9a7-09a2-a94b-a492-54c9fc91d5d2" }
         |  ]
         |}
         |""".stripMargin
    )

  private def noFeedbackReportJson(includeFeedbackId: Boolean = true): JsValue =
    val feedbackIdOutput =
      if includeFeedbackId then s"""{ "name": "feedbackId", "value": "$feedbackId" },""" else ""

    Json.parse(
      s"""
         |{
         |  "links": [],
         |  "version": 2,
         |  "moduleId": "HMRC_ASSIST_VAT_FINSUB_FEEDBACK",
         |  "stepId": "execute",
         |  "executionState": "completed",
         |  "metadata": { "module_id": "HMRC_ASSIST_VAT_FINSUB_FEEDBACK", "step_id": "execute" },
         |  "outputs": [
         |    { "name": "correlationId", "value": null },
         |    { "name": "createdDttm", "value": "2026-10-26T14:16:55.341" },
         |    {
         |      "name": "englishActions",
         |      "value": [
         |        $reportColumns,
         |        { "data": [ [ "0",
         |          "This is not a complete check of your return or confirmation that your return is accurate. Your return has not been submitted at this stage.",
         |          "Consider any further checks before finalising your return.",
         |          "HMRC Feedback: HMRC Assist has not returned any messages.",
         |          "Go to GOV.UK for more information on HMRC Assist",
         |          "https://www.gov.uk",
         |          "0" ] ] }
         |      ]
         |    },
         |    $feedbackIdOutput
         |    { "name": "responseCode", "value": 204.0 },
         |    { "name": "responseMessage", "value": "No feedback applicable - Suppressed Messages" },
         |    { "name": "vrn", "value": "$vrn" },
         |    {
         |      "name": "welshActions",
         |      "value": [
         |        $reportColumns,
         |        { "data": [ [ "0",
         |          "Nid yw hwn yn wiriad cyflawn o’ch Ffurflen Dreth nac yn gadarnhad bod eich Ffurflen Dreth yn gywir.",
         |          "Ystyriwch unrhyw wiriadau pellach cyn cadarnhau’ch Ffurflen Dreth yn derfynol.",
         |          "Adborth CThEF: Nid yw HMRC Assist wedi dychwelyd unrhyw negeseuon.",
         |          "Ewch i GOV.UK i gael rhagor o wybodaeth am HMRC Assist",
         |          "https://www.gov.uk",
         |          "0" ] ] }
         |      ]
         |    },
         |    { "name": "rt_Record_Contacts_Outer", "value": "630982f2-cf51-f94e-9529-8bb1a1ce2927" }
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
        val responseJson: JsValue = acknowledgeJson(responseCode = Some(202))

        stubAcknowledge(Some(responseJson.toString), CREATED)

        val result: ServiceOutcome[RdsAcknowledgeResponse] = await(acknowledge())

        result shouldBe Right(ResponseWrapper(correlationId, responseJson.as[RdsAcknowledgeResponse]))
        result.map(_.responseData.vrn) shouldBe Right(Some(vrn))
        result.map(_.responseData.responseCode) shouldBe Right(Some(202))

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

        val feedback: FeedbackResponse = await(generateReport()).value.responseData

        feedback.reportId shouldBe feedbackId
        feedback.correlationId shouldBe None
        feedback.englishFeedback should have size 1
        feedback.welshFeedback should have size 1

      "build links from the separate link title and url columns" in new Test:
        stubReport(Some(reportJson().toString), CREATED)

        val feedback: FeedbackResponse = await(generateReport()).value.responseData

        feedback.englishFeedback.head.links shouldBe Some(List(FeedbackLink("VAT guidance", "https://www.gov.uk/vat-returns")))

    "the report includes a correlation id" should:
      "pass it through" in new Test:
        stubReport(Some(reportJson(correlationId = Some(rdsCorrelationId)).toString), CREATED)

        await(generateReport()).value.responseData.correlationId shouldBe Some(rdsCorrelationId)

    "the report includes a response code" should:

      "succeed when it is 201" in new Test:
        stubReport(Some(reportJson(responseCode = Some(201)).toString), CREATED)

        await(generateReport()).value.responseData.reportId shouldBe feedbackId

      "succeed when it is 204 (no feedback applicable)" in new Test:
        stubReport(Some(reportJson(responseCode = Some(204)).toString), CREATED)

        await(generateReport()).value.responseData.reportId shouldBe feedbackId

      "return DownstreamError when it is anything else" in new Test:
        stubReport(Some(reportJson(responseCode = Some(500)).toString), CREATED)

        await(generateReport()) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

    "RDS returns its no-feedback report (responseCode 204.0)" should:

      "return the no-feedback response rather than an error" in new Test:
        stubReport(Some(noFeedbackReportJson().toString), CREATED)

        val result: ServiceOutcome[FeedbackResponse] = await(generateReport())

        result.isRight shouldBe true
        result.value.responseData.reportId shouldBe feedbackId

      "return a single item 0 in both languages" in new Test:
        stubReport(Some(noFeedbackReportJson().toString), CREATED)

        val feedback: FeedbackResponse = await(generateReport()).value.responseData

        feedback.englishFeedback.map(_.itemNumber) shouldBe List("0")
        feedback.welshFeedback.map(_.itemNumber) shouldBe List("0")
        feedback.englishFeedback.head.title shouldBe "HMRC Feedback: HMRC Assist has not returned any messages."
        feedback.welshFeedback.head.title shouldBe "Adborth CThEF: Nid yw HMRC Assist wedi dychwelyd unrhyw negeseuon."

      "build the link from the link title and url columns" in new Test:
        stubReport(Some(noFeedbackReportJson().toString), CREATED)

        val feedback: FeedbackResponse = await(generateReport()).value.responseData

        feedback.englishFeedback.head.links shouldBe
          Some(List(FeedbackLink("Go to GOV.UK for more information on HMRC Assist", "https://www.gov.uk")))

      "pass through a null correlation id as None" in new Test:
        stubReport(Some(noFeedbackReportJson().toString), CREATED)

        await(generateReport()).value.responseData.correlationId shouldBe None

      "return DownstreamError when the report has no feedback id" in new Test:
        stubReport(Some(noFeedbackReportJson(includeFeedbackId = false).toString), CREATED)

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