package services

import scala.concurrent.{ExecutionContext, Future}
import java.security.MessageDigest
import java.util.Properties
import javax.inject._
//import com.google.inject.Inject
import play.api.libs.ws.{WSClient, WSRequest}
import redis.RedisClient
import cats.{Monad, Traverse}
import cats.syntax.traverse._
import cats.instances.all._

@Singleton
class ComicsService @Inject() (wsClient: WSClient)(implicit ec: ExecutionContext) {
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
    //val path = s"$baseUrl$subPath?$query&$apiKeysUrlPart"
    val path = s"$baseUrl$subPath/$query?$apiKeysUrlPart"
    println(s"Path: $path")
    path
  }

  case class Comic(
    title: String,
    id: String
  )

  def get2(comicIds: List[Int]): Future[List[String]] = {
    println("Called Get")

    val futures: Future[List[String]] = comicIds.map(id => {
      val requestUrl = apiUrl(id.toString)
      wsClient.url(requestUrl).execute().map(_.body)
    }).sequence[Future, String]

    futures
  }
}