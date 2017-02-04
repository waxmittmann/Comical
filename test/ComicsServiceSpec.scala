import scala.concurrent.duration.Duration

import mockws.MockWS
import org.scalatestplus.play.PlaySpec
import play.api.mvc.Action
import services.ComicsService
import org.scalatest.{FreeSpec, Matchers, OptionValues}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.WSClient
import play.api.mvc.Action
import play.api.mvc.Results._
import play.api.test.Helpers._
import scala.concurrent.{Await, Future}
import scala.util.Try

import services.ComicsService.ComicQueryResult

class ComicsServiceSpec extends PlaySpec {

  "ComicsService" should {
    "pickle a george" in {
      //Given
      val ws = MockWS {
        case (GET, )


        case (GET, "http://dns/url") => Action { Ok("http response") }
        case (GET, "http://dns/url") => Action { Ok("http response") }
      }

      val comicsService = new ComicsService(ws)(play.api.libs.concurrent.Execution.Implicits.defaultContext)
      val expectedResult = Set(

      )

      //When
      val result: Set[ComicQueryResult] = Await.result(comicsService.get(List(1, 2, 3)), Duration.Inf).toSet

      //Then
      result mustEqual(expectedResult)
    }
  }
}
