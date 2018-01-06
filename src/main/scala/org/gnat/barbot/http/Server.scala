package org.gnat.barbot.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.gnat.barbot.http.ClientCachingActor.CachingActorStart

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

  val cacheConfig = ConfigFactory.load().getConfig("barbot.cache")
  val client = system.actorOf(ClientCachingActor.props(cacheConfig), name = "client-caching-actor")

  initDatabase
  client ! CachingActorStart

  logger.info("Started server, press enter to stop")
  StdIn.readLine()
  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())
}
