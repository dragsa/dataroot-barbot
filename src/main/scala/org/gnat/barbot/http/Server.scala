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

  val httpServerConfig = ConfigFactory.load().getConfig("http.server")
  val host = httpServerConfig.getString("host")
  val port = httpServerConfig.getInt("port")

  val bindingFuture = Http().bindAndHandle(route, host, port)

  val httpClientConfig = ConfigFactory.load().getConfig("http.client")
  val client = system.actorOf(ClientCachingActor.props(httpClientConfig), name = "client-caching-actor")

  initDatabase
  client ! CachingActorStart

  logger.info("Started server, press enter to stop")
  StdIn.readLine()
  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())
}
