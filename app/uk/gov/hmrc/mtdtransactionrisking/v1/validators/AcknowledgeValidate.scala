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

package uk.gov.hmrc.mtdtransactionrisking.v1.validators

import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.mvc.Results.BadRequest

import java.time.{Instant, OffsetDateTime, ZoneOffset}


private val reportIdPattern = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
private val presentedDateTimePattern = raw"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$$"

def acknowledgeValidate(reportId: String, presentedDateTime: Option[String]): Either[Result, OffsetDateTime] =
  if !reportId.matches(reportIdPattern) then Left(BadRequest(Json.obj("code" -> "FORMAT_RECEIPT_ID", "message" -> "The provided Report ID is invalid.")))
  else presentedDateTime.flatMap(parsePresentedDateTime).toRight(
    BadRequest(Json.obj("code" -> "FORMAT_DATETIME", "message" -> "The provided Presented Date Time is invalid."))
  )

private def parsePresentedDateTime(value: String): Option[OffsetDateTime] =
  if !value.matches(presentedDateTimePattern) then None
  else scala.util.Try(OffsetDateTime.ofInstant(Instant.parse(value), ZoneOffset.UTC)).toOption


