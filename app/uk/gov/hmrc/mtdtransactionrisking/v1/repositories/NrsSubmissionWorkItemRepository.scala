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

package uk.gov.hmrc.mtdtransactionrisking.v1.repositories

import org.bson.conversions.Bson
import org.mongodb.scala.model.Filters
import org.mongodb.scala.model.Indexes
import org.mongodb.scala.model.{IndexModel, IndexOptions, Updates}
import play.api.libs.json.OFormat
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem, WorkItemFields, WorkItemRepository}
import uk.gov.hmrc.mtdtransactionrisking.config.AppConfig
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.nrs.{NrsSubmission, NrsSubmissionWorkItem}
import uk.gov.hmrc.mtdtransactionrisking.v1.services.NrsRetryPolicy
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}

import java.time.{Clock, Duration, Instant}
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NrsSubmissionWorkItemRepository @Inject()(
                                                 mongoComponent: MongoComponent,
                                                 appConfig: AppConfig,
                                                 retryPolicy: NrsRetryPolicy,
                                                 clock: Clock
                                               )(using ec: ExecutionContext, crypto: Encrypter & Decrypter)
  extends WorkItemRepository[NrsSubmissionWorkItem](
    collectionName = "nrs-submission-work-items",
    mongoComponent = mongoComponent,
    itemFormat = summon[OFormat[NrsSubmissionWorkItem]],
    workItemFields = WorkItemFields.default,
    extraIndexes = Seq(
      IndexModel(
        Indexes.ascending(WorkItemFields.default.updatedAt),
        IndexOptions()
          .name("nrs-submission-work-items-updated-at-ttl")
          .expireAfter(appConfig.nrsRetryRetention.toSeconds, TimeUnit.SECONDS)
      )
    )
  ) with NrsSubmissionWorkItemStore:

  override def now(): Instant =
    Instant.now(clock)

  override val inProgressRetryAfter: Duration =
    Duration.ofMillis(appConfig.nrsRetryInProgressRetryAfter.toMillis)

  override lazy val requiresTtlIndex: Boolean =
    true

  def enqueueRetryableFailure(
                               submission: NrsSubmission,
                               correlationId: CorrelationId
                             ): Future[WorkItem[NrsSubmissionWorkItem]] =
    pushNew(
      item = NrsSubmissionWorkItem(nrsSubmission = submission, correlationId = correlationId),
      availableAt = now().plusMillis(retryPolicy.delayForRetry(1).toMillis)
    )

  def enqueuePermanentFailure(
                               submission: NrsSubmission,
                               correlationId: CorrelationId
                             ): Future[WorkItem[NrsSubmissionWorkItem]] =
    pushNew(
      item = NrsSubmissionWorkItem(nrsSubmission = submission, correlationId = correlationId),
      availableAt = now(),
      initialState = _ => ProcessingStatus.PermanentlyFailed
    )

  def pullDue(): Future[Option[WorkItem[NrsSubmissionWorkItem]]] =
    pullOutstanding(
      failedBefore = now(),
      availableBefore = now()
    )

  def markRetryableFailure(
                            workItem: WorkItem[NrsSubmissionWorkItem]
                          ): Future[Boolean] =
    val completedRetryNumber =
      workItem.failureCount + 1

    val retriesExhausted =
      retryPolicy.isExhaustedAfterFailure(completedRetryNumber)

    val status =
      if retriesExhausted then ProcessingStatus.PermanentlyFailed
      else ProcessingStatus.Failed

    val updates =
      Seq[Bson](
        Updates.set(WorkItemFields.default.status, status),
        Updates.set(WorkItemFields.default.updatedAt, Date.from(now())),
        Updates.inc(WorkItemFields.default.failureCount, 1)
      ) ++
        Option.when(!retriesExhausted) {
          val nextRetryNumber =
            completedRetryNumber + 1

          val nextAvailableAt =
            now().plusMillis(
              retryPolicy.delayForRetry(nextRetryNumber).toMillis
            )

          Updates.set(
            WorkItemFields.default.availableAt,
            Date.from(nextAvailableAt)
          )
        }

    collection
      .updateOne(
        Filters.and(
          Filters.equal(WorkItemFields.default.id, workItem.id),
          Filters.equal(
            WorkItemFields.default.status,
            ProcessingStatus.InProgress
          )
        ),
        Updates.combine(updates *)
      )
      .toFuture()
      .map(_.getModifiedCount == 1)

  def markPermanentFailure(
                            workItem: WorkItem[NrsSubmissionWorkItem]
                          ): Future[Boolean] =
    collection
      .updateOne(
        Filters.and(
          Filters.equal(WorkItemFields.default.id, workItem.id),
          Filters.equal(
            WorkItemFields.default.status,
            ProcessingStatus.InProgress
          )
        ),
        Updates.combine(
          Updates.set(
            WorkItemFields.default.status,
            ProcessingStatus.PermanentlyFailed
          ),
          Updates.set(
            WorkItemFields.default.updatedAt,
            Date.from(now())
          )
        )
      )
      .toFuture()
      .map(_.getModifiedCount == 1)
