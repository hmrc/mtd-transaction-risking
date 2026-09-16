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

import play.api.test.FakeRequest
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.{PresentedDateTimeFormatError, RdsCorrelationIdFormatError, ReportIdFormatError}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.AcknowledgeRequest

class AcknowledgeRequestParserSpec extends UnitSpec:

  private val vrn = "123456789"
  private val validReportId = "f2fb30e5-4ab6-4a29-b3c1-c00000000001"
  private val validCorrelationId = "E9F65715BBC9222477B27074804BBDD5C73CDE62F84D8B00CFD05B883534AF3D"
  private val validDateTime = "2026-06-09T10:30:00Z"

  private def requestWith(presentedDateTime: Option[String]) =
    presentedDateTime.fold(FakeRequest())(dt => FakeRequest("POST", s"/acknowledge?presentedDateTime=$dt"))

  "parseRequest" when:

    "every field is valid" should:
      "return the acknowledge request" in:
        given request: play.api.mvc.Request[?] = requestWith(Some(validDateTime))

        AcknowledgeRequestParser.parseRequest(vrn, validReportId, validCorrelationId) shouldBe Right(
          AcknowledgeRequest(vrn, validReportId, validCorrelationId, validDateTime)
        )

    "the report id is not a valid UUID" should:
      "return ReportIdFormatError" in:
        given request: play.api.mvc.Request[?] = requestWith(Some(validDateTime))

        AcknowledgeRequestParser.parseRequest(vrn, "not-a-uuid", validCorrelationId) shouldBe Left(ReportIdFormatError)

    "the correlation id is not 64 hex characters" should:

      "return RdsCorrelationIdFormatError for a UUID-shaped value" in:
        given request: play.api.mvc.Request[?] = requestWith(Some(validDateTime))

        // A UUID is the wrong shape here RDS correlation id is 64 hex characters not UUID
        AcknowledgeRequestParser.parseRequest(vrn, validReportId, "9EEB55EF-4FA9-A249-54BC-982DF1D59B3D") shouldBe
          Left(RdsCorrelationIdFormatError)

      "return RdsCorrelationIdFormatError for a value that is too short" in:
        given request: play.api.mvc.Request[?] = requestWith(Some(validDateTime))

        AcknowledgeRequestParser.parseRequest(vrn, validReportId, "EXTRASHORTY") shouldBe Left(RdsCorrelationIdFormatError)

      "return RdsCorrelationIdFormatError for a value with non-hex characters" in:
        given request: play.api.mvc.Request[?] = requestWith(Some(validDateTime))

        val notHex = "Z" * 64

        AcknowledgeRequestParser.parseRequest(vrn, validReportId, notHex) shouldBe Left(RdsCorrelationIdFormatError)

    "presentedDateTime is missing" should:
      "return PresentedDateTimeFormatError" in:
        given request: play.api.mvc.Request[?] = requestWith(None)

        AcknowledgeRequestParser.parseRequest(vrn, validReportId, validCorrelationId) shouldBe Left(PresentedDateTimeFormatError)

    "presentedDateTime is not a valid date-time" should:
      "return PresentedDateTimeFormatError" in:
        given request: play.api.mvc.Request[?] = requestWith(Some("09/06/2026"))

        AcknowledgeRequestParser.parseRequest(vrn, validReportId, validCorrelationId) shouldBe Left(PresentedDateTimeFormatError)

    "more than one field is invalid" should:
      "return only the first error, since validation short-circuits" in:
        given request: play.api.mvc.Request[?] = requestWith(None)

        AcknowledgeRequestParser.parseRequest(vrn, "definitely-not-a-uuid", "ClimbingShortUuid") shouldBe Left(ReportIdFormatError)
