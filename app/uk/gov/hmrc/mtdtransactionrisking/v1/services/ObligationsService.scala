package uk.gov.hmrc.mtdtransactionrisking.v1.services

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.ObligationsConnector
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.ObligationsRequest
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.ObligationsResponse

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class ObligationsService @Inject()(connector: ObligationsConnector):

  def getObligations(
                       request: ObligationsRequest
                     )(implicit hc: HeaderCarrier, correlationId: CorrelationId): Future[ServiceOutcome[ObligationsResponse]] =
    connector.getObligations(request)