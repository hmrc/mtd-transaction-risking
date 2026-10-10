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
import play.api.http.Status.{ACCEPTED, TOO_MANY_REQUESTS}
import play.api.libs.json.Json
import play.api.libs.ws.writeableOf_JsValue
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.mtdtransactionrisking.config.AppConfig
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.nrs.{NrsSubmission, NrsSubmissionResult}

import java.io.IOException
import java.util.concurrent.TimeoutException
import javax.inject.{Inject, Singleton}
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NrsConnector @Inject()(
                              httpClient: HttpClientV2,
                              appConfig: AppConfig
                            )(implicit ec: ExecutionContext)
  extends Logging:

  def submit(nrsSubmission: NrsSubmission, correlationId: CorrelationId): Future[NrsSubmissionResult] =
    given HeaderCarrier = HeaderCarrier()

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
            logger.info(
              s"${correlationId.value}::[NrsConnector][submit] " +
                "NRS submission accepted"
            )
            NrsSubmissionResult.Success

          case TOO_MANY_REQUESTS | 499 =>
            logger.warn(
              s"${correlationId.value}::[NrsConnector][submit] " +
                s"NRS submission received retryable status ${response.status}"
            )
            NrsSubmissionResult.RetryableFailure

          case status if status >= 500 && status <= 599 =>
            logger.warn(
              s"${correlationId.value}::[NrsConnector][submit] " +
                s"NRS submission received retryable status $status"
            )
            NrsSubmissionResult.RetryableFailure

          case status =>
            logger.warn(
              s"${correlationId.value}::[NrsConnector][submit] " +
                s"NRS submission received permanent status $status"
            )
            NrsSubmissionResult.PermanentFailure
      }
      .recoverWith {
        case error if NrsConnector.isRetryableTransportFailure(error) =>
          logger.warn(
            s"${correlationId.value}::[NrsConnector][submit] " +
              "NRS submission failed with a retryable transport error",
            error
          )
          Future.successful(NrsSubmissionResult.RetryableFailure)
      }

object NrsConnector:

  private[connectors] def isRetryableTransportFailure(
                                                       error: Throwable
                                                     ): Boolean =

    @tailrec
    def hasRetryableCause(current: Throwable): Boolean =
      current match
        case _: IOException      => true
        case _: TimeoutException => true
        case other =>
          Option(other.getCause) match
            case Some(cause) if cause ne other =>
              hasRetryableCause(cause)
            case _ =>
              false

    hasRetryableCause(error)
    