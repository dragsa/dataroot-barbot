package org.gnat.barbot.tele

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props, Terminated}
import com.typesafe.config.Config
import info.mukel.telegrambot4s.api.declarative.{Commands, Help}
import info.mukel.telegrambot4s.api.{Polling, TelegramBot}
import info.mukel.telegrambot4s.models.{Message, MessageEntityType}
import org.gnat.barbot.Database
import org.gnat.barbot.models.User
import org.gnat.barbot.tele.BotDispatcherActor.Stats
import org.gnat.barbot.tele.BotLexicon._
import org.gnat.barbot.tele.BotUserActor._

import scala.concurrent.duration._
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

  def commands = BarCrawlerBotCommands.values.map(_.toString).toList

  // internal messages
  sealed trait DispatcherBotInternal

  case object Stats extends DispatcherBotInternal

  def props(cachingActor: ActorRef)(implicit config: Config, db: Database) = Props(new BotDispatcherActor(cachingActor))
}

/* Router actor which should:
    - maintain session with Telegram using token
    - pool Telegram for incoming data
    - validate if commands executed have valid syntax
    - execute commands which do not impact user state
    - pass execution of other commands to child user actors
    - bypass messages to child actors
    */

class BotDispatcherActor(cachingActor: ActorRef)(implicit config: Config, db: Database) extends TelegramBot with Polling with Commands with Help with Actor with ActorLogging {
  lazy val token = scala.util.Properties
    .envOrNone("BOT_TOKEN")
    .getOrElse(Source.fromResource("bot.token").getLines.mkString)

  val botConfig = config.getConfig("bot")

  // TODO kind of temp hack, allowing smooth user experience
  //  the most painful debt - move on top of WebHooks implementation
  // as this one leads wo extremely unpleasant user's experience
  override def pollingInterval = botConfig.getInt("polling-interval")

  val statsTimeout = botConfig.getInt("user-actor-session-timeout")

  import BotDispatcherActor.commands

  // commands may:
  // - impact state of session
  // - provide "static" info which is not dependent on session state

  onCommandWithHelp("/hello")("keep-alive command") { implicit msg =>
    reply(helloReply)
  }

  // start session
  // may be executed by remote Telegram participant upon click on Start
  // or by sending /start to bot
  // leads to initial state
  onCommandWithHelp("/start")("starts user session") { implicit msg =>
    val compositeUserActorName = getCompositeUserActorName

    getUserId match {
      case Some(id) =>
        val userName = getUserFullName
        context.child(compositeUserActorName).flatMap { child =>
          reply(String.format(sessionAlreadyExists, userName).stripMargin)
          Some(child)
        }.orElse {
          log.info(s"spawning child actor: $compositeUserActorName")
          // check and create user if this is first encounter
          db.userRepository.getOneById(id).flatMap {
            // enforce order here
            case Some(_) => reply(String.format(greetingForRegistered, userName).stripMargin)
              .andThen { case Success(_) => reply(String.format(sessionStarted, userName).stripMargin) }
            case None =>
              db.userRepository.createOne(User(nickName = getUserNickName,
                firstName = getUserFirstName,
                lastName = getUserLastName,
                id = getUserId.get
              )).andThen { case Success(u) => log.info(s"user $u was created") }
              // enforce order here
              reply(String.format(greetingForFirstEncounter, userName).stripMargin)
                .andThen { case Success(_) => reply(String.format(sessionStarted, userName).stripMargin) }
          }
          // watch fo it
          Option(context.watch(context.actorOf(BotUserActor.props(compositeUserActorName, cachingActor), compositeUserActorName)))
        }
      case None => reply(userNotFound)
    }
  }

  // close session completely
  // "/start" is required to init new one
  onCommandWithHelp("/stop")("kills user session") { implicit msg =>
    val compositeUserActorName = getCompositeUserActorName
    context.child(compositeUserActorName) match {
      case Some(child) =>
        log.debug(s"/stop called for session $compositeUserActorName")
        child ! PoisonPill
        reply(String.format(sessionStopped, getUserFullName))
      case None => sessionNotStartedHandler
      // self ! SessionNotStarted(String.format(sessionNotStarted, getUserFullName(msg)), msg)
    }
  }

  // reset user session to initial state - like calling "/start"
  onCommandWithHelp("/reset")("resets user session") { implicit msg =>
    val compositeUserActorName = getCompositeUserActorName
    context.child(compositeUserActorName) match {
      case Some(child) =>
        log.debug(s"/suggest called for session $compositeUserActorName")
        child ! RequestResetSuggestion(msg)
      case None => sessionNotStartedHandler
    }
  }

  // starts interactive dialog with user
  onCommandWithHelp("/suggest")("starts dialog aiming to find today's bar") { implicit msg =>
    val compositeUserActorName = getCompositeUserActorName
    context.child(compositeUserActorName) match {
      case Some(child) =>
        log.debug(s"/suggest called for session $compositeUserActorName")
        child ! RequestInitSuggestion(msg)
      case None => sessionNotStartedHandler
    }
  }

  private def sessionNotStartedHandler(implicit msg: Message) = {
    log.debug(s"$getUserFullName: decision dialog not started")
    reply(String.format(sessionNotStarted, getUserFullName))
  }

  // TODO implement history of visits, another major feature touching most of the components
  // with parameters to control:
  // - how many records
  // - sorting by
  // - sorting order
  onCommandWithHelp("/history")("show history of visits for this user") { implicit msg =>
    reply(functionalityNotImplemented)
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
            log.debug(s"$getUserFullName: command ${msg.text.getOrElse("")} doesn't exist")
            reply(String.format(commandNotAccepted, msg.text.getOrElse("")))
          case None =>
            log.debug(s"$getUserFullName: entity ${msg.text.getOrElse("")} is known!")
          // reply(String.format(commandAccepted, msg.text.getOrElse("")))
        }
      // proceed if plain non-empty text message and session is established
      case None => msg.text.foreach { _ =>
        context.child(getCompositeUserActorName) match {
          case Some(actor) => actor ! RequestPayload(msg)
          case None => reply(sessionNotStarted)
        }
      }
    }
  }

  override def preStart = {
    log.debug(s"${self.path.name}, Father of all Bot User Actors is here")
    if (statsTimeout > 0) {
      context.system.scheduler.schedule(statsTimeout seconds, statsTimeout seconds, self, Stats)
    }
    run
  }

  override def postStop = {
    log.debug(s"${self.path.name} is dying")
  }

  override def receive: Receive = {
    case Stats => logger.debug(s"I am proud father of these children:\n${context.children.map(_.path.name) mkString "\n"}")

    case eQuestion@EventQuestion(text) => reply(text)(eQuestion.msg)

    case qQuestionEnd@EventQuestionEnd(text) => reply(text)(qQuestionEnd.msg)

    case eError@EventError(text) => reply(text)(eError.msg)

    case eReset@EventReset(text) => reply(text)(eReset.msg)

    case eDecision@EventDecisionMade(text) => reply(text)(eDecision.msg)

    case eTimeout@EventTimeout(text) => reply(text)(eTimeout.msg)

    case Terminated(child) => log.debug(s"user actor ${child.path.name} was terminated")

    case um@_ => log.debug(s"actor ${self.path.name} received unexpected message:\n $um")
  }
}
