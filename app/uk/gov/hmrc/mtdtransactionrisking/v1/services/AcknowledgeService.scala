package uk.gov.hmrc.mtdtransactionrisking.v1.services


import cats.data.EitherT
import cats.implicits.*
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.RdsConnector
import uk.gov.hmrc.mtdtransactionrisking.v1.models.outcomes.ResponseWrapper
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.AcknowledgeRequest
import uk.gov.hmrc.mtdtransactionrisking.v1.services.auth.RdsAuthService

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AcknowledgeService @Inject() (
                                         rdsAuthService: RdsAuthService,
                                         rdsConnector: RdsConnector
                                       )(implicit ec: ExecutionContext):

  def acknowledge(request: AcknowledgeRequest)(implicit hc: HeaderCarrier, correlationId: CorrelationId): Future[ServiceOutcome[Unit]] =
    val result = for
      credentials <- EitherT(rdsAuthService.bearerToken())
      _           <- EitherT(rdsConnector.acknowledge(request, credentials.responseData))
    yield ResponseWrapper(correlationId, ())

    result.value