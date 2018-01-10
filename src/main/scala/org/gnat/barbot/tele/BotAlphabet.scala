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

  val commandNotFound = "command %s is not clear to me!"

  val commandAccepted = "command %s is known and clear, executing..."

  val sessionNotStarted = "dialog is not started, please run /start"

//  val sessionNotStarted = "dialog is not started, please run /start"
}
