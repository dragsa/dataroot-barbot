package databasemock

import acolyte.jdbc.Implicits._
import acolyte.jdbc.RowLists._
import acolyte.jdbc.{AcolyteDSL, Execution, QueryExecution, RowList4, UpdateExecution, Driver => AcolyteDriver}
import org.gnat.barbot.models.{Bar, BarRepository}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.duration._

class BarRepoSpec(implicit ee: ExecutionEnv) extends Specification {
  sequential
  "Bar Repo persistence test suite" title

  val schema = rowList4(
    classOf[String] -> "name",
    classOf[String] -> "info_source",
    classOf[Boolean] -> "is_active",
    classOf[Int] -> "id")

  val standardBarData = List(
    Bar("Staromak", "http://192.168.99.100:8888/Staromak", true, Some(1)),
    Bar("PD1", "http://192.168.99.100:8888/PD1", true, Some(2)),
    Bar("PD2", "http://192.168.1.1:8888/PD2", false, Some(3)),
    Bar("Error", "http://192.168.99.100:8888/Error", true, Some(4))
  )

  val handler = AcolyteDSL.handleStatement.withQueryDetection(
    "^select ") withQueryHandler { e: QueryExecution ⇒
    if (e.sql.startsWith("select ".toLowerCase)) {
      sqlLogger(e)
      if (e.sql.contains("\"is_active\" = true")) {
        appender(schema, standardBarData.filter(_.isActive == true))
      } else if (e.sql.contains("\"is_active\" = false")) {
        appender(schema, standardBarData.filter(_.isActive == false))
      } else if (e.sql.matches(".* where \"id\" = \\d+$")) {
        appender(schema, standardBarData.filter(_.id.contains(Integer.parseInt(e.sql.split("= ")(1)))))
      } else if (e.sql.endsWith("\"bars\"")) {
        appender(schema, standardBarData)
      } else
        schema
    } else
      schema
  } withUpdateHandler { e: UpdateExecution =>
    if (e.sql.startsWith("delete ")) {
      99
    }
    else if (e.sql.startsWith("update ")) {
      1
    } else {
      0
    }
  }

  AcolyteDriver.register("my-handler-id", handler)
  implicit val db = Database.forURL("jdbc:acolyte:anything-you-want?handler=my-handler-id")

  val barRepo = new BarRepository

  "select 1 bar by existing Id" in {
    barRepo.getOneById(1) must beSome(Bar("Staromak", "http://192.168.99.100:8888/Staromak", true, Some(1))).awaitFor(200.millis)
  }

  "select 1 bar by non-existing Id" in {
    barRepo.getOneById(5) must beNone.awaitFor(200.millis)
  }

  "select all bars" in {
    barRepo.getAll must haveSize[Seq[Bar]](4).awaitFor(200.millis)
  }

  "select all active bars" in {
    barRepo.getAllActive must haveSize[Seq[Bar]](3).awaitFor(200.millis)
  }

}
