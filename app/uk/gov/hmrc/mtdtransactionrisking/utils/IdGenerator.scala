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

package uk.gov.hmrc.mtdtransactionrisking.utils

import play.api.libs.json.{Format, JsResult, JsString, JsValue}

import java.util.UUID

object IdGenerator:

  case class CorrelationId(value: String)
  given Format[CorrelationId] = new Format[CorrelationId]:
    override def writes(id: CorrelationId): JsValue = JsString(id.value)
    override def reads(json: JsValue): JsResult[CorrelationId] =
      json
        .validate[String]
        .map: id =>
          CorrelationId(id)

  def generateId(): CorrelationId = CorrelationId(UUID.randomUUID().toString)
