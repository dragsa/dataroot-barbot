package org.gnat.barbot.http

import akka.actor.{Actor, ActorLogging, PoisonPill, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import org.gnat.barbot.http.ClientHttpActor.GetTarget
import spray.json._

object ClientHttpActor {

  sealed trait HttpActorRequest

  case class GetTarget(url: String) extends HttpActorRequest

  case class GetLocation(url: String) extends HttpActorRequest

  def props() = Props(new ClientHttpActor)
}

class ClientHttpActor extends Actor with ClientJsonSupport with ActorLogging {

  var senderRef = sender
  implicit val materializer = ActorMaterializer(
    ActorMaterializerSettings(context.system))
  val http = Http(context.system)

  import akka.pattern.pipe
  import context.dispatcher

  override def receive: Receive = {
    case GetTarget(url) =>
      http.singleRequest(HttpRequest(uri = url)).pipeTo(self)
    case HttpResponse(StatusCodes.OK, headers, entity, _) =>
      // TODO do we really need streaming here?
      entity.dataBytes
        .map(_.utf8String)
        .map(_.parseJson.convertTo[List[BarStateMessage]].head)
        .runForeach(println)
      self ! PoisonPill
    case resp @ HttpResponse(code, _, _, _) =>
      log.info("request failed, response code: " + code)
      resp.discardEntityBytes()
      self ! PoisonPill
    case _ => self ! PoisonPill
  }

  override def preStart = {
    log.debug(s"starting ${self.path} actor ")
  }

  override def postStop = {
    log.debug(s"actor ${self.path} is dying")
  }
}