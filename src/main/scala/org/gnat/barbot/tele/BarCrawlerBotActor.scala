package org.gnat.barbot.tele

import akka.actor.{Actor, Props}
import com.typesafe.config.Config
import info.mukel.telegrambot4s.api.declarative.Commands
import info.mukel.telegrambot4s.api.{Polling, TelegramBot}
import org.gnat.barbot.tele.BarCrawlerBotPhrases._

import scala.io.Source

class BarCrawlerBotActor(implicit config: Config) extends TelegramBot with Polling with Commands with Actor {
  lazy val token = scala.util.Properties
    .envOrNone("BOT_TOKEN")
    .getOrElse(Source.fromResource("bot.token").getLines().mkString)

  val botConfig = config.getConfig("bot")

  onCommand('hello) { implicit msg =>
    reply(helloReply)
  }

  onCommand('start) { implicit msg =>
    reply(String.format(firstEncounter, msg.from.flatMap(u => Option(u.firstName)).getOrElse("Anonymous Alcohol Seeker")).stripMargin)
  }

  onMessage { implicit msg =>
    //    msg.from.map(_.id.toString).foreach { userId =>
    //      val userActor = context
    //        .child(userId)
    //        .getOrElse(
    //          context.actorOf(UserActor.props(userId), userId)
    //        )
    //      userActor ! UserMessage(msg.text.getOrElse(":)"))
    //    }
    logger.info(s"got message: $msg")
  }

  override def preStart = {
    logger.debug(s"actor ${self.path.name} is alive and well")
    logger.debug(s"config: $config")
    run()
  }

  override def postStop = {
    logger.debug(s"actor ${self.path.name} is dying")
  }

  override def receive: Receive = {
    case a => logger.debug(s"actor ${self.path.name} received message\n $a")
  }
}


object BarCrawlerBotActor {
  def props(implicit config: Config) = Props(new BarCrawlerBotActor)
}
