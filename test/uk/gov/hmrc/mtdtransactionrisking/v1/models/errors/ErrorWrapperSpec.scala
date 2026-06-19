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

import play.api.libs.json.{JsSuccess, Json}
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec

class ErrorWrapperSpec extends UnitSpec {

  val correlationId = "test-correlation-id"

  "ErrorWrapper" when {

    "written to JSON" when {

      "there is a single error and no additional errors" should {
        "serialise to code and message only" in {
          Json.toJson(ErrorWrapper(correlationId, VrnFormatError)) shouldBe Json.parse(
            """
              |{
              |  "code": "VRN_INVALID",
              |  "message": "The provided VRN is invalid"
              |}
            """.stripMargin
          )
        }
      }

      "there is a single error with errors set to None" should {
        "not include an errors field" in {
          val json = Json.toJson(ErrorWrapper(correlationId, BadRequestError, None))
          (json \ "errors").toOption shouldBe None
        }
      }

      "there is a single error with an empty errors list" should {
        "not include an errors field" in {
          val json = Json.toJson(ErrorWrapper(correlationId, BadRequestError, Some(Nil)))
          (json \ "errors").toOption shouldBe None  
        }
      }

      "there are multiple errors" should {
        "serialise with an errors array" in {
          val wrapper = ErrorWrapper(
            correlationId,
            BadRequestError,
            Some(List(VrnFormatError, InvalidJsonError))
          )
          Json.toJson(wrapper) shouldBe Json.parse(
            """
              |{
              |  "code": "INVALID_REQUEST",
              |  "message": "Invalid request",
              |  "errors": [
              |    {"code": "VRN_INVALID",       "message": "The provided VRN is invalid"},
              |    {"code": "INVALID_REQUEST",    "message": "Invalid JSON received"}
              |  ]
              |}
            """.stripMargin
          )
        }
      }

      "the error is DownstreamError" should {
        "serialise correctly" in {
          Json.toJson(ErrorWrapper(correlationId, DownstreamError)) shouldBe Json.parse(
            """{"code": "INTERNAL_SERVER_ERROR", "message": "An internal server error occurred"}"""
          )
        }
      }

      "the error is UnauthorisedError" should {
        "serialise correctly" in {
          Json.toJson(ErrorWrapper(correlationId, UnauthorisedError)) shouldBe Json.parse(
            """{"code": "CLIENT_OR_AGENT_NOT_AUTHORISED", "message": "The client and/or agent is not authorised"}"""
          )
        }
      }
    }

    "read from JSON" when {

      "the JSON has a single error and no errors array" should {
        "deserialise correctly" in {
          Json.parse(
            s"""
               |{
               |  "correlationId": "$correlationId",
               |  "error": {"code": "VRN_INVALID", "message": "The provided VRN is invalid"}
               |}
            """.stripMargin
          ).validate[ErrorWrapper] shouldBe JsSuccess(
            ErrorWrapper(correlationId, MtdError("VRN_INVALID", "The provided VRN is invalid"))
          )
        }
      }

      "the JSON has multiple errors" should {
        "deserialise the errors list correctly" in {
          Json.parse(
            s"""
               |{
               |  "correlationId": "$correlationId",
               |  "error": {"code": "INVALID_REQUEST", "message": "Invalid request"},
               |  "errors": [
               |    {"code": "VRN_INVALID",    "message": "The provided VRN is invalid"},
               |    {"code": "INVALID_REQUEST", "message": "Invalid JSON received"}
               |  ]
               |}
            """.stripMargin
          ).validate[ErrorWrapper] shouldBe JsSuccess(
            ErrorWrapper(
              correlationId,
              MtdError("INVALID_REQUEST", "Invalid request"),
              Some(List(
                MtdError("VRN_INVALID",    "The provided VRN is invalid"),
                MtdError("INVALID_REQUEST", "Invalid JSON received")
              ))
            )
          )
        }
      }
    }

    "deserialising vat-api error responses" when {

      "vat-api returns a simple error" should {
        "deserialise correctly" in {
          Json.parse(
            s"""
               |{
               |  "correlationId": "$correlationId",
               |  "error": {"code": "PERIOD_KEY_INVALID", "message": "period key should be a 4 character string"}
               |}
            """.stripMargin
          ).validate[ErrorWrapper].isSuccess shouldBe true
        }
      }

      "vat-api returns a envelope" should {
        "be passed through as VatApiError without deserialisation" in {
          val businessError = Json.parse(
            """
              |{
              |  "code": "BUSINESS_ERROR",
              |  "message": "Business validation error",
              |  "errors": [
              |    {"code": "DUPLICATE_SUBMISSION", "message": "Already submitted"}
              |  ]
              |}
            """.stripMargin
          )
          val vatApiError = VatApiError(409, businessError)
          vatApiError.status shouldBe 409
          vatApiError.body   shouldBe businessError
        }
      }
    }
  }
}