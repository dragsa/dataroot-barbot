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

import scala.reflect.runtime.universe._

def classAccessors[T: TypeTag]: List[MethodSymbol] = typeOf[T].members.collect {
  case m: MethodSymbol if m.isCaseAccessor => m
}.toList

classAccessors[BarStateMessage]

val functionsConfiguration = Map("test" -> "add", "new" -> "add")

val test = (result: Double, weight: (String, Double)) => functionsConfiguration(weight._1) match {
  case _ => result + weight._2
}

val weigths = Map("locations" -> 3.0, "guests" -> 1.0)
weigths.foldLeft(0.0)(test)

