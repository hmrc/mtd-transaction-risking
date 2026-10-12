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

package uk.gov.hmrc.mtdtransactionrisking.v1.models.request.nrs

import play.api.libs.functional.syntax.*
import play.api.libs.json.{Format, Json, OFormat, __}
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.crypto.json.JsonEncryption
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId

final case class NrsSubmissionWorkItem private(
                                                private val encryptedNrsSubmission: SensitiveString,
                                                correlationId: CorrelationId
                                              ):

  lazy val nrsSubmission: NrsSubmission =
    Json
      .parse(encryptedNrsSubmission.decryptedValue)
      .as[NrsSubmission]

object NrsSubmissionWorkItem:

  private val EncryptedNrsSubmission =
    "encryptedNrsSubmission"

  def apply(
             nrsSubmission: NrsSubmission,
             correlationId: CorrelationId
           ): NrsSubmissionWorkItem =
    new NrsSubmissionWorkItem(
      encryptedNrsSubmission = SensitiveString(
        Json.stringify(
          Json.toJson(nrsSubmission)
        )
      ),
      correlationId = correlationId
    )

  private def fromEncrypted(
                             encryptedNrsSubmission: SensitiveString,
                             correlationId: CorrelationId
                           ): NrsSubmissionWorkItem =
    new NrsSubmissionWorkItem(
      encryptedNrsSubmission = encryptedNrsSubmission,
      correlationId = correlationId
    )

  given format(
                using crypto: Encrypter & Decrypter
              ): OFormat[NrsSubmissionWorkItem] =

    given Format[SensitiveString] =
      JsonEncryption.sensitiveEncrypterDecrypter(
        SensitiveString.apply
      )

    (
      (__ \ EncryptedNrsSubmission).format[SensitiveString] and
        (__ \ "correlationId").format[CorrelationId]
      )(
      fromEncrypted,
      workItem => (
        workItem.encryptedNrsSubmission,
        workItem.correlationId
      )
    )