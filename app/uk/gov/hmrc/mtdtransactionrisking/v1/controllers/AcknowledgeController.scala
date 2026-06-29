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

package uk.gov.hmrc.mtdtransactionrisking.v1.controllers

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mtdtransactionrisking.v1.controllers.auth.VATAuthAction
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.AcknowledgeRequest
import uk.gov.hmrc.mtdtransactionrisking.v1.services.AcknowledgeService
import uk.gov.hmrc.mtdtransactionrisking.v1.services.AcknowledgeService.{AcknowledgeServiceError, ClientOrAuthError, InternalServiceError}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import java.time.{Instant, OffsetDateTime, ZoneOffset}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AcknowledgeController @Inject()(
                                       cc: ControllerComponents,
                                       acknowledgeService: AcknowledgeService,
                                       authAction: VATAuthAction
                                     )(implicit ec: ExecutionContext) extends BackendController(cc):

  def acknowledgeReport(vrn: String, reportId: String, correlationId: String): Action[AnyContent] =
    authAction.authorisedFor(vrn).async: request =>
      validate(reportId, request.getQueryString("presentedDateTime")) match
        case Left(result) =>
          Future.successful(result)

        case Right(presentedDateTime) =>
          given HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

          acknowledgeService
            .acknowledge(AcknowledgeRequest(vrn, reportId, correlationId, presentedDateTime))
            .value
            .map {
              case Right(_) =>
                NoContent.withHeaders("X-CorrelationId" -> correlationId)

              case Left(error) =>
                toResult(error, correlationId)
            }

  private def toResult(error: AcknowledgeServiceError, correlationId: String): Result =
    error match
      case ClientOrAuthError(status, code, message) =>
        Status(status)(Json.obj("code" -> code, "message" -> message))
          .withHeaders("X-CorrelationId" -> correlationId)

      case InternalServiceError =>
        InternalServerError(Json.obj("code" -> "INTERNAL_SERVER_ERROR", "message" -> "An unexpected error occurred."))
          .withHeaders("X-CorrelationId" -> correlationId)


  private val reportIdPattern = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
  private val presentedDateTimePattern = raw"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$$"

  private def validate(reportId: String, presentedDateTime: Option[String]): Either[Result, OffsetDateTime] =
    if !reportId.matches(reportIdPattern) then Left(BadRequest(Json.obj("code" -> "FORMAT_RECEIPT_ID", "message" -> "The provided Report ID is invalid.")))
    else presentedDateTime.flatMap(parsePresentedDateTime).toRight(
      BadRequest(Json.obj("code" -> "FORMAT_DATETIME", "message" -> "The provided Presented Date Time is invalid."))
    )

  private def parsePresentedDateTime(value: String): Option[OffsetDateTime] =
    if !value.matches(presentedDateTimePattern) then None
    else scala.util.Try(OffsetDateTime.ofInstant(Instant.parse(value), ZoneOffset.UTC)).toOption