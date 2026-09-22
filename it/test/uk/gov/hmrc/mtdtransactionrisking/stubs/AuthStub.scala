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
    successfulAuthWithIdentityData(vrn, "Individual")

  def successfulAuthWithOrganisation(vrn: String): StubMapping =
    successfulAuthWithIdentityData(vrn, "Organisation")

  def successfulAuthWithAgent(vrn: String, arn: String): StubMapping =
    successfulAuthWithIdentityData(vrn, "Agent", Some(arn))

  def successfulAuthWithNoEnrolments(): StubMapping =
    stubFor(
      post(urlMatching("/auth/authorise"))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(
              """
                |{
                |  "affinityGroup": "Individual",
                |  "internalId": "12345-credId",
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

  private def successfulAuthWithIdentityData(vrn: String, affinityGroup: String, arn: Option[String] = None): StubMapping =
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
                 |  "optionalName": {
                 |    "name": "Test",
                 |    "lastName": "User"
                 |  },
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
                 |  "allEnrolments": [
                 |    {
                 |      "key": "HMRC-MTD-VAT",
                 |      "identifiers": [
                 |        {
                 |          "key": "VRN",
                 |          "value": "$vrn"
                 |        }
                 |      ],
                 |      "state": "Activated"
                 |    }${arn.fold("")(value =>
                s""",
                   |    {
                   |      "key": "HMRC-AS-AGENT",
                   |      "identifiers": [
                   |        {
                   |          "key": "AgentReferenceNumber",
                   |          "value": "$value"
                   |        }
                   |      ],
                   |      "state": "Activated"
                   |    }""")}
                 |  ]
                 |}
                 |""".stripMargin
            )
        )
    )