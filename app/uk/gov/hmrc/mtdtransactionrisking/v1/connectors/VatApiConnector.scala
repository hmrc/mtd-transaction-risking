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
import play.api.http.Status.OK
import play.api.libs.json.JsValue
import play.api.libs.ws.writeableOf_JsValue
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.mtdtransactionrisking.config.AppConfig
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.{DownstreamError, ErrorWrapper}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.outcomes.ResponseWrapper
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.{Obligation, ObligationsResponse}
import uk.gov.hmrc.mtdtransactionrisking.v1.services.ServiceOutcome

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

@Singleton
class VatApiConnector @Inject() (httpClient: HttpClientV2, appConfig: AppConfig)(implicit ec: ExecutionContext) extends Logging:

  def validate(vrn: String, body: JsValue)(implicit hc: HeaderCarrier, correlationId: CorrelationId): Future[ServiceOutcome[Obligation]] =

    logger.info(s"${correlationId.value}::[VatApiConnector:validate] calling vat-api validate for vrn=$vrn")

    val url = s"${appConfig.vatApiBaseUrl}/validate/$vrn"

    httpClient
      .post(url"$url")
      .withBody(body)
      .setHeader(buildHeaders(correlationId, appConfig.appName)*)
      .execute[HttpResponse]
      .map { response =>
        response.status match
          case OK =>
            response.json.asOpt[Obligation] match
              case Some(obligation) =>
                logger.info(s"${correlationId.value}::[VatApiConnector:validate] success status=200 periodKey=${obligation.periodKey}")
                Right(ResponseWrapper(correlationId, obligation))

              case None =>
                val relayBody = Try(response.json).getOrElse(DownstreamError.asJson)
                logger.error(
                  s"${correlationId.value}::[VatApiConnector:validate] malformed success body status=200 body=${response.body}"
                )
                Left(ErrorWrapper(correlationId, DownstreamError, rawBody = Some(relayBody), rawStatus = Some(response.status)))

          case status =>
            val relayBody = Try(response.json).getOrElse(DownstreamError.asJson)
            logger.warn(
              s"${correlationId.value}::[VatApiConnector:validate] downstream error status=$status body=${response.body}"
            )
            Left(ErrorWrapper(correlationId, DownstreamError, rawBody = Some(relayBody), rawStatus = Some(status)))
      }
      .recover { case NonFatal(ex) =>
        logger.error(
          s"${correlationId.value}::[VatApiConnector:validate] exception calling vat-api validate for vrn=$vrn",
          ex
        )
        Left(ErrorWrapper(correlationId, DownstreamError))
      }

  def getObligations(vrn: String)(implicit hc: HeaderCarrier, correlationId: CorrelationId): Future[ServiceOutcome[ObligationsResponse]] =
    httpClient
      .get(url"${appConfig.vatApiBaseUrl}/organisations/vat/$vrn/obligations?status=O")
      .setHeader(buildHeaders(correlationId, appConfig.appName)*)
      .execute[HttpResponse]
      .map { response =>
        logger.debug(s"${correlationId.value}::[VatApiConnector:getObligations] response status=${response.status} for vrn=$vrn")
        response.status match
          case OK =>
            response.json.asOpt[ObligationsResponse] match
              case Some(obligations) =>
                logger.info(
                  s"${correlationId.value}::[VatApiConnector:getObligations] success status=200 obligationCount=${obligations.obligations.size}"
                )
                Right(ResponseWrapper(correlationId, obligations))
              case None =>
                val relayBody = Try(response.json).getOrElse(DownstreamError.asJson)
                logger.error(
                  s"${correlationId.value}::[VatApiConnector:getObligations] malformed success body status=200 body=${response.body}"
                )
                Left(ErrorWrapper(correlationId, DownstreamError, rawBody = Some(relayBody), rawStatus = Some(OK)))

          case status =>
            val relayBody = Try(response.json).getOrElse(DownstreamError.asJson)
            logger.warn(
              s"${correlationId.value}::[VatApiConnector:getObligations] downstream error status=$status body=${response.body}"
            )
            Left(ErrorWrapper(correlationId, DownstreamError, rawBody = Some(relayBody), rawStatus = Some(status)))
      }
      .recover { case NonFatal(ex) =>
        logger.error(
          s"${correlationId.value}::[VatApiConnector:getObligations] exception calling vat-api obligations for vrn=$vrn",
          ex
        )
        Left(ErrorWrapper(correlationId, DownstreamError))
      }

  private def buildHeaders(correlationId: CorrelationId, appName: String)(implicit hc: HeaderCarrier): Seq[(String, String)] =
    Seq(
      "User-Agent" -> appName,
      "Content-Type" -> "application/json",
      "Accept" -> "application/vnd.hmrc.1.0+json",
      "X-CorrelationId" -> correlationId.value
    ) ++ hc.authorization.map(a => "Authorization" -> a.value).toSeq
