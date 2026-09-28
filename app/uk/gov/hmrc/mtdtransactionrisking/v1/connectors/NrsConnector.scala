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

import play.api.Logging
import play.api.http.Status.ACCEPTED
import play.api.libs.json.Json
import play.api.libs.ws.writeableOf_JsValue
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.mtdtransactionrisking.config.AppConfig
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.nrs.NrsSubmission

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NrsConnector @Inject() (
                               httpClient: HttpClientV2,
                               appConfig: AppConfig
                             )(implicit ec: ExecutionContext)
  extends Logging:

  def submit(nrsSubmission: NrsSubmission): Future[Unit] =
        given HeaderCarrier = HeaderCarrier()

        val nrsCorrelationId = IdGenerator.generateId()

        httpClient
          .post(url"${appConfig.nrsSubmissionUrl}")
          .withBody(Json.toJson(nrsSubmission))
          .setHeader(
            "X-API-Key" -> appConfig.nrsApiKey,
            "X-Correlation-Id" -> nrsCorrelationId.value,
            "Content-Type" -> "application/json"
          )
          .execute[HttpResponse]
          .map:
            case response if response.status == ACCEPTED =>
              logger.info(s"${nrsCorrelationId.value}::[NrsConnector][submit] NRS submission accepted")

            case response =>
              logger.warn(
                s"${nrsCorrelationId.value}::[NrsConnector][submit] " +
                  s"NRS submission was not accepted, status ${response.status}"
              )
          .recover:
            case ex =>
              logger.warn(s"${nrsCorrelationId.value}::[NrsConnector][submit] NRS submission failed", ex)
              ()
