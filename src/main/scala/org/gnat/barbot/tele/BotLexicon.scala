package org.gnat.barbot.tele

object BotLexicon {
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

  val questions = Map("location" -> "%s, how far the place can be located?\nyou can reply: 'near', 'far' or 'middle'",
    "openHours" -> "%s, what are ideal open hours for you?\nin 'HH:MM-HH:MM' format, please",
    "placesAvailable" -> "%s, how many guests will be there?",
    "beer" -> "%s, what kind of bear do you prefer?\nI've heard that now in Kiev there are only: 'red', 'cider', 'light, 'dark' or 'craft'",
    "wine" -> "%s, what kind of wine do you prefer?\nrumors are that you can find: 'red dry', 'white dry', 'red semi-sweet', 'white sweet'",
    "cuisine" -> "%s, what is your favorite cuisine?\nchoices are: 'italian', 'european', 'czech', 'jewish'",
    "default" -> "%s, how are you? we are fixing issue on server side...")
}