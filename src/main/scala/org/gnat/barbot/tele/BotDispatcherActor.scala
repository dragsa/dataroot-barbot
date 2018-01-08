package org.gnat.barbot.tele

import akka.actor.{Actor, ActorLogging, Props}
import com.typesafe.config.Config
import info.mukel.telegrambot4s.api.declarative.Commands
import info.mukel.telegrambot4s.api.{Polling, TelegramBot}
import info.mukel.telegrambot4s.models.MessageEntityType
import org.gnat.barbot.tele.BotAlphabet._

import scala.io.Source

object BotDispatcherActor {

  object BarCrawlerBotCommands extends Enumeration {
    val start = Value("/start")
    val hello = Value("/hello")
    val history = Value("/history")
    val suggest = Value("/suggest")
    val stop = Value("/stop")
  }

  def commands = BarCrawlerBotCommands.values.map(_.toString).toList

  def props(implicit config: Config) = Props(new BotDispatcherActor)
}

/* Router actor which should:
    - maintain session with Telegram using token
    - pool Telegram for incoming data
    - validate if commands executed have valid syntax
    - execute commands which do not impact user state
    - pass execution of other commands to child user actors
    - bypass messages to child actors
    */

class BotDispatcherActor(implicit config: Config) extends TelegramBot with Polling with Commands with Actor with ActorLogging {
  lazy val token = scala.util.Properties
    .envOrNone("BOT_TOKEN")
    .getOrElse(Source.fromResource("bot.token").getLines().mkString)

  val botConfig = config.getConfig("bot")

  import BotDispatcherActor.commands

  // commands may:
  // - impact state of session
  // - provide "static" info which is not dependent on session state

  // keep-alive command
  onCommand("/hello") { implicit msg =>
    reply(helloReply)
  }

  // start session, executed by another Telegram endpoint, leads to initial state
  onCommand("/start") { implicit msg =>
    getUserId(msg) match {
      case Some(a) =>
        spawnTheChild(a.toString) ! msg
        // TODO check if user exists and send different messages
        reply(String.format(firstEncounter, getUserFirstName(msg)).stripMargin)
      case None => reply(userNotFound)
    }
  }

  // starts interactive dialog with user
  onCommand("/suggest") { implicit msg =>
    ???
  }

  // reset session state from any other to initial
  onCommand("/stop") { implicit msg =>
    ???
  }

  // provide history of visits
  // TODO add parameters to control:
  // - how many records
  // - sorting by
  // - sorting order
  onCommand("/history") { implicit msg =>
    ???
  }

  // messages effect depends session state in which those are executed
  // also unknown commands are signaled to user in this handler
  onMessage { implicit msg =>
    logger.info(s"got message from API:\n $msg")
    msg.entities.flatMap(entities =>
      entities.find(entity =>
        entity.`type`.equals(MessageEntityType.BotCommand) && !msg.text.exists(commandName => commands.contains(commandName))))
    match {
      case Some(_) =>
        log.debug(s"${getUserFirstName(msg)}: command ${msg.text.getOrElse("")} doesn't exist")
        reply(String.format(commandNotFound, msg.text.getOrElse("")))
      case None => ()
    }
    reply(s"echo of message: ${msg.text.getOrElse("default text")}")

  }

  private def spawnTheChild(userId: String) = {
    context.child(userId.toString).getOrElse(
      context.actorOf(BotUserActor.props(userId), userId)
    )
  }

  override def preStart = {
    log.debug(s"actor ${self.path.name}, Father of all bot user actors is here")
    run()
  }

  override def postStop = {
    log.debug(s"actor ${self.path.name} is dying")
  }

  override def receive: Receive = {
    case a => log.debug(s"actor ${self.path.name} received message:\n $a")
  }
}
