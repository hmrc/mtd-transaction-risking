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

package uk.gov.hmrc.mtdtransactionrisking.services

import cats.data.EitherT
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mtdtransactionrisking.connectors.AcknowledgeConnector
import uk.gov.hmrc.mtdtransactionrisking.models.request.AcknowledgeRequest
import uk.gov.hmrc.mtdtransactionrisking.models.response.AcknowledgeResponse
import uk.gov.hmrc.mtdtransactionrisking.connectors.AcknowledgeConnector.AcknowledgeFailure

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class AcknowledgeService @Inject()(connector: AcknowledgeConnector):

  def acknowledge(request: AcknowledgeRequest)(implicit hc: HeaderCarrier): EitherT[Future, AcknowledgeFailure, Unit] =
    connector.acknowledge(request)