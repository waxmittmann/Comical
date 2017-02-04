package services

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import javax.inject._

import play.api.libs.json.{JsDefined, JsUndefined, Json}
import play.api.libs.ws.WSResponse
import play.api.libs.ws.WSClient
import redis.RedisClient
import cats.{Monad, Traverse}
import cats.syntax.traverse._
import cats.instances.all._
import com.google.inject.ImplementedBy
import play.api.Logger
import services.ComicsService.{ComicQueryResult, MalformedJson, WrongJsonSchema, NotFound, Found}
//import services.ComicsService.{ComicQueryResult, Found, MalformedJson, NotFound, WrongJsonSchema}

object ComicsService {
  sealed trait ComicQueryResult {
    val id: Int
  }

  //Todo: Unwrap the JsDefined
  case class Found(id: Int, comicJson: JsDefined) extends ComicQueryResult
  case class NotFound(id: Int) extends ComicQueryResult
  //Todo: Unwrap the JsDefined
  case class WrongJsonSchema(id: Int, badJson: JsDefined) extends ComicQueryResult
  case class MalformedJson(id: Int, failResponse: String) extends ComicQueryResult
}

//sealed trait ComicQueryResult {
//  val id: Int
//}
//
//case class Found(id: Int, comicJson: JsDefined) extends ComicQueryResult
//case class NotFound(id: Int) extends ComicQueryResult
//case class WrongJsonSchema(id: Int, badJson: JsDefined) extends ComicQueryResult
//case class MalformedJson(id: Int, failResponse: String) extends ComicQueryResult


@ImplementedBy(classOf[ComicsServiceImpl])
trait ComicsService {
  def get(comicIds: Seq[Int]): Future[Seq[ComicQueryResult]]
}

@Singleton
class ComicsServiceImpl @Inject() (wsClient: WSClient, marvelService: MarvelService)(implicit ec: ExecutionContext) extends ComicsService {

//  implicit val akkaSystem = akka.actor.ActorSystem()
//  val redis = RedisClient()
  val baseUrl = s"http://gateway.marvel.com:80/v1/public/"

  override def get(comicIds: Seq[Int]): Future[Seq[ComicQueryResult]] =
    comicIds.toList.map(id => {
      val requestUrl = apiUrl(id.toString)
      getFromMarvel(id, requestUrl)
    }).sequence[Future, ComicQueryResult]

  protected def apiUrl(
    query: String,
    subPath: String = "comics"
  ): String = {
    val apiKeysUrlPart = marvelService.apiKeysUrlPart
    val path = s"$baseUrl$subPath/$query?$apiKeysUrlPart"
    Logger.debug(s"Generated Api: $path")
    path
  }

  protected def getFromMarvel(
    id: Int,
    requestUrl: String
  ): Future[ComicQueryResult] = {
    wsClient.url(requestUrl).execute().map(response => {
      if (response.status == 200) {
        processResponse(id, response)
      }
      else
        NotFound(id)
    })
  }

  protected def processResponse(
    id: Int,
    response: WSResponse
  ): ComicQueryResult = {
    Try(Json.parse(response.body)) match {
      case Failure(exception) => MalformedJson(id, response.body)
      case Success(jsonBody) => {
        val dataPart = jsonBody \ "data" \ "results" \ 0
        dataPart match {
          case json@JsDefined(_) => Found(id, json)
          case _: JsUndefined => WrongJsonSchema(id, JsDefined(jsonBody))
        }
      }
    }
  }
}
