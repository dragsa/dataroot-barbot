package org.gnat.barbot.tele

import akka.actor.{ActorLogging, ActorRef, Cancellable, FSM, PoisonPill, Props}
import com.typesafe.config.Config
import info.mukel.telegrambot4s.models.Message
import org.gnat.barbot.Database
import org.gnat.barbot.http.BarStateMessage
import org.gnat.barbot.http.ClientCachingActor.{CachingActorCache, CachingActorProvideCache}
import org.gnat.barbot.tele.BotLexicon._
import org.gnat.barbot.tele.BotUserActor._
import org.joda.time.{Interval, LocalDate, LocalDateTime, LocalTime}

import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, ExecutionContext}

object BotUserActor {

  // incoming requests
  sealed trait Request

  case class RequestInitSuggestion(msg: Message) extends Request

  case class RequestResetSuggestion(msg: Message) extends Request

  case class RequestPayload(msg: Message) extends Request

  // states
  sealed trait State

  case object StateIdle extends State

  case object StateDialog extends State

  case object StateDecision extends State

  // data
  sealed trait Data

  case object DataEmpty extends Data

  case class DataDialog(routineName: String,
                        routineStepsLeft: List[String],
                        dialogHistory: Map[String, String]) extends Data

  case class DataDecision(msg: Message, dialogResultWithWeights: Map[String, String]) extends Data

  // outgoing events
  // no real need for different events as of now
  // there is no FSM on dispatcher side, just pass through of text to user
  // but keeping it as it is
  sealed trait Event

  case class EventQuestion(text: String)(implicit val msg: Message) extends Event

  case class EventQuestionEnd(text: String)(implicit val msg: Message) extends Event

  case class EventDecisionMade(text: String)(implicit val msg: Message) extends Event

  case class EventError(text: String)(implicit val msg: Message) extends Event

  case class EventReset(text: String)(implicit val msg: Message) extends Event

  case class EventTimeout(text: String)(implicit val msg: Message) extends Event

  // internal messages
  sealed trait UserBotInternal

  case object SessionTimeout extends UserBotInternal

  def props(userId: String, cachingActor: ActorRef)(implicit config: Config, db: Database) = Props(new BotUserActor(userId, cachingActor))

}

/* User actor which should:
    - hold the state of each user session
    - provide facilities to maintain FSM
    - all of the above include processing of commands and messages
    */

class BotUserActor(userId: String, cachingActor: ActorRef)(implicit config: Config, db: Database) extends FSM[State, Data] with ActorLogging {

  // TODO remove blocking code in favor of DB access failures
  // not that flexible, but avoiding separate threads in replies to parent is mandatory
  val actualFlows = Await.result(db.flowRepository.getAll, Duration.Inf)
  val botConfig = config.getConfig("bot")
  val sessionTimeout = botConfig.getInt("user-actor-session-timeout")

  var timeoutEvent: Option[Cancellable] = None
  var lastMessage: Option[Message] = None

  import scala.collection.JavaConverters._

  // sensible default values for absent options if this will happen
  // this combination drops impact of that option to 0
  val prioritiesConfiguration = botConfig.getObject("factor-priorities").unwrapped()
    .asScala.toMap.map { case (k, v) => (k, v.asInstanceOf[Int]) }
    .withDefaultValue(0)
  val functionsConfiguration = botConfig.getObject("factor-functions").unwrapped()
    .asScala.toMap.map { case (k, v) => (k, v.asInstanceOf[String]) }
    .withDefaultValue("or")
  val provideZeroValueTargets = botConfig.getBoolean("zero-value-targets")

  startWith(StateIdle, DataEmpty)

  when(StateIdle) {
    case Event(RequestInitSuggestion(msg), DataEmpty) =>
      implicit val msgRef = msg
      goto(StateDialog) using DataEmpty replying {
        EventQuestion((String.format(decisionDialogStarted, getUserFirstName) +
          (actualFlows.map(f => "|" + f.name + " -> " + f.description) mkString "\n")).stripMargin)
      }

    case Event(RequestResetSuggestion(msg), _) =>
      implicit val msgRef = msg
      goto(StateIdle) using DataEmpty replying EventReset(sessionRestartedInIdle + "\n\n" +
        String.format(sessionStarted, getUserFirstName).stripMargin)

    case Event(RequestPayload(msg), _) =>
      implicit val msgRef = msg
      stay replying EventError(String.format(sessionStarted, getUserFirstName).stripMargin)
  }

