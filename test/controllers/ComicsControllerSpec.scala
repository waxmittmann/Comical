package controllers

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

import org.scalatestplus.play.PlaySpec
import services.{ComicsService, ComicsServiceImpl}
import org.scalatest.mock.MockitoSugar
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.FakeRequest
import play.mvc.Http.RequestBuilder
import play.api.libs.concurrent.Execution.Implicits._
import org.mockito.Mockito._
import play.api.http.HttpEntity
import play.api.http.HttpEntity.{Chunked, Streamed, Strict}
import play.api.test.Helpers._

class ComicsControllerSpec extends PlaySpec {
  def setup(): (ComicsService, ComicsController) = {
    val mockComicsService = MockitoSugar.mock[ComicsService]
    (mockComicsService, new ComicsController(mockComicsService))
  }

  trait Context {
    val mockComicsService = MockitoSugar.mock[ComicsServiceImpl]
    val comicsController = new ComicsController(mockComicsService)
  }

  "ComicsController GET" should {
    "return BadRequest if the comicsId parameter is not present in the url" in new Context {
      //Given
      when(mockComicsService.get(Seq(1))) thenReturn(Future.successful(Seq()))

      val req: Request[AnyContent] = FakeRequest(
        method = "GET",
        path = "http://www.whatevs.com/comics"
      )

      //When
      val result = comicsController.comics()(req)

      //Then
      val content = contentAsString(result)
      val rStatus: Int = status(result)

      rStatus mustEqual(400)
      content mustEqual("Include a url-encoded comicIds parameter whose value is a list of comma-separated comic ids")
    }

    "return BadRequest if the comicsId parameter cannot be parsed to a list of integers" in new Context {
      //Given
      when(mockComicsService.get(Seq(1))) thenReturn(Future.successful(Seq()))

      val req: Request[AnyContent] = FakeRequest(
        method = "GET",
        path = "http://www.whatevs.com/comics?comicIds=1a"
      )

      //When
      val result = comicsController.comics()(req)

      //Then
      val content = contentAsString(result)
      val rStatus: Int = status(result)

      rStatus mustEqual(400)
      content mustEqual("""comicIds (ArrayBuffer(1a)) could not be parsed to an array of ints: java.lang.NumberFormatException: For input string: "1a"""")
    }

    "return Ok and a json result describing the result returned from ComicsService" in new Context {
      when(mockComicsService.get(Seq(1))) thenReturn(Future.successful(Seq()))

      val req: Request[AnyContent] = FakeRequest(
        method = "GET",
        path = "http://www.whatevs.com/comics?comicIds=1"
      )

      val r = comicsController.comics()(req)
      println(s"R is: $r")
    }
  }
}