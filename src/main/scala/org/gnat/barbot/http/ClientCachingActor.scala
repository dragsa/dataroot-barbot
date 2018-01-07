package org.gnat.barbot.http

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import com.typesafe.config.Config
import org.gnat.barbot.Database
import org.gnat.barbot.http.ClientCachingActor.{CachingActorProvideCache, CachingActorRefreshCache, CachingActorStart}
import org.gnat.barbot.http.ClientHttpActor.GetTarget
import org.gnat.barbot.http.Utils._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Success

object ClientCachingActor {

  sealed trait CachingActorControlMessage

  case object CachingActorStart extends CachingActorControlMessage

  case object CachingActorRefreshCache extends CachingActorControlMessage

  case object CachingActorProvideCache extends CachingActorControlMessage

  def props(config: Config)(implicit db: Database) =
    Props(new ClientCachingActor(config))

  object CacheHolder {
    // [Bar Id, (Bar State, Bar Limb Time)] Bar Limb Time: 0 - if alive, not 0 - if is "sort of dead"
    // "sort of dead" means that target won't be included during decision calculations
    var cacheValue: Map[Int, (BarStateMessage, Long)] = Map[Int, (BarStateMessage, Long)]()

    def eligible = cacheValue.filter(target => !target._2._1.name.isEmpty && target._2._2 == 0L)

    override def toString: String = cachePrettyFormat(cacheValue)
  }

}

class ClientCachingActor(config: Config)(implicit db: Database)
  extends Actor
    with ActorLogging {

  var refreshTimer: Option[Cancellable] = None
  var cachedTargets = ClientCachingActor.CacheHolder
  var senderRef: Option[ActorRef] = None
  val cacheTimeout = config.getInt("cache-timeout")
  val limboResurrectionTimeout = config.getLong("limbo-resurrection-timeout")
  val banishToValhalla = config.getBoolean("banish-to-valhalla")

  //  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
  //    {
  //      case msg@default => if (banishToValhalla) {
  //        log.info(s"child actor process is banished to Valhalla due to ${msg.getMessage}")
  //        Stop
  //      } else {
  //        log.info("child actor process will be restarted")
  //        super.supervisorStrategy.decider.applyOrElse(default, (_: Any) => Escalate)
  //      }
  //    }
  //  }

  override def receive: Receive = {
    case CachingActorStart =>
      log.info("received Start message")
      refreshTimer = Option(
        context.system.scheduler.schedule(10 seconds,
          cacheTimeout seconds,
          self,
          CachingActorRefreshCache))

    case CachingActorRefreshCache =>
      log.info("received Refresh message")
      db.barRepository.getAllActive.map(
        bars => {
          val cachedTargetsReadyForRefresh = if (cachedTargets.cacheValue.isEmpty) {
            val initialCache = bars.map(_.id.get)
            log.debug(s"initial cache contains targets with Ids:\n ${iterableIdsPrettyFormat(initialCache)}")
            cachedTargets.cacheValue = (initialCache zip List.fill(initialCache.size)(BarStateMessage("", "", "", List(), List(), List()), 0L)).toMap
            initialCache
          }
          else {
            val currentlyWatched = cachedTargets.cacheValue.filter(target => {
              target._2._2 == 0L || System.nanoTime() - target._2._2 > limboResurrectionTimeout * 1000000000L
            }).keys.toSeq
            (bars.map(_.id.get) diff cachedTargets.cacheValue.keys.toSeq) ++ currentlyWatched
          }
          log.debug(s"currently next targets are eligible for selection algorithm:\n ${cachePrettyFormat(cachedTargets.eligible)}")
          val targetsReadyForRefresh = bars.filter(bar => cachedTargetsReadyForRefresh.contains(bar.id.get))
          log.debug(s"active targets in database:\n ${iterableIdsPrettyFormat(bars.map(_.id.get))}")
          log.debug(s"active targets in cache:\n ${iterableIdsPrettyFormat(cachedTargetsReadyForRefresh)}")
          targetsReadyForRefresh.foreach(target =>
            context.actorOf(Props(new ClientHttpActor(config))) ! GetTarget(
              target.id.get, target.infoSource))
        })

    case CachingActorProvideCache =>
      senderRef = Option(sender)
      senderRef.foreach(_ ! cachedTargets)

    // TODO type erasure here, seems to be safe due to actor messaging in place
    case bsmTuple: (Some[Int], BarStateMessage) =>
      log.info(s"received next BarState for bar ${bsmTuple._1.get} from child:\n ${bsmTuple._2}")
      cachedTargets.cacheValue = cachedTargets.cacheValue.updated(bsmTuple._1.get, (bsmTuple._2, 0L))
      log.debug(s"current cache state:\n $cachedTargets")

    case bem@BarExpiredMessage(id) =>
      log.info(s"received BarExpired from child:\n $bem")
      cachedTargets.cacheValue = cachedTargets.cacheValue.updated(id, (cachedTargets.cacheValue(id)._1, System.nanoTime()))
      log.info(s"disabling refresh of $id for $limboResurrectionTimeout seconds")

    case bdm@BarDeadMessage(id) =>
      log.info(s"received BarDead from child:\n $bdm")
      if (banishToValhalla) {
        log.info(s"bar $id is MIA, excluding from list until update by source")
        cachedTargets.cacheValue = cachedTargets.cacheValue - id
        db.barRepository.getOneById(id)
          .andThen { case Success(barOpt) => barOpt match {
            case Some(bar) => db.barRepository.updateOne(bar.copy(isActive = false))
            case None => log.info(s"unknown bar was reported Dead with id: $id")
          }
          }
      } else {
        log.info(s"will make another attempt to get info about $id soon")
      }

    case um@_ =>
      log.info(s"received unexpected message:\n $um")
  }
}
