package org.gnat.barbot.tele

import akka.actor.{Actor, ActorLogging, Props}
import com.typesafe.config.Config
import info.mukel.telegrambot4s.api.declarative.Commands
import info.mukel.telegrambot4s.api.{Polling, TelegramBot}
import info.mukel.telegrambot4s.models.MessageEntityType
import org.gnat.barbot.Database
import org.gnat.barbot.models.User
import org.gnat.barbot.tele.BotAlphabet._

import scala.io.Source
import scala.util.Success

object BotDispatcherActor {

  object BarCrawlerBotCommands extends Enumeration {
    val start = Value("/start")
    val hello = Value("/hello")
    val history = Value("/history")
    val suggest = Value("/suggest")
    val stop = Value("/stop")
  }

  def commands = BarCrawlerBotCommands.values.map(_.toString).toList

  def props(implicit config: Config, db: Database) = Props(new BotDispatcherActor)
}

/* Router actor which should:
    - maintain session with Telegram using token
    - pool Telegram for incoming data
    - validate if commands executed have valid syntax
    - execute commands which do not impact user state
    - pass execution of other commands to child user actors
    - bypass messages to child actors
    */

class BotDispatcherActor(implicit config: Config, db: Database) extends TelegramBot with Polling with Commands with Actor with ActorLogging {
  lazy val token = scala.util.Properties
    .envOrNone("BOT_TOKEN")
    .getOrElse(Source.fromResource("bot.token").getLines().mkString)

  val botConfig = config.getConfig("bot")

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
      case Some(id) =>
        val userName = getUserFullName(msg)
        val compositeUserActorName = getCompositeUserActorName(msg)
        context.child(compositeUserActorName).getOrElse {
          logger.info(s"spawning child actor: $compositeUserActorName")
          db.userRepository.getOneById(id).flatMap {
            case Some(_) => reply(String.format(greetingForRegistered, userName).stripMargin)
            case None =>
              db.userRepository.createOne(User(nickName = getUserNickName(msg),
                firstName = getUserFirstName(msg).get,
                lastName = getUserLastName(msg),
                id = getUserId(msg).get
              )).andThen { case Success(u) => logger.info(s"user $u was created") }
              reply(String.format(greetingsForfirstEncounter, userName).stripMargin)
          }
          context.actorOf(BotUserActor.props(compositeUserActorName), compositeUserActorName) ! msg
        }
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
  import BotDispatcherActor.commands
  onMessage { implicit msg =>
    logger.info(s"got message from API:\n $msg")
    msg.entities match {
      // stop and reply - unknown command
      case Some(entitiesList) =>
        entitiesList.find(entity =>
          entity.`type`.equals(MessageEntityType.BotCommand) && !msg.text.exists(commandName => commands.contains(commandName))) match {
          case Some(_) =>
            log.debug(s"${getUserFullName(msg)}: command ${msg.text.getOrElse("")} doesn't exist")
            reply(String.format(commandNotFound, msg.text.getOrElse("")))
          case None =>
            log.debug(s"${getUserFullName(msg)}: entity ${msg.text.getOrElse("")} is known!")
            reply(String.format(commandAccepted, msg.text.getOrElse("")))
        }
      // proceed - plain text message
      case None => for {
        messageText <- msg.text
        actor <- context.child(getCompositeUserActorName(msg))
      } yield {
        // TODO remove later
        reply(s"echo of message: ${msg.text.getOrElse("default text")}")
        actor ! messageText
      }
    }
  }


  private def spawnTheChild(userId: String) = {
    context.child(userId).getOrElse(
      context.actorOf(BotUserActor.props(userId), userId)
    )
  }

  override def preStart = {
    log.debug(s"actor ${self.path.name}, Father of all Bot User Actors is here")
    run()
  }

  override def postStop = {
    log.debug(s"actor ${self.path.name} is dying")
  }

  override def receive: Receive = {
    case a => log.debug(s"actor ${self.path.name} received message:\n $a")
  }
}
