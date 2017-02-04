package controllers

import akka.actor.ActorSystem
import javax.inject._

import play.api._
import play.api.mvc._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration._

import play.api.libs.json.{JsArray, JsBoolean, JsNumber, JsObject, JsValue}
import play.twirl.api.Html
import services.ComicsService
import services.ComicsService.{WrongJsonSchema, ComicQueryResult, Failed, Found, NotFound}

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

  def comics = Action.async {
    val x: Future[List[ComicQueryResult]] = comicsService.get(List(42882, 41530, 999999999, 60754))

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

    //x.map(li => Ok(Html(li.map(i => s"<div>$i</div><div>-------</div>").mkString(""))))
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
