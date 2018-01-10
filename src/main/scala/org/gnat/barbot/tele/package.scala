package org.gnat.barbot

import info.mukel.telegrambot4s.models.Message

package object tele {
  def getUserFullName(msg: Message) = msg.from.flatMap(u => Option(u.firstName + u.lastName.flatMap(ln => Option(" " + ln)).getOrElse(""))).getOrElse("Anonymous Alcohol Seeker")

  def getUserId(msg: Message) = msg.from.flatMap(u => Option(u.id))

  def getUserFirstName(msg: Message) = msg.from.flatMap(u => Option(u.firstName))

  def getUserLastName(msg: Message) = msg.from.flatMap(u => u.lastName)

  def getUserNickName(msg: Message) = msg.from.flatMap(u => u.username)

  def getCompositeUserActorName(msg : Message) = getUserId(msg).get + "_for_" + getUserFullName(msg).replaceAll("\\s", "")
}
