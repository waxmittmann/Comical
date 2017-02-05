package services

import java.security.MessageDigest
import java.time.{LocalDateTime, ZoneOffset}
import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import play.api.Logger

@ImplementedBy(classOf[UrlServiceImpl])
trait UrlService {
  def apiUrl(query: String): String
}

@Singleton
class UrlServiceImpl @Inject()(configuration: play.api.Configuration) extends UrlService {
  //Todo: DRY reading / error-throwing
  //Todo: Should check somewhere specifically for api errors from invalid public or private key
  //right now that just winds up as all NotFound responses
  val baseUrl = configuration.getString("comical.marvel.url")
    .getOrElse(throw new RuntimeException("application.conf is missing the marvel api url (comical.marvel.url)"))
  val publicKey = configuration.getString("comical.marvel.publickey")
    .getOrElse(throw new RuntimeException("application.conf is missing the public key (comical.marvel.publickey)"))
  val privateKey = configuration.getString("comical.marvel.privatekey")
    .getOrElse(throw new RuntimeException("application.conf is missing the private key (comical.marvel.privatekey)"))

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
}
