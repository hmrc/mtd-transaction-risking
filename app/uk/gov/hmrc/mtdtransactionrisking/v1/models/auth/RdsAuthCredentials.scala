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

import play.api.libs.json.{Json, OFormat}

import java.time.{Duration, Instant}

/** Token returned by the login service, used as a bearer token on RDS calls. */
final case class RdsAuthCredentials(access_token: String, token_type: String, expires_in: Int):

  def expiresAt(issuedAt: Instant, margin: Duration): Instant =
    issuedAt.plusSeconds(expires_in).minus(margin)

  def bearerHeader: (String, String) = "Authorization" -> s"Bearer $access_token"

object RdsAuthCredentials:
  given format: OFormat[RdsAuthCredentials] = Json.format[RdsAuthCredentials]
