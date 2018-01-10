package org.gnat.barbot.tele

object BotAlphabet {
  val greetingForFirstEncounter =
    s"""hello, %s!
       |it is the first time we see each other.
       |hopefully I will be able to assist you in getting
       |wasted today and on many other occasions.
    """

  val greetingForRegistered =
    s"""hello, %s!
       |I am pleased to see you back!
    """

  val helloReply = "ready so serve, my Lord!"

  val goodbye = "goodbye, %s!"

  val userNotFound = "user is unknown."

  val commandNotAccepted = "command %s is not clear to me! please check /help details"

  val commandAccepted = "command %s is known and clear, executing..."

  val sessionNotStarted = "session is not started, please run /start"

  val sessionStarted =
    s"""hey, %s!
       |make a choice of questionnaire length:
       |1 - short, fast but less accurate
       |2 - painful, slow but precise
    """
}
