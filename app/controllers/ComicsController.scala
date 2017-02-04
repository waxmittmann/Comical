package controllers

import javax.inject._

import play.api.mvc._
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.reflect.internal.Precedence
import scala.util.{Failure, Success, Try}

import play.api.libs.json.{JsArray, JsBoolean, JsNumber, JsObject, JsValue}
import services.ComicsService
import cats.syntax.either._
import play.api.Logger
import services.ComicsService.{ComicQueryResult, Found, MalformedJson, NotFound, WrongJsonSchema}


@Singleton
class ComicsController @Inject()(configuration: play.api.Configuration, comicsService: ComicsService)(implicit exec: ExecutionContext) extends Controller {
  //val maxQueriesPerRequest = 50
  val maxQueriesPerRequest = configuration.getInt("comical.maxQueriesPerRequest").get

  def index: Action[AnyContent] = Action {
    Ok("This is a proxy for the marvel api. Hit /comics with a comicIds parameter containing a comma-separated list of ids.")
  }

  def comics: Action[AnyContent] = Action.async { implicit request =>
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
      ).flatMap(comicIds =>
        if (comicIds.length > maxQueriesPerRequest)
          Left(Future.successful(BadRequest(s"Too many items, please include at most $maxQueriesPerRequest ids")))
        else
          Right(comicIds)
      )

    val comicsResult = comicIdsOrBadRequest.map(comicIds => {
      val x: Future[Seq[ComicQueryResult]] = comicsService.get(comicIds)

      println(s"Got future: $x")

      x.map(queryResults => {
        println(s"Query Results : $queryResults")

        val mapToId = (v: ComicQueryResult) => JsNumber(v.id)

        val found = extractByType[Found, JsValue](queryResults)(v => v.comicJson)
        val notFound = extractByType[NotFound, JsNumber](queryResults)(mapToId)
        val wrongJsonSchema = extractByType[WrongJsonSchema, JsNumber](queryResults)(mapToId)
        val malformedJson = extractByType[MalformedJson, JsNumber](queryResults)(mapToId)

        val result =
          JsObject(Seq(
            "data" -> JsArray(found),
            "success" -> JsBoolean(malformedJson.size == 0),

            "notFound" -> JsArray(notFound),
            "badJsonSchema" -> JsArray(wrongJsonSchema),
            "malformedJson" -> JsArray(malformedJson)
          ))

        Ok(result)
      }).recover {
        case err: Throwable => {
          Logger.error(s"Failed to complete request:\n${err.getStackTrace.mkString("\n")}")
          InternalServerError("There was an error handling your request. Please try again")
        }
      }
    })

    comicsResult.merge
  }

  protected def extractByType[S: ClassTag, T](
    results: Seq[ComicQueryResult]
  )(
    fn: S => T
  ): Seq[T] = {
    results
      .flatMap(_ match {
        case v: S => Some(fn(v))
        case _ => None
      })
  }
}
