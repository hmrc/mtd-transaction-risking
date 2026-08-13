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
import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.{FeedbackConnector, InsightsConnector, VatApiConnector}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.outcomes.ResponseWrapper
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.InsightsRequest
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.{FeedbackResponse, InsightsResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

/** Orchestrates feedback generation: validates the VAT return with vat-api, which also returns the
 * open obligation for the period, then asks insights-proxy to assess the risk.
 */
@Singleton
class GenerateFeedbackService @Inject() (
                                          vatApiConnector: VatApiConnector,
                                          insightsConnector: InsightsConnector,
                                          feedbackStubConnector: FeedbackConnector
                                        )(implicit ec: ExecutionContext)
  extends Logging:

  def generateFeedback(vrn: String, body: JsValue)(implicit
                                                   hc: HeaderCarrier,
                                                   correlationId: CorrelationId): Future[ServiceOutcome[InsightsResponse]] =

    val result = for
      obligation <- EitherT(vatApiConnector.validate(vrn, body))
      _ = logger.info(
        s"${correlationId.value}::[GenerateFeedbackService][generateFeedback] VAT return validated, " +
          s"matched obligation for periodKey ${obligation.responseData.periodKey}, " +
          s"period ${obligation.responseData.start} to ${obligation.responseData.end}")
      insights <- EitherT(insightsConnector.getRiskInsights(InsightsRequest(vrn)))
    yield ResponseWrapper(correlationId, insights.responseData)

    result.value

  def requestStubFeedback(vrn: String)(implicit
                                       hc: HeaderCarrier,
                                       correlationId: CorrelationId): Future[ServiceOutcome[FeedbackResponse]] =
    feedbackStubConnector.requestFeedback(InsightsRequest(vrn))