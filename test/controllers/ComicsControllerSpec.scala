package controllers

import scala.concurrent.Future

import org.scalatestplus.play.PlaySpec
import services.ComicsServiceImpl
import org.scalatest.mock.MockitoSugar
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.libs.concurrent.Execution.Implicits._
import org.mockito.Mockito._
import play.api.libs.json.{JsArray, JsBoolean, JsNumber, JsObject, JsString, JsValue}
import play.api.test.Helpers._
import services.ComicsService.{FoundInCache, MalformedJson, NotFound, WrongJsonSchema}

class ComicsControllerSpec extends PlaySpec {
  trait Context {
    val apiConfig = play.api.Configuration.from(Map(
      "comical.maxQueriesPerRequest" -> 50
    ))

    val mockComicsService = MockitoSugar.mock[ComicsServiceImpl]
    val comicsController = new ComicsController(apiConfig, mockComicsService)
  }

  "ComicsController GET" should {
    "return BadRequest if the comicsId parameter is not present in the url" in new Context {
      //Given
      val request = FakeRequest(
        method = "GET",
        path = "http://www.whatevs.com/comics"
      )

      //When
      val result = comicsController.comics()(request)

      //Then
      val content = contentAsString(result)
      val rStatus: Int = status(result)

      rStatus mustEqual(400)
      content mustEqual("Include a url-encoded comicIds parameter whose value is a list of comma-separated comic ids")
    }

    "return BadRequest if the comicsId parameter cannot be parsed to a list of integers" in new Context {
      //Given
      val request = FakeRequest(
        method = "GET",
        path = "http://www.whatevs.com/comics?comicIds=1a"
      )

      //When
      val result = comicsController.comics()(request)

      //Then
      val content = contentAsString(result)
      val rStatus: Int = status(result)

      rStatus mustEqual(400)
      content mustEqual("""comicIds (ArrayBuffer(1a)) could not be parsed to an array of ints: java.lang.NumberFormatException: For input string: "1a"""")
    }

    "return Ok and a json result describing the result returned from ComicsService" in new Context {
      //Given
      val validComicJson = JsObject(Seq("a" -> JsNumber(1)))
      val comicResult = Seq(
        FoundInCache(1, validComicJson),
        NotFound(2),
        MalformedJson(3, "Malformed!"),
        WrongJsonSchema(4, JsObject(Seq("b" -> JsString("wrong"))))
      )
      when(mockComicsService.get(Seq(1,2,3,4))) thenReturn(Future.successful(comicResult))

      val expectedJson =
        JsObject(Seq(
          "data" -> JsArray(Seq(validComicJson)),
          "notFound" -> JsArray(Seq(JsNumber(2))),
          "malformedJson" -> JsArray(Seq(JsNumber(3))),
          "badJsonSchema" -> JsArray(Seq(JsNumber(4))),
          "success" -> JsBoolean(false)
        ))

      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(
        method = "GET",
        path = "http://www.whatevs.com/comics?comicIds=1,2,3,4".replace(",", "%2C")
      )

      //When
      val result = comicsController.comics()(request)

      //Then
      val content: JsValue = contentAsJson(result)
      val rStatus: Int = status(result)

      rStatus mustEqual(200)
      content mustEqual(expectedJson)
    }
  }

  //Todo: Test for sensible (ie 500) response if ComicsService returns a failed
  //future
}