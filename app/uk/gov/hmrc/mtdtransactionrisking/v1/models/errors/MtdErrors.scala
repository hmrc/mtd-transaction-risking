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

import play.api.libs.functional.syntax.*
import play.api.libs.json.{JsObject, JsValue, Json, Reads, Writes, OWrites}

case class MtdError(code: String, message: String, httpStatus: Int = 400, customJson: Option[JsValue] = None) {
  lazy val toJson: JsValue = Json.obj(
    "code"    -> this.code,
    "message" -> this.message
  )
}

object MtdError {
  implicit val writes: Writes[MtdError] = {
    case o @ MtdError(_, _, _, None) => o.toJson
    case MtdError("INVALID_REQUEST", _, _, Some(customJson)) =>
      BadRequestError.toJson.as[JsObject] + ("errors" -> Json.toJson(Seq(customJson)))
    case MtdError(_, _, _, Some(customJson)) => customJson
  }

  implicit def genericWrites[T <: MtdError]: Writes[T] =
    writes.contramap[T](c => c: MtdError)

  implicit val reads: Reads[MtdError] = (
    (play.api.libs.json.JsPath \ "code").read[String] and
      (play.api.libs.json.JsPath \ "message").read[String]
    )((code, message) => MtdError(code, message))
}

case class MtdErrorWrapper(code: String, message: String, path: Option[String], errors: Option[Seq[MtdErrorWrapper]] = None)

object MtdErrorWrapper {
  implicit val writes: OWrites[MtdErrorWrapper] = Json.writes[MtdErrorWrapper]

  implicit def genericWrites[T <: MtdErrorWrapper]: OWrites[T] =
    writes.contramap[T](c => c: MtdErrorWrapper)

  implicit val reads: Reads[MtdErrorWrapper] = Json.reads[MtdErrorWrapper]
}

// Format Errors
object VrnFormatError extends MtdError("VRN_INVALID", "The provided VRN is invalid") 
object PeriodKeyFormatError extends MtdError("PERIOD_KEY_INVALID", "The provided period key is invalid")

// Rule Errors
object RuleIncorrectOrEmptyBodyError extends MtdError("RULE_INCORRECT_OR_EMPTY_BODY_SUBMITTED", "An empty or non-matching body was submitted")

object TaxPeriodNotEndedError extends MtdError("TAX_PERIOD_NOT_ENDED", "The remote endpoint has indicated that the submission is for a tax period that has not ended", httpStatus = 403)

object RuleInsolventTraderError extends MtdError("RULE_INSOLVENT_TRADER", "The remote endpoint has indicated that the Trader is insolvent", httpStatus = 403)

object RuleIncorrectGovTestScenarioError extends MtdError("RULE_INCORRECT_GOV_TEST_SCENARIO", "The Gov-Test-Scenario was not found")   // 400 default

// Standard Errors
object NotFoundError extends MtdError("MATCHING_RESOURCE_NOT_FOUND", "Matching resource not found", httpStatus = 404)
object DownstreamError extends MtdError("INTERNAL_SERVER_ERROR", "An internal server error occurred", httpStatus = 500)
object BadRequestError extends MtdError("INVALID_REQUEST", "Invalid request")
object ServiceUnavailableError extends MtdError("SERVICE_UNAVAILABLE", "Internal server error", httpStatus = 503)
object InvalidJson extends MtdError("INVALID_JSON", "Invalid JSON received")
object UnexpectedFailure {
  def mtdError(status: Int, body: String): MtdError =
    MtdError("UNEXPECTED_FAILURE", s"Unexpected failure. Status $status, body $body", httpStatus = status)
}

// Authorisation Errors
object UnauthorisedError extends MtdError("CLIENT_OR_AGENT_NOT_AUTHORISED", "The client and/or agent is not authorised", httpStatus = 403)
object InvalidBearerTokenError extends MtdError("UNAUTHORIZED", "Bearer token is missing or not authorized", httpStatus = 401)

object ForbiddenDownstreamError extends MtdError(
  code       = "INTERNAL_SERVER_ERROR",
  message    = "An internal server error occurred",
  httpStatus = 500,
  customJson = Some(
    Json.parse(
      """
        |{
        |  "code": "INTERNAL_SERVER_ERROR",
        |  "message": "An internal server error occurred"
        |}
      """.stripMargin
    )
  )
)

// Accept header Errors
object InvalidAcceptHeaderError extends MtdError("ACCEPT_HEADER_INVALID", "The accept header is missing or invalid")
object UnsupportedVersionError extends MtdError("NOT_FOUND", "The requested resource could not be found", httpStatus = 404)
object InvalidBodyTypeError extends MtdError("INVALID_BODY_TYPE", "Expecting text/json or application/json body")