  when(StateDialog) {
    case Event(RequestInitSuggestion(msg), _) =>
      implicit val msgRef = msg
      stay replying EventError(String.format(decisionDialogAlreadyExists, getUserFirstName).stripMargin)

    case Event(RequestResetSuggestion(msg), _) =>
      implicit val msgRef = msg
      goto(StateDialog) using DataEmpty replying EventReset(sessionRestartedInDialog + "\n\n" +
        (String.format(decisionDialogStarted, getUserFirstName) +
          (actualFlows.map(f => "|" + f.name + " -> " + f.description) mkString "\n")).stripMargin)

    case Event(RequestPayload(msg), DataEmpty) =>
      // first step in dialog - flow choice should be made
      // Option.get is safe here - no empty inputs are propagated due to DispatcherActor logic in front
      implicit val msgRef = msg
      actualFlows.find(_.name == msg.text.get) match {
        case Some(flow) =>
          val flowSteps = flow.steps.split(",").toList
          goto(StateDialog) using DataDialog(msg.text.get, flowSteps, Map()) replying
            EventQuestion(String.format(questions(flowSteps.headOption.getOrElse("default")), getUserFirstName))
        case None =>
          stay replying EventError(s"flow '${msg.text.get}' doesn't exists")
      }

    case Event(RequestPayload(msg), dd@DataDialog(flow, stepsLeft, history)) =>
      // TODO user input validation needed, EventError can be used here
      // "recursive" steps in dialog - all flow steps steps should be fulfilled
      // Option.get is safe here - no empty inputs are propagated due to DispatcherActor logic in front
      implicit val msgRef = msg
      if (stepsLeft.length == 1) {
        goto(StateDecision) using DataDecision(msg, history + (stepsLeft.head -> msg.text.get)) replying
          EventQuestionEnd(s"calculating, please be patient...")
      } else {
        goto(StateDialog) using dd.copy(flow, stepsLeft.tail, history + (stepsLeft.head -> msg.text.get)) replying
          EventQuestion(String.format(questions(stepsLeft.tail.head), getUserFirstName))
      }
  }

  when(StateDecision) {
    case Event(RequestResetSuggestion(msg), _) =>
      implicit val msgRef = msg
      goto(StateDialog) using DataEmpty replying EventReset(sessionRestartedInDialog + "\n\n" +
        (String.format(decisionDialogStarted, getUserFirstName) +
          (actualFlows.map(f => "|" + f.name + " -> " + f.description) mkString "\n")).stripMargin)

    case Event(RequestInitSuggestion(msg), _) =>
      implicit val msgRef = msg
      stay replying EventError(String.format(decisionDialogAlreadyExists, getUserFirstName).stripMargin)

    case Event(CachingActorCache(cache), DataDecision(msg, dialogHistory)) =>
      implicit val msgRef = msg
      log.info(s"got next cache from sibling:\n ${cache mkString "\\n"}")
      val barSortedByWeight = cache.map(barEvaluator(_, dialogHistory))
        .filter(bar => if (provideZeroValueTargets) bar._1 >= 0.0 else bar._1 > 0.0)
        .sortBy(_._1)
        .reverse.map(bar => s"score: ${bar._1}, bar: ${bar._2}, site: ${bar._3}")
      context.parent ! EventDecisionMade(if (barSortedByWeight.isEmpty) String.format(noTargetMatched, getUserFirstName) else barSortedByWeight mkString "\n")
      stay
  }

  def booleanToDouble(b: Boolean) = if (b) 1D else 0D

