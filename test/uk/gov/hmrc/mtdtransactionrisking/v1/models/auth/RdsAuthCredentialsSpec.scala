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

import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec

import java.time.{Duration, Instant}

class RdsAuthCredentialsSpec extends UnitSpec:

  private val credentials = RdsAuthCredentials(
    access_token = "a-bearer-token",
    token_type   = "bearer",
    expires_in   = 14399
  )

  private val credentialsJson: JsValue = Json.parse(
    """
      |{
      |  "access_token": "a-bearer-token",
      |  "token_type": "bearer",
      |  "expires_in": 14399
      |}
      |""".stripMargin
  )

  "RdsAuthCredentials" when:

    "read from JSON" should:
      "deserialise the SAS token response" in:
        credentialsJson.as[RdsAuthCredentials] shouldBe credentials

      "ignore fields the model does not use" in:
        val withExtraFields = Json.parse(
          """
            |{
            |  "access_token": "a-bearer-token",
            |  "token_type": "bearer",
            |  "expires_in": 14399,
            |  "scope": "txr_api",
            |  "jti": "30b2023212d547b185a012d2d29cb78b"
            |}
            |""".stripMargin
        )

        withExtraFields.as[RdsAuthCredentials] shouldBe credentials

    "written to JSON" should:
      "produce the SAS field names" in:
        Json.toJson(credentials) shouldBe credentialsJson

  "expiresAt" should:

    "subtract the refresh margin from the token's lifetime" in:
      val issuedAt = Instant.parse("2026-08-13T09:00:00Z")

      credentials.expiresAt(issuedAt, Duration.ofMinutes(5)) shouldBe Instant.parse("2026-08-13T12:54:59Z")

    "return the full lifetime when no margin is given" in:
      val issuedAt = Instant.parse("2026-08-13T09:00:00Z")

      credentials.expiresAt(issuedAt, Duration.ZERO) shouldBe Instant.parse("2026-08-13T12:59:59Z")

  "bearerHeader" should:
    "produce an Authorization header carrying the access token" in:
      credentials.bearerHeader shouldBe ("Authorization" -> "Bearer a-bearer-token")
