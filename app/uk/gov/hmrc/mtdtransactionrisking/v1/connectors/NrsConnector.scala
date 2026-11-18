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
import play.api.http.Status.{ACCEPTED, INTERNAL_SERVER_ERROR, TOO_MANY_REQUESTS}
import scala.util.control.NonFatal
import play.api.libs.json.Json
import play.api.libs.ws.writeableOf_JsValue
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.mtdtransactionrisking.config.AppConfig
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.SubmissionResult
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.nrs.NrsSubmission

import java.io.IOException
import java.util.concurrent.TimeoutException
import javax.inject.{Inject, Singleton}
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class NrsConnector @Inject() (
    httpClient: HttpClientV2,
    appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends Logging:

  def submit(nrsSubmission: NrsSubmission, correlationId: CorrelationId): Future[SubmissionResult] =
    given HeaderCarrier = HeaderCarrier()

    val prefix = s"${correlationId.value}::[NrsConnector][submit]"
    val event = nrsSubmission.metadata.notableEvent

    logger.info(s"$prefix sending NRS submission for $event")

    httpClient
      .post(url"${appConfig.nrsSubmissionUrl}")
      .withBody(Json.toJson(nrsSubmission))
      .setHeader(
        "X-API-Key" -> appConfig.nrsApiKey,
        "X-Correlation-Id" -> correlationId.value,
        "Content-Type" -> "application/json"
      )
      .execute[HttpResponse]
      .map { response =>
        response.status match
          case ACCEPTED =>
            val nrsSubmissionId = Try((response.json \ "nrSubmissionId").asOpt[String]).toOption.flatten
            logger.info(s"$prefix NRS submission accepted for $event, nrsSubmissionId=${nrsSubmissionId.getOrElse("<not returned>")}")
            SubmissionResult.Success

          case status if status == TOO_MANY_REQUESTS || status == NrsConnector.CLIENT_CLOSED_REQUEST || status >= INTERNAL_SERVER_ERROR =>
            logger.warn(s"$prefix NRS submission received retryable status $status for $event")
            SubmissionResult.RetryableFailure

          case status =>
            logger.error(s"$prefix NRS submission received permanent status $status for $event")
            SubmissionResult.PermanentFailure
      }
      .recover {
        case error if NrsConnector.isRetryableTransportFailure(error) =>
          logger.warn(s"$prefix NRS submission failed with a retryable transport error for $event", error)
          SubmissionResult.RetryableFailure

        case NonFatal(error) =>
          logger.warn(s"$prefix NRS request failed for $event; treating as retryable", error)
          SubmissionResult.RetryableFailure
      }

object NrsConnector:

  private[connectors] val CLIENT_CLOSED_REQUEST = 499

  private[connectors] def isRetryableTransportFailure(error: Throwable): Boolean =

    @tailrec
    def hasRetryableCause(current: Throwable): Boolean =
      current match
        case _: IOException      => true
        case _: TimeoutException => true
        case other =>
          Option(other.getCause) match
            case Some(cause) if cause ne other => hasRetryableCause(cause)
            case _                             => false

    hasRetryableCause(error)
