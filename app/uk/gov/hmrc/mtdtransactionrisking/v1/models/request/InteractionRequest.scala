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

import play.api.libs.json.{JsObject, JsValue, Json, OWrites}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.{FeedbackMessage, FeedbackResponse, Obligation}

import java.time.Instant

final case class Interaction(
                              serviceRegime: String,
                              eventName: String,
                              feedbackId: String,
                              eventTimestamp: String,
                              metadata: Seq[InteractionMetadata],
                              payload: InteractionPayload
                            )

object Interaction:

  private val serviceRegime = "vat-assist"
  private val eventName     = "generate-report"

  given writes: OWrites[Interaction] = Json.writes[Interaction]

  def from(feedback: FeedbackResponse, obligation: Obligation, vrn: String, vendorBody: JsValue, occurredAt: Instant): Interaction =
    Interaction(
      serviceRegime  = serviceRegime,
      eventName      = eventName,
      feedbackId     = feedback.reportId,
      eventTimestamp = occurredAt.toString,
      metadata       = Seq(InteractionMetadata.from(vrn, obligation, vendorBody)),
      payload        = InteractionPayload.from(feedback)
    )

final case class InteractionMetadata(vrn: String, start: String, end: String, additionalProperties: JsValue)

object InteractionMetadata:

  private val vatReturnFields = Seq(
    "periodKey",
    "vatDueSales",
    "vatDueAcquisitions",
    "totalVatDue",
    "vatReclaimedCurrPeriod",
    "netVatDue",
    "totalValueSalesExVAT",
    "totalValuePurchasesExVAT",
    "totalValueGoodsSuppliedExVAT",
    "totalAcquisitionsExVAT"
  )

  given writes: OWrites[InteractionMetadata] = Json.writes[InteractionMetadata]

  /** The vendor's return as submitted, keeping only the fields the datastore holds. */
  def from(vrn: String, obligation: Obligation, vendorBody: JsValue): InteractionMetadata =
    InteractionMetadata(
      vrn                  = vrn,
      start                = obligation.start,
      end                  = obligation.end,
      additionalProperties = JsObject(vatReturnFields.flatMap(field => (vendorBody \ field).asOpt[JsValue].map(field -> _)))
    )

final case class InteractionPayload(reportId: String, messages: Seq[InteractionMessage])

object InteractionPayload:

  given writes: OWrites[InteractionPayload] = Json.writes[InteractionPayload]
  
  def from(feedback: FeedbackResponse): InteractionPayload =
    val welshByItemNumber = feedback.welshFeedback.map(message => message.itemNumber -> message).toMap

    InteractionPayload(
      reportId = feedback.reportId,
      messages = feedback.englishFeedback.flatMap { english =>
        welshByItemNumber.get(english.itemNumber).flatMap(welsh => InteractionMessage.from(english, welsh))
      }
    )

final case class InteractionMessage(englishActions: InteractionAction, welshActions: InteractionAction)

object InteractionMessage:

  given writes: OWrites[InteractionMessage] = Json.writes[InteractionMessage]

  def from(english: FeedbackMessage, welsh: FeedbackMessage): Option[InteractionMessage] =
    english.itemNumber.toIntOption.map { itemNumber =>
      InteractionMessage(
        englishActions = InteractionAction.from(itemNumber, english),
        welshActions   = InteractionAction.from(itemNumber, welsh)
      )
    }

final case class InteractionAction(
                                    itemNumber: Int,
                                    title: String,
                                    body: String,
                                    action: String,
                                    links: Seq[InteractionLink],
                                    path: String
                                  )

object InteractionAction:

  given writes: OWrites[InteractionAction] = Json.writes[InteractionAction]

  def from(itemNumber: Int, message: FeedbackMessage): InteractionAction =
    InteractionAction(
      itemNumber = itemNumber,
      title      = message.title,
      body       = message.body,
      action     = message.action.getOrElse(""),
      links      = message.links.getOrElse(Nil).map(link => InteractionLink(link.title, link.url)),
      path       = message.path
    )
    
final case class InteractionLink(linkTitle: String, linkUrl: String)

object InteractionLink:
  given writes: OWrites[InteractionLink] = Json.writes[InteractionLink]