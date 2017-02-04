package services

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Try}

import mockws.MockWS
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.cache.CacheApi
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{JsArray, JsNumber, JsObject, JsString, JsValue}
import play.api.mvc.Action
import play.api.mvc.Results._
import play.api.test.Helpers._
import org.mockito.Matchers.{eq => mockitoEq, _}

class ComicsServiceSpec extends PlaySpec {

  val apiKeysPart: String = "apiKeysPart=something"

  val alwaysEmptyCache: CacheApi = {
    val cache = MockitoSugar.mock[CacheApi]
    when(cache.get[JsValue](any[String]())(any[scala.reflect.ClassTag[play.api.libs.json.JsValue]])) thenReturn None
    cache
  }

  /**
    * Utility methods
    */
  def comicsService(ws: MockWS, mockCache: CacheApi = alwaysEmptyCache) = {
    new ComicsServiceImpl(ws, mockMarvelService, mockCache)(play.api.libs.concurrent.Execution.Implicits.defaultContext, null)
  }

  val mockMarvelService: MarvelService = {
    val mockMarvelService = MockitoSugar.mock[MarvelService]
    when(mockMarvelService.apiKeysUrlPart) thenReturn apiKeysPart
    mockMarvelService
  }

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

  def comicUrl(id: Int) =
    s"http://gateway.marvel.com:80/v1/public/comics/$id?" + apiKeysPart

  case class MarvelResponse(jsonResponse: JsObject, queryPartOnly: JsObject)

  sealed trait MockResponseStatus
  case object OK extends MockResponseStatus

  case class MockResponse(
    comicId: Int,
    status: MockResponseStatus
  )

