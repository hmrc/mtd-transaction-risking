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

import play.api.libs.json.Json
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec

class MtdErrorsSpec extends UnitSpec {

  "MtdError" when {

    "written to JSON" should {
      "serialise code and message correctly" in {
        Json.toJson(MtdError("SOME_CODE", "Some message")) shouldBe Json.parse(
          """{"code": "SOME_CODE", "message": "Some message"}"""
        )
      }
    }

    "read from JSON" should {
      "deserialise a simple error correctly" in {
        Json.parse("""{"code": "SOME_CODE", "message": "Some message"}""")
          .as[MtdError] shouldBe MtdError("SOME_CODE", "Some message")
      }
    }
  }

  "Error objects" when {
    "written to JSON" should {

      // --- VRN ---
      "serialise VrnFormatError" in {
        Json.toJson(VrnFormatError: MtdError) shouldBe Json.parse(
          """{"code": "VRN_INVALID", "message": "The provided VRN is invalid"}"""
        )
      }


      "serialise BadRequestError" in {
        Json.toJson(BadRequestError: MtdError) shouldBe Json.parse(
          """{"code": "INVALID_REQUEST", "message": "Invalid request"}"""
        )
      }

      // --- Auth ---
      "serialise UnauthorisedError" in {
        Json.toJson(UnauthorisedError: MtdError) shouldBe Json.parse(
          """{"code": "CLIENT_OR_AGENT_NOT_AUTHORISED", "message": "The client and/or agent is not authorised"}"""
        )
      }

      // --- Downstream ---
      "serialise DownstreamError" in {
        Json.toJson(DownstreamError: MtdError) shouldBe Json.parse(
          """{"code": "INTERNAL_SERVER_ERROR", "message": "An internal server error occurred"}"""
        )
      }
      "serialise ForbiddenDownstreamError using its customJson" in {
        Json.toJson(ForbiddenDownstreamError: MtdError) shouldBe Json.parse(
          """{"code": "INTERNAL_SERVER_ERROR", "message": "An internal server error occurred"}"""
        )
      }

      // --- Routing ---
      "serialise UnsupportedVersionError" in {
        Json.toJson(UnsupportedVersionError: MtdError) shouldBe Json.parse(
          """{"code": "NOT_FOUND", "message": "The requested resource could not be found"}"""
        )
      }

      "serialise InvalidAcceptHeaderError" in {
        Json.toJson(InvalidAcceptHeaderError: MtdError) shouldBe Json.parse(
          """{"code": "ACCEPT_HEADER_INVALID", "message": "The accept header is missing or invalid"}"""
        )
      }

      "serialise RuleIncorrectGovTestScenarioError" in {
        Json.toJson(RuleIncorrectGovTestScenarioError: MtdError) shouldBe Json.parse(
          """{"code": "RULE_INCORRECT_GOV_TEST_SCENARIO", "message": "The Gov-Test-Scenario was not found"}"""
        )
      }

    }
  }
}