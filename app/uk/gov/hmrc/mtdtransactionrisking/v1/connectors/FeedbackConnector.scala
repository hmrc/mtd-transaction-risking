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

package uk.gov.hmrc.mtdtransactionrisking.v1.connectors

import cats.data.EitherT
import play.api.Logging
import play.api.libs.json.Json
import play.api.libs.ws.writeableOf_JsValue
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.mtdtransactionrisking.config.AppConfig
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.InsightsRequest
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.FeedbackResponse

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FeedbackConnector @Inject()(
                                   httpClient: HttpClientV2,
                                   appConfig: AppConfig
                                 )(implicit ec: ExecutionContext) extends Logging:

  private def requiredHeaders(
                               correlationId: CorrelationId,
                               appName: String
                             )(implicit hc: HeaderCarrier): Seq[(String, String)] =
    Seq(
      "User-Agent" -> appName,
      "Content-Type" -> "application/json",
      "X-Correlation-Id" -> correlationId.value
    ) ++ hc.headers(appConfig.feedbackEnvironmentHeaders.getOrElse(Seq.empty))

  def requestFeedback(
                       request: InsightsRequest
                     )(implicit hc: HeaderCarrier, correlationId: CorrelationId): EitherT[Future, (Int, String), FeedbackResponse] =
    logger.info(s"${correlationId.value}::[FeedbackConnector][requestFeedback] calling feedback service")

    EitherT(
      httpClient
        .post(url"${appConfig.feedbackStubBaseUrl.getOrElse("")}")
        .withBody(Json.toJson(request))
        .setHeader(requiredHeaders(correlationId, appConfig.appName) *)
        .execute[HttpResponse]
        .map { response =>
          response.status match
            case 200 =>
              response.json.asOpt[FeedbackResponse] match
                case Some(feedback) =>
                  logger.info(s"${correlationId.value}::[FeedbackConnector][requestFeedback] success")
                  Right(feedback)
                case None =>
                  logger.error(s"${correlationId.value}::[FeedbackConnector][requestFeedback] malformed response")
                  Left((500, Json.obj("code" -> "INTERNAL_SERVER_ERROR", "message" -> "Malformed response from feedback service").toString))
            case status =>
              logger.error(s"${correlationId.value}::[FeedbackConnector][requestFeedback] failed $status: ${response.body}")
              Left((status, response.body))
}
        .recover:
          case ex =>
            logger.error(s"${correlationId.value}::[FeedbackConnector][requestFeedback] unexpected exception", ex)
            Left((500, s"Exception calling feedback service: ${ex.getMessage}"))
    )