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
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.mtdtransactionrisking.config.AppConfig
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.{DownstreamError, ErrorWrapper}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.outcomes.ResponseWrapper
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.ObligationsResponse
import uk.gov.hmrc.mtdtransactionrisking.v1.services.ServiceOutcome

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class ObligationsConnector @Inject() (
                                       httpClient: HttpClientV2,
                                       appConfig: AppConfig
                                     )(implicit ec: ExecutionContext)
  extends Logging:

  def getObligations(vrn: String)(implicit
                                  hc: HeaderCarrier,
                                  correlationId: CorrelationId
  ): Future[ServiceOutcome[ObligationsResponse]] =
    logger.debug(s"${correlationId.value}::[ObligationsConnector:getObligations] calling obligations API")

    httpClient
      .get(url"${appConfig.vatApiBaseUrl}/organisations/vat/$vrn/obligations?status=O")
      .setHeader(buildHeaders(correlationId, appConfig.appName)*)
      .execute[HttpResponse]
      .map { response =>
        response.status match
          case 200 =>
            response.json.asOpt[ObligationsResponse] match
              case Some(obligations) =>
                logger.debug(s"${correlationId.value}::[ObligationsConnector:getObligations] success fetching raw obligations")
                Right(ResponseWrapper(correlationId, obligations))

              case None =>
                logger.error(s"${correlationId.value}::[ObligationsConnector:getObligations] malformed response")
                val body = Try(response.json).getOrElse(DownstreamError.asJson)
                Left(ErrorWrapper(correlationId, DownstreamError, rawBody = Some(body), rawStatus = Some(response.status)))

          case status =>
            logger.error(s"${correlationId.value}::[ObligationsConnector:getObligations] failed status $status: ${response.body}")
            val body = Try(response.json).getOrElse(DownstreamError.asJson)
            Left(ErrorWrapper(correlationId, DownstreamError, rawBody = Some(body), rawStatus = Some(status)))
      }
      .recover:
        case ex =>
          logger.error(s"${correlationId.value}::[ObligationsConnector:getObligations] unexpected exception", ex)
          Left(ErrorWrapper(correlationId, DownstreamError))

  private def buildHeaders(correlationId: CorrelationId, appName: String)(implicit hc: HeaderCarrier): Seq[(String, String)] =
    Seq(
      "User-Agent"      -> appName,
      "Accept"          -> "application/vnd.hmrc.1.0+json",
      "X-CorrelationId" -> correlationId.value
    ) ++ hc.authorization.toSeq.map(a => "Authorization" -> a.value)