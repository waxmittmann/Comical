import scala.concurrent.duration.Duration

import mockws.MockWS
import org.scalatestplus.play.PlaySpec
import play.api.mvc.Action
import services.{ComicsService, MarvelService, MarvelServiceImpl}
import org.scalatest.{FreeSpec, Matchers, OptionValues}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.WSClient
import play.api.mvc.Action
import play.api.mvc.Results._
import play.api.test.Helpers._
import scala.concurrent.{Await, Future}
import scala.util.Try

import org.mockito.Mockito._
import org.scalatest._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play._
import services.ComicsService.ComicQueryResult

class ComicsServiceSpec extends PlaySpec {

  "ComicsService" should {
    "pickle a george" in {
      //Given
      val mockMarvelService: MarvelService = MockitoSugar.mock[MarvelService]
      val apiKeysPart: String = "?apiKeysPart=something"
      when(mockMarvelService.apiKeysUrlPart) thenReturn apiKeysPart

      val comic1Url = "http://gateway.marvel.com:80/v1/public/comics/1" + apiKeysPart
      val comic2Url = "http://gateway.marvel.com:80/v1/public/comics/2" + apiKeysPart

      val ws = MockWS {
        case (GET, comic1Url) => Action { Ok("http response") }
        case (GET, comic2Url) => Action { Ok("http response") }
      }

      val comicsService = new ComicsService(ws, mockMarvelService)(play.api.libs.concurrent.Execution.Implicits.defaultContext)
      val expectedResult = Set(

      )

      //When
      val result: Set[ComicQueryResult] = Await.result(comicsService.get(List(1, 2, 3)), Duration.Inf).toSet
      println("Result size: " + result.size)

      //Then
      result mustEqual(expectedResult)
    }
  }
}
