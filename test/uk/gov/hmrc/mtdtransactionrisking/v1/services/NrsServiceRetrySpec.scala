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

import com.google.inject.Provider
import com.typesafe.config.ConfigFactory
import org.bson.types.ObjectId
import play.api.Configuration
import play.api.libs.json.Json
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem}
import uk.gov.hmrc.mtdtransactionrisking.support.{MockAppConfig, UnitSpec}
import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.NrsConnector
import uk.gov.hmrc.mtdtransactionrisking.v1.models.auth.IdentityData
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.nrs.{
  AssistRequestFeedback,
  Metadata,
  NrsSubmission,
  NrsSubmissionResult,
  NrsSubmissionWorkItem,
  SearchKeys
}
import uk.gov.hmrc.mtdtransactionrisking.v1.repositories.NrsSubmissionWorkItemStore
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.concurrent.{Future, Promise}

class NrsServiceRetrySpec extends UnitSpec, MockAppConfig:

  private val timestamp =
    Instant.parse("2026-10-06T10:00:00Z")

  private val correlationId =
    CorrelationId("bd6c0972-34e7-11f1-9715-f35b3eb40b52")  

  private val evidence =
    Json.obj(
      "periodKey" -> "18AD",
      "vatDueSales" -> 105.50
    )

  private val identityData =
    Json
      .parse(
        """
          |{
          |  "internalId": "internal-id",
          |  "confidenceLevel": 200,
          |  "agentInformation": {},
          |  "credentialRole": null,
          |  "itmpName": {},
          |  "itmpAddress": {},
          |  "affinityGroup": "Organisation",
          |  "loginTimes": {
          |    "currentLogin": "2026-10-06T10:00:00Z"
          |  }
          |}
          |""".stripMargin
      )
      .as[IdentityData]

  private val requestHeaders =
    Seq(
      "Authorization" -> "Bearer vendor-token",
      "Accept" -> "application/vnd.hmrc.1.0+json"
    )

  private val persistedSubmission =
    NrsSubmission(
      payload = "eyJwZXJpb2RLZXkiOiIxOEFEIn0=",
      metadata = Metadata(
        businessId = "vata",
        notableEvent = "vata-request-feedback",
        payloadContentType = "application/json",
        payloadSha256Checksum = "checksum",
        userSubmissionTimestamp = timestamp.toString,
        identityData = Json.obj("internalId" -> "internal-id"),
        userAuthToken = "Bearer vendor-token",
        headerData = Json.obj(
          "Accept" -> "application/vnd.hmrc.1.0+json"
        ),
        searchKeys = SearchKeys(
          vrn = "123456789",
          reportId = "a1e8057e-fbbc-47a8-a8b4-78d9f015c253"
        )
      )
    )

  private val storedWorkItem =
    WorkItem(
      id = new ObjectId(),
      receivedAt = timestamp,
      updatedAt = timestamp,
      availableAt = timestamp,
      status = ProcessingStatus.InProgress,
      failureCount = 0,
      item = NrsSubmissionWorkItem(nrsSubmission = persistedSubmission, correlationId = correlationId)
    )

  private def enabledFeatureSwitch: Option[Configuration] =
    Some(
      Configuration(
        ConfigFactory.parseString(
          "nrs-submission.enabled = true"
        )
      )
    )

  private trait Test:

    def retryBatchSize: Int =
      100

    val connector =
      mock[NrsConnector]

    val repository =
      mock[NrsSubmissionWorkItemStore]

    val repositoryProvider =
      mock[Provider[NrsSubmissionWorkItemStore]]

    val retryPolicy =
      mock[NrsRetryPolicy]

    MockedAppConfig.featureSwitch
      .returns(enabledFeatureSwitch)
      .anyNumberOfTimes()

    MockedAppConfig.nrsRetryBatchSize
      .returns(retryBatchSize)
      .anyNumberOfTimes()

    MockedAppConfig.nrsRetryRetention
      .returns(28.days)
      .anyNumberOfTimes()

    val service =
      new NrsService(
        appConfig = mockAppConfig,
        connector = connector,
        nrsSubmissionWorkItemRepositoryProvider = repositoryProvider,
        retryPolicy = retryPolicy
      )

    def expectedBuiltSubmission: NrsSubmission =
      service
        .buildNrsSubmission(
          evidence = evidence,
          vrn = "123456789",
          reportId = "a1e8057e-fbbc-47a8-a8b4-78d9f015c253",
          submissionTimestamp = timestamp,
          identityData = Some(identityData),
          userAuthToken = Some("Bearer vendor-token"),
          requestHeaders = requestHeaders,
          notableEventType = AssistRequestFeedback
        )
        .value

    def submitInitial(): Unit =
      service.submit(
        evidence = evidence,
        vrn = "123456789",
        reportId = "a1e8057e-fbbc-47a8-a8b4-78d9f015c253",
        submissionTimestamp = timestamp,
        identityData = Some(identityData),
        userAuthToken = Some("Bearer vendor-token"),
        requestHeaders = requestHeaders,
        notableEventType = AssistRequestFeedback,
        correlationId = correlationId
      )

    def expectRepositoryProvider(): Unit =
      (() => repositoryProvider.get())
        .expects()
        .returning(repository)
        .anyNumberOfTimes()

  "NrsService.submit" should:

    "not request the retry repository when NRS accepts the initial submission" in new Test:
      val connectorCalled =
        Promise[Unit]()

      (connector.submit(_: NrsSubmission, _: CorrelationId))
       .expects(expectedBuiltSubmission, correlationId)
        .onCall { _ =>
          connectorCalled.success(())
          Future.successful(NrsSubmissionResult.Success)
        }

      submitInitial()

      await(connectorCalled.future) shouldBe ()

    "persist the exact built submission after an initial retryable failure" in new Test:
      val persisted =
        Promise[NrsSubmission]()

      (connector.submit(_: NrsSubmission, _: CorrelationId))
        .expects(expectedBuiltSubmission, correlationId)
        .returning(
          Future.successful(NrsSubmissionResult.RetryableFailure)
        )

      expectRepositoryProvider()

      (repository.enqueueRetryableFailure(_: NrsSubmission, _: CorrelationId))
        .expects(expectedBuiltSubmission, correlationId)
        .onCall { (submission, _) =>
          persisted.success(submission)
          Future.successful(storedWorkItem)
        }

      submitInitial()

      await(persisted.future) shouldBe expectedBuiltSubmission

    "persist the exact built submission after an initial permanent failure" in new Test:
      val persisted =
        Promise[NrsSubmission]()

      (connector.submit(_: NrsSubmission, _: CorrelationId))
        .expects(expectedBuiltSubmission, correlationId)
        .returning(
          Future.successful(NrsSubmissionResult.PermanentFailure)
        )

      expectRepositoryProvider()

      (repository.enqueuePermanentFailure(_: NrsSubmission, _: CorrelationId))
        .expects(expectedBuiltSubmission, correlationId)
        .onCall { (submission, _) =>
          persisted.success(submission)
          Future.successful(storedWorkItem)
        }

      submitInitial()

      await(persisted.future) shouldBe expectedBuiltSubmission

    "not fail the VAT Assist journey if retry persistence fails" in new Test:
      val persistenceAttempted =
        Promise[Unit]()

      (connector.submit(_: NrsSubmission, _: CorrelationId))
        .expects(expectedBuiltSubmission, correlationId)
        .returning(
          Future.successful(NrsSubmissionResult.RetryableFailure)
        )

      expectRepositoryProvider()

      (repository.enqueueRetryableFailure(_: NrsSubmission, _: CorrelationId))
        .expects(expectedBuiltSubmission, correlationId)
        .onCall { _ =>
          persistenceAttempted.success(())
          Future.failed(
            new RuntimeException("Mongo is unavailable")
          )
        }

      submitInitial()

      await(persistenceAttempted.future) shouldBe ()

  "NrsService.processDueWorkItems" should:

    "complete when there are no due work items" in new Test:
      expectRepositoryProvider()

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(None))

      await(service.processDueWorkItems()) shouldBe ()

    "delete a claimed work item when NRS accepts the retry" in new Test:
      expectRepositoryProvider()

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(Some(storedWorkItem)))
        .once()

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(None))
        .once()

      (connector.submit(_: NrsSubmission, _: CorrelationId))
        .expects(persistedSubmission, correlationId)
        .returning(
          Future.successful(NrsSubmissionResult.Success)
        )

      (repository.completeAndDelete(_: ObjectId))
        .expects(storedWorkItem.id)
        .returning(Future.successful(true))

      await(service.processDueWorkItems()) shouldBe ()

    "reuse the stored submission when retrying" in new Test:
      expectRepositoryProvider()

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(Some(storedWorkItem)))
        .once()

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(None))
        .once()

      (connector.submit(_: NrsSubmission, _: CorrelationId))
      .expects(
        storedWorkItem.item.nrsSubmission,
        storedWorkItem.item.correlationId
      )
        .returning(
          Future.successful(NrsSubmissionResult.Success)
        )

      (repository.completeAndDelete(_: ObjectId))
        .expects(storedWorkItem.id)
        .returning(Future.successful(true))

      await(service.processDueWorkItems()) shouldBe ()

    "reschedule a claimed item after a retryable NRS failure" in new Test:
      expectRepositoryProvider()

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(Some(storedWorkItem)))
        .once()

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(None))
        .once()

      (connector.submit(_: NrsSubmission, _: CorrelationId))
        .expects(persistedSubmission, correlationId)
        .returning(
          Future.successful(NrsSubmissionResult.RetryableFailure)
        )

      (retryPolicy.isExhaustedAfterFailure(_: Int))
        .expects(1)
        .returning(false)

      (() => retryPolicy.maxRetries)
        .expects()
        .returning(10)

      (retryPolicy.delayForRetry(_: Int))
        .expects(2)
        .returning(20.minutes)

      (repository.markRetryableFailure(_: WorkItem[NrsSubmissionWorkItem]))
        .expects(storedWorkItem)
        .returning(Future.successful(true))

      await(service.processDueWorkItems()) shouldBe ()

    "mark a claimed item permanently failed after a permanent NRS failure" in new Test:
      expectRepositoryProvider()

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(Some(storedWorkItem)))
        .once()

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(None))
        .once()

      (connector.submit(_: NrsSubmission, _: CorrelationId))
        .expects(persistedSubmission, correlationId)
        .returning(
          Future.successful(NrsSubmissionResult.PermanentFailure)
        )

      (repository.markPermanentFailure(_: WorkItem[NrsSubmissionWorkItem]))
        .expects(storedWorkItem)
        .returning(Future.successful(true))

      await(service.processDueWorkItems()) shouldBe ()

    "not schedule an eleventh retry after retry ten fails" in new Test:
      val retryTenWorkItem =
        storedWorkItem.copy(failureCount = 9)

      expectRepositoryProvider()

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(Some(retryTenWorkItem)))
        .once()

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(None))
        .once()

      (connector.submit(_: NrsSubmission, _: CorrelationId))
        .expects(persistedSubmission, correlationId)
        .returning(
          Future.successful(NrsSubmissionResult.RetryableFailure)
        )

      (retryPolicy.isExhaustedAfterFailure(_: Int))
        .expects(10)
        .returning(true)

      (() => retryPolicy.maxRetries)
        .expects()
        .returning(10)

      (repository.markRetryableFailure(_: WorkItem[NrsSubmissionWorkItem]))
        .expects(retryTenWorkItem)
        .returning(Future.successful(true))

      await(service.processDueWorkItems()) shouldBe ()

    "reuse the persisted original correlation ID when retrying a work item" in new Test:
      expectRepositoryProvider()

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(Some(storedWorkItem)))
        .once()

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(None))
        .once()

      (connector.submit(_: NrsSubmission, _: CorrelationId))
        .expects(
          storedWorkItem.item.nrsSubmission,
          storedWorkItem.item.correlationId
        )
        .returning(Future.successful(NrsSubmissionResult.Success))

      (repository.completeAndDelete(_: ObjectId))
        .expects(storedWorkItem.id)
        .returning(Future.successful(true))

      await(service.processDueWorkItems()) shouldBe()

    "process no more than the configured number of due work items in one run" in new Test:
      override def retryBatchSize: Int = 2

      val firstWorkItem =
        storedWorkItem

      val secondWorkItem =
        storedWorkItem.copy(
          id = new ObjectId()
        )

      expectRepositoryProvider()

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(Some(firstWorkItem)))
        .once()

      (() => repository.pullDue())
        .expects()
        .returning(Future.successful(Some(secondWorkItem)))
        .once()

      (connector.submit(_: NrsSubmission, _: CorrelationId))
        .expects(
          firstWorkItem.item.nrsSubmission,
          firstWorkItem.item.correlationId
        )
        .returning(
          Future.successful(NrsSubmissionResult.Success)
        )
        .once()

      (connector.submit(_: NrsSubmission, _: CorrelationId))
        .expects(
          secondWorkItem.item.nrsSubmission,
          secondWorkItem.item.correlationId
        )
        .returning(
          Future.successful(NrsSubmissionResult.Success)
        )
        .once()

      (repository.completeAndDelete(_: ObjectId))
        .expects(firstWorkItem.id)
        .returning(Future.successful(true))
        .once()

      (repository.completeAndDelete(_: ObjectId))
        .expects(secondWorkItem.id)
        .returning(Future.successful(true))
        .once()

      await(
        service.processDueWorkItems()
      ) shouldBe()
