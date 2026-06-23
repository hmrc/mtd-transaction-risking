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

import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec

class MtdErrorsSpec extends UnitSpec {

  "MtdError" when {

    "written to JSON with no customJson" should {
      "generate the correct JSON" in {
        Json.toJson(MtdError("SOME_CODE", "Some message")) shouldBe Json.parse(
          """{"code": "SOME_CODE", "message": "Some message"}"""
        )
      }
    }

    "written to JSON with customJson and code INVALID_REQUEST" should {
      "wrap customJson in a single-element array under 'errors'" in {
        val customJson: JsValue = Json.parse(
          """{"code": "FIELD_INVALID", "message": "bad field"}"""
        )
        Json.toJson(MtdError("INVALID_REQUEST", "Invalid request", Some(customJson))) shouldBe Json.parse(
          """
            |{
            |  "code": "INVALID_REQUEST",
            |  "message": "Invalid request",
            |  "errors": [
            |    {
            |      "code": "FIELD_INVALID",
            |      "message": "bad field"
            |    }
            |  ]
            |}
          """.stripMargin
        )
      }
    }

    "written to JSON with customJson and a non-INVALID_REQUEST code" should {
      "return the customJson directly" in {
        val customJson: JsValue = Json.parse(
          """{"code": "CUSTOM", "message": "custom error"}"""
        )
        Json.toJson(MtdError("SOME_OTHER_CODE", "Some message", Some(customJson))) shouldBe customJson
      }
    }

    "read from JSON" should {
      "deserialise a simple error correctly" in {
        Json.parse("""{"code": "SOME_CODE", "message": "Some message"}""")
          .as[MtdError] shouldBe MtdError("SOME_CODE", "Some message", None)
      }
    }
  }

  "Standard error objects" when {
    "written to JSON" should {

      "serialise VrnFormatError" in {
        Json.toJson(VrnFormatError: MtdError) shouldBe Json.parse(
          """{"code": "VRN_INVALID", "message": "The provided VRN is invalid"}"""
        )
      }

      "serialise RuleIncorrectOrEmptyBodyError" in {
        Json.toJson(RuleIncorrectOrEmptyBodyError: MtdError) shouldBe Json.parse(
          """{"code": "RULE_INCORRECT_OR_EMPTY_BODY_SUBMITTED", "message": "An empty or non-matching body was submitted"}"""
        )
      }

      "serialise NotFoundError" in {
        Json.toJson(NotFoundError: MtdError) shouldBe Json.parse(
          """{"code": "MATCHING_RESOURCE_NOT_FOUND", "message": "Matching resource not found"}"""
        )
      }

      "serialise DownstreamError" in {
        Json.toJson(DownstreamError: MtdError) shouldBe Json.parse(
          """{"code": "INTERNAL_SERVER_ERROR", "message": "An internal server error occurred"}"""
        )
      }

      "serialise BadRequestError" in {
        Json.toJson(BadRequestError: MtdError) shouldBe Json.parse(
          """{"code": "INVALID_REQUEST", "message": "Invalid request"}"""
        )
      }

      "serialise ServiceUnavailableError" in {
        Json.toJson(ServiceUnavailableError: MtdError) shouldBe Json.parse(
          """{"code": "SERVICE_UNAVAILABLE", "message": "Internal server error"}"""
        )
      }

      "serialise InvalidJson" in {
        Json.toJson(InvalidJson: MtdError) shouldBe Json.parse(
          """{"code": "INVALID_JSON", "message": "Invalid JSON received"}"""
        )
      }

      "serialise UnauthorisedError" in {
        Json.toJson(UnauthorisedError: MtdError) shouldBe Json.parse(
          """{"code": "CLIENT_OR_AGENT_NOT_AUTHORISED", "message": "The client and/or agent is not authorised"}"""
        )
      }

      "serialise InvalidBearerTokenError" in {
        Json.toJson(InvalidBearerTokenError: MtdError) shouldBe Json.parse(
          """{"code": "UNAUTHORIZED", "message": "Bearer token is missing or not authorized"}"""
        )
      }

      "serialise ForbiddenDownstreamError using its customJson" in {
        Json.toJson(ForbiddenDownstreamError: MtdError) shouldBe Json.parse(
          """{"code": "INTERNAL_SERVER_ERROR", "message": "An internal server error occurred"}"""
        )
      }

      "serialise InvalidAcceptHeaderError" in {
        Json.toJson(InvalidAcceptHeaderError: MtdError) shouldBe Json.parse(
          """{"code": "ACCEPT_HEADER_INVALID", "message": "The accept header is missing or invalid"}"""
        )
      }

      "serialise UnsupportedVersionError" in {
        Json.toJson(UnsupportedVersionError: MtdError) shouldBe Json.parse(
          """{"code": "NOT_FOUND", "message": "The requested resource could not be found"}"""
        )
      }

      "serialise InvalidBodyTypeError" in {
        Json.toJson(InvalidBodyTypeError: MtdError) shouldBe Json.parse(
          """{"code": "INVALID_BODY_TYPE", "message": "Expecting text/json or application/json body"}"""
        )
      }

      "serialise RuleIncorrectGovTestScenarioError" in {
        Json.toJson(RuleIncorrectGovTestScenarioError: MtdError) shouldBe Json.parse(
          """{"code": "RULE_INCORRECT_GOV_TEST_SCENARIO", "message": "The Gov-Test-Scenario was not found"}"""
        )
      }
    }
  }

