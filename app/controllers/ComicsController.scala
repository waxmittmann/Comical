package controllers

import akka.actor.ActorSystem
import javax.inject._

import play.api._
import play.api.mvc._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

import play.api.libs.json.{JsArray, JsBoolean, JsNumber, JsObject, JsValue}
import play.twirl.api.Html
import services.ComicsService
import services.ComicsService.{ComicQueryResult, Failed, Found, NotFound, WrongJsonSchema}
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
    val r1: Either[Future[Result], Seq[String]] =
      Either.fromOption(
        request.queryString.get("comicIds"),
        Future.successful(BadRequest("Include a url-encoded comicIds parameter whose value is a list of comma-separated comic ids"))
      )

    val r2 =
      r1.flatMap(comicIdQueryString =>
        // Todo: Find the util for this, Either(Try(...)) doesn't seem to work
        Try(comicIdQueryString.map(_.toInt)) match {
          case Failure(_) => Left(Future.successful(BadRequest("comicIds could not be parsed to an array of ints.")))
          case Success(value) => Right(value)
        }
      )

    val r3 = r2.map(comicIds => {
      val x: Future[Seq[ComicQueryResult]] = comicsService.get(comicIds)

      x.map(li => {
        val found: Seq[JsValue] =
          li.flatMap(_ match {
            case v : Found => Some(v)
            case _ => None
          }).map(_.comicJson.value)

        val notFound: Seq[JsNumber] =
          li
            .flatMap(_ match {
              case v: NotFound => Some(v)
              case _ => None
            })
            .map(nf => JsNumber(nf.id))

        val badJson: Seq[JsNumber] =
          li
            .flatMap(_ match {
              case v: WrongJsonSchema => Some(v)
              case _ => None
            })
            .map(nf => JsNumber(nf.id))

        val failed: Seq[JsNumber] =
          li
            .flatMap(_ match {
              case v: Failed => Some(v)
              case _ => None
            })
            .map(nf => JsNumber(nf.id))

        val result =
          JsObject(Seq(
            "data" -> JsArray(found),
            "success" -> JsBoolean(failed.size == 0),

            "notFound" -> JsArray(notFound),
            "badJson" -> JsArray(badJson),
            "failed" -> JsArray(failed)
          ))
        Ok(result)
      })
    })
    r3.merge

/*
    Either.fromOption(request.queryString.get("comicIds"), Future.successful(BadRequest("Include a url-encoded comicIds parameter whose value is a list of comma-separated comic ids")))
      .map(comicIdQueryString =>
        // Todo: Find the util for this, Either(Try(...)) doesn't seem to work
        Try(comicIdQueryString.map(_.toInt)) match {
          case Failure(_) => Left(BadRequest("comicIds could not be parsed to an array of ints."))
          case Success(value) => Right(value)
        }
      )
      .map(comicIds => {
        println(s"Params: $params")

        val x: Future[List[ComicQueryResult]] = comicsService.get(comicIds)

        x.map(li => {
          val found: List[JsValue] =
            li.flatMap(_ match {
              case v : Found => Some(v)
              case _ => None
            }).map(_.comicJson.value)

          val notFound: List[JsNumber] =
            li
              .flatMap(_ match {
                case v: NotFound => Some(v)
                case _ => None
              })
              .map(nf => JsNumber(nf.id))

          val badJson: List[JsNumber] =
            li
              .flatMap(_ match {
                case v: WrongJsonSchema => Some(v)
                case _ => None
              })
              .map(nf => JsNumber(nf.id))

          val failed: List[JsNumber] =
            li
              .flatMap(_ match {
                case v: Failed => Some(v)
                case _ => None
              })
              .map(nf => JsNumber(nf.id))

          val result =
            JsObject(Seq(
              "data" -> JsArray(found),
              "success" -> JsBoolean(failed.size == 0),

              "notFound" -> JsArray(notFound),
              "badJson" -> JsArray(badJson),
              "failed" -> JsArray(failed)
            ))
          Ok(result)
        })
      })
      */
     //.getOrElse(Future.successful(BadRequest("Include a url-encoded comicIds parameter whose value is a list of comma-separated comic ids")))
  }

  /**
   * Create an Action that returns a plain text message after a delay
   * of 1 second.
   *
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/message`.
   */
  def message = Action.async {
    getFutureMessage(1.second).map { msg => Ok(msg) }
  }

//  def getComics = Action.async {
//
//  }

  private def getFutureMessage(delayTime: FiniteDuration): Future[String] = {
    val promise: Promise[String] = Promise[String]()
    actorSystem.scheduler.scheduleOnce(delayTime) { promise.success("Hi!") }
    promise.future
  }

}
