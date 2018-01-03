package org.gnat.barbot.http

import akka.actor.{Actor, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import com.typesafe.scalalogging.LazyLogging
import org.gnat.barbot.http.ClientHttpActor.GetTarget
import spray.json._

object ClientHttpActor {

  sealed trait HttpActorRequest

  case class GetTarget(url: String) extends HttpActorRequest

  case class GetLocation(url: String) extends HttpActorRequest

  def props() = Props(new ClientHttpActor)
}

class ClientHttpActor extends Actor with ClientJsonSupport with LazyLogging {

  var senderRef = sender
  implicit val materializer = ActorMaterializer(ActorMaterializerSettings(context.system))
  val http = Http(context.system)

  import akka.pattern.pipe
  import context.dispatcher

  override def receive: Receive = {
    case GetTarget(url) => http.singleRequest(HttpRequest(uri = url)).pipeTo(self)
    case HttpResponse(StatusCodes.OK, headers, entity, _) =>
      // TODO add generic response here, there is no need for streaming
      entity.dataBytes.map(_.utf8String).map(_.parseJson.convertTo[List[BarStateMessage]].head).runForeach(println)
//      logger.info("got next bar state parsed: " + parsedBarState)
    case resp@HttpResponse(code, _, _, _) =>
      logger.info("request failed, response code: " + code)
      resp.discardEntityBytes()
  }

  override def preStart = {
    logger.debug(s"starting ${self.path} actor ")
  }

  override def postStop = {
    logger.debug(s"actor ${self.path} is dying")
  }
}
