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
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId

class ErrorWrapperSpec extends UnitSpec {

  private val correlationId = CorrelationId("test-correlation-id")

  "ErrorWrapper" when {

    "written to JSON with a single simple error and no additional errors" should {
      "generate the correct JSON" in {
        val wrapper = ErrorWrapper(correlationId, NotFoundError, None)
        Json.toJson(wrapper) shouldBe Json.parse(
          """{"code": "MATCHING_RESOURCE_NOT_FOUND", "message": "Matching resource not found"}"""
        )
      }
    }

    "written to JSON with a single simple error and an empty errors sequence" should {
      "generate the correct JSON without an errors field" in {
        val wrapper = ErrorWrapper(correlationId, VrnFormatError, Some(Seq.empty))
        Json.toJson(wrapper) shouldBe Json.parse(
          """{"code": "VRN_INVALID", "message": "The provided VRN is invalid"}"""
        )
      }
    }

    "written to JSON with multiple simple errors" should {
      "generate the correct JSON with an errors array" in {
        val wrapper = ErrorWrapper(
          correlationId,
          BadRequestError,
          Some(Seq(VrnFormatError, RuleIncorrectOrEmptyBodyError))
        )
        Json.toJson(wrapper) shouldBe Json.parse(
          """
            |{
            |  "code": "INVALID_REQUEST",
            |  "message": "Invalid request",
            |  "errors": [
            |    { "code": "VRN_INVALID", "message": "The provided VRN is invalid" },
            |    { "code": "RULE_INCORRECT_OR_EMPTY_BODY_SUBMITTED", "message": "An empty or non-matching body was submitted" }
            |  ]
            |}
          """.stripMargin
        )
      }
    }

    "written to JSON with an error whose customJson is a valid MtdErrorWrapper with nested errors" should {
      "unwrap and flatten the nested errors into the errors array" in {
        val innerWrapper = MtdErrorWrapper(
          code    = "NESTED_CODE",
          message = "nested message",
          path    = Some("/field"),
          errors  = Some(Seq(MtdErrorWrapper("DEEP_CODE", "deep message", Some("/deep"))))
        )
        val customJson      = Json.toJson(innerWrapper)
        val errorWithCustom = MtdError("INVALID_REQUEST", "Invalid request", customJson = Some(customJson))

        val wrapper = ErrorWrapper(correlationId, BadRequestError, Some(Seq(errorWithCustom)))
        val result  = Json.toJson(wrapper)

        (result \ "errors").as[Seq[MtdErrorWrapper]] shouldBe Seq(
          MtdErrorWrapper("DEEP_CODE", "deep message", Some("/deep"))
        )
      }
    }

    "written to JSON with an error whose customJson is a valid MtdErrorWrapper with no nested errors" should {
      "include the wrapper itself in the errors array" in {
        val innerWrapper = MtdErrorWrapper(
          code    = "WRAPPER_CODE",
          message = "wrapper message",
          path    = Some("/path"),
          errors  = None
        )
        val customJson      = Json.toJson(innerWrapper)
        val errorWithCustom = MtdError("SOME_CODE", "some message", customJson = Some(customJson))

        val wrapper = ErrorWrapper(correlationId, BadRequestError, Some(Seq(errorWithCustom)))
        val result  = Json.toJson(wrapper)

        (result \ "errors").as[Seq[MtdErrorWrapper]] shouldBe Seq(innerWrapper)
      }
    }

    "written to JSON with an error whose customJson is not a valid MtdErrorWrapper" should {
      "include the raw customJson in the errors array" in {
        val rawCustom       = Json.parse("""{"arbitrary": "json"}""")
        val errorWithCustom = MtdError("SOME_CODE", "some message", customJson = Some(rawCustom))

        val wrapper = ErrorWrapper(correlationId, BadRequestError, Some(Seq(errorWithCustom)))
        val result  = Json.toJson(wrapper)

        (result \ "errors")(0) shouldBe rawCustom
      }
    }
  }
}