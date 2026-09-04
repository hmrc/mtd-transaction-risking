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
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.Obligation
import uk.gov.hmrc.mtdtransactionrisking.v1.services.ServiceOutcome

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class VatApiConnector @Inject() (httpClient: HttpClientV2, appConfig: AppConfig)(implicit ec: ExecutionContext) extends Logging:

  def validate(vrn: String, body: JsValue)(implicit hc: HeaderCarrier, correlationId: CorrelationId): Future[ServiceOutcome[Obligation]] =

    logger.info(s"${correlationId.value}::[VatApiConnector][validate] validating VAT return for VRN $vrn")

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
                logger.info(
                  s"${correlationId.value}::[VatApiConnector][validate] validation passed, " +
                    s"matched obligation for periodKey ${obligation.periodKey}")
                Right(ResponseWrapper(correlationId, obligation))

              case None =>
                logger.error(s"${correlationId.value}::[VatApiConnector][validate] malformed obligation response")
                Left(ErrorWrapper(correlationId, DownstreamError))

          case status =>
            logger.warn(s"${correlationId.value}::[VatApiConnector][validate] validation failed $status: ${response.body}")
            val relayBody = Try(response.json).getOrElse(DownstreamError.asJson)
            Left(ErrorWrapper(correlationId, DownstreamError, rawBody = Some(relayBody), rawStatus = Some(status)))
      }
      .recover:
        case e =>
          logger.error(s"${correlationId.value}::[VatApiConnector][validate] unexpected exception", e)
          Left(ErrorWrapper(correlationId, DownstreamError))

  private def buildHeaders(correlationId: CorrelationId, appName: String)(implicit hc: HeaderCarrier): Seq[(String, String)] =
    Seq(
      "User-Agent" -> appName,
      "Content-Type" -> "application/json",
      "Accept" -> "application/vnd.hmrc.1.0+json",
      "X-CorrelationId" -> correlationId.value
    ) ++ hc.authorization.map(a => "Authorization" -> a.value).toSeq
