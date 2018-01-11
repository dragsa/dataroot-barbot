package org.gnat.barbot.tele

import akka.actor.{ActorLogging, FSM, Props}
import com.typesafe.config.Config
import info.mukel.telegrambot4s.models.Message
import org.gnat.barbot.Database
import org.gnat.barbot.tele.BotAlphabet._
import org.gnat.barbot.tele.BotUserActor._

import scala.concurrent.ExecutionContext.Implicits.global


object BotUserActor {

  // incoming events
  sealed trait Trigger

  case class TriggerInitDecision(msg: Message) extends Trigger

  case object TriggerResetDecision extends Trigger

  case class TriggerPayload(msg: Message) extends Trigger

  // states
  sealed trait State

  case object StateIdle extends State

  case object StateDecision extends State

  // data
  sealed trait Data

  case object DataEmpty extends Data

  case class DataDialog(routineName: String, routineStep: Int, messageHistory: List[Message]) extends Data

  // outgoing events
  sealed trait Event

  case class EventQuestion(text: String)(implicit val msg: Message) extends Event

  case class EventRest(msg: Message) extends Event

  def props(userId: String)(implicit config: Config, db: Database) = Props(new BotUserActor(userId))
}

/* User actor which should:
    - hold the state of each user session
    - provide facilities to maintain FSM
    - all of the above include processing of commands and messages
    */

class BotUserActor(userId: String)(implicit config: Config, db: Database) extends FSM[State, Data] with ActorLogging {

  def getFlows = db.flowRepository.getAll

  startWith(StateIdle, DataEmpty)

  when(StateIdle) {
    case Event(TriggerInitDecision(msg), DataEmpty) => {
      implicit val msgRef = msg
      //      messageHistory.push(msg)
      for {flows <- getFlows} yield
        context.parent ! EventQuestion((String.format(decisionDialogStarted, getUserFirstName) +
          (flows.map(f => "|" + f.name + " -> " + f.description) mkString "\n")).stripMargin)
      goto(StateDecision) using DataEmpty
    }
  }

  when(StateDecision) {
    case Event(TriggerResetDecision, _) =>
      //      context.parent ! EventRest(messageHistory.pop)
      goto(StateIdle) using DataEmpty
    case Event(TriggerPayload(msg), DataEmpty) =>
      goto(StateDecision) using DataDialog(msg.text.get, 0, msg :: List())
  }

  onTransition {
    //    case stateChange@(StateIdle -> StateDecision) =>
    //      log.info(s"I am ${self.path.name} and my state is changing ${stateChange._1 -> stateChange._2}")
    case stateChange =>
      log.info(s"I am ${self.path.name}:\n state is changing ${stateChange._1 + " -> " + stateChange._2}\n current data is $stateData\n next data is $nextStateData")
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
