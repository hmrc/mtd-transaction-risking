/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.mtdtransactionrisking.v1.models.errors

import play.api.http.Status.*
import play.api.libs.functional.syntax.*
import play.api.libs.json.*

case class MtdError(code: String, message: String, httpStatus: Int, path: Option[String] = None):
  val asJson: JsObject = Json.toJson(this).as[JsObject]

object MtdError:

  given writes: OWrites[MtdError] = (
    (JsPath \ "code").write[String] and
      (JsPath \ "message").write[String] and
      (JsPath \ "path").writeNullable[String]
  )(e => (e.code, e.message, e.path))

  given genericWrites[T <: MtdError]: OWrites[T] =
    writes.contramap[T](c => c: MtdError)

// Format Errors
object VrnFormatError extends MtdError("VRN_INVALID", "The provided VRN is invalid", BAD_REQUEST)

object PeriodKeyFormatError extends MtdError("PERIOD_KEY_INVALID", "The provided period key is invalid", BAD_REQUEST)

// Rule Errors
object RuleIncorrectOrEmptyBodyError
    extends MtdError("RULE_INCORRECT_OR_EMPTY_BODY_SUBMITTED", "An empty or non-matching body was submitted", BAD_REQUEST)

object TaxPeriodNotEndedError
    extends MtdError(
      "TAX_PERIOD_NOT_ENDED",
      "The remote endpoint has indicated that the submission is for a tax period that has not ended",
      FORBIDDEN)

object RuleInsolventTraderError extends MtdError("RULE_INSOLVENT_TRADER", "The remote endpoint has indicated that the Trader is insolvent", FORBIDDEN)

object RuleIncorrectGovTestScenarioError extends MtdError("RULE_INCORRECT_GOV_TEST_SCENARIO", "The Gov-Test-Scenario was not found", BAD_REQUEST)

// Standard Errors
object NotFoundError extends MtdError("MATCHING_RESOURCE_NOT_FOUND", "Matching resource not found", NOT_FOUND)

object DownstreamError extends MtdError("INTERNAL_SERVER_ERROR", "An internal server error occurred", INTERNAL_SERVER_ERROR)

object BadRequestError extends MtdError("INVALID_REQUEST", "Invalid request", BAD_REQUEST)

object ServiceUnavailableError extends MtdError("SERVICE_UNAVAILABLE", "Internal server error", SERVICE_UNAVAILABLE)

object InvalidJson extends MtdError("INVALID_JSON", "Invalid JSON received", BAD_REQUEST)

object UnexpectedFailure:
  def mtdError(status: Int, body: String): MtdError =
    MtdError("UNEXPECTED_FAILURE", s"Unexpected failure. Status $status, body $body", status)

// Authorisation Errors
object UnauthorisedError extends MtdError("CLIENT_OR_AGENT_NOT_AUTHORISED", "The client and/or agent is not authorised", FORBIDDEN)

object InvalidBearerTokenError extends MtdError("UNAUTHORIZED", "Bearer token is missing or not authorized", UNAUTHORIZED)

// Accept header Errors
object InvalidAcceptHeaderError extends MtdError("ACCEPT_HEADER_INVALID", "The accept header is missing or invalid", NOT_ACCEPTABLE)

object UnsupportedVersionError extends MtdError("NOT_FOUND", "The requested resource could not be found", NOT_FOUND)

object InvalidBodyTypeError extends MtdError("INVALID_BODY_TYPE", "Expecting text/json or application/json body", UNSUPPORTED_MEDIA_TYPE)
