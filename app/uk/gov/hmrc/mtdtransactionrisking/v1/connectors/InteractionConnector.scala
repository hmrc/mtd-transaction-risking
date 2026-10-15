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
import play.api.http.Status.NO_CONTENT
import play.api.libs.ws.writeableOf_JsValue
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{BadGatewayException, GatewayTimeoutException, HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.mtdtransactionrisking.config.AppConfig
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.models.auth.InteractionCredentials
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.SubmissionResult
import play.api.libs.json.JsValue

import java.net.{ConnectException, SocketException, UnknownHostException, SocketTimeoutException}
import java.net.http.HttpTimeoutException
import java.util.concurrent.TimeoutException
import scala.annotation.tailrec
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64
import javax.inject.{Inject, Singleton}
import javax.net.ssl.SSLException
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

@Singleton
class InteractionConnector @Inject() (httpClient: HttpClientV2, appConfig: AppConfig)(implicit ec: ExecutionContext) extends Logging:

  def store(body: JsValue, feedbackId: String, eventName: String, correlationId: CorrelationId)(implicit hc: HeaderCarrier): Future[SubmissionResult] =

    logger.info(s"${correlationId.value}::[InteractionConnector][store] storing interaction for feedbackId $feedbackId")

    Future
      .delegate {
        httpClient
          .post(url"${appConfig.interactionsBaseUrl}")
          .withBody(body)
          .setHeader(
            buildHeaders(
              correlationId = correlationId,
              credentials = appConfig.interactionCredentials
            ) *
          )
          .execute[HttpResponse]
          .map { response =>
            response.status match
              case NO_CONTENT =>
                logger.info(
                  s"${correlationId.value}::[InteractionConnector][store] " +
                    s"interaction stored for $eventName, " +
                    s"feedbackId $feedbackId"
                )
                SubmissionResult.Success

              case status if status == 499 || (status >= 500 && status <= 599) =>
                logger.warn(
                  s"${correlationId.value}::[InteractionConnector][store] " +
                    s"retryable RSD status $status for $eventName, " +
                    s"feedbackId $feedbackId"
                )
                SubmissionResult.RetryableFailure

              case status =>
                logger.warn(
                  s"${correlationId.value}::[InteractionConnector][store] " +
                    s"non-retryable RSD status $status for $eventName, " +
                    s"feedbackId $feedbackId"
                )
                SubmissionResult.PermanentFailure
          }
      }
      .recoverWith {
        case error if InteractionConnector.containsSslFailure(error) =>
          logger.warn(
            s"${correlationId.value}::[InteractionConnector][store] " +
              s"permanent SSL/TLS failure for $eventName, feedbackId $feedbackId",
            error
          )
          Future.successful(SubmissionResult.PermanentFailure)

        case error if InteractionConnector.isRetryableTransportFailure(error) =>
          logger.warn(
            s"${correlationId.value}::[InteractionConnector][store] " +
              s"retryable RSD transport failure for $eventName, " +
              s"feedbackId $feedbackId: ${error.getClass.getSimpleName}",
            error
          )
          Future.successful(SubmissionResult.RetryableFailure)

      }

  private def buildHeaders(correlationId: CorrelationId, credentials: InteractionCredentials): Seq[(String, String)] =
    val encoded = Base64.getEncoder.encodeToString(s"${credentials.clientId}:${credentials.clientSecret}".getBytes(UTF_8))

    Seq(
      "Content-Type" -> "application/json",
      "Accept" -> "*/*",
      "CorrelationId" -> correlationId.value,
      "Authorization" -> s"Basic $encoded"
    )

object InteractionConnector:

  private[connectors] def containsSslFailure(error: Throwable): Boolean =
    containsCause[SSLException](error)

  private[connectors] def isRetryableTransportFailure(
                                                       error: Throwable
                                                     ): Boolean =
    !containsSslFailure(error) && (
      containsCause[GatewayTimeoutException](error) ||
        containsCause[BadGatewayException](error) ||
        containsCause[TimeoutException](error) ||
        containsCause[SocketTimeoutException](error) ||
        containsCause[HttpTimeoutException](error) ||
        containsCause[ConnectException](error) ||
        containsCause[SocketException](error) ||
        containsCause[UnknownHostException](error))

  private def containsCause[T <: Throwable: ClassTag](
                                                       error: Throwable
                                                     ): Boolean =
    @tailrec
    def loop(current: Throwable): Boolean =
      if summon[ClassTag[T]].runtimeClass.isInstance(current) then
        true
      else
        Option(current.getCause) match
          case Some(cause) if cause ne current =>
            loop(cause)

          case _ =>
            false

    loop(error)
