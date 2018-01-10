package org.gnat.barbot.tele

import akka.actor.{Actor, ActorLogging, PoisonPill, Props, Terminated}
import com.typesafe.config.Config
import info.mukel.telegrambot4s.api.declarative.{Commands, Help}
import info.mukel.telegrambot4s.api.{Polling, TelegramBot}
import info.mukel.telegrambot4s.models.{Message, MessageEntityType}
import org.gnat.barbot.Database
import org.gnat.barbot.models.User
import org.gnat.barbot.tele.BotAlphabet._
import org.gnat.barbot.tele.BotUserActor._

import scala.io.Source
import scala.util.Success

object BotDispatcherActor {

  object BarCrawlerBotCommands extends Enumeration {

    // TODO add removal of user from bot dialog

    // stateful
    val start = Value("/start")
    val stop = Value("/stop")
    val reset = Value("/reset")
    val suggest = Value("/suggest")

    // stateless
    val help = Value("/help")
    val hello = Value("/hello")
    val history = Value("/history")
  }

  // TODO sending messages to self and then reply doesn't seem to help with delays at all
  //  trait DispatcherReply
  //
  //  case class SessionNotStarted(str: String, msg: Message) extends DispatcherReply
  //
  //  case class Greeting(str: String, msg: Message) extends DispatcherReply

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

// TODO the most painful TODO - move on top of WebHooks implementation as this one leads wo extremely unpleasant user's experience

class BotDispatcherActor(implicit config: Config, db: Database) extends TelegramBot with Polling with Commands with Help with Actor with ActorLogging {
  lazy val token = scala.util.Properties
    .envOrNone("BOT_TOKEN")
    .getOrElse(Source.fromResource("bot.token").getLines().mkString)

  val botConfig = config.getConfig("bot")

  // TODO kind of temp hack, allowing smooth user experience
  override def pollingInterval = botConfig.getInt("polling-interval")

  import BotDispatcherActor.commands

  // commands may:
  // - impact state of session
  // - provide "static" info which is not dependent on session state

  onCommandWithHelp("/hello")("keep-alive command") { implicit msg =>
    reply(helloReply)
  }

  // start session, executed by remote Telegram participant upon click on Start, leads to initial state
  onCommandWithHelp("/start")("starts user session") { implicit msg =>
    val compositeUserActorName = getCompositeUserActorName(msg)
    getUserId(msg) match {
      case Some(id) =>
        val userName = getUserFullName(msg)
        context.child(compositeUserActorName).getOrElse {
          log.info(s"spawning child actor: $compositeUserActorName")
          // create user if this is first encounter
          db.userRepository.getOneById(id).flatMap {
            case Some(_) => reply(String.format(greetingForRegistered, userName).stripMargin)
            case None =>
              db.userRepository.createOne(User(nickName = getUserNickName(msg),
                firstName = getUserFirstName(msg),
                lastName = getUserLastName(msg),
                id = getUserId(msg).get
              )).andThen { case Success(u) => log.info(s"user $u was created") }
              reply(String.format(greetingForFirstEncounter, userName).stripMargin)
          }
          context.watch(context.actorOf(BotUserActor.props(compositeUserActorName), compositeUserActorName))
//          Some(childActor)
        }
      case None => reply(userNotFound)
    }
  }

  // close session completely, "/start" is required to init new one
  onCommandWithHelp("/stop")("kills user session") { implicit msg =>
    val compositeUserActorName = getCompositeUserActorName(msg)
    context.child(compositeUserActorName) match {
      case Some(child) =>
        log.debug(s"/stop called for session $compositeUserActorName")
        child ! PoisonPill
        reply(String.format(goodbye, getUserFullName(msg)))
      case None => sessionNotStartedHandler
      //        self ! SessionNotStarted(String.format(sessionNotStarted, getUserFullName(msg)), msg)
    }
  }

  // reset user session to initial state - like calling "/start"
  onCommandWithHelp("/reset")("resets user session") { implicit msg =>
    val compositeUserActorName = getCompositeUserActorName(msg)
    context.child(compositeUserActorName) match {
      case Some(child) =>
        log.debug(s"/suggest called for session $compositeUserActorName")
        child ! TriggerResetDecision
        reply(String.format(greetingForRegistered, getUserFullName(msg)).stripMargin)
      case None => sessionNotStartedHandler
    }
  }

  // starts interactive dialog with user
  onCommandWithHelp("/suggest")("starts dialog aiming to find today's bar") { implicit msg =>
    val compositeUserActorName = getCompositeUserActorName(msg)
    context.child(compositeUserActorName) match {
      case Some(child) =>
        log.debug(s"/suggest called for session $compositeUserActorName")
        child ! TriggerInitDecision
        reply(String.format(sessionStarted, getUserFirstName(msg)).stripMargin)
      case None => sessionNotStartedHandler
    }
  }

  private def sessionNotStartedHandler(implicit msg: Message) = {
    log.debug(s"${getUserFullName(msg)}: session not started")
    reply(String.format(sessionNotStarted, getUserFullName(msg)))
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
    log.info(s"got message from API:\n $msg")
    msg.entities match {
      // inform user if command is known on server side
      case Some(entitiesList) =>
        entitiesList.find(entity =>
          entity.`type`.equals(MessageEntityType.BotCommand) && !msg.text.exists(commandName => commands.contains(commandName))) match {
          case Some(_) =>
            log.debug(s"${getUserFullName(msg)}: command ${msg.text.getOrElse("")} doesn't exist")
            reply(String.format(commandNotAccepted, msg.text.getOrElse("")))
          case None =>
            log.debug(s"${getUserFullName(msg)}: entity ${msg.text.getOrElse("")} is known!")
            reply(String.format(commandAccepted, msg.text.getOrElse("")))
        }
      // proceed - plain text message
      case None => for {
        messageText <- msg.text
        actor <- context.child(getCompositeUserActorName(msg))
      } yield {
        // TODO remove echo later
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
    //    case SessionNotStarted(text, msg) => reply(text)(msg)
    case Terminated(child) => log.debug(s"user actor ${child.path.name} was terminated")
    case um@_ => log.debug(s"actor ${self.path.name} received message:\n $um")
  }
}
