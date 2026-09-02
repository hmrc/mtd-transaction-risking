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

package uk.gov.hmrc.mtdtransactionrisking.v1.models.request
import play.api.libs.json.{Json, OWrites}

case class AcknowledgeRequest(vrn: String, reportId: String, correlationId: String, presentedDateTime: String)

object AcknowledgeRequest:

  given writes: OWrites[AcknowledgeRequest] =
    OWrites { request =>
      Json.obj(
        "inputs" -> Json.arr(
          Json.obj(
            "name" -> "correlationID",
            "value" -> request.correlationId
          ),
          Json.obj(
            "name" -> "feedbackId",
            "value" -> request.reportId
          ),
          Json.obj(
            "name" -> "vrn",
            "value" -> request.vrn
          ),
          Json.obj(
            "name" -> "presentedDateTime",
            "value" -> request.presentedDateTime
          )
        )
      )
    }
