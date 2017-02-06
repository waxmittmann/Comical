package util

/**
  * Util methods that encapsulate reading an int or string with key 'key' from a
  * Configuration object and throwing an error with 'errorMsg' if it is not found.
  */
object ConfigReader {
  def getString(key: String, errorMsg: String)(implicit config: play.api.Configuration): String =
    config
      .getString(key)
      .getOrElse(throw new RuntimeException(s"$errorMsg ($key)"))

  def getInt(key: String, errorMsg: String)(implicit config: play.api.Configuration): Int =
    config
      .getInt(key)
      .getOrElse(throw new RuntimeException(s"$errorMsg ($key)"))
}
