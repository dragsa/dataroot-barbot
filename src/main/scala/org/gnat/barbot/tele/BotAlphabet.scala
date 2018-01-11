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

  val userNotFound = "user is unknown"

  val commandNotAccepted = "command %s is not clear to me! please check /help details"

  val commandAccepted = "command %s is known and clear, executing..."

  val sessionStopped = "goodbye, %s!"

  val sessionStarted = "my dear %s, session was established, should I /suggest?"

  val sessionAlreadyExists = "my dear %s, session already exists, should I /suggest?"

  val sessionNotStarted = "session is not started, please run /start"

  val decisionDialogStarted =
    s"""hey, %s!
       |make a choice of questionnaire length:
    """
}
