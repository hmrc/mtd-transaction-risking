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

package uk.gov.hmrc.mtdtransactionrisking.v1.controllers

import play.api.mvc.*
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.controllers.auth.VATAuthAction
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.AcknowledgeRequest
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.ResponseHandler
import uk.gov.hmrc.mtdtransactionrisking.v1.services.AcknowledgeStubService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class AcknowledgeController @Inject()(
                                       cc:                 ControllerComponents,
                                       acknowledgeService: AcknowledgeStubService,
                                       authAction:         VATAuthAction
                                     )(implicit ec: ExecutionContext) extends BackendController(cc), ResponseHandler:

  def acknowledgeReport(vrn: String, reportId: String, correlationId: String): Action[AnyContent] =
    authAction.authorisedFor(vrn).async: request =>
      given Request[AnyContent] = request
      given genCorrelationId: CorrelationId = IdGenerator.generateId()

      val presentedDateTime = request.getQueryString("presentedDateTime").getOrElse("")

      acknowledgeService
        .acknowledge(AcknowledgeRequest(vrn, reportId, correlationId, presentedDateTime))
        .map(handleOutcomeUnit)
