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
import play.api.http.Status.*
import play.api.libs.json.Json
import play.api.libs.ws.writeableOf_JsValue
import play.api.libs.json.JsError
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.mtdtransactionrisking.config.AppConfig
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.models.auth.RdsAuthCredentials
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.{DownstreamError, ErrorWrapper, ServiceUnavailableError}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.outcomes.ResponseWrapper
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.{AcknowledgeRequest, ReportRequest}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.{AcknowledgeResponseWrapper, FeedbackResponse, ReportResponse, ReportResponseTransform}
import uk.gov.hmrc.mtdtransactionrisking.v1.services.ServiceOutcome

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class RdsConnector @Inject() (httpClient: HttpClientV2, appConfig: AppConfig)(implicit ec: ExecutionContext) extends Logging:

  def acknowledge(request: AcknowledgeRequest, credentials: Option[RdsAuthCredentials])(implicit
                                                                                        hc: HeaderCarrier,
                                                                                        correlationId: CorrelationId): Future[ServiceOutcome[Unit]] =

    logger.info(
      s"${correlationId.value}::[RdsConnector][acknowledge] calling acknowledge service"
    )
    httpClient
      .post(url"${appConfig.rdsAcknowledgeUrl}")
      .withBody(Json.toJson(request))
      .setHeader(buildHeaders(correlationId, appConfig.appName, credentials) *)
      .withProxy
      .execute[HttpResponse]
      .map { response =>
        response.status match

          case ACCEPTED =>
            response.json
              .validate[AcknowledgeResponseWrapper]
              .fold(
                errors =>
                  logger.error(
                    s"${correlationId.value}::[RdsConnector][acknowledge] " +
                      s"malformed response: ${JsError.toJson(errors)}"
                  )

                  Left(
                    ErrorWrapper(
                      correlationId,
                      DownstreamError,
                      rawBody = Some(response.json),
                      rawStatus = Some(response.status)
                    )
                  ),

                acknowledgeResponse =>
                  if acknowledgeResponse.output.responseCode.contains(ACCEPTED) then
                    logger.info(
                      s"${correlationId.value}::[RdsConnector][acknowledge] success"
                    )

                    Right(ResponseWrapper(correlationId, ()))
                  else
                    logger.error(
                      s"${correlationId.value}::[RdsConnector][acknowledge] " +
                        s"unexpected response code: " +
                        s"${acknowledgeResponse.output.responseCode.getOrElse("missing")}"
                    )

                    Left(
                      ErrorWrapper(
                        correlationId,
                        DownstreamError,
                        rawBody = Some(response.json),
                        rawStatus = Some(response.status)
                      )
                    )
              )

          case NOT_FOUND | REQUEST_TIMEOUT | SERVICE_UNAVAILABLE =>
            logger.error(
              s"${correlationId.value}::[RdsConnector][acknowledge] " +
                s"RDS unavailable, status ${response.status}"
            )

            Left(ErrorWrapper(correlationId, ServiceUnavailableError))

          case status =>
            logger.error(
              s"${correlationId.value}::[RdsConnector][acknowledge] " +
                s"failed $status: ${response.body}"
            )

            val body =
              Try(response.json).getOrElse(DownstreamError.asJson)

            Left(
              ErrorWrapper(
                correlationId,
                DownstreamError,
                rawBody = Some(body),
                rawStatus = Some(status)
              )
            )
      }
      .recover:
        case ex =>
          logger.error(
            s"${correlationId.value}::[RdsConnector][acknowledge] unexpected exception",
            ex
          )

          Left(ErrorWrapper(correlationId, DownstreamError))

  /** A 201 means the call executed. The decision itself is in the report responseCode field. */
  def generateReport(vrn: String, request: ReportRequest, credentials: Option[RdsAuthCredentials])(implicit
      hc: HeaderCarrier,
      correlationId: CorrelationId): Future[ServiceOutcome[FeedbackResponse]] =

    logger.info(s"${correlationId.value}::[RdsConnector][generateReport] requesting report for VRN $vrn")

    httpClient
      .post(url"${appConfig.rdsSubmitUrl}")
      .withBody(Json.toJson(request))
      .setHeader(buildHeaders(correlationId, appConfig.appName, credentials)*)
      .withProxy
      .execute[HttpResponse]
      .map { response =>
        response.status match
          case CREATED =>
            handleReport(response)

          case BAD_REQUEST =>
            logger.error(s"${correlationId.value}::[RdsConnector][generateReport] RDS rejected the request: ${response.body}")
            Left(ErrorWrapper(correlationId, DownstreamError))

          case NOT_FOUND | REQUEST_TIMEOUT | SERVICE_UNAVAILABLE =>
            logger.error(s"${correlationId.value}::[RdsConnector][generateReport] RDS unavailable, status ${response.status}")
            Left(ErrorWrapper(correlationId, ServiceUnavailableError))

          case status =>
            logger.error(s"${correlationId.value}::[RdsConnector][generateReport] unexpected status $status: ${response.body}")
            Left(ErrorWrapper(correlationId, DownstreamError))
      }
      .recover:
        case ex =>
          logger.error(s"${correlationId.value}::[RdsConnector][generateReport] unexpected exception", ex)
          Left(ErrorWrapper(correlationId, DownstreamError))

  private def handleReport(response: HttpResponse)(implicit correlationId: CorrelationId): ServiceOutcome[FeedbackResponse] =
    response.json.asOpt[ReportResponse] match
      case None =>
        logger.error(s"${correlationId.value}::[RdsConnector][generateReport] malformed report: ${response.body}")
        Left(ErrorWrapper(correlationId, DownstreamError))

      case Some(report) =>
        report.responseCode match
          case Some(CREATED) =>
            ReportResponseTransform.toFeedbackResponse(report) match
              case Some(feedback) =>
                logger.info(s"${correlationId.value}::[RdsConnector][generateReport] report generated")
                Right(ResponseWrapper(correlationId, feedback))
              case None => Left(ErrorWrapper(correlationId, DownstreamError))

          case Some(code) =>
            logger.error(
              s"${correlationId.value}::[RdsConnector][generateReport] unexpected responseCode $code: " +
                s"${report.responseMessage.getOrElse("no message")}")
            Left(ErrorWrapper(correlationId, DownstreamError))

          case None =>
            logger.error(s"${correlationId.value}::[RdsConnector][generateReport] report has no responseCode")
            Left(ErrorWrapper(correlationId, DownstreamError))

  private def buildHeaders(correlationId: CorrelationId, appName: String, credentials: Option[RdsAuthCredentials]): Seq[(String, String)] =
    Seq(
      "User-Agent" -> appName,
      "Content-Type" -> "application/json",
      "X-CorrelationId" -> correlationId.value
    ) ++ credentials.map(_.bearerHeader)
