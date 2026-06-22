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

import cats.data.EitherT
import play.api.Logging
import play.api.http.Status.*
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.mtdtransactionrisking.config.AppConfig
import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.AcknowledgeConnector.*
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.AcknowledgeRequest
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.AcknowledgeResponse

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}


object AcknowledgeConnector:
  final case class AcknowledgeFailure(statusCode: Int, code: String, message: String)
  private final case class UpstreamErrorBody(code: String, message: String)
  private given Reads[UpstreamErrorBody] = Json.reads[UpstreamErrorBody]

@Singleton
class AcknowledgeConnector @Inject()(
                                      httpClient: HttpClientV2,
                                      appConfig: AppConfig
                                    )(implicit ec: ExecutionContext) extends Logging:


  def acknowledge(request: AcknowledgeRequest)(implicit hc: HeaderCarrier): EitherT[Future, AcknowledgeFailure, Unit] =
    EitherT(
      httpClient
        .post(url"${appConfig.cipRiskServiceBaseUrl}")
        .execute[Either[UpstreamErrorResponse, Unit]]
        .map {
         case Right(_) =>
            logger.info(s"${request.correlationId}::[AcknowledgeConnector:acknowledge] success")
              Right(())
          case Left(errorResponse) =>
           logger.error(s"${request.correlationId}::[AcknowledgeConnector:acknowledge] failed status ${errorResponse.statusCode}: ${errorResponse.message}")
            val parsed = scala.util.Try(Json.parse(errorResponse.message).as[UpstreamErrorBody]).toOption
              parsed match
              case Some(body) => Left(AcknowledgeFailure(errorResponse.statusCode, body.code, body.message))
              case None       => Left(AcknowledgeFailure(errorResponse.statusCode, "INTERNAL_SERVER_ERROR", "An unexpected error occurred."))
        }
    )
