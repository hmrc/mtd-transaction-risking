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
import uk.gov.hmrc.mtdtransactionrisking.config.AppConfig
import uk.gov.hmrc.mtdtransactionrisking.utils.{IdGenerator, Logging}
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.controllers.auth.VATAuthAction
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.AcknowledgeRequest
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.ResponseHandler
import uk.gov.hmrc.mtdtransactionrisking.v1.services.AcknowledgeService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class AcknowledgeController @Inject() (
                                        cc: ControllerComponents,
                                        acknowledgeService: AcknowledgeService,
                                        authAction: VATAuthAction,
                                        appConfig: AppConfig
                                      )(implicit ec: ExecutionContext)
  extends BackendController(cc),
    ResponseHandler, Logging:

  def acknowledgeReport(vrn: String, reportId: String, correlationId: String): Action[AnyContent] =
    authAction
      .authorisedFor(vrn)
      .async: request =>

        given Request[AnyContent] = request
        given internalCorrelationId: CorrelationId = IdGenerator.generateId()

        val presentedDateTime  = request.getQueryString("presentedDateTime").getOrElse("")
        val acknowledgeRequest = AcknowledgeRequest(vrn, reportId, correlationId, presentedDateTime)

        appConfig.acknowledgeStubBaseUrl match
          // Acknowledge stub path used in external test while the real downstream is built
          case Some(_) =>
            logger.info(s"${internalCorrelationId.value}::[AcknowledgeController][acknowledgeReport] using stub path for reportId $reportId")
            acknowledgeService.stubAcknowledge(acknowledgeRequest).map(handleOutcomeUnit)

          case None =>
            acknowledgeService.acknowledge(acknowledgeRequest).map(handleOutcomeUnit)