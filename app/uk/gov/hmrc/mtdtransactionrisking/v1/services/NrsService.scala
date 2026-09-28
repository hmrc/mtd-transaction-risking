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

import play.api.libs.json.{JsObject, JsValue, Json}
import uk.gov.hmrc.mtdtransactionrisking.config.{AppConfig, FeatureSwitch, NrsSubmissionFeature}
import uk.gov.hmrc.mtdtransactionrisking.utils.Logging
import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.NrsConnector
import uk.gov.hmrc.mtdtransactionrisking.v1.models.auth.IdentityData
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.nrs.{Metadata, NotableEventType, NrsSubmission, SearchKeys}

import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class NrsService @Inject() (
                             appConfig: AppConfig,
                             connector: NrsConnector
                           )(implicit ec: ExecutionContext)
  extends Logging:

  def submit(
              evidence: JsValue,
              vrn: String,
              reportId: String,
              submissionTimestamp: Instant,
              identityData: Option[IdentityData],
              userAuthToken: Option[String],
              requestHeaders: Seq[(String, String)],
              notableEventType: NotableEventType
            ): Unit =
    if !FeatureSwitch(appConfig.featureSwitch).isEnabled(NrsSubmissionFeature) then
      logger.debug(s"[NrsService][submit] NRS submission is disabled for ${notableEventType.value}")
    else
      buildNrsSubmission(
        evidence = evidence,
        vrn = vrn,
        reportId = reportId,
        submissionTimestamp = submissionTimestamp,
        identityData = identityData,
        userAuthToken = userAuthToken,
        requestHeaders = requestHeaders,
        notableEventType = notableEventType
      ) match
        case Left(reason) =>
          logger.warn(s"[NrsService][submit] NRS submission skipped: $reason")

        case Right(submission) =>
          connector.submit(submission).recover:
            case ex =>
              logger.warn("[NrsService][submit] Unexpected NRS submission failure", ex)
              ()

  def buildNrsSubmission(
                          evidence: JsValue,
                          vrn: String,
                          reportId: String,
                          submissionTimestamp: Instant,
                          identityData: Option[IdentityData],
                          userAuthToken: Option[String],
                          requestHeaders: Seq[(String, String)],
                          notableEventType: NotableEventType
                        ): Either[String, NrsSubmission] =
    for
      authenticatedIdentityData <- identityData.toRight("identity data is unavailable")
      token <- userAuthToken.toRight("original Authorization header is unavailable")
      headers = headerData(requestHeaders)
    yield
      /*
       * Request Feedback receives the original parsed VAT return JSON submitted
       * to POST /feedback/:vrn and that was passed to VAT API validation. It is never rebuilt from RDS.
       *
       * These UTF-8 bytes are created once and used for both:
       *   - payloadSha256Checksum
       *   - Base64 payload
       */
      val evidenceBytes = Json.stringify(evidence).getBytes(UTF_8)

      NrsSubmission(
        payload = Base64.getEncoder.encodeToString(evidenceBytes),
        metadata = Metadata(
          businessId = "vaa",
          notableEvent = notableEventType.value,
          payloadContentType = "application/json",
          payloadSha256Checksum = sha256(evidenceBytes),
          userSubmissionTimestamp = submissionTimestamp.toString,
          identityData = Json.toJson(authenticatedIdentityData),
          userAuthToken = token,
          headerData = headers,
          searchKeys = SearchKeys(
            vrn = vrn,
            reportId = reportId
          )
        )
      )

  private def headerData(headers: Seq[(String, String)]): JsObject =
    Json.obj(
      headers.collect {
        case (name, value) if !name.equalsIgnoreCase("Authorization") =>
          name -> value
      }*
    )

  private def sha256(bytes: Array[Byte]): String =
    MessageDigest
      .getInstance("SHA-256")
      .digest(bytes)
      .map("%02x".format(_))
      .mkString
