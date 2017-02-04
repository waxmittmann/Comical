package controllers

import scala.concurrent.Future

import org.scalatestplus.play.PlaySpec
import services.{ComicsService, ComicsServiceImpl}
import org.scalatest.mock.MockitoSugar
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import play.mvc.Http.RequestBuilder
import play.api.libs.concurrent.Execution.Implicits._
import org.mockito.Mockito._

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
//      val rb = (new RequestBuilder()).uri("http://www.dontcare.com/comics?comicIds=1")

      when(mockComicsService.get(Seq(1))) thenReturn(Future.successful(Seq()))

      val req: Request[AnyContent] = FakeRequest(
        method = "GET",
        path = "http://www.whatevs.com/comics?comicIds=1"
      )

      val r = comicsController.comics()(req)
      println(s"R is: $r")
    }

    "return BadRequest if the comicsId parameter cannot be parsed to a list of integers" in {

    }

    "return Ok and a json result describing the result returned from ComicsService" in {

    }
  }
}