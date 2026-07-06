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
import play.api.libs.json.{Json, Reads}
import play.api.libs.ws.DefaultBodyWritables.writeableOf_WsBody
import play.api.libs.ws.EmptyBody
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.mtdtransactionrisking.config.AppConfig
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.AcknowledgeRequest

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

object AcknowledgeConnector:
  final case class AcknowledgeConnectorError(statusCode: Int, code: String, message: String)

  private final case class UpstreamErrorBody(code: String, message: String)
  private given Reads[UpstreamErrorBody] = Json.reads[UpstreamErrorBody]

@Singleton
class AcknowledgeConnector @Inject()(
                                      httpClient: HttpClientV2,
                                      appConfig: AppConfig
                                    )(implicit ec: ExecutionContext) extends Logging:



  def acknowledge(request: AcknowledgeRequest)(implicit hc: HeaderCarrier): EitherT[Future, AcknowledgeConnector.AcknowledgeConnectorError, Unit] =    EitherT(
      httpClient
        .post(url"${appConfig.acknowledgeStubServiceBaseUrl}").withBody(Json.obj())
        .execute[Either[UpstreamErrorResponse, Unit]]
        .map {
          case Right(_) =>
            logger.info(s"${request.correlationId}::[AcknowledgeConnector:acknowledge] success")
            Right(())

          case Left(errorResponse) =>
            logger.error(
              s"${request.correlationId}::[AcknowledgeConnector:acknowledge] failed status ${errorResponse.statusCode}: ${errorResponse.message}"
            )


            val parsedBody = scala.util.Try(Json.parse(errorResponse.message).as[AcknowledgeConnector.UpstreamErrorBody]).toOption
            parsedBody match
              case Some(body) =>
                Left(AcknowledgeConnector.AcknowledgeConnectorError(errorResponse.statusCode, body.code, body.message))
              case None =>
                Left(AcknowledgeConnector.AcknowledgeConnectorError(errorResponse.statusCode, "INTERNAL_SERVER_ERROR", "An unexpected error occurred."))
        }
        .recover {
          case ex =>
            logger.error(
              s"${request.correlationId}::[AcknowledgeConnector:acknowledge] unexpected exception: ${ex.getMessage}", ex
            )
            Left(AcknowledgeConnector.AcknowledgeConnectorError(500, "INTERNAL_SERVER_ERROR", "An unexpected error occurred."))
        }
    )