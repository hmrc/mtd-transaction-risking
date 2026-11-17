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

package uk.gov.hmrc.mtdtransactionrisking.stubs

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.http.HeaderNames
import play.api.http.Status.*

object AuthStub:

  val headers: (String, String) =
    HeaderNames.COOKIE -> SessionCookieBaker.bakeSessionCookie(Map("authToken" -> "mock-bearer-token"), Some("en"))

  def successfulAuthWith(vrn: String): StubMapping =
    successfulAuthWithIdentityData("Individual", vatEnrolment(vrn))

  def successfulAuthWithOrganisation(vrn: String): StubMapping =
    successfulAuthWithIdentityData("Organisation", vatEnrolment(vrn))

  def successfulAuthWithAgent(arn: String): StubMapping =
    successfulAuthWithIdentityData("Agent", agentEnrolment(arn))

  private def vatEnrolment(vrn: String): String = enrolment("HMRC-MTD-VAT", "VRN", vrn)

  private def agentEnrolment(arn: String): String = enrolment("HMRC-AS-AGENT", "AgentReferenceNumber", arn)

  private def enrolment(key: String, identifierKey: String, value: String): String =
    s"""{"key": "$key", "identifiers": [{"key": "$identifierKey", "value": "$value"}], "state": "Activated"}"""

  private def successfulAuthWithIdentityData(affinityGroup: String, enrolments: String*): StubMapping =
    stubFor(
      post(urlMatching("/auth/authorise"))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(
              s"""
                 |{
                 |  "affinityGroup": "$affinityGroup",
                 |  "internalId": "12345-credId",
                 |  "externalId": "external-id",
                 |  "agentCode": "agent-code",
                 |  "optionalCredentials": {
                 |    "providerId": "12345-credId",
                 |    "providerType": "GovernmentGateway"
                 |  },
                 |  "confidenceLevel": 200,
                 |  "nino": "AA000003D",
                 |  "saUtr": "1234567890",
                 |  "dateOfBirth": "1990-01-01",
                 |  "email": "test.user@example.com",
                 |  "agentInformation": {
                 |    "agentCode": "agent-code",
                 |    "agentFriendlyName": "Test Agent",
                 |    "agentId": "agent-id"
                 |  },
                 |  "groupIdentifier": "group-identifier",
                 |  "credentialRole": "User",
                 |  "mdtpInformation": {
                 |    "deviceId": "device-id",
                 |    "sessionId": "session-id"
                 |  },
                 |  "credentialStrength": "strong",
                 |  "loginTimes": {
                 |    "currentLogin": "2026-09-20T10:00:00.000Z",
                 |    "previousLogin": "2026-09-19T10:00:00.000Z"
                 |  },
                 |  "optionalItmpName": {
                 |    "givenName": "Test",
                 |    "middleName": "Middle",
                 |    "familyName": "User"
                 |  },
                 |  "itmpDateOfBirth": "1990-01-01",
                 |  "optionalItmpAddress": {
                 |    "line1": "1 Test Street",
                 |    "postCode": "TE1 1ST",
                 |    "countryName": "United Kingdom",
                 |    "countryCode": "GB"
                 |  },
                 |  "allEnrolments": [${enrolments.mkString(", ")}]
                 |}
                 |""".stripMargin
            )
        )
    )

  def insufficientEnrolments(): StubMapping =
    stubFor(
      post(urlMatching("/auth/authorise"))
        .willReturn(
          aResponse()
            .withStatus(UNAUTHORIZED)
            .withHeader(HeaderNames.WWW_AUTHENTICATE, "MDTP detail=\"InsufficientEnrolments\"")
        )
    )

  def successfulAuthWithNoUserId(): StubMapping =
    stubFor(
      post(urlMatching("/auth/authorise"))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(
              """
                |{
                |  "affinityGroup": "Individual",
                |  "optionalCredentials": {
                |    "providerId": "12345-credId",
                |    "providerType": "GovernmentGateway"
                |  },
                |  "confidenceLevel": 200,
                |  "agentInformation": {},
                |  "loginTimes": {
                |    "currentLogin": "2026-09-20T10:00:00.000Z"
                |  },
                |  "allEnrolments": []
                |}
                |""".stripMargin
            )
        )
    )
