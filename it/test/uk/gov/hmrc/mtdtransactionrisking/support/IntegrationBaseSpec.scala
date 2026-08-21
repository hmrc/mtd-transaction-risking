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

package uk.gov.hmrc.mtdtransactionrisking.support

import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import play.api.{Application, Environment, Mode}
import uk.gov.hmrc.mongo.play.PlayMongoModule
import uk.gov.hmrc.mtdtransactionrisking.stubs.AuthStub

trait IntegrationBaseSpec
  extends AnyWordSpecLike
    with Matchers
    with WireMockHelper
    with GuiceOneServerPerSuite
    with BeforeAndAfterAll:

  lazy val client: WSClient = app.injector.instanceOf[WSClient]
  val mockHost: String = WireMockHelper.host
  val mockPort: Int = WireMockHelper.wireMockPort

  def servicesConfig: Map[String, Any] = Map(
    "microservice.services.insights-proxy.host" -> mockHost,
    "microservice.services.insights-proxy.port" -> mockPort,
    "microservice.services.vat-api.host"        -> mockHost,
    "microservice.services.vat-api.port"        -> mockPort,
    "microservice.services.vat-api.submit-url"  -> "/internal",
    "microservice.services.auth.host"           -> mockHost,
    "microservice.services.auth.port"           -> mockPort,
    "feature-switch.version-1.enabled"          -> true,
    "microservice.services.rds.host"            -> mockHost,
    "microservice.services.rds.port"            -> mockPort,
    "microservice.services.rds.protocol"        -> "http",
    "microservice.services.rds.submit-url"      -> "/microanalyticScore/modules/HMRC_ASSIST_VAT_FINSUB_FEEDBACK/steps/execute",
    "microservice.services.rds.RdsAuthRequired" -> false
  )

  override implicit lazy val app: Application =
    GuiceApplicationBuilder()
      .in(Environment.simple(mode = Mode.Dev))
      .configure(servicesConfig)
      .disable[PlayMongoModule]
      .build()

  override def beforeAll(): Unit =
    super.beforeAll()
    startWireMock()

  override def afterAll(): Unit =
    stopWireMock()
    super.afterAll()

  def buildRequest(path: String): WSRequest =
    client
      .url(s"http://localhost:$port$path")
      .withFollowRedirects(false)
      .withHttpHeaders(
        "Authorization"-> "Bearer abc123",
        "Accept"       -> "application/vnd.hmrc.1.0+json",
        "Content-Type" -> "application/json",
        AuthStub.headers
      )

  def document(response: WSResponse): JsValue =
    Json.parse(response.body)