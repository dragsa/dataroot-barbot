package org.gnat.barbot.http

object Utils {
  def cachePrettyFormat(cache: Map[Int, (BarStateMessage, Long)]) = cache
    .map { case (k, v) => k + " -> " + "Bar" + v._1 + " is in Limb since " + v._2 + " ns" } mkString "\n "

  def iterableIdsPrettyFormat(iterable: Iterable[Int]) = s"ids: ${iterable mkString " "}"
}
