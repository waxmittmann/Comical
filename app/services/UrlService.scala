package services

import java.security.MessageDigest
import java.time.{LocalDateTime, ZoneOffset}
import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import play.api.Logger
import util.ConfigReader.getString

@ImplementedBy(classOf[UrlServiceImpl])
trait UrlService {
  def comicUrl(query: String): String
}

@Singleton
class UrlServiceImpl @Inject()(implicit configuration: play.api.Configuration) extends UrlService {
  val baseUrl = getString("comical.marvel.url", "application.conf is missing the marvel api url")
  val publicKey = getString("comical.marvel.publickey", "application.conf is missing the public key")
  val privateKey = getString("comical.marvel.privatekey", "application.conf is missing the private key")

  def comicUrl(query: String): String = {
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
