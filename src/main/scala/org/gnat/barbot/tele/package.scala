package org.gnat.barbot

import info.mukel.telegrambot4s.models.Message

package object tele {
  def getUserFullName(implicit msg: Message) = msg.from.flatMap(u => Option(u.firstName + u.lastName.flatMap(ln => Option(" " + ln)).getOrElse(""))).getOrElse("Anonymous Alcohol Seeker")

  def getUserId(implicit msg: Message) = msg.from.flatMap(u => Option(u.id))

  def getUserFirstName(implicit msg: Message) = msg.from.flatMap(u => Option(u.firstName)).getOrElse("Anonymous")

  def getUserLastName(implicit msg: Message) = msg.from.flatMap(u => u.lastName)

  def getUserNickName(implicit msg: Message) = msg.from.flatMap(u => u.username)

  def getCompositeUserActorName(implicit msg: Message) = getUserId(msg).getOrElse("default_user_id") + "_for_" + getUserFullName(msg).replaceAll("\\s", "")
}
