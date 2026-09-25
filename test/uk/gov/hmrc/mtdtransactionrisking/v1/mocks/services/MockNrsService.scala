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

package uk.gov.hmrc.mtdtransactionrisking.v1.mocks.services

import org.scalamock.handlers.CallHandler
import org.scalamock.scalatest.MockFactory
import org.scalatest.TestSuite
import play.api.libs.json.JsValue
import uk.gov.hmrc.mtdtransactionrisking.v1.models.auth.IdentityData
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.nrs.NotableEventType
import uk.gov.hmrc.mtdtransactionrisking.v1.services.NrsService

import java.time.Instant

trait MockNrsService extends MockFactory:
  this: TestSuite =>

  val mockNrsService: NrsService = mock[NrsService]

  object MockNrsService:

    def submit(
                evidence: JsValue,
                vrn: String,
                reportId: String,
                submissionTimestamp: Instant,
                identityData: Option[IdentityData],
                userAuthToken: Option[String],
                requestHeaders: Seq[(String, String)],
                notableEventType: NotableEventType
              ): CallHandler[Unit] =
      (mockNrsService.submit(
        _: JsValue,
        _: String,
        _: String,
        _: Instant,
        _: Option[IdentityData],
        _: Option[String],
        _: Seq[(String, String)],
        _: NotableEventType
      )).expects(
        evidence,
        vrn,
        reportId,
        submissionTimestamp,
        identityData,
        userAuthToken,
        requestHeaders,
        notableEventType
      )
