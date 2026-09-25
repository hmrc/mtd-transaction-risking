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

package uk.gov.hmrc.mtdtransactionrisking.v1.services

import com.typesafe.config.ConfigFactory
import play.api.Configuration
import play.api.libs.json.Json
import uk.gov.hmrc.mtdtransactionrisking.support.{MockAppConfig, UnitSpec}
import uk.gov.hmrc.mtdtransactionrisking.v1.mocks.connectors.MockNrsConnector
import uk.gov.hmrc.mtdtransactionrisking.v1.models.auth.IdentityData
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.nrs.AssistRequestFeedback

import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class NrsServiceSpec extends UnitSpec, MockAppConfig, MockNrsConnector:

  private val evidence = Json.parse(
    """
      |{
      |  "periodKey": "AB12",
      |  "vatDueSales": 100.00
      |}
      |""".stripMargin
  )

  private val identityData = Json
    .parse(
      """
        |{
        |  "internalId": "internal-id",
        |  "confidenceLevel": 200,
        |  "agentInformation": {},
        |  "credentialRole": null,
        |  "itmpName": {},
        |  "itmpAddress": {},
        |  "affinityGroup": "Organisation",
        |  "loginTimes": {
        |    "currentLogin": "2026-09-20T10:00:00Z"
        |  }
        |}
        |""".stripMargin
    )
    .as[IdentityData]

  private val timestamp = Instant.parse("2026-09-24T10:15:30Z")

  private val requestHeaders = Seq(
    "Authorization" -> "Bearer vendor-token",
    "Accept" -> "application/vnd.hmrc.1.0+json",
    "Gov-Client-Timezone" -> "UTC+00:00"
  )

  private def featureSwitch(enabled: Boolean): Option[Configuration] =
    Some(Configuration(ConfigFactory.parseString(s"nrs-submission.enabled = $enabled")))

  private trait Test:
    val service = new NrsService(mockAppConfig, mockNrsConnector)

  "NrsService.buildNrsSubmission" should:

    "build Request Feedback evidence from the supplied validated VAT return JSON" in new Test:
      val submission = service
        .buildNrsSubmission(
          evidence = evidence,
          vrn = "123456789",
          reportId = "a1e8057e-fbbc-47a8-a8b4-78d9f015c253",
          submissionTimestamp = timestamp,
          identityData = Some(identityData),
          userAuthToken = Some("Bearer vendor-token"),
          requestHeaders = requestHeaders,
          notableEventType = AssistRequestFeedback
        )
        .value

      val evidenceBytes = Json.stringify(evidence).getBytes(UTF_8)

      submission.payload shouldBe Base64.getEncoder.encodeToString(evidenceBytes)
      submission.metadata.payloadSha256Checksum shouldBe
        MessageDigest
          .getInstance("SHA-256")
          .digest(evidenceBytes)
          .map("%02x".format(_))
          .mkString

      submission.metadata.businessId shouldBe "vaa"
      submission.metadata.notableEvent shouldBe "vaa-request-feedback"
      submission.metadata.searchKeys.vrn shouldBe "123456789"
      submission.metadata.searchKeys.reportId shouldBe "a1e8057e-fbbc-47a8-a8b4-78d9f015c253"
      submission.metadata.userSubmissionTimestamp shouldBe timestamp.toString
      submission.metadata.userAuthToken shouldBe "Bearer vendor-token"

    "exclude Authorization from headerData case-insensitively" in new Test:
      val submission = service
        .buildNrsSubmission(
          evidence = evidence,
          vrn = "123456789",
          reportId = "report-id",
          submissionTimestamp = timestamp,
          identityData = Some(identityData),
          userAuthToken = Some("Bearer vendor-token"),
          requestHeaders = Seq(
            "AUTHORIZATION" -> "Bearer vendor-token",
            "Accept" -> "application/vnd.hmrc.1.0+json"
          ),
          notableEventType = AssistRequestFeedback
        )
        .value

      submission.metadata.headerData shouldBe Json.obj(
        "Accept" -> "application/vnd.hmrc.1.0+json"
      )

    "not build a submission when identity data is unavailable" in new Test:
      service.buildNrsSubmission(
        evidence = evidence,
        vrn = "123456789",
        reportId = "report-id",
        submissionTimestamp = timestamp,
        identityData = None,
        userAuthToken = Some("Bearer vendor-token"),
        requestHeaders = requestHeaders,
        notableEventType = AssistRequestFeedback
      ) shouldBe Left("identity data is unavailable")

    "not build a submission when the original Authorization token is unavailable" in new Test:
      service.buildNrsSubmission(
        evidence = evidence,
        vrn = "123456789",
        reportId = "report-id",
        submissionTimestamp = timestamp,
        identityData = Some(identityData),
        userAuthToken = None,
        requestHeaders = requestHeaders,
        notableEventType = AssistRequestFeedback
      ) shouldBe Left("original Authorization header is unavailable")

  "NrsService.submit" should:

    "not call NRS when the feature switch is disabled" in new Test:
      MockedAppConfig.featureSwitch
        .returns(featureSwitch(enabled = false))
        .anyNumberOfTimes()

      service.submit(
        evidence = evidence,
        vrn = "123456789",
        reportId = "report-id",
        submissionTimestamp = timestamp,
        identityData = Some(identityData),
        userAuthToken = Some("Bearer vendor-token"),
        requestHeaders = requestHeaders,
        notableEventType = AssistRequestFeedback
      )

    "call NRS when the feature switch is enabled and a submission can be built" in new Test:
      MockedAppConfig.featureSwitch
        .returns(featureSwitch(enabled = true))
        .anyNumberOfTimes()

      val expectedSubmission = service
        .buildNrsSubmission(
          evidence = evidence,
          vrn = "123456789",
          reportId = "report-id",
          submissionTimestamp = timestamp,
          identityData = Some(identityData),
          userAuthToken = Some("Bearer vendor-token"),
          requestHeaders = requestHeaders,
          notableEventType = AssistRequestFeedback
        )
        .value

      MockNrsConnector.submit(expectedSubmission).returns(Future.unit)

      service.submit(
        evidence = evidence,
        vrn = "123456789",
        reportId = "report-id",
        submissionTimestamp = timestamp,
        identityData = Some(identityData),
        userAuthToken = Some("Bearer vendor-token"),
        requestHeaders = requestHeaders,
        notableEventType = AssistRequestFeedback
      )
