package controllers

import akka.actor.ActorSystem
import javax.inject._

import play.api._
import play.api.mvc._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

import play.api.libs.json.{JsArray, JsBoolean, JsNumber, JsObject, JsValue}
import play.twirl.api.Html
import services.ComicsService
import services.ComicsService.{ComicQueryResult, MalformedJson, Found, NotFound, WrongJsonSchema}
import cats._
import cats.instances.all._
import cats.syntax.either._
import cats.instances.either._
/**
 * This controller creates an `Action` that demonstrates how to write
 * simple asynchronous code in a controller. It uses a timer to
 * asynchronously delay sending a response for 1 second.
 *
 * @param actorSystem We need the `ActorSystem`'s `Scheduler` to
 * run code after a delay.
 * @param exec We need an `ExecutionContext` to execute our
 * asynchronous code.
 */
@Singleton
class ComicsController @Inject() (actorSystem: ActorSystem, comicsService: ComicsService)(implicit exec: ExecutionContext) extends Controller {

  def comics = Action.async { implicit request =>
    println(s"QS: ${request.queryString}")

    val queryStringOrBadRequest: Either[Future[Result], Seq[String]] =
      Either.fromOption(
        request.queryString.get("comicIds"),
        Future.successful(BadRequest("Include a url-encoded comicIds parameter whose value is a list of comma-separated comic ids"))
      )

    val comicIdsOrBadRequest =
      queryStringOrBadRequest.flatMap(comicIdQueryString => {
        // Todo: Find the util for this, Either(Try(...)) doesn't seem to work
        Try(comicIdQueryString(0).split(",").toSeq.map(_.toInt)) match {
          case Failure(err) => Left(Future.successful(BadRequest(s"comicIds ($comicIdQueryString) could not be parsed to an array of ints: $err")))
          case Success(value) => Right(value)
        }}
      )

    val comicsResult = comicIdsOrBadRequest.map(comicIds => {
      val x: Future[Seq[ComicQueryResult]] = comicsService.get(comicIds)

      x.map(li => {
        val mapToId = (v: ComicQueryResult) => JsNumber(v.id)

        val found             = extractByType[Found, JsValue](li)(v => v.comicJson.value)
        val notFound          = extractByType[NotFound, JsNumber](li)(mapToId)
        val wrongJsonSchema   = extractByType[WrongJsonSchema, JsNumber](li)(mapToId)
        val malformedJson     = extractByType[MalformedJson, JsNumber](li)(mapToId)

        val result =
          JsObject(Seq(
            "data" -> JsArray(found),
            "success" -> JsBoolean(malformedJson.size == 0),

            "notFound" -> JsArray(notFound),
            "badJsonSchema" -> JsArray(wrongJsonSchema),
            "malformedJson" -> JsArray(malformedJson)
          ))

        Ok(result)
      })
    })

    comicsResult.merge
  }

  protected def extractByType[S: ClassTag, T](
    results: Seq[ComicQueryResult]
  )(fn: S => T): Seq[T] = {
    results
      .flatMap(_ match {
        case v: S => Some(fn(v))
        case _ => None
      })
  }
}
