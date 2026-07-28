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

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ObligationsConnector @Inject() (
    val httpClient: HttpClientV2,
    appConfig: AppConfig
)(implicit val ec: ExecutionContext)
    extends Logging:

  private def buildHeaders(correlationId: CorrelationId)(implicit hc: HeaderCarrier): Seq[(String, String)] =
    Seq(
      "Environment" -> appConfig.obligationEnv,
      "Authorization" -> appConfig.obligationAuthToken,
      "X-CorrelationId" -> correlationId.value
    )

  def getObligations(VRN: String)(implicit hc: HeaderCarrier, correlationId: CorrelationId): Future[ServiceOutcome[ObligationsResponse]] =
    logger.debug(s"${correlationId.value}::[ObligationsConnector:getObligations] calling obligations API")

    httpClient
      .get(url"${appConfig.obligationsServiceBaseUrl}/$VRN/VATC?status=O")
      .setHeader(buildHeaders(correlationId)*)
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
                Left(ErrorWrapper(correlationId, DownstreamError))

          case status =>
            logger.error(s"${correlationId.value}::[ObligationsConnector:getObligations] failed status $status: ${response.body}")
            Left(ErrorWrapper(correlationId, DownstreamError))
      }
      .recover:
        case ex =>
          logger.error(s"${correlationId.value}::[ObligationsConnector:getObligations] unexpected exception", ex)
          Left(ErrorWrapper(correlationId, DownstreamError))
