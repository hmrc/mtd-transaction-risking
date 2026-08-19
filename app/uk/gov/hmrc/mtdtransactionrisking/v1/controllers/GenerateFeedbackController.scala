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

import play.api.libs.json.JsValue
import play.api.mvc.*
import uk.gov.hmrc.mtdtransactionrisking.config.AppConfig
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.utils.{IdGenerator, Logging}
import uk.gov.hmrc.mtdtransactionrisking.v1.controllers.auth.VATAuthAction
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.{FeedbackResponse, ResponseHandler}
import uk.gov.hmrc.mtdtransactionrisking.v1.services.GenerateFeedbackService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class GenerateFeedbackController @Inject() (cc: ControllerComponents,
                                            generateFeedbackService: GenerateFeedbackService,
                                            authAction: VATAuthAction,
                                            appConfig: AppConfig)(implicit ec: ExecutionContext)
    extends BackendController(cc),
      Logging,
      ResponseHandler:

  def generateFeedback(vrn: String): Action[JsValue] =
    authAction
      .authorisedFor(vrn)
      .async(parse.json): request =>

        given Request[JsValue] = request
        given correlationId: CorrelationId = IdGenerator.generateId()

        appConfig.feedbackStubBaseUrl match
          // Feedback stub path used in external test while the real downstream is built
          case Some(_) =>
            generateFeedbackService.requestStubFeedback(vrn).map(handleOutcome)

          case None =>
            generateFeedbackService
              .generateFeedback(
                vrn = vrn,
                body = request.body,
                agentReferenceNumber = request.arn,
                requestHeaders = request.headers.headers
              )
              .map(handleOutcome)
