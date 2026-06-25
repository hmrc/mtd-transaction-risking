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

package uk.gov.hmrc.mtdtransactionrisking.v1.models.response

import play.api.libs.json.{Json, OFormat}

case class FeedbackResponse(
                             reportId: String,
                             englishFeedback: List[FeedbackMessage],
                             welshFeedback: List[FeedbackMessage],
                             correlationId: String
                           )

object FeedbackResponse:
  given format: OFormat[FeedbackResponse] = Json.format[FeedbackResponse]

case class FeedbackMessage(
                            itemNumber: String,
                            title: String,
                            body: String,
                            action: Option[String],
                            links: Option[List[FeedbackLink]],
                            path: String
                          )

object FeedbackMessage:
  given format: OFormat[FeedbackMessage] = Json.format[FeedbackMessage]

case class FeedbackLink(
                         title: String,
                         url: String
                       )

object FeedbackLink:
  given format: OFormat[FeedbackLink] = Json.format[FeedbackLink]

