package org.gnat.barbot.tele

import akka.actor.{Actor, ActorLogging, PoisonPill, Props}
import com.typesafe.config.Config
import info.mukel.telegrambot4s.api.declarative.{Commands, Help}
import info.mukel.telegrambot4s.api.{Polling, TelegramBot}
import info.mukel.telegrambot4s.models.MessageEntityType
import org.gnat.barbot.Database
import org.gnat.barbot.models.User
import org.gnat.barbot.tele.BotAlphabet._

import scala.io.Source
import scala.util.Success

object BotDispatcherActor {

  object BarCrawlerBotCommands extends Enumeration {
    val help = Value("/help")
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

// TODO the most painful TODO - move on top of WebHooks implementaion as this one leads wo extremely unpleasant user's experience

class BotDispatcherActor(implicit config: Config, db: Database) extends TelegramBot with Polling with Commands with Help with Actor with ActorLogging {
  lazy val token = scala.util.Properties
    .envOrNone("BOT_TOKEN")
    .getOrElse(Source.fromResource("bot.token").getLines().mkString)

  val botConfig = config.getConfig("bot")

  import BotDispatcherActor.commands

  // commands may:
  // - impact state of session
  // - provide "static" info which is not dependent on session state

  onCommandWithHelp("/hello")("keep-alive command") { implicit msg =>
    reply(helloReply)
  }

  // start session, executed by remote Telegram participant upon click on Start, leads to initial state
  onCommandWithHelp("/start")("starts user session") { implicit msg =>
    getUserId(msg) match {
      case Some(id) =>
        val userName = getUserFullName(msg)
        val compositeUserActorName = getCompositeUserActorName(msg)
        context.child(compositeUserActorName).getOrElse {
          logger.info(s"spawning child actor: $compositeUserActorName")
          // create user if this is first encounter
          db.userRepository.getOneById(id).flatMap {
            case Some(_) => reply(String.format(greetingForRegistered, userName).stripMargin)
            case None =>
              db.userRepository.createOne(User(nickName = getUserNickName(msg),
                firstName = getUserFirstName(msg),
                lastName = getUserLastName(msg),
                id = getUserId(msg).get
              )).andThen { case Success(u) => logger.info(s"user $u was created") }
              reply(String.format(greetingsForFirstEncounter, userName).stripMargin)
          }
          // TODO extract data and switch to case class passing here
          context.child(compositeUserActorName).getOrElse {
            val childActor = context.actorOf(BotUserActor.props(compositeUserActorName), compositeUserActorName)
            context.watch(childActor)
            childActor
          } ! msg
        }
      case None => reply(userNotFound)
    }
  }

  // close session completely, "/start" is required to init new one
  onCommandWithHelp("/stop")("kills user session") { implicit msg =>
    context.child(getCompositeUserActorName(msg)).foreach {
      logger.debug(s"/stop called for session ${getCompositeUserActorName(msg)}")
      reply(String.format(goodbye, getUserFullName(msg)))
      _ ! PoisonPill
    }
  }

  // resets user session to initial state - like "/start" run
  onCommandWithHelp("/reset")("resets user session") { implicit msg =>
    context.child(getCompositeUserActorName(msg)).foreach {
      logger.debug(s"/reset called for session ${getCompositeUserActorName(msg)}")
      reply(String.format(goodbye, getUserFullName(msg)))
      // TODO extract data and switch to case class passing here
      _ ! "Dummy"
    }
  }

  // starts interactive dialog with user
  onCommandWithHelp("/suggest")("starts dialog aiming to find today's bar") { implicit msg =>
    // TODO
    ???
  }

  // provide history of visits
  // TODO add parameters to control:
  // - how many records
  // - sorting by
  // - sorting order
  onCommandWithHelp("/history")("show history of visits for this user") { implicit msg =>
    // TODO
    ???
  }

  // messages effect depends session state in which those are executed
  // also unknown commands are signaled to user in this handler

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