  /**
    * Tests
    */
  "ComicsService" should {
    "without the cache" should {
      "correctly handle 200 responses containing well-formed json containing a data.results attribute" in {
        //Given
        val (comicId1, comicId2, comicId3) = (1, 2, 3)

        //Todo: See if we can make this cleaner later
        val (comic1Url, comic2Url, comic3Url) =
        (comicUrl(comicId1), comicUrl(comicId2), comicUrl(comicId3))

        val (validJson1, validJson2, validJson3) =
          (createValidJson(comicId1), createValidJson(comicId2), createValidJson(comicId3))

        val ws: MockWS = MockWS {
          case (GET, `comic1Url`) => Action {
            Ok(validJson1.jsonResponse)
          }
          case (GET, `comic2Url`) => Action {
            Ok(validJson2.jsonResponse)
          }
          case (GET, `comic3Url`) => Action {
            Ok(validJson3.jsonResponse)
          }
        }

        val expectedResult = Set(
          ComicsService.FoundRemotely(comicId1, validJson1.queryPartOnly),
          ComicsService.FoundRemotely(comicId2, validJson2.queryPartOnly),
          ComicsService.FoundRemotely(comicId3, validJson3.queryPartOnly)
        )

        //When
        val result = Await.result(comicsService(ws).get(List(comicId1, comicId2, comicId3)), Duration.Inf).toSet

        //Then
        result mustEqual (expectedResult)
      }
    }

    "return BadJson for 200 responses containing well-formed json missing the data.results attribute" in {
      //Given
      val comic1Url = "http://gateway.marvel.com:80/v1/public/comics/1?" + apiKeysPart
      val comic2Url = "http://gateway.marvel.com:80/v1/public/comics/2?" + apiKeysPart

      val validJson = createValidJson(1)
      val jsonLackingResult = JsObject(Seq("someAttribute" -> JsNumber(1)))

      val ws: MockWS = MockWS {
        case (GET, `comic1Url`) => Action {
          Ok(validJson.jsonResponse)
        }
        case (GET, `comic2Url`) => Action {
          Ok(jsonLackingResult)
        }
      }

      val expectedResult = Set(
        ComicsService.FoundRemotely(1, validJson.queryPartOnly),
        ComicsService.WrongJsonSchema(2, jsonLackingResult)
      )

      //When
      val result = Await.result(comicsService(ws).get(List(1, 2)), Duration.Inf).toSet

      //Then
      result mustEqual (expectedResult)
    }

    "return NotFound for 404 responses" in {
      //Given
      val comic1Url = "http://gateway.marvel.com:80/v1/public/comics/1?" + apiKeysPart
      val comic2Url = "http://gateway.marvel.com:80/v1/public/comics/2?" + apiKeysPart

      val validJson = createValidJson(1)

      val ws: MockWS = MockWS {
        case (GET, `comic1Url`) => Action {
          Ok(validJson.jsonResponse)
        }
        case (GET, `comic2Url`) => Action {
          NotFound("")
        }
      }

      val expectedResult = Set(
        ComicsService.FoundRemotely(1, validJson.queryPartOnly),
        ComicsService.NotFound(2)
      )

      //When
      val result = Await.result(comicsService(ws).get(List(1, 2)), Duration.Inf).toSet

      //Then
      result mustEqual (expectedResult)
    }

    "return Failed for requests that contain malformed json" in {
      //Given
      val (comicId1, comicId2) = (1, 2)

      val (comic1Url, comic2Url) = (comicUrl(comicId1), comicUrl(comicId2))

      val validJson = createValidJson(comicId1)
      val invalidJson = "ThisAintJson"

      val ws: MockWS = MockWS {
        case (GET, `comic1Url`) => Action {
          Ok(validJson.jsonResponse)
        }
        case (GET, `comic2Url`) => Action {
          Ok(invalidJson)
        }
      }

      val expectedResult = Set(
        ComicsService.FoundRemotely(comicId1, validJson.queryPartOnly),
        ComicsService.MalformedJson(comicId2, invalidJson)
      )

      //When
      val result = Await.result(comicsService(ws).get(List(comicId1, comicId2)), Duration.Inf).toSet

      //Then
      result mustEqual (expectedResult)
    }

    "return a failed future if one of the remote requests fails" in {
      //Given
      val comic1Url = "http://gateway.marvel.com:80/v1/public/comics/1?" + apiKeysPart
      val comic2Url = "http://gateway.marvel.com:80/v1/public/comics/2?" + apiKeysPart

      val validJson = createValidJson(1)
      val exception = new RuntimeException("Well that didn't work!")

      val ws: MockWS = MockWS {
        case (GET, `comic1Url`) => Action {
          Ok(validJson.jsonResponse)
        }
        case (GET, `comic2Url`) => Action.async {
          Future.failed(exception)
        }
      }

      //When / Then
      //Todo: Sure there's a nice utility method for this somewhere in the framework
      Try(Await.result(comicsService(ws).get(List(1, 2)), Duration.Inf)) mustEqual (Failure(exception))
    }
  }

  "serve Found items from the cache when available, and when not store the result in the cache" in {
    //Given
    val cachedJson =
      JsObject(Seq(
        "fieldA" -> JsString("Hello"),
        "fieldB" -> JsNumber(1)
      ))

    val cache = {
      val cache = MockitoSugar.mock[CacheApi]
      when(cache.get[JsValue](mockitoEq("1"))(any[scala.reflect.ClassTag[play.api.libs.json.JsValue]])) thenReturn Some(cachedJson)
      when(cache.get[JsValue](mockitoEq("2"))(any[scala.reflect.ClassTag[play.api.libs.json.JsValue]])) thenReturn None
      cache
    }

    val validJson = createValidJson(2)

    val ws = {
      val comic2Url = comicUrl(2)
      MockWS {
        case (GET, `comic2Url`) => Action {
          Ok(validJson.jsonResponse)
        }
      }
    }

    val expectedResult = Set(
      ComicsService.FoundInCache(1, cachedJson),
      ComicsService.FoundRemotely(2, validJson.queryPartOnly)
    )

    //When
    val result = Await.result(comicsService(ws, cache).get(List(1, 2)), Duration.Inf).toSet

    //Then
    result mustEqual (expectedResult)
    verify(cache, times(1)).set(mockitoEq("2"), mockitoEq(validJson.queryPartOnly), any())
    verify(cache, never()).set(mockitoEq("1"), any(), any())
  }
}
