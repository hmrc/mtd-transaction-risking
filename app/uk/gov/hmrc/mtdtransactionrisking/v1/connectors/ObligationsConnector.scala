package uk.gov.hmrc.mtdtransactionrisking.v1.connectors

import play.api.Logging
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.mtdtransactionrisking.config.AppConfig
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.{DownstreamError, ErrorWrapper, TaxPeriodNotEndedError}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.outcomes.ResponseWrapper
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.ObligationsRequest
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.ObligationsResponse
import uk.gov.hmrc.mtdtransactionrisking.v1.services.ServiceOutcome

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}


class ObligationsConnector @Inject()(
                                      val httpClient: HttpClientV2,
                                      appConfig: AppConfig
                                    )(implicit val ec: ExecutionContext) extends Logging:

  private def requiredHeaders(correlationId: CorrelationId, appName: String)(implicit hc: HeaderCarrier): Seq[(String, String)] =
    Seq(
      "User-Agent"       -> appName,
      "Content-Type"     -> "application/json",
      "X-Correlation-Id" -> correlationId.value
    )


  def getObligations(request: ObligationsRequest)
                    (implicit hc: HeaderCarrier, correlationId: CorrelationId): Future[ServiceOutcome[ObligationsResponse]] =
    logger.debug(s"${correlationId.value}::[ObligationsConnector:getObligations] calling obligations API")


    httpClient
      .post(url"${appConfig.obligationsServiceUrl(request.VRN)}")
      .setHeader(requiredHeaders(correlationId, appConfig.appName) *)
      .execute[HttpResponse]
      .map { response =>
        response.status match
          case 200 =>
            response.json.asOpt[ObligationsResponse] match
              case Some(obligations) =>
                obligations.findOpenObligationPeriod(request.periodKey) match
                  case Left(_) =>
                    logger.debug(s"${correlationId.value}::[ObligationsConnector:getObligations] tax period not ended")
                    Left(ErrorWrapper(correlationId, TaxPeriodNotEndedError))
                  case Right(_) =>
                    logger.debug(s"${correlationId.value}::[ObligationsConnector:getObligations] success")
                    Right(ResponseWrapper(correlationId, obligations))

              case None =>
                logger.error(s"${correlationId.value}::[ObligationsConnector:getObligations] malformed response")
                Left(ErrorWrapper(correlationId, DownstreamError))

          case status =>
            logger.error(s"${correlationId.value}::[ObligationsConnector:getObligations] failed status $status: ${response.body}")
            Left(ErrorWrapper(correlationId, DownstreamError))
      }
      .recover:
        case ex =>
          logger.error(s"${correlationId.value}::[ObligationsConnector:getObligations] unexpected exception", ex)
          Left(ErrorWrapper(correlationId, DownstreamError))

