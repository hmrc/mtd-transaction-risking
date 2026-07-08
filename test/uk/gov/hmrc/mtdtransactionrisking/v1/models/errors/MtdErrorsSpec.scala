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

package uk.gov.hmrc.mtdtransactionrisking.v1.models.errors

import play.api.http.Status.*
import play.api.libs.json.Json
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec

class MtdErrorsSpec extends UnitSpec:

  "MtdError" when:

    "written to JSON without a path" should:
      "generate code and message only" in:
        Json.toJson(MtdError("SOME_CODE", "Some message", BAD_REQUEST)) shouldBe Json.parse(
          """{"code": "SOME_CODE", "message": "Some message"}"""
        )

    "written to JSON with a path" should:
      "include the path field" in:
        Json.toJson(MtdError("PERIOD_KEY_INVALID", "period key should be a 4 character string", BAD_REQUEST, Some("/periodKey"))) shouldBe Json.parse(
          """{"code": "PERIOD_KEY_INVALID", "message": "period key should be a 4 character string", "path": "/periodKey"}"""
        )

    "written to JSON" should:
      "never serialise httpStatus" in:
        val json = Json.toJson(MtdError("SOME_CODE", "Some message", FORBIDDEN))
        (json \ "httpStatus").toOption shouldBe None

  "Standard error objects" should:

    "generate the correct JSON and status for VrnFormatError" in:
      Json.toJson(VrnFormatError) shouldBe Json.parse(
        """{"code": "VRN_INVALID", "message": "The provided VRN is invalid"}"""
      )
      VrnFormatError.httpStatus shouldBe BAD_REQUEST

    "generate the correct JSON and status for PeriodKeyFormatError" in:
      Json.toJson(PeriodKeyFormatError) shouldBe Json.parse(
        """{"code": "PERIOD_KEY_INVALID", "message": "The provided period key is invalid"}"""
      )
      PeriodKeyFormatError.httpStatus shouldBe BAD_REQUEST

    "generate the correct JSON and status for RuleIncorrectOrEmptyBodyError" in:
      Json.toJson(RuleIncorrectOrEmptyBodyError) shouldBe Json.parse(
        """{"code": "RULE_INCORRECT_OR_EMPTY_BODY_SUBMITTED", "message": "An empty or non-matching body was submitted"}"""
      )
      RuleIncorrectOrEmptyBodyError.httpStatus shouldBe BAD_REQUEST

    "generate the correct JSON and status for TaxPeriodNotEndedError" in:
      Json.toJson(TaxPeriodNotEndedError) shouldBe Json.parse(
        """{"code": "TAX_PERIOD_NOT_ENDED", "message": "The remote endpoint has indicated that the submission is for a tax period that has not ended"}"""
      )
      TaxPeriodNotEndedError.httpStatus shouldBe FORBIDDEN

    "generate the correct JSON and status for RuleInsolventTraderError" in:
      Json.toJson(RuleInsolventTraderError) shouldBe Json.parse(
        """{"code": "RULE_INSOLVENT_TRADER", "message": "The remote endpoint has indicated that the Trader is insolvent"}"""
      )
      RuleInsolventTraderError.httpStatus shouldBe FORBIDDEN

    "generate the correct JSON and status for RuleIncorrectGovTestScenarioError" in:
      Json.toJson(RuleIncorrectGovTestScenarioError) shouldBe Json.parse(
        """{"code": "RULE_INCORRECT_GOV_TEST_SCENARIO", "message": "The Gov-Test-Scenario was not found"}"""
      )
      RuleIncorrectGovTestScenarioError.httpStatus shouldBe BAD_REQUEST

    "generate the correct JSON and status for NotFoundError" in:
      Json.toJson(NotFoundError) shouldBe Json.parse(
        """{"code": "MATCHING_RESOURCE_NOT_FOUND", "message": "Matching resource not found"}"""
      )
      NotFoundError.httpStatus shouldBe NOT_FOUND

    "generate the correct JSON and status for DownstreamError" in:
      Json.toJson(DownstreamError) shouldBe Json.parse(
        """{"code": "INTERNAL_SERVER_ERROR", "message": "An internal server error occurred"}"""
      )
      DownstreamError.httpStatus shouldBe INTERNAL_SERVER_ERROR

    "generate the correct JSON and status for BadRequestError" in:
      Json.toJson(BadRequestError) shouldBe Json.parse(
        """{"code": "INVALID_REQUEST", "message": "Invalid request"}"""
      )
      BadRequestError.httpStatus shouldBe BAD_REQUEST

    "generate the correct JSON and status for ServiceUnavailableError" in:
      Json.toJson(ServiceUnavailableError) shouldBe Json.parse(
        """{"code": "SERVICE_UNAVAILABLE", "message": "Internal server error"}"""
      )
      ServiceUnavailableError.httpStatus shouldBe SERVICE_UNAVAILABLE

    "generate the correct JSON and status for InvalidJson" in:
      Json.toJson(InvalidJson) shouldBe Json.parse(
        """{"code": "INVALID_JSON", "message": "Invalid JSON received"}"""
      )
      InvalidJson.httpStatus shouldBe BAD_REQUEST

    "generate the correct JSON and status for UnauthorisedError" in:
      Json.toJson(UnauthorisedError) shouldBe Json.parse(
        """{"code": "CLIENT_OR_AGENT_NOT_AUTHORISED", "message": "The client and/or agent is not authorised"}"""
      )
      UnauthorisedError.httpStatus shouldBe FORBIDDEN

    "generate the correct JSON and status for InvalidBearerTokenError" in:
      Json.toJson(InvalidBearerTokenError) shouldBe Json.parse(
        """{"code": "UNAUTHORIZED", "message": "Bearer token is missing or not authorized"}"""
      )
      InvalidBearerTokenError.httpStatus shouldBe UNAUTHORIZED

    "generate the correct JSON and status for InvalidAcceptHeaderError" in:
      Json.toJson(InvalidAcceptHeaderError) shouldBe Json.parse(
        """{"code": "ACCEPT_HEADER_INVALID", "message": "The accept header is missing or invalid"}"""
      )
      InvalidAcceptHeaderError.httpStatus shouldBe NOT_ACCEPTABLE

    "generate the correct JSON and status for UnsupportedVersionError" in:
      Json.toJson(UnsupportedVersionError) shouldBe Json.parse(
        """{"code": "NOT_FOUND", "message": "The requested resource could not be found"}"""
      )
      UnsupportedVersionError.httpStatus shouldBe NOT_FOUND

    "generate the correct JSON and status for InvalidBodyTypeError" in:
      Json.toJson(InvalidBodyTypeError) shouldBe Json.parse(
        """{"code": "INVALID_BODY_TYPE", "message": "Expecting text/json or application/json body"}"""
      )
      InvalidBodyTypeError.httpStatus shouldBe UNSUPPORTED_MEDIA_TYPE

  "UnexpectedFailure" should:
    "include the status and body in the message and carry the given status" in:
      val error = UnexpectedFailure.mtdError(BAD_GATEWAY, "bad gateway")
      Json.toJson(error) shouldBe Json.parse(
        """{"code": "UNEXPECTED_FAILURE", "message": "Unexpected failure. Status 502, body bad gateway"}"""
      )
      error.httpStatus shouldBe BAD_GATEWAY