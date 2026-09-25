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

package uk.gov.hmrc.mtdtransactionrisking.v1.services

import controllers.Execution.trampoline
import play.api.http.Status.BAD_REQUEST
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.mocks.connectors.{MockAcknowledgeConnector, MockRdsConnector}
import uk.gov.hmrc.mtdtransactionrisking.v1.mocks.services.{MockInteractionService, MockRdsAuthService, MockNrsService}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.auth.{IdentityData,RdsAuthCredentials}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.{DownstreamError, ErrorWrapper}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.outcomes.ResponseWrapper
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.AcknowledgeRequest
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.RdsAcknowledgeResponse
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.nrs.AssistReportAcknowledged

import java.time.Instant
import scala.concurrent.Future

class AcknowledgeServiceSpec extends UnitSpec, MockAcknowledgeConnector, MockInteractionService, MockRdsAuthService, MockNrsService, MockRdsConnector:

  implicit private val hc: HeaderCarrier = HeaderCarrier()
  implicit private val correlationId: CorrelationId = CorrelationId("test-correlation-id")

  private val request = AcknowledgeRequest(
    vrn = "123456789",
    reportId = "f2fb30e5-4ab6-4a29-b3c1-c00000000001",
    correlationId = "9EEB55EF4FA9A24954BC982DF1D59B3D02BC097F6B1377B8B335C7583D92B959",
    presentedDateTime = "2026-06-09T10:30:00Z"
  )

  private val identityData: Option[IdentityData] = None
  private val userAuthToken = Some("Bearer vendor-token")
  private val requestHeaders = Seq("Accept" -> "application/vnd.hmrc.1.0+json")
  private val submissionTimestamp = Instant.parse("2026-09-24T10:15:30Z")

  private val credentials = RdsAuthCredentials(
    access_token = "a-bearer-token",
    token_type = "Bearer",
    expires_in = 3600
  )

  private val response = RdsAcknowledgeResponse(
    vrn = Some("123456789"),
    feedbackId = Some("f2fb30e5-4ab6-4a29-b3c1-c00000000001"),
    createdDttm = Some("2026-06-09T10:30:00Z"),
    responseCode = Some(202),
    responseMessage = Some("Accepted")
  )

  private trait Test:
    val service = new AcknowledgeService(
      mockRdsAuthService,
      mockRdsConnector,
      mockInteractionService,
      mockNrsService,
      mockAcknowledgeConnector
    )

  "acknowledge" should:

    "call auth and RDS and store the interaction and return a successful outcome" in new Test:
      MockInteractionService.storeAcknowledgement(request).returns(())

      MockNrsService
        .submit(
          evidence = Json.toJson(request),
          vrn = request.vrn,
          reportId = request.correlationId,
          submissionTimestamp = submissionTimestamp,
          identityData = identityData,
          userAuthToken = userAuthToken,
          requestHeaders = requestHeaders,
          notableEventType = AssistReportAcknowledged
        )
        .returns(())

      MockRdsAuthService.bearerToken
        .returns(Future.successful(Right(ResponseWrapper(correlationId, Some(credentials)))))

      MockRdsConnector
        .acknowledge(request, Some(credentials))
        .returns(Future.successful(Right(ResponseWrapper(correlationId, response))))

      await(
        service.acknowledge(
          request = request,
          identityData = identityData,
          userAuthToken = userAuthToken,
          requestHeaders = requestHeaders,
          submissionTimestamp = submissionTimestamp
        )
      ) shouldBe Right(ResponseWrapper(correlationId, ()))

    "call RDS with no credentials when auth is not required" in new Test:
      MockRdsAuthService.bearerToken
        .returns(Future.successful(Right(ResponseWrapper(correlationId, None))))

      MockRdsConnector
        .acknowledge(request, None)
        .returns(Future.successful(Right(ResponseWrapper(correlationId, response))))

      MockInteractionService.storeAcknowledgement(request).returns(())

      MockNrsService
        .submit(
          evidence = Json.toJson(request),
          vrn = request.vrn,
          reportId = request.correlationId,
          submissionTimestamp = submissionTimestamp,
          identityData = identityData,
          userAuthToken = userAuthToken,
          requestHeaders = requestHeaders,
          notableEventType = AssistReportAcknowledged
        )
        .returns(())

      await(
        service.acknowledge(
          request = request,
          identityData = identityData,
          userAuthToken = userAuthToken,
          requestHeaders = requestHeaders,
          submissionTimestamp = submissionTimestamp
        )
      ) shouldBe Right(ResponseWrapper(correlationId, ()))

    "pass through the error and skip RDS and the interaction store when auth fails" in new Test:
      private val errorWrapper = ErrorWrapper(correlationId, DownstreamError)

      MockRdsAuthService.bearerToken
        .returns(Future.successful(Left(errorWrapper)))

      await(
        service.acknowledge(
          request = request,
          identityData = identityData,
          userAuthToken = userAuthToken,
          requestHeaders = requestHeaders,
          submissionTimestamp = submissionTimestamp
        )
      ) shouldBe Left(errorWrapper)

    "pass through the error and skip the interaction store when RDS rejects the acknowledgement" in new Test:
      private val errorWrapper = ErrorWrapper(correlationId, DownstreamError)

      MockRdsAuthService.bearerToken
        .returns(Future.successful(Right(ResponseWrapper(correlationId, Some(credentials)))))

      MockRdsConnector
        .acknowledge(request, Some(credentials))
        .returns(Future.successful(Left(errorWrapper)))

      await(
        service.acknowledge(
          request = request,
          identityData = identityData,
          userAuthToken = userAuthToken,
          requestHeaders = requestHeaders,
          submissionTimestamp = submissionTimestamp
        )
      ) shouldBe Left(errorWrapper)

  "requestStubAcknowledge" should:

    "pass through a successful outcome from the connector" in new Test:
      MockAcknowledgeConnector
        .acknowledge(request)
        .returns(Future.successful(Right(ResponseWrapper(correlationId, ()))))

      await(service.stubAcknowledge(request)) shouldBe Right(ResponseWrapper(correlationId, ()))

    "pass through an error outcome from the connector" in new Test:
      private val errorWrapper = ErrorWrapper(correlationId, DownstreamError, rawStatus = Some(BAD_REQUEST))

      MockAcknowledgeConnector
        .acknowledge(request)
        .returns(Future.successful(Left(errorWrapper)))

      await(service.stubAcknowledge(request)) shouldBe Left(errorWrapper)
