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
import play.api.libs.json.{JsArray, JsDefined, JsNumber, JsObject, JsString}
import play.libs.Json
import services.ComicsService.ComicQueryResult

class ComicsServiceSpec extends PlaySpec {

  "ComicsService" should {
    "pickle a george" in {
      //Given
      val mockMarvelService: MarvelService = MockitoSugar.mock[MarvelService]
      val apiKeysPart: String = "apiKeysPart=something"
      when(mockMarvelService.apiKeysUrlPart) thenReturn apiKeysPart

      val comic1Url = "http://gateway.marvel.com:80/v1/public/comics/1?" + apiKeysPart
      val comic2Url = "http://gateway.marvel.com:80/v1/public/comics/2?" + apiKeysPart
      val comic3Url = "http://gateway.marvel.com:80/v1/public/comics/3?" + apiKeysPart

      //jsonBody \ "data" \ "results" \ 0
      val validJson1 = createValidJson(1)
      val validJson2 = createValidJson(2)
      val validJson3 = createValidJson(3)

      val ws = MockWS {
        case (GET, `comic1Url`) => Action { Ok(validJson1.jsonResponse) }
        case (GET, `comic2Url`) => Action { Ok(validJson2.jsonResponse) }
        case (GET, `comic3Url`) => Action { Ok(validJson3.jsonResponse) }
      }

      val comicsService = new ComicsService(ws, mockMarvelService)(play.api.libs.concurrent.Execution.Implicits.defaultContext)
      val expectedResult = Set(
        ComicsService.Found(JsDefined(validJson1.queryPartOnly)),
        ComicsService.Found(JsDefined(validJson2.queryPartOnly)),
        ComicsService.Found(JsDefined(validJson3.queryPartOnly))
      )

      //When
      val result: Set[ComicQueryResult] = Await.result(comicsService.get(List(1, 2, 3)), Duration.Inf).toSet
      println("Result size: " + result.size)

      //Then
      result mustEqual(expectedResult)
    }
  }

  case class MarvelResponse(jsonResponse: JsObject, queryPartOnly: JsObject)

  def createValidJson(uniqueValue: Int): MarvelResponse = {
    val results =
      JsObject(Seq(
        "fieldA" -> JsString("Hello"),
        "fieldB" -> JsNumber(uniqueValue)
      ))

    val jsonResponse = JsObject(Seq(
      "someField" -> JsString("SomeVal"),
      "otherThing" -> JsArray(Seq(JsNumber(1), JsNumber(2))),
      "data" -> JsObject(Seq(
        "results" -> JsArray(Seq(results))
      ))
    ))

    MarvelResponse(jsonResponse, results)
  }
}
