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

package uk.gov.hmrc.mtdtransactionrisking.v1.services

import play.api.Logging
import play.api.libs.json.JsValue
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.InteractionConnector
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.{AcknowledgeRequest, Interaction}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.{FeedbackResponse, Obligation}

import java.time.{Clock, Instant}
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class InteractionService @Inject() (connector: InteractionConnector, clock: Clock)(implicit ec: ExecutionContext) extends Logging:

  def store(feedback: FeedbackResponse, obligation: Obligation, vrn: String, vendorBody: JsValue)(implicit
      hc: HeaderCarrier,
      correlationId: CorrelationId): Unit =
    send(Interaction.forGeneratedReport(feedback, obligation, vrn, vendorBody, Instant.now(clock)))

  def storeAcknowledgement(request: AcknowledgeRequest)(implicit hc: HeaderCarrier, correlationId: CorrelationId): Unit =
    send(Interaction.forAcknowledgement(request, Instant.now(clock)))  

  private def send(interaction: Interaction)(implicit hc: HeaderCarrier, correlationId: CorrelationId): Unit =
    connector
      .store(interaction)
      .foreach {
        case Right(_) => ()
        case Left(errorWrapper) =>
          logger.warn(
            s"${correlationId.value}::[InteractionService] failed to store ${interaction.eventName} interaction " +
              s"for feedbackId:${interaction.feedbackId} error:${errorWrapper.error.code}")
      }
