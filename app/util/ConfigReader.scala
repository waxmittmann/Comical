package util

//Wanted to have this as a trait, but declaring config in a val didn't play
//nicely with injecting it as a param into the class :(
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
