package org.gnat.barbot.tele

object BotAlphabet {
  val firstEncounter =
    s"""Hello, %s!
       |it is the first time we see each other.
       |I hope I will be able to assist you
       |in getting wasted today and many other days.
    """
  val greetingForRegistered =
    s"""Hello, %s!
       |I am pleased to see you back!
    """

  val helloReply = "ready so serve, my Lord!"

  val userNotFound = "user is unknown"

  val commandNotFound = "command %s is not clear..."
}
