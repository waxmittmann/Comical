package services

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import javax.inject._

import akka.actor.ActorSystem
import cats.instances.all._
import cats.syntax.traverse._
import com.google.inject.ImplementedBy
import play.api.Logger
import play.api.cache.CacheApi
import play.api.libs.json.{JsDefined, JsUndefined, JsValue, Json}
import play.api.libs.ws.{WSClient, WSResponse}
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

  /**
    * Get comics for a list of comic ids. Returns a (future of) a seq of
    * ComicQueryResult's that can be either:
    *
    * - Found, in which case they contain the json response
    *
    * - NotFound, indicating there is no comic with that id
    *
    * - WrongJsonSchema, indicating that the proxy doesn't know how to get the
    *     data out of the remote server response (either remote server error or a
    *     change in response format)
    *
    * - MalformedJson, indicating that the body did not contain well-formed json
    *     which would most likely be caused by an error in the remote server or
    *     by partially transmitted data.
    */
  override def comics(comicIds: List[Int]): Future[Seq[ComicQueryResult]] =
    comicIds
      .map(comic)
      .sequence[Future, ComicQueryResult]

  protected def comic(id: Int): Future[ComicQueryResult] =
    comicFromCache(id)
      .getOrElse(comicRemotely(id, urlService.comicUrl(id.toString)))

  protected def comicFromCache(id: Int): Option[Future[ComicQueryResult]] =
  // Get a comic from the cache, if available
    cacheClient
      .get[JsValue](id.toString)
      .map(json => Future.successful(FoundInCache(id, json)))

  // Get a comic from the marvel api
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

  //Todo: This could be factored out into a separate parser or service as it's
  // the only part really concerned with the structure of the result (well, that
  // and the case statement in 'resultFromResponse' for NOT_FOUND with notFoundJsonBody,
  // but we could handle both in here)
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
