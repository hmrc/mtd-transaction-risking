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
import play.api.libs.json.JsValue
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.utils.Logging
import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.{FeedbackConnector, InsightsConnector, RdsConnector, VatApiConnector}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.{DownstreamError, ErrorWrapper}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.outcomes.ResponseWrapper
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.{InsightsRequest, RdsRequest}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.{FeedbackResponse, InsightsResponse, Obligation}
import uk.gov.hmrc.mtdtransactionrisking.v1.services.auth.RdsAuthService

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

/** Orchestrates feedback generation: validates the VAT return with vat-api, which also returns the open obligation for the period, then asks
  * insights-proxy to assess the risk.
  */
@Singleton
class GenerateFeedbackService @Inject() (
    rdsAuthService: RdsAuthService,
    interactionService: InteractionService,
    vatApiConnector: VatApiConnector,
    insightsConnector: InsightsConnector,
    feedbackStubConnector: FeedbackConnector,
    rdsConnector: RdsConnector
)(implicit ec: ExecutionContext)
    extends Logging:

  def requestStubFeedback(vrn: String)(implicit hc: HeaderCarrier, correlationId: CorrelationId): Future[ServiceOutcome[FeedbackResponse]] =
    feedbackStubConnector.requestFeedback(InsightsRequest(vrn))

  def generateFeedback(vrn: String, body: JsValue, agentReferenceNumber: Option[String], requestHeaders: Seq[(String, String)])(implicit
      hc: HeaderCarrier,
      correlationId: CorrelationId): Future[ServiceOutcome[FeedbackResponse]] =

    val result = for
      obligation <- EitherT(vatApiConnector.validate(vrn, body))
      _ = logger.info(
        s"${correlationId.value}::[GenerateFeedbackService][generateFeedback] VAT return validated, " +
          s"matched obligation for periodKey ${obligation.responseData.periodKey}")

      insights <- EitherT(insightsConnector.getRiskInsights(InsightsRequest(vrn)))

      reportRequest <- EitherT.fromOption[Future](
        buildReportRequest(obligation.responseData, insights.responseData, body, agentReferenceNumber, requestHeaders),
        reportRequestFailure
      )

      credentials <- EitherT(rdsAuthService.bearerToken())

      report <- EitherT(rdsConnector.generateReport(vrn, reportRequest, credentials.responseData))

      _ = interactionService.store(report.responseData, obligation.responseData, vrn, body)
    yield ResponseWrapper(correlationId, report.responseData)

    result.value

  private def buildReportRequest(obligation: Obligation,
                                 insights: InsightsResponse,
                                 vendorBody: JsValue,
                                 agentReferenceNumber: Option[String],
                                 requestHeaders: Seq[(String, String)])(implicit correlationId: CorrelationId): Option[RdsRequest] =

    val strategicRisk = insights.insights.strategicRisk

    RdsRequest.from(
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
    logger.error(s"${correlationId.value}::[GenerateFeedbackService][generateFeedback] validated body missing mandatory VAT figures")
    ErrorWrapper(correlationId, DownstreamError)
