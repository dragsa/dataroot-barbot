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

object Server extends ServerApiRouter with LazyLogging {

  @volatile var keepRunning = true

  def main(args: Array[String]) {
    implicit val system = ActorSystem("barbot-system")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher
    implicit val dbRef = this

    val serverConfig = ConfigFactory.load().getConfig("barbot.server")
    val host = serverConfig.getString("host")
    val port = serverConfig.getInt("port")

    val bindingFuture = Http().bindAndHandle(route, host, port)

    implicit val barbotConfig = ConfigFactory.load().getConfig("barbot")
    val barCachingActor =
      system.actorOf(ClientCachingActor.props, name = "client-caching-actor")
    val barCrawlerBotActor = system.actorOf(
      BotDispatcherActor.props(barCachingActor),
      name = "bar-crawler-dispatcher-actor")

    initDatabase
    barCachingActor ! CachingActorStart

    logger.info("started server, waiting for SIGTERM to stop")
    val mainThread = Thread.currentThread ()
    Runtime.getRuntime.addShutdownHook (new Thread () {
      override def run = {
        logger.info("inside shutDownHook handler")
        keepRunning = false
        mainThread.join ()
        bindingFuture
          .flatMap(_.unbind())
          .onComplete(_ => system.terminate())
      }
    })
    while (keepRunning) {
    }
    logger.info("stopping server")
  }
}
