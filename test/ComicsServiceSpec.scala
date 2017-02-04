import scala.concurrent.duration.Duration

import mockws.MockWS
import org.scalatestplus.play.PlaySpec
import play.api.mvc.{Action, EssentialAction}
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
import services.{ComicsService, MarvelService}
import services.ComicsService.ComicQueryResult

class ComicsServiceSpec extends PlaySpec {

  val apiKeysPart: String = "apiKeysPart=something"

  /**
    * Utility methods
    */
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

  val mockMarvelService: MarvelService = {
    val mockMarvelService = MockitoSugar.mock[MarvelService]
    when(mockMarvelService.apiKeysUrlPart) thenReturn apiKeysPart
    mockMarvelService
  }

  def comicsService(ws: MockWS) =
    new ComicsService(ws, mockMarvelService)(play.api.libs.concurrent.Execution.Implicits.defaultContext)

  def comicUrl(id: Int) =
    s"http://gateway.marvel.com:80/v1/public/comics/$id?" + apiKeysPart

  case class MarvelResponse(jsonResponse: JsObject, queryPartOnly: JsObject)

  sealed trait MockResponseStatus
  case object OK extends MockResponseStatus

  case class MockResponse(
    comicId: Int,
    status: MockResponseStatus
  )

  //def mockWs(responses: Seq[MockResponse]): MockWS = {
  def mockWs(responses: Map[String, (String, MockResponseStatus)]): MockWS = {
    val ws: MockWS = MockWS {
      case (GET, url) => {
        val responseData = responses.get(url).get
        Action {
          responseData._2 match {
            case OK => Ok(responseData._1)
          }
        }
      }
    }
    ws
  }

  /**
    * Tests
    */
  "ComicsService" should {
    "correctly handle 200 responses containing well-formed json containing a data.results attribute" in {
      //Given
      val (comicId1, comicId2, comicId3) = (1, 2, 3)

      //Todo: See if we can make this cleaner later
      val (comic1Url, comic2Url, comic3Url) =
        (comicUrl(comicId1), comicUrl(comicId2), comicUrl(comicId3))

      val (validJson1, validJson2, validJson3) =
        (createValidJson(comicId1), createValidJson(comicId2), createValidJson(comicId3))

//      val comic1Url = "http://gateway.marvel.com:80/v1/public/comics/1?" + apiKeysPart
//      val comic2Url = "http://gateway.marvel.com:80/v1/public/comics/2?" + apiKeysPart
//      val comic3Url = "http://gateway.marvel.com:80/v1/public/comics/3?" + apiKeysPart

//      val validJson1 = createValidJson(1)
//      val validJson2 = createValidJson(2)
//      val validJson3 = createValidJson(3)

      val ws: MockWS = MockWS {
        case (GET, `comic1Url`) => Action { Ok(validJson1.jsonResponse) }
        case (GET, `comic2Url`) => Action { Ok(validJson2.jsonResponse) }
        case (GET, `comic3Url`) => Action { Ok(validJson3.jsonResponse) }
      }

//      val ws: MockWS = mockWs(Map(
//        comic1Url -> (validJson1.jsonResponse.toString(), OK),
//        comic2Url -> (validJson2.jsonResponse.toString(), OK),
//        comic3Url -> (validJson3.jsonResponse.toString(), OK)
//      ))

      val expectedResult = Set(
        ComicsService.Found(JsDefined(validJson1.queryPartOnly)),
        ComicsService.Found(JsDefined(validJson2.queryPartOnly)),
        ComicsService.Found(JsDefined(validJson3.queryPartOnly))
      )

      //When
      val result = Await.result(comicsService(ws).get(List(comicId1, comicId2, comicId3)), Duration.Inf).toSet

      //Then
      result mustEqual(expectedResult)
    }
  }

  "return BadJson for 200 responses containing well-formed json missing the data.results attribute" in {
    //Given

    val comic1Url = "http://gateway.marvel.com:80/v1/public/comics/1?" + apiKeysPart
    val comic2Url = "http://gateway.marvel.com:80/v1/public/comics/2?" + apiKeysPart

    val validJson   = createValidJson(1)
    val jsonLackingResult = JsObject(Seq("someAttribute" -> JsNumber(1)))

    val ws: MockWS = MockWS {
      case (GET, `comic1Url`) => Action { Ok(validJson.jsonResponse) }
      case (GET, `comic2Url`) => Action { Ok(jsonLackingResult) }
    }

    val comicsService = new ComicsService(ws, mockMarvelService)(play.api.libs.concurrent.Execution.Implicits.defaultContext)
    val expectedResult = Set(
      ComicsService.Found(JsDefined(validJson.queryPartOnly)),
      ComicsService.WrongJsonSchema(2, JsDefined(jsonLackingResult))
    )

    //When
    val result = Await.result(comicsService.get(List(1, 2)), Duration.Inf).toSet

    //Then
    result mustEqual(expectedResult)
  }

  "return NotFound for 404 responses" in {
    //Given

    val comic1Url = "http://gateway.marvel.com:80/v1/public/comics/1?" + apiKeysPart
    val comic2Url = "http://gateway.marvel.com:80/v1/public/comics/2?" + apiKeysPart

    val validJson   = createValidJson(1)

    val ws: MockWS = MockWS {
      case (GET, `comic1Url`) => Action { Ok(validJson.jsonResponse) }
      case (GET, `comic2Url`) => Action { NotFound("") }
    }

    val comicsService = new ComicsService(ws, mockMarvelService)(play.api.libs.concurrent.Execution.Implicits.defaultContext)
    val expectedResult = Set(
      ComicsService.Found(JsDefined(validJson.queryPartOnly)),
      ComicsService.NotFound(2)
    )

    //When
    val result = Await.result(comicsService.get(List(1, 2)), Duration.Inf).toSet

    //Then
    result mustEqual(expectedResult)
  }

  "return Failed for requests that contain malformed json" in {
    //Given

    val comic1Url = "http://gateway.marvel.com:80/v1/public/comics/1?" + apiKeysPart
    val comic2Url = "http://gateway.marvel.com:80/v1/public/comics/2?" + apiKeysPart

    val validJson   = createValidJson(1)
    val invalidJson = "ThisAintJson"

    val ws: MockWS = MockWS {
      case (GET, `comic1Url`) => Action { Ok(validJson.jsonResponse) }
      case (GET, `comic2Url`) => Action { Ok(invalidJson) }
    }

    val comicsService = new ComicsService(ws, mockMarvelService)(play.api.libs.concurrent.Execution.Implicits.defaultContext)
    val expectedResult = Set(
      ComicsService.Found(JsDefined(validJson.queryPartOnly)),
      ComicsService.Failed(2, invalidJson)
    )

    //When
    val result = Await.result(comicsService.get(List(1, 2)), Duration.Inf).toSet

    //Then
    result mustEqual(expectedResult)
  }
}
