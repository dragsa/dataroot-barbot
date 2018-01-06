package org.gnat.barbot.http

import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, PoisonPill, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import com.typesafe.config.Config
import org.gnat.barbot.http.ClientHttpActor.GetTarget
import spray.json._

object ClientHttpActor {

  sealed trait HttpActorRequest

  case class GetTarget(id: Int, url: String) extends HttpActorRequest

  case class GetLocation(url: String) extends HttpActorRequest

  def props(config: Config) = Props(new ClientHttpActor(config))
}

// TODO factor out pattern "actor ${self.path.name}" into logging configuration or utils
class ClientHttpActor(config: Config) extends Actor with ClientJsonSupport with ActorLogging {

  // TODO Caching -> Http refs doubt
  // is the context always pointing to parent?
  // and thus don't we need to keep sender ref to avoid lost messages?
  // var senderRef = Option[ActorRef]
  var barId: Option[Int] = None
  val banishToValhalla = config.getBoolean("banish-to-valhalla")
  implicit val materializer = ActorMaterializer(ActorMaterializerSettings(context.system))
  val http = Http(context.system)

  import akka.pattern.pipe
  import context.dispatcher

  override def receive: Receive = {

    case gt@GetTarget(id, url) =>
      // senderRef = sender
      barId = Option(id)
      log.info(s"actor ${self.path.name} received:\n $gt")
      http.singleRequest(HttpRequest(uri = url)).pipeTo(self)

    case HttpResponse(StatusCodes.OK, headers, entity, _) =>
      // TODO do we really need streaming here?
      entity.dataBytes
        .map(_.utf8String)
        .map(_.parseJson.convertTo[List[BarStateMessage]].head)
        .runForeach(bsm => {
          log.info(s"actor ${self.path.name} sending next BarState to parent:\n $bsm")
          context.parent ! (barId, bsm)
        })
      self ! PoisonPill

    case resp@HttpResponse(code, headers, entity, _) =>
      log.info(s"actor ${self.path.name} request failed, response code:\n $code")
      resp.discardEntityBytes()
      // TODO for simplicity all non-200OK responses are now triggers for expiration
      // target in question will not be in refresh list for http.client.limbo-resurrection-timeout seconds
      val bem = BarExpiredMessage(barId.get)
      log.info(s"actor ${self.path.name} sending next BarExpired to parent:\n $bem")
      context.parent ! bem
      self ! PoisonPill

    case Failure(msg) =>
      log.info(s"actor ${self.path.name} actor failure happened:\n $msg")
      val bdm = BarDeadMessage(barId.get)
      log.info(s"actor ${self.path.name} sending next BarDead to parent:\n $bdm")
      context.parent ! BarDeadMessage(barId.get)
      self ! PoisonPill

    case um@_ =>
      log.info(s"actor ${self.path.name} received unexpected message:\n $um")
      self ! PoisonPill
  }

  override def preStart = {
    log.debug(s"actor ${self.path.name} is alive and well")
  }

  override def postStop = {
    log.debug(s"actor ${self.path.name} is dying")
  }
}
