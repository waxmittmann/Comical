package services

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import javax.inject._

import akka.actor.ActorSystem
import akka.util.ByteString
import play.api.libs.json.{JsArray, JsBoolean, JsDefined, JsNull, JsNumber, JsObject, JsString, JsUndefined, Json}
import play.api.libs.ws.WSResponse
import play.api.libs.ws.WSClient
import redis.{ByteStringDeserializer, ByteStringSerializer, RedisClient}
import cats.{Monad, Traverse}
import cats.syntax.traverse._
import cats.instances.all._
import com.google.inject.ImplementedBy
import play.api.Logger
import services.ComicsService.{ComicQueryResult, Found, FoundInCache, FoundRemotely, MalformedJson, NotFound, WrongJsonSchema}
//import services.ComicsService.{ComicQueryResult, Found, MalformedJson, NotFound, WrongJsonSchema}

object ComicsService {
  sealed trait ComicQueryResult {
    val id: Int
  }

  //Todo: Unwrap the JsDefined

  sealed trait Found extends ComicQueryResult {
    val comicJson: JsDefined
  }
  case class FoundRemotely(id: Int, comicJson: JsDefined) extends Found
  case class FoundInCache(id: Int, comicJson: JsDefined) extends Found

  //case class Found(id: Int, comicJson: JsDefined) extends ComicQueryResult

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
//(actorSystem: ActorSystem)
@Singleton
//class ComicsServiceImpl @Inject() (wsClient: WSClient, marvelService: MarvelService, actorSystem: ActorSystem)(implicit ec: ExecutionContext) extends ComicsService {
class ComicsServiceImpl @Inject() (wsClient: WSClient, marvelService: MarvelService)(implicit ec: ExecutionContext, actorSystem: ActorSystem) extends ComicsService {

//  implicit val akkaSystem = akka.actor.ActorSystem()
  val redis = RedisClient()
  val baseUrl = s"http://gateway.marvel.com:80/v1/public/"

  implicit val jsonDeserializer = new ByteStringDeserializer[JsObject] {
    override def deserialize(bs: ByteString): JsObject = {
      Json.parse(bs.toString()) match {
        case jsObject @ JsObject(_) => jsObject
        case _ => throw new RuntimeException("This ain't no JsObject!")
      }
    }
  }

  implicit val jsonSerializer = new ByteStringSerializer[JsObject] {
    override def serialize(data: JsObject): ByteString = {
      ByteString(data.toString())
    }
  }


  override def get(comicIds: Seq[Int]): Future[Seq[ComicQueryResult]] =
    comicIds.toList
      .map(comicDetails)
      .sequence[Future, ComicQueryResult]

  def comicDetails(id: Int): Future[ComicQueryResult] = {
//    val r: Future[Option[ComicQueryResult]] =
//      redis
//        .get[JsObject](id.toString).map(_.map[ComicQueryResult](json => FoundInCache(id, JsDefined(json))))

    val r1: Future[Option[JsObject]] = redis.get[JsObject](id.toString)

    val r2: Future[Option[ComicQueryResult]] =
      r1.map(_
        .map[ComicQueryResult](json => FoundInCache(id, JsDefined(json)))
      )

    val r3: Future[ComicQueryResult] =
      r2.flatMap(_ match {
        case Some(x) => Future.successful(x)
        case None => {
          val requestUrl = apiUrl(id.toString)
          getFromMarvel(id, requestUrl)
        }
      })

    r3

//        .map(_
//        .map[ComicQueryResult](json => FoundInCache(id, JsDefined(json)))
//      )
//      .flatMap(_.fold {
//        val requestUrl = apiUrl(id.toString)
//        getFromMarvel(id, requestUrl)
//      })(v => Future.successful(v))


//
//      .map(_.map[ComicQueryResult](json => FoundInCache(id, JsDefined(json))))
//      .fallbackTo {
//        val requestUrl = apiUrl(id.toString)
//        getFromMarvel(id, requestUrl)
//      }
  }

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
        redis.set(f.id.toString, f.comicJson.value)
      }
      case r @ _ => {
        Logger.debug(s"Not caching non-success result $r")
      }
    }
  }

  protected def processResponse(
    id: Int,
    response: WSResponse
  ): ComicQueryResult = {
    Try(Json.parse(response.body)) match {
      case Failure(exception) => MalformedJson(id, response.body)
      case Success(jsonBody) => {
        // Assuming that there will only ever be a single result in the result
        // set as the ids should be unique (right? right??)
        val dataPart = jsonBody \ "data" \ "results" \ 0
        dataPart match {
          case json@JsDefined(_) => FoundRemotely(id, json)
          case _: JsUndefined => WrongJsonSchema(id, JsDefined(jsonBody))
        }
      }
    }
  }
}
