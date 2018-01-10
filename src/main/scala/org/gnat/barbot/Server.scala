package org.gnat.barbot

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.gnat.barbot.http._
import org.gnat.barbot.http.ClientCachingActor._
import org.gnat.barbot.tele.BotDispatcherActor

import scala.io.StdIn

object Server extends App with ServerApiRouter with LazyLogging {

  implicit val system = ActorSystem("barbot-system")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  implicit val dbRef = this

  val serverConfig = ConfigFactory.load().getConfig("barbot.server")
  val host = serverConfig.getString("host")
  val port = serverConfig.getInt("port")

  val bindingFuture = Http().bindAndHandle(route, host, port)

  implicit val barbotConfig = ConfigFactory.load().getConfig("barbot")
  val barCachingActor = system.actorOf(ClientCachingActor.props, name = "client-caching-actor")
  val barCrawlerBotActor = system.actorOf(BotDispatcherActor.props, name = "bar-crawler-dispatcher-actor")

  initDatabase
//  barCachingActor ! CachingActorStart

  logger.info("Started server, press enter to stop")
  StdIn.readLine()
  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())
}
