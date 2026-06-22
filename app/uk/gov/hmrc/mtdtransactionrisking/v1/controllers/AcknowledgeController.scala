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
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.AcknowledgeConnector.AcknowledgeFailure
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.AcknowledgeRequest
import uk.gov.hmrc.mtdtransactionrisking.v1.services.AcknowledgeService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.{Instant, OffsetDateTime, ZoneOffset}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class AcknowledgeController @Inject()(
                                       cc: ControllerComponents,
                                       acknowledgeService: AcknowledgeService,
                                       idGenerator: IdGenerator
                                     )(implicit ec: ExecutionContext) extends BackendController(cc) {

  def acknowledgeReport(vrn: String, reportId: String, correlationId: String): Action[AnyContent] = Action.async { implicit request =>
    validate(vrn, reportId, request.getQueryString("presentedDateTime")) match

      case Left(result) =>
        Future.successful(result)

      case Right(presentedDateTime) =>
        acknowledgeService.acknowledge(AcknowledgeRequest(vrn, reportId, correlationId, presentedDateTime)).value.map {

        case Right(_) =>
          NoContent.withHeaders("X-CorrelationId" -> idGenerator.generateId())

        case Left(AcknowledgeFailure(status, code, message)) if Set(400, 401, 403, 404).contains(status) =>
          Status(status)(Json.obj("code" -> code, "message" -> message))

        case Left(_) =>
          InternalServerError(Json.obj("code" -> "INTERNAL_SERVER_ERROR", "message" -> "An unexpected error occurred."))

    }

  }

  private val vrnPattern = "^[0-9]{9}$"

  private val reportIdPattern = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"

  private val presentedDateTimePattern = raw"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$$"


  private def validate(vrn: String, reportId: String, presentedDateTime: Option[String]): Either[play.api.mvc.Result, OffsetDateTime] =

    if !vrn.matches(vrnPattern) then Left(BadRequest(Json.obj("code" -> "FORMAT_VRN", "message" -> "The provided Vrn is invalid.")))
    else if !reportId.matches(reportIdPattern) then Left(BadRequest(Json.obj("code" -> "FORMAT_RECEIPT_ID", "message" -> "The provided Report ID is invalid.")))
    else presentedDateTime.flatMap(parsePresentedDateTime).toRight(
      BadRequest(Json.obj("code" -> "FORMAT_DATETIME", "message" -> "The provided Presented Date Time is invalid."))
    )


  private def parsePresentedDateTime(value: String): Option[OffsetDateTime] =
    if !value.matches(presentedDateTimePattern) then None
    else scala.util.Try(OffsetDateTime.ofInstant(Instant.parse(value), ZoneOffset.UTC)).toOption
}