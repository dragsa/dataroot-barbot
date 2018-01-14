System.nanoTime()
943776208911L
120000000000L
System.nanoTime() - 943776208911L

val firstEncounter =
  s"""Hello, %s!
     | it is the first time we see each other.
     | I hope I will be able to assist you
     | in getting wasted today and many other days.
    """
String.format(firstEncounter, "TEST")
'test

"Andrii Gnatiuk".replaceAll("\\s", "")

val decisionDialogStarted =
  s"""hey, %s!
     |make a choice of questionnaire length:
    """

val flows = List(("basic", "short, fast but less accurate"),
  ("full", "painful, slow but precise"))
(String.format(decisionDialogStarted, "Andrii") + (flows.map(f => "|" + f._1 + " -> " + f._2) mkString "\n")).stripMargin
flows.flatMap(_._2.split(","))

Map("location" -> "%s, how far the place can be located?\nyou can reply: 'near', 'far' or 'middle'",
  "openHours" -> "%s, what are ideal open hours for you?\nin 'HH:MM-HH:MM' format, please",
  "placesAvailable" -> "%s, how many guests will be there?",
  "beer" -> "%s, what kind of bear do you prefer?\nI've heard that now in Kiev there are only: 'red', 'cider', 'light, 'dark' or 'craft'",
  "wine" -> "%s, what kind of wine do you prefer?\nrumors are that you can find: 'red dry', 'white dry', 'red semi-sweet', 'white sweet'",
  "cuisine" -> "%s, what is your favorite cuisine?\nchoices are: 'italian', 'european', 'czech', 'jewish'",
  "default" -> "%s, how are you? we are fixing issue on server side...").keys

import org.gnat.barbot.http.BarStateMessage
import org.joda.time.{Interval, LocalDate, LocalDateTime, LocalTime}

import scala.reflect.runtime.universe._

def classAccessors[T: TypeTag]: List[MethodSymbol] = typeOf[T].members.collect {
  case m: MethodSymbol if m.isCaseAccessor => m
}.toList

classAccessors[BarStateMessage]

val functionsConfiguration = Map("test" -> "add", "new" -> "add")

val test = (result: Double, weight: (String, Double)) => functionsConfiguration(weight._1) match {
  case _ => result + weight._2
}

val weigths = Map("test" -> 3.0, "new" -> 1.0)
weigths.foldLeft(0.0)(test)

val startDate = new LocalDate()

val (targetStartTime, targetStopTime) = {
  val times = "13:00-03:00".split("-").map(a => LocalTime.parse(a))
  (times.head, times.tail.head)
}
val targetStopDate = if (targetStopTime.isBefore(targetStartTime)) startDate.plusDays(1) else startDate

val targetStartLdt = new LocalDateTime(startDate.getYear, startDate.getMonthOfYear, startDate.getDayOfMonth,
  targetStartTime.getHourOfDay, targetStartTime.getMinuteOfHour, targetStartTime.getSecondOfMinute)
val targetStopLdt = new LocalDateTime(targetStopDate.getYear, targetStopDate.getMonthOfYear, targetStopDate.getDayOfMonth,
  targetStopTime.getHourOfDay, targetStopTime.getMinuteOfHour, targetStopTime.getSecondOfMinute)

val targetInterval = new Interval(targetStartLdt.toDateTime, targetStopLdt.toDateTime)
println(s"\n target openHours are: $targetInterval")

val (requestedStartTime, requestedStopTime) = {
  val times = "14:00-16:00".split("-").map(a => LocalTime.parse(a))
  (times.head, times.tail.head)
}
val requestedStopDate = if (requestedStopTime.isBefore(requestedStartTime)) startDate.plusDays(1) else startDate

val requestedStartLdt = new LocalDateTime(startDate.getYear, startDate.getMonthOfYear, startDate.getDayOfMonth,
  requestedStartTime.getHourOfDay, requestedStartTime.getMinuteOfHour, requestedStartTime.getSecondOfMinute)
val requestedStopLdt = new LocalDateTime(requestedStopDate.getYear, requestedStopDate.getMonthOfYear, requestedStopDate.getDayOfMonth,
  requestedStopTime.getHourOfDay, requestedStopTime.getMinuteOfHour, requestedStopTime.getSecondOfMinute)

val requestedInterval = new Interval(requestedStartLdt.toDateTime, requestedStopLdt.toDateTime)
println(s"\n requested openHours are: $requestedInterval")

// might return null, wrapping in Option
println(s"overlap is ${
  (Option(requestedInterval.overlap(targetInterval))
  match {
    case Some(duration) => duration.toDuration.getStandardMinutes.toDouble
    case None => 0D
  }) / requestedInterval.toDuration.getStandardMinutes.toDouble
}")

