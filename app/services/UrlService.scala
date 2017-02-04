package services

import scala.concurrent.ExecutionContext
import java.security.MessageDigest
import java.time.{LocalDateTime, ZoneOffset}
import java.util.Properties
import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import play.api.Logger

@ImplementedBy(classOf[UrlServiceImpl])
trait UrlService {
  def apiUrl(query: String): String
}

@Singleton
class UrlServiceImpl @Inject()() extends UrlService {
  val (publicKey, privateKey) = readApiKeys
  val baseUrl = s"http://gateway.marvel.com:80/v1/public/"

  def apiUrl(query: String): String = {
    val path = s"${baseUrl}comics/$query?$apiKeysUrlPart"
    Logger.debug(s"Generated Api: $path")
    path
  }

  protected def apiKeysUrlPart: String = {
    val ts = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
    val digest = MessageDigest.getInstance("MD5").digest(s"$ts$privateKey$publicKey".getBytes) //.toString
    val hash = digest.map("%02x".format(_)).mkString

    s"ts=$ts&apikey=$publicKey&hash=$hash"
  }

  protected def readApiKeys: (String, String) = {
    val filename = "api.properties"
    val input = getClass().getClassLoader().getResourceAsStream(filename)
    if (input == null) {
      throw new RuntimeException("Sorry, unable to find " + filename)
    }
    val prop = new Properties()
    prop.load(input)
    (prop.getProperty("publickey"), prop.getProperty("privatekey"))
  }
}
