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

package uk.gov.hmrc.mtdtransactionrisking.v1.services

import cats.data.EitherT
import cats.implicits.*
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.utils.Logging
import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.{FeedbackConnector, InsightsConnector, RdsConnector, VatApiConnector}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.auth.IdentityData
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.{DownstreamError, ErrorWrapper}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.outcomes.ResponseWrapper
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.{InsightsRequest, ReportRequest}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.nrs.{AssistReportGenerated, AssistRequestFeedback}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.{FeedbackResponse, InsightsResponse, Obligation}
import uk.gov.hmrc.mtdtransactionrisking.v1.services.auth.RdsAuthService

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class GenerateFeedbackService @Inject() (
                                          rdsAuthService: RdsAuthService,
                                          interactionService: InteractionService,
                                          nrsService: NrsService,
                                          vatApiConnector: VatApiConnector,
                                          insightsConnector: InsightsConnector,
                                          feedbackStubConnector: FeedbackConnector,
                                          rdsConnector: RdsConnector)(implicit ec: ExecutionContext) extends Logging:

  def requestStubFeedback(vrn: String)(implicit hc: HeaderCarrier, correlationId: CorrelationId): Future[ServiceOutcome[FeedbackResponse]] =
    feedbackStubConnector.requestFeedback(InsightsRequest(vrn))

  def generateFeedback(
                        vrn: String,
                        body: JsValue,
                        agentReferenceNumber: Option[String],
                        requestHeaders: Seq[(String, String)],
                        identityData: Option[IdentityData],
                        userAuthToken: Option[String],
                        submissionTimestamp: Instant)(implicit hc: HeaderCarrier, correlationId: CorrelationId): Future[ServiceOutcome[FeedbackResponse]] =

    val result = for
      /*
       * Preserve the original validated VAT Assist request as Request Feedback
       * NRS evidence. It is not reconstructed from RDS output.
       */
      obligation <- EitherT(vatApiConnector.validate(vrn, body))

      insights <- EitherT(insightsConnector.getRiskInsights(InsightsRequest(vrn)))

      reportRequest <- EitherT.fromOption[Future](
        buildReportRequest(obligation.responseData, insights.responseData, body, agentReferenceNumber, requestHeaders),
        reportRequestFailure
      )

      credentials <- EitherT(rdsAuthService.bearerToken())

      report <- EitherT(rdsConnector.generateReport(vrn, reportRequest, credentials.responseData))

      _ = interactionService.store(report.responseData, obligation.responseData, vrn, body)

      _ =
        if hasActualFeedback(report.responseData) then
          submitNrsEvents(
            requestFeedbackEvidence = body,
            generateReportEvidence = report.responseData,
            vrn = vrn,
            reportId = correlationId.value,
            submissionTimestamp = submissionTimestamp,
            identityData = identityData,
            userAuthToken = userAuthToken,
            requestHeaders = requestHeaders
          )
    yield ResponseWrapper(correlationId, report.responseData)

    result.value

  private def submitNrsEvents(
                               requestFeedbackEvidence: JsValue,
                               generateReportEvidence: FeedbackResponse,
                               vrn: String,
                               reportId: String,
                               submissionTimestamp: Instant,
                               identityData: Option[IdentityData],
                               userAuthToken: Option[String],
                               requestHeaders: Seq[(String, String)]
                             ): Unit =
    nrsService.submit(
      evidence = requestFeedbackEvidence,
      vrn = vrn,
      reportId = reportId,
      submissionTimestamp = submissionTimestamp,
      identityData = identityData,
      userAuthToken = userAuthToken,
      requestHeaders = requestHeaders,
      notableEventType = AssistRequestFeedback
    )

    nrsService.submit(
      evidence = Json.toJson(generateReportEvidence),
      vrn = vrn,
      reportId = reportId,
      submissionTimestamp = submissionTimestamp,
      identityData = identityData,
      userAuthToken = userAuthToken,
      requestHeaders = requestHeaders,
      notableEventType = AssistReportGenerated
    )

  private def hasActualFeedback(feedbackResponse: FeedbackResponse): Boolean =
    (feedbackResponse.englishFeedback ++ feedbackResponse.welshFeedback)
      .exists(_.itemNumber != "0")

  private def buildReportRequest(obligation: Obligation,
                                  insights: InsightsResponse,
                                  vendorBody: JsValue,
                                  agentReferenceNumber: Option[String],
                                  requestHeaders: Seq[(String, String)])(implicit correlationId: CorrelationId): Option[ReportRequest] =

    val strategicRisk = insights.insights.strategicRisk

    ReportRequest.from(
      correlationId = correlationId.value,
      vendorBody = vendorBody,
      agentReferenceNumber = agentReferenceNumber,
      periodKey = obligation.periodKey,
      startDate = obligation.start,
      endDate = obligation.end,
      fraudRiskReportScore = strategicRisk.riskScore,
      fraudRiskReportReasons = strategicRisk.reasons,
      requestHeaders = requestHeaders
    )

  private def reportRequestFailure(implicit correlationId: CorrelationId): ErrorWrapper =
    logger.error(
      s"${correlationId.value}::[GenerateFeedbackService][generateFeedback] " +
        "validated body missing mandatory VAT figures"
    )
    ErrorWrapper(correlationId, DownstreamError)