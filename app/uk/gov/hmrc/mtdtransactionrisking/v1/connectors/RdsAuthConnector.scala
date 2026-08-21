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
import play.api.libs.ws.WSBodyWritables.writeableOf_urlEncodedForm
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.mtdtransactionrisking.config.AppConfig
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.models.auth.{RdsAuthCredentials, RdsCredentials}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.{DownstreamError, ErrorWrapper}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.outcomes.ResponseWrapper
import uk.gov.hmrc.mtdtransactionrisking.v1.services.ServiceOutcome

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RdsAuthConnector @Inject() (httpClient: HttpClientV2, appConfig: AppConfig)(implicit ec: ExecutionContext) extends Logging:

  def retrieveBearerToken()(implicit hc: HeaderCarrier, correlationId: CorrelationId): Future[ServiceOutcome[RdsAuthCredentials]] =

    logger.info(s"${correlationId.value}::[RdsAuthConnector] requesting a bearer token from SAS")

    httpClient
      .post(url"${appConfig.rdsAuthUrl}")
      .setHeader(buildHeaders(appConfig.rdsCredentials)*)
      .withBody(Map("grant_type" -> Seq("client_credentials")))
      .withProxy
      .execute[HttpResponse]
      .map { response =>
        response.status match
          case OK =>
            response.json.asOpt[RdsAuthCredentials] match
              case Some(credentials) =>
                logger.info(s"${correlationId.value}::[RdsAuthConnector] token issued, expires in ${credentials.expires_in}s")
                Right(ResponseWrapper(correlationId, credentials))

              case None =>
                logger.error(s"${correlationId.value}::[RdsAuthConnector] malformed token response")
                Left(ErrorWrapper(correlationId, DownstreamError))

          case status =>
            logger.error(s"${correlationId.value}::[RdsAuthConnector] failed $status: ${response.body}")
            Left(ErrorWrapper(correlationId, DownstreamError))
      }
      .recover:
        case ex =>
          logger.error(s"${correlationId.value}::[RdsAuthConnector] unexpected exception", ex)
          Left(ErrorWrapper(correlationId, DownstreamError))

  private def buildHeaders(credentials: RdsCredentials): Seq[(String, String)] =
    val encoded = Base64.getEncoder.encodeToString(s"${credentials.clientId}:${credentials.clientSecret}".getBytes(UTF_8))

    Seq(
      "Content-Type" -> "application/x-www-form-urlencoded",
      "Accept" -> "application/json",
      "Authorization" -> s"Basic $encoded"
    )
