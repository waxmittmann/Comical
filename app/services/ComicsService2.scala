package services

import scala.concurrent.{ExecutionContext, Future}
import java.security.MessageDigest
import java.util.Properties
import javax.inject._
//import com.google.inject.Inject
import cats.instances.all._
import cats.syntax.traverse._
import play.api.libs.ws.WSClient
import redis.RedisClient

@Singleton
class ComicsService2 @Inject() (wsClient: WSClient)(implicit ec: ExecutionContext) {
  implicit val akkaSystem = akka.actor.ActorSystem()

  val redis = RedisClient()

  val (publicKey, privateKey) = readApiKeys

  val baseUrl = s"http://gateway.marvel.com:80/v1/public/"

  def apiKeysUrlPart = {
    //val ts = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
    val ts = 1
    val digest = MessageDigest.getInstance("MD5").digest(s"$ts$privateKey$publicKey".getBytes) //.toString
    val hash = digest.map("%02x".format(_)).mkString

    s"ts=$ts&apikey=$publicKey&hash=$hash"
  }

  def readApiKeys: (String, String) = {
    println("Reading props")
    val filename = "api.properties"
    val input = getClass().getClassLoader().getResourceAsStream(filename)
    if (input == null) {
      System.out.println("Sorry, unable to find " + filename)
      throw new RuntimeException("Sorry, unable to find " + filename)
    }
    val prop = new Properties()
    prop.load(input)
    println("Props read")
    (prop.getProperty("publickey"), prop.getProperty("privatekey"))
  }

  def apiUrl(
    query: String,
    subPath: String = "comics"
  ): String = {
    val path = s"$baseUrl$subPath?$query&$apiKeysUrlPart"
    println(s"Path: $path")
    path
  }

  case class Comic(
    title: String,
    id: String
  )

/*
  def get(comicId: String): Future[String] = {
    println("Called Get")

    val escapedComicId = comicId.replace(",", "%2C")

//  def get(comicId: Int): Future[Comic] = {
    //val result = Comic(Http(apiUrl(s"digitalId=$comicId")).asString.body, comicId)
    //val result = Comic(Http(apiUrl(s"id=$comicId")).asString.body, comicId)

    val requestUrl = apiUrl(s"id=$escapedComicId")
//    val result = Comic(Http(requestUrl).asString.body, comicId)



//    val request = Request

//    ComicalRequest()
//    wsClient.asyncHttpClient.executeRequest()

    val x = wsClient.url(requestUrl).execute()
    val y = wsClient.url(requestUrl).execute()

    import scala.concurrent.Future
    implicit val ec = scala.concurrent.ExecutionContext.global

    //x.rec

    //val all1 = List(x, y).traverse
//    val all2: Future[List[WSRequest]] = List(x, y).sequence[Future, String]
//
//    val rv: Future[Option[String]] = redis.get[String]("a")

    val r: Future[String] =
      redis
        .get[String]("a")
        .flatMap(_.fold {
            val f = wsClient.url(requestUrl).execute().map(_.body)
            f.foreach(body => redis.set("a", body))
            f
          }(Future.successful(_))
        )

    r
  }
*/

  def get2(comicIds: List[Int]): Future[List[String]] = {
    println("Called Get")

    val futures: Future[List[String]] = comicIds.map(id => {
      val r: Future[String] =
        redis
          .get[String]("a")
          .flatMap(
            _.fold {
              val requestUrl = "TODO"
              val f = wsClient.url(requestUrl).execute().map(_.body)
              f.foreach(body => redis.set("a", body))
              f
            }(Future.successful(_))
          )
      r
    }).sequence[Future, String]

    futures
  }
}
