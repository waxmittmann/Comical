package services

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import javax.inject._

import akka.actor.ActorSystem
import play.api.libs.json.{JsDefined, JsUndefined, JsValue, Json}
import play.api.libs.ws.WSResponse
import play.api.libs.ws.WSClient
import cats.{Monad, Traverse}
import cats.syntax.traverse._
import cats.instances.all._
import com.google.inject.ImplementedBy
import play.api.Logger
import play.api.cache.CacheApi
import services.ComicsService.{ComicQueryResult, Found, FoundInCache, FoundRemotely, MalformedJson, NotFound, WrongJsonSchema}

object ComicsService {
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
  def get(comicIds: Seq[Int]): Future[Seq[ComicQueryResult]]
}

@Singleton
class ComicsServiceImpl @Inject() (wsClient: WSClient, urlService: UrlService, cacheClient: CacheApi)(implicit ec: ExecutionContext, actorSystem: ActorSystem) extends ComicsService {

  val baseUrl = s"http://gateway.marvel.com:80/v1/public/"

  override def get(comicIds: Seq[Int]): Future[Seq[ComicQueryResult]] =
    comicIds.toList
      .map(comicDetails)
      .sequence[Future, ComicQueryResult]

  protected def comicDetails(id: Int): Future[ComicQueryResult] = {
      cacheClient
        .get[JsValue](id.toString)
        .map(json => FoundInCache(id, json))
        .fold {
          val requestUrl = urlService.apiUrl(id.toString)
          getFromMarvel(id, requestUrl)
        }(v => Future.successful(v))
  }

  protected def getFromMarvel(
    id: Int,
    requestUrl: String
  ): Future[ComicQueryResult] = {
    wsClient.url(requestUrl).execute().map(response => {
      if (response.status == 200) {
        val queryResult = processResponse(id, response)
        cache(queryResult)
        queryResult
      }
      else
        NotFound(id)
    })
  }

  //Todo: Maybe should cache NotFound's too
  protected def cache(queryResult: ComicQueryResult) = {
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

  protected def processResponse(id: Int, response: WSResponse): ComicQueryResult = {
    Try(Json.parse(response.body)) match {
      case Failure(exception) => MalformedJson(id, response.body)
      case Success(jsonBody) => {
        // Assuming that there will only ever be a single result in the result
        // set as the ids should be unique (right? right??)
        val dataPart = jsonBody \ "data" \ "results" \ 0
        dataPart match {
          case json@JsDefined(_) => FoundRemotely(id, json.value)
          case _: JsUndefined => WrongJsonSchema(id, jsonBody)
        }
      }
    }
  }
}
