package org.gnat.barbot

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.io.StdIn

object WebServer extends App with ApiRouter with LazyLogging {

    implicit val system = ActorSystem("barbot-system")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    val httpServerConfig = ConfigFactory.load().getConfig("http-server")
    val host = httpServerConfig.getString("host")
    val port = httpServerConfig.getInt("port")

    val bindingFuture = Http().bindAndHandle(route, host, port)

    initDatabase

    logger.info("Started server, press enter to stop")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  }
