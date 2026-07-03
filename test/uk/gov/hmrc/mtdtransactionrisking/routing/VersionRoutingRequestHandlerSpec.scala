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

package uk.gov.hmrc.mtdtransactionrisking.routing

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.mockito.Mockito.when
import org.scalatest.Inside
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.{Application, Configuration}
import play.api.http.{HttpConfiguration, HttpErrorHandler, HttpFilters}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.*
import play.api.routing.Router
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.mtdtransactionrisking.support.{MockAppConfig, UnitSpec}
import uk.gov.hmrc.mongo.play.PlayMongoModule
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.{InvalidAcceptHeaderError, UnsupportedVersionError}

class VersionRoutingRequestHandlerSpec extends UnitSpec with Inside with MockAppConfig with GuiceOneAppPerSuite {
  test =>

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .disable[PlayMongoModule]
      .build()

  implicit private val actorSystem: ActorSystem = ActorSystem("test")
  val action: DefaultActionBuilder              = app.injector.instanceOf[DefaultActionBuilder]

  import play.api.mvc.Handler
  import play.api.routing.sird.*

  object DefaultHandler extends Handler
  object V1Handler      extends Handler

  private val defaultRouter = Router.from { case POST(p"") =>
    DefaultHandler
  }

  private val v1Router = Router.from { case POST(p"/assist/123456789") =>
    V1Handler
  }

  private val routingMap = new VersionRoutingMap {
    override val defaultRouter: Router    = test.defaultRouter
    override val map: Map[String, Router] = Map("1.0" -> v1Router)
  }

  private def enabledConfig: Option[Configuration] = Some(Configuration(ConfigFactory.parseString(
    "version-1.enabled = true"
  )))

  private def disabledConfig: Option[Configuration] = Some(Configuration(ConfigFactory.parseString(
    "version-1.enabled = false"
  )))

  class Test(featureSwitchConfig: Option[Configuration] = enabledConfig)(implicit acceptHeader: Option[String]) {
    val httpConfiguration: HttpConfiguration = HttpConfiguration("context")
    private val errorHandler                 = mock[HttpErrorHandler]
    private val filters                      = mock[HttpFilters]
    when(filters.filters).thenReturn(Nil)

    MockedAppConfig.featureSwitch(featureSwitchConfig)

    val requestHandler: VersionRoutingRequestHandler =
      new VersionRoutingRequestHandler(routingMap, errorHandler, httpConfiguration, mockAppConfig, filters, action)

    def buildRequest(path: String): RequestHeader =
      acceptHeader
        .foldLeft(FakeRequest("POST", path)) { (req, accept) =>
          req.withHeaders((ACCEPT, accept))
        }
  }

  "Routing requests with no Accept header" should {
    implicit val acceptHeader: None.type = None

    handleWithDefaultRoutes()

    "return 406 for an API path" in new Test {
      val request: RequestHeader = buildRequest("/assist/123456789")
      inside(requestHandler.routeRequest(request)) { case Some(b: EssentialAction) =>
        val result = b.apply(request)
        status(result)        shouldBe NOT_ACCEPTABLE
        contentAsJson(result) shouldBe Json.toJson(InvalidAcceptHeaderError)
      }
    }
  }

  "Routing requests with a valid v1 Accept header" should {
    implicit val acceptHeader: Some[String] = Some("application/vnd.hmrc.1.0+json")

    handleWithDefaultRoutes()
    handleWithVersionRoutes("/assist/123456789", V1Handler)
  }

  "Routing requests with an unsupported version in the Accept header" should {
    implicit val acceptHeader: Some[String] = Some("application/vnd.hmrc.9.0+json")

    "return 406" in new Test {
      val request: RequestHeader = buildRequest("/assist/123456789")
      inside(requestHandler.routeRequest(request)) { case Some(b: EssentialAction) =>
        val result = b.apply(request)
        status(result)        shouldBe NOT_ACCEPTABLE
        contentAsJson(result) shouldBe Json.toJson(InvalidAcceptHeaderError)
      }
    }
  }

  "Routing requests for a known version that is disabled" should {
    implicit val acceptHeader: Some[String] = Some("application/vnd.hmrc.1.0+json")

    "return 404 with UnsupportedVersionError" in new Test(disabledConfig) {
      val request: RequestHeader = buildRequest("/assist/123456789")
      inside(requestHandler.routeRequest(request)) { case Some(b: EssentialAction) =>
        val result = b.apply(request)
        status(result)        shouldBe NOT_FOUND
        contentAsJson(result) shouldBe Json.toJson(UnsupportedVersionError)
      }
    }
  }

  private def handleWithDefaultRoutes()(implicit acceptHeader: Option[String]): Unit = {
    "when the path matches the default router" should {
      "route to the default handler" in new Test {
        requestHandler.routeRequest(buildRequest("")) shouldBe Some(DefaultHandler)
      }
    }

    "when the path ends with a trailing slash and matches after stripping it" should {
      "route to the default handler" in new Test {
        requestHandler.routeRequest(buildRequest("/")) shouldBe Some(DefaultHandler)
      }
    }
  }

  private def handleWithVersionRoutes(path: String, handler: Handler)(implicit acceptHeader: Option[String]): Unit = {
    "when the path matches the version router" should {
      "route to the correct version handler" in new Test {
        requestHandler.routeRequest(buildRequest(path)) shouldBe Some(handler)
      }
    }

    "when the path ends with a trailing slash" should {
      "strip the slash and route to the correct version handler" in new Test {
        requestHandler.routeRequest(buildRequest(s"$path/")) shouldBe Some(handler)
      }
    }
  }
}