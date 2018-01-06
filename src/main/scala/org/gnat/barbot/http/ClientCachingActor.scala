package org.gnat.barbot.http

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import com.typesafe.config.Config
import org.gnat.barbot.Database
import org.gnat.barbot.http.ClientCachingActor.{CachingActorProvideCache, CachingActorRefreshCache, CachingActorStart}
import org.gnat.barbot.http.ClientHttpActor.GetTarget

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object ClientCachingActor {

  sealed trait CachingActorControlMessage

  case object CachingActorStart extends CachingActorControlMessage

  case object CachingActorRefreshCache extends CachingActorControlMessage

  case object CachingActorProvideCache extends CachingActorControlMessage

  def props(config: Config)(implicit db: Database) =
    Props(new ClientCachingActor(config))
}

class ClientCachingActor(config: Config)(implicit db: Database)
  extends Actor
    with ActorLogging {

  var refreshTimer: Option[Cancellable] = None
  // [Bar Id, (Bar State, Bar Death Time)] Bar Death Time: 0 - if alive, not 0 - if dead
  // TODO good toString method
  var cachedTargets: Map[Int, (BarStateMessage, Long)] = Map[Int, (BarStateMessage, Long)]()
  var senderRef: Option[ActorRef] = None
  val cacheTimeout = config.getInt("cache-timeout")
  val resurrectionTimeout = config.getLong("resurrection-timeout")

  override def receive: Receive = {
    case CachingActorStart =>
      log.info("received Start message")
      refreshTimer.foreach(_.cancel)
      refreshTimer = Option(
        context.system.scheduler.schedule(10 seconds,
          cacheTimeout seconds,
          self,
          CachingActorRefreshCache))
      db.barRepository.getAllActive.map(
        bars => bars.map(_.id.get)).map(initialCache => {
        cachedTargets = (initialCache zip List.fill(initialCache.size)(BarStateMessage("", "", "", List(), List(), List()), 0L)).toMap
        log.debug(s"initial cache contains targets with Ids:\n $initialCache")
      })
    case CachingActorRefreshCache =>
      log.info("received Refresh message")
      db.barRepository.getAllActive.map(
        bars => {
          val cachedTargetsReadyForRefresh = cachedTargets.filter(target => {
            target._2._2 == 0L || System.nanoTime() - target._2._2 > resurrectionTimeout * 1000000000L
          })
          val targetsReadyForRefresh = bars.filter(bar => cachedTargetsReadyForRefresh.contains(bar.id.get))
          log.debug(s"active targets in database:\n ${targetsReadyForRefresh.map(_.id).map(_.get)}")
          targetsReadyForRefresh.foreach(target =>
            context.actorOf(Props(new ClientHttpActor)) ! GetTarget(
              target.id.get, target.infoSource))
        })
    case CachingActorProvideCache =>
      senderRef = Option(sender)
      senderRef.foreach(_ ! cachedTargets)
    case bsmTuple: (Some[Int], BarStateMessage) =>
      log.info(s"received next BStateM for bar ${bsmTuple._1.get} from child:\n ${bsmTuple._2}")
      cachedTargets = cachedTargets.updated(bsmTuple._1.get, (bsmTuple._2, 0L))
      log.debug(s"current cache state:\n $cachedTargets")
    case bem@BarExpiredMessage(id) =>
      log.info(s"received BExpiredM from child:\n $bem")
      cachedTargets = cachedTargets.updated(id, (cachedTargets(id)._1, System.nanoTime()))
      log.info(s"disabling refresh of $id for $resurrectionTimeout seconds")
    case um@_ =>
      log.info(s"received unexpected message:\n $um")
  }

  private def updateTargetsCache = ???
}
