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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import play.api.http.Status.{BAD_REQUEST, INTERNAL_SERVER_ERROR, NO_CONTENT, OK}
import play.api.libs.json.{Json, OFormat}
import play.api.mvc.{ControllerComponents, Result}
import play.api.test.Helpers
import play.api.test.Helpers.{contentAsJson, defaultAwaitTimeout, header, status}
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.{DownstreamError, ErrorWrapper, VrnFormatError}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.outcomes.ResponseWrapper
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.Future

class ResponseHandlerSpec extends UnitSpec:

  private val correlationId = CorrelationId("test-correlation-id")
  private val handler = new Host(Helpers.stubControllerComponents())

  private given mat: Materializer = Materializer(system)

  private given system: ActorSystem = ActorSystem("test")

  private given OFormat[Payload] = Json.format[Payload]

  /** The handler returns a plain Result and test helpers expect a Future. */
  private def resultOf(result: Result): Future[Result] = Future.successful(result)

  private case class Payload(value: String)

  private class Host(cc: ControllerComponents) extends BackendController(cc), ResponseHandler

  "handleOutcome" should:

    "return 200 with the payload and correlation header on Right" in:
      val result = resultOf(handler.handleOutcome(Right(ResponseWrapper(correlationId, Payload("hello")))))

      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.obj("value" -> "hello")
      header("X-CorrelationId", result) shouldBe Some("test-correlation-id")

    "return the error status and body with correlation header on Left" in:
      val result = resultOf(handler.handleOutcome[Payload](Left(ErrorWrapper(correlationId, VrnFormatError))))

      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "code").as[String] shouldBe "VRN_INVALID"
      header("X-CorrelationId", result) shouldBe Some("test-correlation-id")

    "relay a raw error body with its status" in:
      val rawBody = Json.obj(
        "code" -> "INVALID_REQUEST",
        "message" -> "Invalid request",
        "errors" -> Json.arr(Json.obj("code" -> "PERIOD_KEY_INVALID", "message" -> "bad", "path" -> "/periodKey"))
      )

      val outcome = Left(ErrorWrapper(correlationId, DownstreamError, rawBody = Some(rawBody), rawStatus = Some(BAD_REQUEST)))
      val result = resultOf(handler.handleOutcome[Payload](outcome))

      status(result) shouldBe BAD_REQUEST
      contentAsJson(result) shouldBe rawBody

  "handleOutcomeUnit" should:

    "return 204 with correlation header on Right" in:
      val result = resultOf(handler.handleOutcomeUnit(Right(ResponseWrapper(correlationId, ()))))

      status(result) shouldBe NO_CONTENT
      header("X-CorrelationId", result) shouldBe Some("test-correlation-id")

    "return the error status and body with correlation header on Left" in:
      val result = resultOf(handler.handleOutcomeUnit(Left(ErrorWrapper(correlationId, DownstreamError))))

      status(result) shouldBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "code").as[String] shouldBe "INTERNAL_SERVER_ERROR"
      header("X-CorrelationId", result) shouldBe Some("test-correlation-id")
