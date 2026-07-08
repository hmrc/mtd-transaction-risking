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
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId

class ErrorWrapperSpec extends UnitSpec:

  private val correlationId = CorrelationId("test-correlation-id")

  "ErrorWrapper" when:

    "written to JSON with a structured error and no raw body" should:

      "serialise the error's code and message" in:
        val wrapper = ErrorWrapper(correlationId, NotFoundError)
        Json.toJson(wrapper) shouldBe Json.parse(
          """{"code": "MATCHING_RESOURCE_NOT_FOUND", "message": "Matching resource not found"}"""
        )

      "serialise a structured error that carries a path" in:
        val errorWithPath = MtdError("PERIOD_KEY_INVALID", "period key should be a 4 character string", BAD_REQUEST, Some("/periodKey"))
        val wrapper       = ErrorWrapper(correlationId, errorWithPath)
        Json.toJson(wrapper) shouldBe Json.parse(
          """{"code": "PERIOD_KEY_INVALID", "message": "period key should be a 4 character string", "path": "/periodKey"}"""
        )

    "written to JSON with a raw body present" should:

      "relay the raw body verbatim, ignoring the placeholder error" in:
        val downstreamBody = Json.parse(
          """
            |{
            |  "code": "INVALID_REQUEST",
            |  "message": "Invalid request",
            |  "errors": [
            |    { "code": "PERIOD_KEY_INVALID", "message": "period key should be a 4 character string", "path": "/periodKey" }
            |  ]
            |}
          """.stripMargin
        )
        val wrapper = ErrorWrapper(correlationId, DownstreamError, rawBody = Some(downstreamBody), rawStatus = Some(BAD_REQUEST))
        Json.toJson(wrapper) shouldBe downstreamBody

  "statusCode" should:

    "return the error's httpStatus when no rawStatus is set" in:
      ErrorWrapper(correlationId, TaxPeriodNotEndedError).statusCode shouldBe FORBIDDEN

    "return rawStatus when present, overriding the error's httpStatus" in:
      val wrapper = ErrorWrapper(correlationId, DownstreamError, rawStatus = Some(BAD_REQUEST))
      wrapper.statusCode shouldBe BAD_REQUEST

    "prefer rawStatus even when the placeholder error has a different status" in:
      val wrapper = ErrorWrapper(correlationId, DownstreamError, rawBody = Some(Json.obj()), rawStatus = Some(FORBIDDEN))
      wrapper.statusCode shouldBe FORBIDDEN