  "UnexpectedFailure" when {
    "written to JSON" should {
      "include the status and body in the message" in {
        Json.toJson(UnexpectedFailure.mtdError(502, "bad gateway")) shouldBe Json.parse(
          """
            |{
            |  "code": "UNEXPECTED_FAILURE",
            |  "message": "Unexpected failure. Status 502, body bad gateway"
            |}
          """.stripMargin
        )
      }
    }
  }

  "MtdErrorWrapper" when {

    "written to JSON with a path" should {
      "generate the correct JSON" in {
        Json.toJson(MtdErrorWrapper("CODE", "message", Some("/field"))) shouldBe Json.parse(
          """{"code": "CODE", "message": "message", "path": "/field"}"""
        )
      }
    }

    "written to JSON without a path" should {
      "omit the path field" in {
        Json.toJson(MtdErrorWrapper("CODE", "message", None)) shouldBe Json.parse(
          """{"code": "CODE", "message": "message"}"""
        )
      }
    }

    "written to JSON with nested errors" should {
      "generate the correct JSON" in {
        val inner   = MtdErrorWrapper("INNER", "inner message", Some("/inner"))
        val wrapper = MtdErrorWrapper("OUTER", "outer message", None, Some(Seq(inner)))
        Json.toJson(wrapper) shouldBe Json.parse(
          """
            |{
            |  "code": "OUTER",
            |  "message": "outer message",
            |  "errors": [
            |    {
            |      "code": "INNER",
            |      "message": "inner message",
            |      "path": "/inner"
            |    }
            |  ]
            |}
          """.stripMargin
        )
      }
    }

    "read from JSON with a path" should {
      "deserialise correctly" in {
        Json.parse("""{"code": "CODE", "message": "message", "path": "/field"}""")
          .as[MtdErrorWrapper] shouldBe MtdErrorWrapper("CODE", "message", Some("/field"))
      }
    }

    "read from JSON without a path" should {
      "deserialise with path as None" in {
        Json.parse("""{"code": "CODE", "message": "message"}""")
          .as[MtdErrorWrapper] shouldBe MtdErrorWrapper("CODE", "message", None)
      }
    }

    "read from JSON with nested errors" should {
      "deserialise correctly" in {
        Json.parse(
          """
            |{
            |  "code": "OUTER",
            |  "message": "outer message",
            |  "errors": [
            |    {
            |      "code": "INNER",
            |      "message": "inner message",
            |      "path": "/inner"
            |    }
            |  ]
            |}
          """.stripMargin
        ).as[MtdErrorWrapper] shouldBe MtdErrorWrapper(
          "OUTER", "outer message", None,
          Some(Seq(MtdErrorWrapper("INNER", "inner message", Some("/inner"))))
        )
      }
    }
  }
}