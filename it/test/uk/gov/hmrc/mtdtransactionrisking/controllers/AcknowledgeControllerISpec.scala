package uk.gov.hmrc.mtdtransactionrisking.controllers

import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, defaultAwaitTimeout, headers, status}
import uk.gov.hmrc.http.SessionKeys.authToken
import uk.gov.hmrc.mtdtransactionrisking.stubs.{AcknowledgeStub, AuthStub, CommonTestData}
import uk.gov.hmrc.mtdtransactionrisking.support.IntegrationBaseSpec
import uk.gov.hmrc.mtdtransactionrisking.v1.controllers.AcknowledgeController

import scala.concurrent.Future


class AcknowledgeControllerISpec extends IntegrationBaseSpec:
  "AcknowledgeController" when :

    "POST /acknowledge/:vrn/:reportId/:correlationId" should :

      "return 204 " when :
        "a valid VRN is provided and acknowledge responds successfully" in new Test:
          override def setupStubs(): StubMapping = {
            AuthStub.successfulAuthWith(vrn)
            AcknowledgeStub.successResponse(vrn, reportId, correlationId, presentedDateTime)
          }

          val response: Future[Result] = request(vrn)
          status(response) shouldBe 204
          headers(response).get("X-CorrelationId") shouldBe defined
          contentAsString(response) shouldBe ""



  private trait Test:

      def vrn: String = CommonTestData.simpleVrn
      def reportId: String = "123456aB-012A-345B-678C-012345678abc"
      def correlationId: String = "a1e8057e-fbbc-47a8-a8b4-78d9f015c253"
      def presentedDateTime: String = "2023-06-01T12:00:00Z"


      def setupStubs(): StubMapping


      def request(VRN: String = vrn, Report_Id: String = reportId, Correlation_Id: String = correlationId, Presented_DateTime: String = presentedDateTime): Future[Result] =
        setupStubs()
        app.injector.instanceOf[AcknowledgeController].acknowledgeReport(vrn, reportId, correlationId).apply(
          FakeRequest("POST", s"/acknowledge/$VRN/$Report_Id/$Correlation_Id?presentedDateTime=$presentedDateTime")
            .withSession(authToken -> vrn)
            .withHeaders(
              "Authorization" -> "Bearer abc123",
              "Accept" -> "application/vnd.hmrc.1.0+json",
              "Content-Type" -> "application/json"
            )

        )

      def requestWithoutPresentDateTime(vrn: String): Future[Result] =
        setupStubs()
        app.injector.instanceOf[AcknowledgeController].acknowledgeReport(vrn, reportId, correlationId).apply(
          FakeRequest("POST", s"/acknowledge/$vrn/$reportId/$correlationId")
            .withSession(authToken -> vrn)
            .withHeaders(
              "Authorization" -> "Bearer abc123",
              "Accept" -> "application/vnd.hmrc.1.0+json",
              "Content-Type" -> "application/json"
            )

        )