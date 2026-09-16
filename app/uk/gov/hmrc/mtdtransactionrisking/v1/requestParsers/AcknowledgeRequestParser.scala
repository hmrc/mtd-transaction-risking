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

package uk.gov.hmrc.mtdtransactionrisking.v1.requestParsers

import play.api.mvc.Request
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.{MtdError, PresentedDateTimeFormatError, ReportIdFormatError, RdsCorrelationIdFormatError}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.AcknowledgeRequest

import java.time.Instant
import scala.util.Try

object AcknowledgeRequestParser:

  private val reportIdRegex      = "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
  private val correlationIdRegex = "^[0-9A-Fa-f]{64}$"

  def parseRequest(vrn: String, reportId: String, correlationId: String)(implicit request: Request[?]): Either[MtdError, AcknowledgeRequest] =

    val presentedDateTime = request.getQueryString("presentedDateTime")

    for
      _  <- validateReportId(reportId)
      _  <- validateCorrelationId(correlationId)
      dateTime <- validatePresentedDateTime(presentedDateTime)
    yield AcknowledgeRequest(vrn, reportId, correlationId, dateTime)

  private def validateReportId(reportId: String): Either[MtdError, Unit] =
    if reportId.matches(reportIdRegex) then Right(()) else Left(ReportIdFormatError)

  private def validateCorrelationId(correlationId: String): Either[MtdError, Unit] =
    if correlationId.matches(correlationIdRegex) then Right(()) else Left(RdsCorrelationIdFormatError)

  private def validatePresentedDateTime(value: Option[String]): Either[MtdError, String] =
    value match
      case Some(dateTime) if Try(Instant.parse(dateTime)).isSuccess => Right(dateTime)
      case _                                                        => Left(PresentedDateTimeFormatError)