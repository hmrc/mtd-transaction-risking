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

package uk.gov.hmrc.mtdtransactionrisking.v1.models.auth

import play.api.libs.json.Json
import uk.gov.hmrc.auth.core.retrieve.*
import uk.gov.hmrc.auth.core.User
import uk.gov.hmrc.auth.core.{AffinityGroup, ConfidenceLevel, CredentialRole}
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec

import java.time.{Instant, LocalDate}

class IdentityDataSpec extends UnitSpec:

  private val identityData = IdentityData(
    internalId = Some("internal-id"),
    externalId = Some("external-id"),
    agentCode = Some("agent-code"),
    credentials = Some(Credentials("provider-id", "GovernmentGateway")),
    confidenceLevel = ConfidenceLevel.L200,
    nino = Some("AA000003D"),
    saUtr = Some("1234567890"),
    name = Some(Name(Some("Test"), Some("User"))),
    dateOfBirth = Some(LocalDate.parse("1990-01-01")),
    email = Some("test.user@example.com"),
    agentInformation = AgentInformation(
      agentCode = Some("agent-code"),
      agentFriendlyName = Some("Test Agent"),
      agentId = Some("agent-id")
    ),
    groupIdentifier = Some("group-identifier"),
    credentialRole = Some(User),
    mdtpInformation = Some(MdtpInformation("device-id", "session-id")),
    itmpName = ItmpName(
      givenName = Some("Test"),
      middleName = Some("Middle"),
      familyName = Some("User")
    ),
    itmpDateOfBirth = Some(LocalDate.parse("1990-01-01")),
    itmpAddress = ItmpAddress(
      line1 = Some("1 Test Street"),
      line2 = None,
      line3 = None,
      line4 = None,
      line5 = None,
      postCode = Some("TE1 1ST"),
      countryName = Some("United Kingdom"),
      countryCode = Some("GB")
    ),
    affinityGroup = Some(AffinityGroup.Individual),
    credentialStrength = Some("strong"),
    loginTimes = LoginTimes(
      currentLogin = Instant.parse("2026-09-20T10:00:00Z"),
      previousLogin = Some(Instant.parse("2026-09-19T10:00:00Z"))
    )
  )

  "IdentityData" when:

    "serialised to JSON" should:

      "write all VAT API-equivalent identity fields" in:
        Json.toJson(identityData) shouldBe Json.parse(
          """
            |{
            |  "internalId": "internal-id",
            |  "externalId": "external-id",
            |  "agentCode": "agent-code",
            |  "credentials": {
            |    "providerId": "provider-id",
            |    "providerType": "GovernmentGateway"
            |  },
            |  "confidenceLevel": 200,
            |  "nino": "AA000003D",
            |  "saUtr": "1234567890",
            |  "name": {
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
            |  "itmpName": {
            |    "givenName": "Test",
            |    "middleName": "Middle",
            |    "familyName": "User"
            |  },
            |  "itmpDateOfBirth": "1990-01-01",
            |  "itmpAddress": {
            |    "line1": "1 Test Street",
            |    "postCode": "TE1 1ST",
            |    "countryName": "United Kingdom",
            |    "countryCode": "GB"
            |  },
            |  "affinityGroup": "Individual",
            |  "credentialStrength": "strong",
            |  "loginTimes": {
            |    "currentLogin": "2026-09-20T10:00:00Z",
            |    "previousLogin": "2026-09-19T10:00:00Z"
            |  }
            |}
            |""".stripMargin
        )

    "used for an organisation or agent" should:

      "preserve the supplied affinity group and agent values" in:
        val agentIdentity = identityData.copy(
          affinityGroup = Some(AffinityGroup.Agent),
          agentCode = Some("agent-code"),
          agentInformation = AgentInformation(
            agentCode = Some("agent-code"),
            agentFriendlyName = Some("Test Agent"),
            agentId = Some("agent-id")
          )
        )

        val json = Json.toJson(agentIdentity)

        (json \ "affinityGroup").as[String] shouldBe "Agent"
        (json \ "agentCode").as[String] shouldBe "agent-code"
        (json \ "agentInformation" \ "agentId").as[String] shouldBe "agent-id"

    "optional fields are absent" should:

      "not write fields that Auth did not provide" in:
        val minimalIdentity = identityData.copy(
          externalId = None,
          agentCode = None,
          credentials = None,
          nino = None,
          saUtr = None,
          name = None,
          dateOfBirth = None,
          email = None,
          groupIdentifier = None,
          credentialRole = None,
          mdtpInformation = None,
          itmpDateOfBirth = None,
          affinityGroup = None,
          credentialStrength = None,
          loginTimes = identityData.loginTimes.copy(previousLogin = None)
        )

        val json = Json.toJson(minimalIdentity)

        (json \ "externalId").toOption shouldBe None
        (json \ "nino").toOption shouldBe None
        (json \ "name").toOption shouldBe None
        (json \ "itmpDateOfBirth").toOption shouldBe None
        (json \ "affinityGroup").toOption shouldBe None
        (json \ "loginTimes" \ "previousLogin").toOption shouldBe None
