//package uk.gov.hmrc.mtdtransactionrisking.v1.services
//
//import cats.data.EitherT
//import org.mockito.ArgumentMatchers.{any, eq as eqTo}
//import org.mockito.Mockito.when
//import org.scalatest.matchers.should.Matchers
//import org.scalatest.wordspec.AnyWordSpec
//import org.scalatestplus.mockito.MockitoSugar
//import uk.gov.hmrc.http.HeaderCarrier
//import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.AcknowledgeConnector
//import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.AcknowledgeConnector.AcknowledgeConnectorError
//import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.AcknowledgeRequest
//import uk.gov.hmrc.mtdtransactionrisking.v1.services.AcknowledgeService.{ClientOrAuthError, InternalServiceError}
//
//import java.time.OffsetDateTime
//import scala.concurrent.ExecutionContext.Implicits.global
//import scala.concurrent.duration.*
//import scala.concurrent.{Await, Future}
//
//class AcknowledgeServiceSpec extends AnyWordSpec with Matchers with MockitoSugar:
//
//  implicit val hc: HeaderCarrier = HeaderCarrier()
//
//  private val acknowledgeRequest = AcknowledgeRequest(
//    vrn = "123456789",
//    reportId = "123456aB-012A-345B-678C-012345678abc",
//    correlationId = "a1e8057e-fbbc-47a8-a8b4-78d9f015c253",
//    presentedDateTime = OffsetDateTime.parse("2023-06-01T12:00:00Z")
//  )
//
//  private def await[T](f: Future[T]): T =
//    Await.result(f, 5.seconds)
//
//  private def rightT: EitherT[Future, AcknowledgeConnectorError, Unit] =
//    EitherT.rightT(())
//
//  private def leftT(status: Int, code: String, message: String): EitherT[Future, AcknowledgeConnectorError, Unit] =
//    EitherT.leftT(AcknowledgeConnectorError(status, code, message))
//
//  class Test:
//    val mockConnector: AcknowledgeConnector = mock[AcknowledgeConnector]
//    val service = new AcknowledgeService(mockConnector)
//
//  "AcknowledgeService" when:
//
//    "acknowledge is called and the connector succeeds" must:
//      "return Right(())" in new Test:
//        when(mockConnector.acknowledge(eqTo(acknowledgeRequest))(any()))
//          .thenReturn(rightT)
//
//        val result = await(service.acknowledge(acknowledgeRequest).value)
//
//        result shouldBe Right(())
//
//    "acknowledge is called and the connector returns a 400, 401, 403 or 404" must:
//      "return a ClientOrAuthError with the same status, code and message" in new Test:
//        Seq(400, 401, 403, 404).foreach { status =>
//          when(mockConnector.acknowledge(eqTo(acknowledgeRequest))(any()))
//            .thenReturn(leftT(status, "SOME_CODE", "Some message"))
//
//          val result = await(service.acknowledge(acknowledgeRequest).value)
//
//          result shouldBe Left(ClientOrAuthError(status, "SOME_CODE", "Some message"))
//        }
//
//    "acknowledge is called and the connector returns a non pass-through error" must:
//      "return InternalServiceError" in new Test:
//        when(mockConnector.acknowledge(eqTo(acknowledgeRequest))(any()))
//          .thenReturn(leftT(500, "INTERNAL_SERVER_ERROR", "An unexpected error occurred."))
//
//        val result = await(service.acknowledge(acknowledgeRequest).value)
//
//        result shouldBe Left(InternalServiceError)



package uk.gov.hmrc.mtdtransactionrisking.v1.services

import cats.data.EitherT
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.AcknowledgeConnector
import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.AcknowledgeConnector.AcknowledgeConnectorError
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.AcknowledgeRequest
import uk.gov.hmrc.mtdtransactionrisking.v1.services.AcknowledgeService.{ClientOrAuthError, InternalServiceError}

import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

