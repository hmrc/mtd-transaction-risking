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

import play.api.Logging
import play.api.libs.json.Json
import play.api.libs.ws.writeableOf_JsValue
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.mtdtransactionrisking.config.AppConfig
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.{DownstreamError, ErrorWrapper}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.outcomes.ResponseWrapper
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.InsightsRequest
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.InsightsResponse
import uk.gov.hmrc.mtdtransactionrisking.v1.services.ServiceOutcome

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class InsightsConnector @Inject() (
    val httpClient: HttpClientV2,
    appConfig: AppConfig
)(implicit val ec: ExecutionContext)
    extends Logging:

  def getRiskInsights(request: InsightsRequest)(implicit hc: HeaderCarrier, correlationId: CorrelationId): Future[ServiceOutcome[InsightsResponse]] =
    logger.debug(s"${correlationId.value}::[InsightsConnector:getRiskInsights] calling insights API")

    httpClient
      .post(url"${appConfig.insightsProxyServiceBaseUrl}")
      .withBody(Json.toJson(request))
      .setHeader(buildHeaders(correlationId, appConfig.appName)*)
      .execute[HttpResponse]
      .map { response =>
        response.status match
          case 200 =>
            response.json.asOpt[InsightsResponse] match
              case Some(insights) =>
                logger.debug(s"${correlationId.value}::[InsightsConnector:getRiskInsights] success")
                Right(ResponseWrapper(correlationId, insights))
              case None =>
                logger.error(s"${correlationId.value}::[InsightsConnector:getRiskInsights] malformed response")
                Left(ErrorWrapper(correlationId, DownstreamError))
          case status =>
            logger.error(s"${correlationId.value}::[InsightsConnector:getRiskInsights] failed status $status: ${response.body}")
            Left(ErrorWrapper(correlationId, DownstreamError))
      }
      .recover:
        case ex =>
          logger.error(s"${correlationId.value}::[InsightsConnector:getRiskInsights] unexpected exception", ex)
          Left(ErrorWrapper(correlationId, DownstreamError))

  private def buildHeaders(correlationId: CorrelationId, appName: String): Seq[(String, String)] =
    Seq(
      "User-Agent" -> appName,
      "Content-Type" -> "application/json",
      "X-CorrelationId" -> correlationId.value
    )
