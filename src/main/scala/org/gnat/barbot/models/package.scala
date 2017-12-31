package org.gnat.barbot

import java.sql.Timestamp


package object models {

  case class User(nickName: String,
                  firstName: Option[String],
                  lastName: Option[String],
                  birthDate: Timestamp,
                  gender: String,
                  phone: String,
                  email: String,
                  favoriteDrink: Option[String],
                  favoriteMeal: Option[String],
                  id: Int)

  case class Bar(name: String,
                 infoSource: String,
                 id: Int)

  case class Visit(user: Int,
                   place: Int,
                   mark: Int,
                   date: Timestamp,
                   id: Int)

  case class Flow(name: String,
                  steps: String,
                  id: Int)

}