class AcknowledgeServiceSpec extends AnyWordSpec with Matchers with MockitoSugar:

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val acknowledgeRequest = AcknowledgeRequest(
    vrn = "123456789",
    reportId = "123456aB-012A-345B-678C-012345678abc",
    correlationId = "a1e8057e-fbbc-47a8-a8b4-78d9f015c253",
    presentedDateTime = OffsetDateTime.parse("2023-06-01T12:00:00Z")
  )

  private def await[T](f: Future[T]): T = Await.result(f, 5.seconds)

  private def rightT: EitherT[Future, AcknowledgeConnectorError, Unit] =
    EitherT.rightT(())

  private def leftT(error: AcknowledgeConnectorError): EitherT[Future, AcknowledgeConnectorError, Unit] =
    EitherT.leftT(error)

  class Test:
    val mockConnector: AcknowledgeConnector = mock[AcknowledgeConnector]
    val service = new AcknowledgeService(mockConnector)

  "AcknowledgeService" when:

    "acknowledge is called and the connector succeeds" must:
      "return Right(())" in new Test:
        when(mockConnector.acknowledge(eqTo(acknowledgeRequest))(any()))
          .thenReturn(rightT)

        val result: Either[AcknowledgeService.AcknowledgeServiceError, Unit] =
          await(service.acknowledge(acknowledgeRequest).value)

        result shouldBe Right(())

    "acknowledge is called and the connector returns a 400 error" must:
      "return a ClientOrAuthError" in new Test:
        when(mockConnector.acknowledge(eqTo(acknowledgeRequest))(any()))
          .thenReturn(leftT(AcknowledgeConnectorError(400, "FORMAT_VRN", "The provided Vrn is invalid.")))

        val result: Either[AcknowledgeService.AcknowledgeServiceError, Unit] =
          await(service.acknowledge(acknowledgeRequest).value)

        result shouldBe Left(ClientOrAuthError(400, "FORMAT_VRN", "The provided Vrn is invalid."))

    "acknowledge is called and the connector returns a 401 error" must:
      "return a ClientOrAuthError" in new Test:
        when(mockConnector.acknowledge(eqTo(acknowledgeRequest))(any()))
          .thenReturn(leftT(AcknowledgeConnectorError(401, "INVALID_CREDENTIALS", "Invalid authentication information provided.")))

        val result: Either[AcknowledgeService.AcknowledgeServiceError, Unit] =
          await(service.acknowledge(acknowledgeRequest).value)

        result shouldBe Left(ClientOrAuthError(401, "INVALID_CREDENTIALS", "Invalid authentication information provided."))

    "acknowledge is called and the connector returns a 403 error" must:
      "return a ClientOrAuthError" in new Test:
        when(mockConnector.acknowledge(eqTo(acknowledgeRequest))(any()))
          .thenReturn(leftT(AcknowledgeConnectorError(403, "NOT_AUTHORISED", "The client and/or agent is not authorised.")))

        val result: Either[AcknowledgeService.AcknowledgeServiceError, Unit] =
          await(service.acknowledge(acknowledgeRequest).value)

        result shouldBe Left(ClientOrAuthError(403, "NOT_AUTHORISED", "The client and/or agent is not authorised."))

    "acknowledge is called and the connector returns a 404 error" must:
      "return a ClientOrAuthError" in new Test:
        when(mockConnector.acknowledge(eqTo(acknowledgeRequest))(any()))
          .thenReturn(leftT(AcknowledgeConnectorError(404, "MATCHING_RESOURCE_NOT_FOUND", "A matching resource was not found.")))

        val result: Either[AcknowledgeService.AcknowledgeServiceError, Unit] =
          await(service.acknowledge(acknowledgeRequest).value)

        result shouldBe Left(ClientOrAuthError(404, "MATCHING_RESOURCE_NOT_FOUND", "A matching resource was not found."))

    "acknowledge is called and the connector returns a 500 error" must:
      "return InternalServiceError" in new Test:
        when(mockConnector.acknowledge(eqTo(acknowledgeRequest))(any()))
          .thenReturn(leftT(AcknowledgeConnectorError(500, "INTERNAL_SERVER_ERROR", "An unexpected error occurred.")))

        val result: Either[AcknowledgeService.AcknowledgeServiceError, Unit] =
          await(service.acknowledge(acknowledgeRequest).value)

        result shouldBe Left(InternalServiceError)