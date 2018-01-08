package org.gnat.barbot

import info.mukel.telegrambot4s.models.Message

package object tele {
  def getUserFirstName(msg: Message) = msg.from.flatMap(u => Option(u.firstName)).getOrElse("Anonymous Alcohol Seeker")
  def getUserId(msg: Message) = msg.from.flatMap(u => Option(u.id))
}
