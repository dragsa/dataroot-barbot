package org.gnat.barbot.http

import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, PoisonPill, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import org.gnat.barbot.http.ClientHttpActor.GetTarget
import spray.json._

object ClientHttpActor {

  sealed trait HttpActorRequest

  case class GetTarget(id: Int, url: String) extends HttpActorRequest

  case class GetLocation(url: String) extends HttpActorRequest

  def props() = Props(new ClientHttpActor)
}

class ClientHttpActor extends Actor with ClientJsonSupport with ActorLogging {

  // TODO is the context always pointing to parent
  // and don't we need to keep sender ref to avoid lost messages?
  var barId: Option[Int] = None
  implicit val materializer = ActorMaterializer(
    ActorMaterializerSettings(context.system))
  val http = Http(context.system)

  import akka.pattern.pipe
  import context.dispatcher

  override def receive: Receive = {
    case GetTarget(id, url) =>
      //      senderRef = sender
      barId = Option(id)
      http.singleRequest(HttpRequest(uri = url)).pipeTo(self)
    case HttpResponse(StatusCodes.OK, headers, entity, _) =>
      // TODO do we really need streaming here?
      entity.dataBytes
        .map(_.utf8String)
        .map(_.parseJson.convertTo[List[BarStateMessage]].head)
        .runForeach(bsm => {
          log.info(s"sending next BStatM to parent $bsm")
          context.parent ! bsm
        })
      self ! PoisonPill
    case resp@HttpResponse(code, headers, entity, _) =>
      log.info(s"request failed, response code: $code")
      resp.discardEntityBytes()
      val bem = BarExpiredMessage(barId.get)
      log.info(s"sending next BExpM to parent: $bem")
      context.parent ! bem
      self ! PoisonPill
    case Failure(msg) =>
      log.info(s"actor failure happened: $msg")
    case um@_ =>
      log.info(s"received unexpected message $um")
      self ! PoisonPill
  }

  override def preStart = {
    log.debug(s"actor ${self.path} is alive and well")
  }

  override def postStop = {
    log.debug(s"actor ${self.path} is dying")
  }
}
