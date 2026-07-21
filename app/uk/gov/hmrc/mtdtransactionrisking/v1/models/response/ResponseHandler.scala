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

import play.api.libs.json.{Json, Writes}
import play.api.mvc.*
import uk.gov.hmrc.mtdtransactionrisking.v1.services.ServiceOutcome
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

trait ResponseHandler:
  self: BackendController =>

  def handleOutcome[A](outcome: ServiceOutcome[A])(implicit writes: Writes[A]): Result =
    outcome match
      case Right(wrapper) =>
        Status(OK)(Json.toJson(wrapper.responseData))
          .withHeaders("X-CorrelationId" -> wrapper.correlationId.value)
      case Left(errorWrapper) =>
        Status(errorWrapper.statusCode)(Json.toJson(errorWrapper))
          .withHeaders("X-CorrelationId" -> errorWrapper.correlationId.value)

  def handleOutcomeUnit(outcome: ServiceOutcome[Unit]): Result =
    outcome match
      case Right(wrapper) =>
        NoContent.withHeaders("X-CorrelationId" -> wrapper.correlationId.value)
      case Left(errorWrapper) =>
        Status(errorWrapper.statusCode)(Json.toJson(errorWrapper))
          .withHeaders("X-CorrelationId" -> errorWrapper.correlationId.value)
