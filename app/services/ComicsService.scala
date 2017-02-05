package services

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import javax.inject._

import akka.actor.ActorSystem
import play.api.libs.json.{JsDefined, JsUndefined, JsValue, Json}
import play.api.libs.ws.WSResponse
import play.api.libs.ws.WSClient
import cats.syntax.traverse._
import cats.instances.all._
import com.google.inject.ImplementedBy
import play.api.Logger
import play.api.cache.CacheApi
import play.mvc.Http
import services.ComicsService.{ComicQueryResult, Found, FoundInCache, FoundRemotely, MalformedJson, NotFound, WrongJsonSchema, notFoundJsonBody}

object ComicsService {
  val notFoundJsonBody = """{"code":404,"status":"We couldn't find that comic_issue"}"""

  sealed trait ComicQueryResult {
    val id: Int
  }
  sealed trait Found extends ComicQueryResult {
    val comicJson: JsValue
  }
  case class FoundRemotely(id: Int, comicJson: JsValue) extends Found
  case class FoundInCache(id: Int, comicJson: JsValue) extends Found
  case class NotFound(id: Int) extends ComicQueryResult
  case class WrongJsonSchema(id: Int, badJson: JsValue) extends ComicQueryResult
  case class MalformedJson(id: Int, failResponse: String) extends ComicQueryResult
}

@ImplementedBy(classOf[ComicsServiceImpl])
trait ComicsService {
  def comics(comicIds: List[Int]): Future[Seq[ComicQueryResult]]
}

@Singleton
class ComicsServiceImpl @Inject() (wsClient: WSClient, urlService: UrlService, cacheClient: CacheApi)(implicit ec: ExecutionContext, actorSystem: ActorSystem) extends ComicsService {

  override def comics(comicIds: List[Int]): Future[Seq[ComicQueryResult]] =
    comicIds
      .map(comic)
      .sequence[Future, ComicQueryResult]

  protected def comic(id: Int): Future[ComicQueryResult] =
    comicFromCache(id)
      .getOrElse(comicRemotely(id, urlService.comicUrl(id.toString)))

  protected def comicFromCache(id: Int): Option[Future[ComicQueryResult]] =
    cacheClient
      .get[JsValue](id.toString)
      .map(json => Future.successful(FoundInCache(id, json)))

  protected def comicRemotely(id: Int, requestUrl: String): Future[ComicQueryResult] =
    for {
      response    <- wsClient.url(requestUrl).execute()
      queryResult <- resultFromResponse(id, response)
    } yield queryResult

  protected def resultFromResponse(id: Int, response: WSResponse): Future[ComicQueryResult] =
    response.status match {
      case Http.Status.OK => {
        val queryResult = processResponse(id, response)
        putInCache(queryResult)
        Future.successful(queryResult)
      }

      //Todo: This is lame, we should try parsing the json and analysing the response...
      case Http.Status.NOT_FOUND if response.body == notFoundJsonBody =>
        Future.successful(NotFound(id))

      case otherStatus => {
        Logger.error(s"Request to marvel failed with status $otherStatus and body:\n${response.body}")
        Future.failed(new RuntimeException(s"Request to marvel failed with status $otherStatus and body:\n${response.body}"))
      }
    }

  protected def processResponse(id: Int, response: WSResponse): ComicQueryResult =
    Try(Json.parse(response.body)) match {
      case Failure(exception) =>
        MalformedJson(id, response.body)

      // Assuming that there will only ever be a single result in the result
      // set as the ids should be unique (right? right??)
      case Success(jsonBody) => {
        val dataPart = jsonBody \ "data" \ "results" \ 0
        dataPart match {
          case json@JsDefined(_)  => FoundRemotely(id, json.value)
          case _: JsUndefined     => WrongJsonSchema(id, jsonBody)
        }
      }
    }

  //Todo: Maybe should cache NotFound's too
  protected def putInCache(queryResult: ComicQueryResult): Unit =
    queryResult match {
      case f: Found => {
        Logger.debug(s"Caching Found with ${f.id}")
        cacheClient.set(f.id.toString, f.comicJson)
      }
      case r @ _ => {
        Logger.debug(s"Not caching non-success result $r")
      }
    }
}
