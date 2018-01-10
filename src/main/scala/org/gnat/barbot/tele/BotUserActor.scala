package org.gnat.barbot.tele

import akka.actor.{ActorLogging, FSM, Props}
import com.typesafe.config.Config
import org.gnat.barbot.tele.BotUserActor._


object BotUserActor {

  sealed trait Trigger
  case object TriggerInitDecision extends Trigger
  case object TriggerResetDecision extends Trigger
//  case object UserActorTriggerStop extends UserActorTrigger
//  case object UserActorTriggerSuggest extends UserActorTrigger

  sealed trait State
  case object StateIdle extends State
  case object StateDecision extends State

  sealed trait Data
  case object DataEmpty extends Data
  case class DataDialog(routineName: String, routineStep: Int) extends Data

  def props(userId: String)(implicit config: Config) = Props(new BotUserActor(userId))
}

/* User actor which should:
    - hold the state of each user session
    - provide facilities to maintain FSM
    - all of the above include processing of commands and messages
    */

class BotUserActor(userIr: String)(implicit config: Config) extends FSM[State, Data] with ActorLogging {

  startWith(StateIdle, DataEmpty)

  when(StateIdle) {
    case Event(TriggerInitDecision, DataEmpty) =>
      goto(StateDecision) using DataEmpty
  }

  when(StateDecision) {
    case Event(TriggerResetDecision, DataEmpty) =>
      goto(StateIdle) using DataEmpty
  }

  onTransition {
//    case stateChange@(StateIdle -> StateDecision) =>
//      log.info(s"I am ${self.path.name} and my state is changing ${stateChange._1 -> stateChange._2}")
    case stateChange =>
      log.info(s"I am ${self.path.name} and my state is changing ${stateChange._1 + " -> " + stateChange._2}")

  }

  override def preStart = {
    log.debug(s"actor ${self.path.name} is alive and well")
    super.preStart
  }

  override def postStop = {
    log.debug(s"actor ${self.path.name} is dying")
    super.postStop
  }

//  override def receive: Receive = {
//    case a => log.debug(s"actor ${self.path.name} received message:\n $a")
//      super.receive
//  }
  initialize()
}