  private def barEvaluator(barState: BarStateMessage, dialogHistory: Map[String, String]): (Double, String, String) = {
    val weights = dialogHistory.map {
      // TODO parameters validation done during input collection should exclude any parsing issues which might occur in code below
      // see case Event(RequestPayload(msg), dd@DataDialog(flow, stepsLeft, history))
      case (entityName, entityCollectedValue) => (entityName, (entityName match {
        case "location" => booleanToDouble(barState.location == entityCollectedValue)
        case "openHours" =>
          // start is always today's date both for requestor and target
          val startDate = new LocalDate()

          val (targetStartTime, targetStopTime) = {
            val times = barState.openHours.split("-").map(a => LocalTime.parse(a))
            (times.head, times.tail.head)
          }
          // TODO there is definitely a way to simplify this mess
          val targetStopDate = if (targetStopTime.isBefore(targetStartTime)) startDate.plusDays(1) else startDate
          val targetStartLdt = new LocalDateTime(startDate.getYear, startDate.getMonthOfYear, startDate.getDayOfMonth,
            targetStartTime.getHourOfDay, targetStartTime.getMinuteOfHour, targetStartTime.getSecondOfMinute)
          val targetStopLdt = new LocalDateTime(targetStopDate.getYear, targetStopDate.getMonthOfYear, targetStopDate.getDayOfMonth,
            targetStopTime.getHourOfDay, targetStopTime.getMinuteOfHour, targetStopTime.getSecondOfMinute)
          val targetInterval = new Interval(targetStartLdt.toDateTime, targetStopLdt.toDateTime)
          log.debug(s"target openHours are: $targetInterval")

          val (requestedStartTime, requestedStopTime) = {
            val times = entityCollectedValue.split("-").map(a => LocalTime.parse(a))
            (times.head, times.tail.head)
          }
          val requestedStopDate = if (requestedStopTime.isBefore(requestedStartTime)) startDate.plusDays(1) else startDate
          val requestedStartLdt = new LocalDateTime(startDate.getYear, startDate.getMonthOfYear, startDate.getDayOfMonth,
            requestedStartTime.getHourOfDay, requestedStartTime.getMinuteOfHour, requestedStartTime.getSecondOfMinute)
          val requestedStopLdt = new LocalDateTime(requestedStopDate.getYear, requestedStopDate.getMonthOfYear, requestedStopDate.getDayOfMonth,
            requestedStopTime.getHourOfDay, requestedStopTime.getMinuteOfHour, requestedStopTime.getSecondOfMinute)
          val requestedInterval = new Interval(requestedStartLdt.toDateTime, requestedStopLdt.toDateTime)
          log.debug(s"requested openHours are: $requestedInterval")

          // might return null, wrapping in Option
          val requestedIntervalFulfilment = (Option(requestedInterval.overlap(targetInterval))
          match {
            case Some(duration) => duration.toDuration.getStandardMinutes.toDouble
            case None => 0D
          }) / requestedInterval.toDuration.getStandardMinutes.toDouble
          log.debug(s"overlap is ${Option(targetInterval.overlap(requestedInterval))} which covers $requestedIntervalFulfilment of requested")
          requestedIntervalFulfilment
        case "placesAvailable" => booleanToDouble(barState.placesAvailable >= Integer.parseInt(entityCollectedValue))
        case "cuisine" => booleanToDouble(barState.cuisine.contains(entityCollectedValue))
        case "wine" => booleanToDouble(barState.wineList.contains(entityCollectedValue))
        case "beer" => booleanToDouble(barState.beersList.contains(entityCollectedValue))
      }) * prioritiesConfiguration(entityName))
    }


    val (addFactors, multFactors) = weights.partition { case (name, _) => functionsConfiguration(name) == "or" }
    log.debug(s"\n for target:\n\t$barState\n with dialog inputs:\n\t(${dialogHistory mkString ", "})\n OR factors:\n\t$addFactors\n AND factors:\n\t$multFactors")
    // matching is not really needed here, but in case of any further partitioning keeping as is
    val addFactorsApplied = addFactors.foldLeft(botConfig.getDouble("base-factor-priority"))((result: Double, weight: (String, Double)) => functionsConfiguration(weight._1) match {
      case "or" => result + weight._2
    })
    // matching is not really needed here, but in case of any further partitioning keeping as is
    val multFactorsApplied = multFactors.foldLeft(addFactorsApplied)((result: Double, weight: (String, Double)) => functionsConfiguration(weight._1) match {
      case "and" => result * weight._2
    })
    (multFactorsApplied, barState.name, barState.site)
  }

  onTransition {
    case stateChange@(StateDialog -> StateDecision) =>
      log.info(s"I am ${self.path.name}:\n state is changing\n\t${stateChange._1 + " -> " + stateChange._2}\n current data is\n\t$stateData\n next data is\n\t$nextStateData")
      log.info(s"calculating possible variants")
      cachingActor ! CachingActorProvideCache

    case stateChange =>
      log.info(s"I am ${self.path.name}:\n state is changing\n\t${stateChange._1 + " -> " + stateChange._2}\n current data is $stateData\n next data is\n\t$nextStateData")
  }

  implicit val ec: ExecutionContext = context.dispatcher

  override def receive: Receive = {
    case SessionTimeout =>
      log.debug(s"${self.path.name} there was no input from user for $sessionTimeout seconds, my heart is broken")
      lastMessage.foreach(lm => context.parent ! EventTimeout(sessionStoppedDueTimeout)(lm))
      self ! PoisonPill
    case mes =>
      log.debug(s"${self.path.name} got some input from user, my heart is beating")
      timeoutEvent.foreach(_.cancel)
      timeoutEvent = Option(context.system.scheduler.scheduleOnce(sessionTimeout seconds, self, SessionTimeout))
      mes match {
        // ugly, but can't bind arg of constructor in case of multi-match
        // arg is needed for proper reply
        case RequestInitSuggestion(body) => lastMessageHandler(body)
        case RequestResetSuggestion(body) => lastMessageHandler(body)
        case RequestPayload(body) => lastMessageHandler(body)
        case _ => log.debug(s"last message won't change $lastMessage")
      }
      super.receive(mes)
  }

  private def lastMessageHandler(body: Message) = {
    lastMessage = Option(body)
    log.debug(s"last message is now $lastMessage")
  }

  override def preStart = {
    log.debug(s"${self.path.name} got initial input from user, my heart starts beating")
    timeoutEvent = Option(context.system.scheduler.scheduleOnce(sessionTimeout seconds, self, SessionTimeout))
    super.preStart
  }

  override def postStop = {
    log.debug(s"${self.path.name} is dying")
    super.postStop
  }

  initialize
}
