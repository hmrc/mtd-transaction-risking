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

import play.api.libs.json.Json
import play.api.mvc.*
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.controllers.auth.VATAuthAction
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.InsightsRequest
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.InsightsResponse
import uk.gov.hmrc.mtdtransactionrisking.v1.services.InsightsService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class GenerateFeedbackController @Inject()(cc: ControllerComponents,
                                           insightsService: InsightsService,
                                           authAction: VATAuthAction)(implicit ec: ExecutionContext)
  extends BackendController(cc):

  def generateFeedback(vrn: String): Action[AnyContent] = authAction.authorisedFor(vrn).async:
    request =>
      given Request[AnyContent] = request
      given CorrelationId = IdGenerator.generateId()

      insightsService.assess(InsightsRequest(vrn)).map:
        riskResponse =>
          riskResponse
        // nextResponse <- nextService.call(riskResponse.riskScore)

      .value.map {
        case Right(response: InsightsResponse) =>
          Ok(Json.toJson(response))
            .withHeaders("X-CorrelationId" -> summon[CorrelationId].value)

        case Left(error: String) =>
          InternalServerError(Json.obj("message" -> error))
            .withHeaders("X-CorrelationId" -> summon[CorrelationId].value)
      }
