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

package uk.gov.hmrc.mtdtransactionrisking.repositories

import org.bson.types.ObjectId
import org.mongodb.scala.ObservableFuture
import org.mongodb.scala.model.{Filters, Updates}
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem, WorkItemFields}
import uk.gov.hmrc.mtdtransactionrisking.config.AppConfig
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.nrs.{Metadata, NrsSubmission, NrsSubmissionWorkItem, SearchKeys}
import uk.gov.hmrc.mtdtransactionrisking.v1.repositories.NrsSubmissionWorkItemRepository
import uk.gov.hmrc.mtdtransactionrisking.v1.services.NrsRetryPolicy

import java.time.{Clock, Instant, ZoneId, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.*

class NrsSubmissionWorkItemRepositoryISpec
  extends AnyWordSpec
    with Matchers
    with OptionValues
    with ScalaFutures
    with BeforeAndAfterEach
    with DefaultPlayMongoRepositorySupport[WorkItem[NrsSubmissionWorkItem]]
    with MockFactory:

  /*
   * The original NRS call is not a retry.
   *
   * failureCount records completed retry attempts that have failed:
   *
   * failureCount = 0 -> retry 1 is being attempted
   * failureCount = 8 -> retry 9 is being attempted
   * failureCount = 9 -> retry 10 is being attempted
   *
   * A failed retry 10 increments failureCount to 10 and marks the item
   * PermanentlyFailed. Retry 11 must never be scheduled.
   */
  private val maxRetries = 10

  private val initialNow =
    Instant.parse("2026-10-06T10:00:00Z")

  private val clock =
    new MutableClock(initialNow)

  /*
   * The repository only reads these AppConfig values for:
   *
   * - the updatedAt TTL-index retention duration;
   * - stale InProgress work-item recovery.
   *
   * Retry calculations are controlled by TestRetryPolicy, keeping this ISpec
   * focused on persistence and work-item lifecycle behaviour.
   */
  private val appConfig =
    stub[AppConfig]

  (() => appConfig.nrsRetryRetention)
    .when()
    .returns(28.days)

  (() => appConfig.nrsRetryInProgressRetryAfter)
    .when()
    .returns(5.minutes)

  private object TestRetryPolicy extends NrsRetryPolicy:

    override val maxRetries: Int =
      NrsSubmissionWorkItemRepositoryISpec.this.maxRetries

    override def delayForRetry(
                                retryNumber: Int
                              ): FiniteDuration =
      require(
        retryNumber >= 1 && retryNumber <= maxRetries,
        s"Retry number $retryNumber must be between 1 and $maxRetries"
      )

      /*
       * Retry 1  = 10 minutes
       * Retry 2  = 20 minutes
       * Retry 3  = 40 minutes
       * ...
       * Retry 10 = 5120 minutes
       */
      (10.minutes.toMillis * Math.pow(2.0, retryNumber - 1))
        .toLong
        .millis

    override def isExhaustedAfterFailure(
                                          failureCount: Int
                                        ): Boolean =
      failureCount >= maxRetries

  override protected val repository: NrsSubmissionWorkItemRepository =
    new NrsSubmissionWorkItemRepository(
      mongoComponent = mongoComponent,
      appConfig = appConfig,
      retryPolicy = TestRetryPolicy,
      clock = clock
    )

  private val submission =
    NrsSubmission(
      payload = "eyJwZXJpb2RLZXkiOiJBQjEyIn0=",
      metadata = Metadata(
        businessId = "vata",
        notableEvent = "vata-request-feedback",
        payloadContentType = "application/json",
        payloadSha256Checksum = "checksum",
        userSubmissionTimestamp = initialNow.toString,
        identityData = Json.obj(
          "internalId" -> "internal-id"
        ),
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

  private val nrsSubmissionWorkItem =
    NrsSubmissionWorkItem(
      nrsSubmission = submission
    )

  override protected def beforeEach(): Unit =
    super.beforeEach()
    clock.set(initialNow)

  "NrsSubmissionWorkItemRepository" should:

    "create the updatedAt TTL index for terminal-failure retention" in:
      val indexNames =
        repository
          .collection
          .listIndexes()
          .toFuture()
          .futureValue
          .flatMap(
            _.get("name").map(_.asString().getValue)
          )

      indexNames should contain(
        "nrs-submission-work-items-updated-at-ttl"
      )

    "persist an initial retryable failure for retry 1 after ten minutes" in:
      val persisted =
        repository
          .enqueueRetryableFailure(submission)
          .futureValue

      persisted.item shouldBe nrsSubmissionWorkItem
      persisted.status shouldBe ProcessingStatus.ToDo
      persisted.failureCount shouldBe 0
      persisted.availableAt shouldBe
        initialNow.plusSeconds(10.minutes.toSeconds)

      /*
       * updatedAt is present on all WorkItems. This is also the field used by
       * the 28-day Mongo TTL index.
       */
      persisted.updatedAt shouldBe initialNow

    "persist an initial permanent failure without scheduling automatic retry" in:
      val persisted =
        repository
          .enqueuePermanentFailure(submission)
          .futureValue

      persisted.item shouldBe nrsSubmissionWorkItem
      persisted.status shouldBe ProcessingStatus.PermanentlyFailed
      persisted.failureCount shouldBe 0
      persisted.updatedAt shouldBe initialNow

      /*
       * The document is retained for the configured TTL period but is not
       * eligible for automated retry processing.
       */
      findStored(persisted.id).value.status shouldBe
        ProcessingStatus.PermanentlyFailed

      repository.pullDue().futureValue shouldBe None

    "atomically claim one due item and change it to InProgress" in:
      val stored =
        repository
          .pushNew(
            item = nrsSubmissionWorkItem,
            availableAt = initialNow.minusSeconds(1)
          )
          .futureValue

      val claimed =
        repository
          .pullDue()
          .futureValue
          .value

      claimed.id shouldBe stored.id
      claimed.item shouldBe nrsSubmissionWorkItem
      claimed.status shouldBe ProcessingStatus.InProgress
      claimed.updatedAt shouldBe initialNow

    "not claim an item before its availableAt time" in:
      repository
        .pushNew(
          item = nrsSubmissionWorkItem,
          availableAt = initialNow.plusSeconds(10.minutes.toSeconds)
        )
        .futureValue

      repository.pullDue().futureValue shouldBe None

    "not allow two concurrent calls to claim the same due item" in:
      repository
        .pushNew(
          item = nrsSubmissionWorkItem,
          availableAt = initialNow.minusSeconds(1)
        )
        .futureValue

      /*
       * pullOutstanding uses Mongo findOneAndUpdate to atomically set the
       * selected item to InProgress.
       */
      val claims =
        Future
          .sequence(
            Seq(
              repository.pullDue(),
              repository.pullDue()
            )
          )
          .futureValue

      claims.flatten should have size 1
      claims.flatten.head.status shouldBe ProcessingStatus.InProgress

    "schedule retry 2 twenty minutes after retry 1 fails" in:
      val claimed =
        createAndClaimDueWorkItem()

      /*
       * failureCount = 0 means retry 1 has just been attempted.
       */
      claimed.failureCount shouldBe 0

      repository
        .markRetryableFailure(claimed)
        .futureValue shouldBe true

      val stored =
        findStored(claimed.id).value

      stored.status shouldBe ProcessingStatus.Failed
      stored.failureCount shouldBe 1
      stored.updatedAt shouldBe initialNow

      /*
       * Retry 1 failed, so schedule retry 2:
       *
       * 10 minutes × 2^(2 - 1) = 20 minutes.
       */
      stored.availableAt shouldBe
        initialNow.plusSeconds(20.minutes.toSeconds)

    "schedule retry 10 after retry 9 fails" in:
      val claimed =
        createAndClaimDueWorkItem()

      /*
       * Set failureCount = 8, meaning retry 9 is the retry that has failed.
       */
      setFailureCount(
        id = claimed.id,
        value = 8
      )

      val retryNineWorkItem =
        claimed.copy(
          failureCount = 8
        )

      repository
        .markRetryableFailure(retryNineWorkItem)
        .futureValue shouldBe true

      val stored =
        findStored(claimed.id).value

      stored.status shouldBe ProcessingStatus.Failed
      stored.failureCount shouldBe 9
      stored.updatedAt shouldBe initialNow

      /*
       * Retry 9 failed, so retry 10 is scheduled:
       *
       * 10 minutes × 2^(10 - 1) = 5120 minutes.
       */
      stored.availableAt shouldBe
        initialNow.plusSeconds(5120.minutes.toSeconds)

    "mark the record PermanentlyFailed after retry 10 fails without scheduling retry 11" in:
      val claimed =
        createAndClaimDueWorkItem()

      /*
       * failureCount = 9 means retry 10 is the current retry attempt.
       */
      setFailureCount(
        id = claimed.id,
        value = 9
      )

      val retryTenWorkItem =
        claimed.copy(
          failureCount = 9
        )

      repository
        .markRetryableFailure(retryTenWorkItem)
        .futureValue shouldBe true

      val stored =
        findStored(claimed.id).value

      stored.status shouldBe ProcessingStatus.PermanentlyFailed
      stored.failureCount shouldBe 10
      stored.updatedAt shouldBe initialNow

      /*
       * No retry 11 is scheduled. The terminal updatedAt value is now the
       * start of the configured 28-day Mongo TTL retention period.
       */
      repository.pullDue().futureValue shouldBe None

      findStored(claimed.id).value shouldBe stored

    "mark a claimed item PermanentlyFailed after a non-retryable NRS response" in:
      val claimed =
        createAndClaimDueWorkItem()

      repository
        .markPermanentFailure(claimed)
        .futureValue shouldBe true

      val stored =
        findStored(claimed.id).value

      stored.status shouldBe ProcessingStatus.PermanentlyFailed
      stored.failureCount shouldBe 0
      stored.updatedAt shouldBe initialNow

      /*
       * This represents HTTP 422 or another non-retryable 4xx response.
       */
      repository.pullDue().futureValue shouldBe None

    "delete a claimed work item after a successful retry" in:
      val claimed =
        createAndClaimDueWorkItem()

      /*
       * NrsService calls this only after NrsConnector maps HTTP 202 Accepted
       * to NrsSubmissionResult.Success.
       */
      repository
        .completeAndDelete(claimed.id)
        .futureValue shouldBe true

      findStored(claimed.id) shouldBe None

  private def createAndClaimDueWorkItem()
  : WorkItem[NrsSubmissionWorkItem] =
    repository
      .pushNew(
        item = nrsSubmissionWorkItem,
        availableAt = initialNow.minusSeconds(1)
      )
      .futureValue

    repository
      .pullDue()
      .futureValue
      .value

  private def findStored(
                          id: ObjectId
                        ): Option[WorkItem[NrsSubmissionWorkItem]] =
    repository
      .collection
      .find(
        Filters.equal(
          WorkItemFields.default.id,
          id
        )
      )
      .toFuture()
      .futureValue
      .headOption

  private def setFailureCount(
                               id: ObjectId,
                               value: Int
                             ): Unit =
    repository
      .collection
      .updateOne(
        Filters.equal(
          WorkItemFields.default.id,
          id
        ),
        Updates.set(
          WorkItemFields.default.failureCount,
          value
        )
      )
      .toFuture()
      .futureValue

private class MutableClock(
                            initial: Instant
                          ) extends Clock:

  private var current =
    initial

  def set(
           value: Instant
         ): Unit =
    current = value

  override def getZone: ZoneId =
    ZoneOffset.UTC

  override def withZone(
                         zone: ZoneId
                       ): Clock =
    this

  override def instant(): Instant =
    current
