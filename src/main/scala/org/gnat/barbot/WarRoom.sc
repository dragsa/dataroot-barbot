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