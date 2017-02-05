package controllers

import javax.inject._

import play.api.mvc._
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

import play.api.libs.json.{JsArray, JsBoolean, JsNumber, JsObject, JsValue}
import services.ComicsService
import cats.syntax.either._
import play.api.Logger
import services.ComicsService.{ComicQueryResult, Found, MalformedJson, NotFound, WrongJsonSchema}
import util.ConfigReader

@Singleton
class ComicsController @Inject()(comicsService: ComicsService)(implicit configuration: play.api.Configuration, exec: ExecutionContext) extends Controller {
  val maxQueriesPerRequest = ConfigReader.getInt("comical.maxQueriesPerRequest", "application.conf is missing maxQueriesPerRequest")

  def index: Action[AnyContent] = Action {
    Ok("This is a proxy for the marvel api. Hit /comics with a comicIds parameter containing a comma-separated list of ids.")
  }

  def comics: Action[AnyContent] = Action.async { implicit request =>
    // Get and parse the query string, then use the comic ids to make a request
    // to ComicsService and create a response from that
    val response = (for {
      queryString   <- queryString(request)
      comicIds      <- parse(queryString)
      _             <- validateMaxIdsLimit(comicIds)
    } yield
      comicsResponse(comicIds)).merge

    //If the future fails, map it to an internal server error
    response.recover {
      case err: Throwable => {
        Logger.error(s"Failed to complete request:\n${err.getStackTrace.mkString("\n")}")
        InternalServerError("There was an error handling your request. Please try again.")
      }
    }
  }

  protected def validateMaxIdsLimit(comicIds: Seq[Int]): Either[Future[Result], Seq[Int]] =
    if (comicIds.length > maxQueriesPerRequest)
      Left(Future.successful(BadRequest(s"Too many items, please include at most $maxQueriesPerRequest ids")))
    else
      Right(comicIds)

  protected def parse(queryString: Seq[String]): Either[Future[Result], Seq[Int]] =
    // Todo: Find the util for this, Either(Try(...)) doesn't seem to work
    Try(queryString(0).split(",").toSeq.map(_.toInt)) match {
      case Failure(err) => Left(Future.successful(BadRequest(s"comicIds ($queryString) could not be parsed to an array of ints: $err")))
      case Success(value) => Right(value)
    }

  protected def queryString(request: Request[AnyContent]): Either[Future[Result], Seq[String]] =
    Either.fromOption(
      request.queryString.get("comicIds"),
      Future.successful(BadRequest("Include a url-encoded comicIds parameter whose value is a list of comma-separated comic ids"))
    )

  protected def comicsResponse(comicIds: Seq[Int]): Future[Result] =
    for {
      queryResults  <- comicsService.comics(comicIds.toList)
      response      = searchResponse(queryResults)
    } yield response

  def searchResponse(queryResults: Seq[ComicQueryResult]): Result = {
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
  }

  protected def extractByType[S: ClassTag, T](results: Seq[ComicQueryResult])(fn: S => T): Seq[T] =
    results
      .flatMap(_ match {
        case v: S => Some(fn(v))
        case _ => None
      })
}
