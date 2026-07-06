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
import cats.data.EitherT
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.AcknowledgeConnector
import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.AcknowledgeConnector.AcknowledgeConnectorError
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.AcknowledgeRequest

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

object AcknowledgeService:
  sealed trait AcknowledgeServiceError
  final case class ClientOrAuthError(status: Int, code: String, message: String) extends AcknowledgeServiceError
  case object InternalServiceError extends AcknowledgeServiceError

@Singleton
class AcknowledgeService @Inject()(connector: AcknowledgeConnector)(implicit ec: ExecutionContext):

  private val passThroughStatuses = Set(400, 401, 403, 404)

  def acknowledge(request: AcknowledgeRequest)(implicit hc: HeaderCarrier): EitherT[Future, AcknowledgeService.AcknowledgeServiceError, Unit] =    connector.acknowledge(request).leftMap {
    case AcknowledgeConnectorError(status, code, message) if passThroughStatuses.contains(status) =>
      AcknowledgeService.ClientOrAuthError(status, code, message)
    case _ =>
      AcknowledgeService.InternalServiceError
  }