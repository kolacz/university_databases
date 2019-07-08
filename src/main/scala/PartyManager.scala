import doobie._
import doobie.implicits._
import doobie.util.ExecutionContexts
import cats._
import cats.data._
import cats.implicits._
import cats.effect.{ContextShift, IO}
import org.json4s._
import org.json4s.native.JsonMethods._


object ApiCall {
  private var xaTransactor: Option[Transactor.Aux[IO, Unit]] = None
}

sealed trait ApiCall[Repr <: ApiCall[Repr]] {
 // def perform(func: Repr): Unit  // had dreams of an abstract method
  def setTransactor(xa: Transactor.Aux[IO, Unit]): Unit = this.synchronized { ApiCall.xaTransactor = Some(xa) }
  def getTransactor: Transactor.Aux[IO, Unit] =
    ApiCall.xaTransactor match {
      case Some(x) => x
      case None    => throw new Exception("Perform 'open' call first!")
    }

  def addMember(member: Long, password: String, timestamp: Long, isLeader: Boolean): Unit = {
    sql"INSERT INTO unique_ids VALUES ($member)"
      .update.run.transact(this.getTransactor).unsafeRunSync

    sql"""INSERT INTO member (id, is_leader, password, latest_activity)
         |VALUES ($member, $isLeader, crypt($password, gen_salt('bf', 8)), $timestamp)
      """.stripMargin
      .update.run.transact(this.getTransactor).unsafeRunSync
  }

  def isMemberFrozen(member: Long, timestamp: Long): Boolean = {
    sql"""SELECT CASE WHEN EXISTS (
         |SELECT * FROM member WHERE member.id = $member AND
         |age(to_timestamp($timestamp), to_timestamp(member.latest_activity)) >= INTERVAL '1 YEAR' )
         |THEN CAST(1 AS BIT) ELSE CAST(0 AS BIT) END""".stripMargin
      .query[Boolean].unique.transact(this.getTransactor).unsafeRunSync
  }

  def addMemberIfNew(member: Long, password: String, timestamp: Long, isLeader: Boolean = false): Boolean = {
    val memberIds = sql"SELECT member.id FROM member".query[Long].to[List].transact(this.getTransactor).unsafeRunSync
    if (!(memberIds contains member)) {
      addMember(member, password, timestamp, isLeader)
      true
    }
    else false
  }

  def handleMember(member: Long, password: String, timestamp: Long): Boolean = {
    if (isMemberFrozen(member, timestamp)) throw new Exception("Member is frozen!")

    if (!addMemberIfNew(member, password, timestamp))
      checkPassword(member, password)

    true
  }

  def updateMemberLastActivity(member: Long, timestamp: Long): Unit = {
    sql"UPDATE member SET latest_activity = $timestamp WHERE id = $member"
      .update.run.transact(this.getTransactor).unsafeRunSync
  }

  def checkPassword(member: Long, password: String): Unit = {
    val correct = sql"""SELECT CASE WHEN EXISTS (
         |SELECT * FROM member WHERE member.id = $member AND member.password = crypt($password, member.password) )
         |THEN CAST(1 AS BIT) ELSE CAST(0 AS BIT) END""".stripMargin
      .query[Boolean].unique.transact(this.getTransactor).unsafeRunSync
    if (!correct) throw new Exception("Incorrect credentials!")
  }

  def checkFrozen(member: Long, timestamp: Long): Unit = {
    if (isMemberFrozen(member, timestamp)) throw new Exception("Member is frozen!")
  }

  def checkLeader(member: Long): Unit = {
    val isLeader =
      sql"""SELECT CASE WHEN EXISTS (
           |SELECT * FROM member WHERE member.id = $member AND member.is_leader )
           |THEN CAST(1 AS BIT) ELSE CAST(0 AS BIT) END""".stripMargin
      .query[Boolean].unique.transact(this.getTransactor).unsafeRunSync

    if (!isLeader) throw new Exception("Member is not a leader!")
  }

  def addProject(project: Long, authority: Option[Long]): Boolean = {
    authority match {
      case None =>
        sql"INSERT INTO unique_ids VALUES ($project)"
          .update.run.transact(this.getTransactor).unsafeRunSync

      case Some(auth) =>
        sql"INSERT INTO unique_ids VALUES ($project), ($auth)"
          .update.run.transact(this.getTransactor).unsafeRunSync
        sql"INSERT INTO authority VALUES ($auth)"
          .update.run.transact(this.getTransactor).unsafeRunSync
    }

    sql"INSERT INTO project VALUES ($project)"
      .update.run.transact(this.getTransactor).unsafeRunSync

    true
  }

  def addAction(actionType: String, timestamp: Long, member: Long,
                password: String, action: Long, project: Long, authority: Option[Long]): Boolean = {
    if (!handleMember(member, password, timestamp))
      false
    else {
      val uniqueIds = sql"SELECT id FROM unique_ids".query[Long].to[List].transact(this.getTransactor).unsafeRunSync
      println(uniqueIds)
      val projectOk = if (!(uniqueIds contains project)) addProject(project, authority) else false

      if (projectOk) {
        sql"INSERT INTO unique_ids VALUES ($action)"
          .update.run.transact(this.getTransactor).unsafeRunSync

        sql"INSERT INTO action VALUES ($action, $project, $member, $actionType :: action_t)"
          .update.run.transact(this.getTransactor).unsafeRunSync
      }
      projectOk
    }
  }

  def addVote(voteType: String, member: Long, action: Long): Boolean = {
    sql"INSERT INTO vote VALUES ($action, $member, $voteType :: vote_t)"
      .update.run.transact(this.getTransactor).unsafeRunSync

    if (voteType == "upvote")
      sql"UPDATE member SET upvotes = upvotes + 1 WHERE id = member"
        .update.run.transact(this.getTransactor).unsafeRunSync
    else
      sql"UPDATE member SET downvotes = downvotes + 1 WHERE id = member"
        .update.run.transact(this.getTransactor).unsafeRunSync

    true
  }
}


/**
  *
  * API FUNCTIONS IMPLEMENTATION
  *
  */


case class Open(database: String,
                   login: String,
                password: String) extends ApiCall[Open]

object Open extends ApiCall[Open] {
  def perform(func: Open): (String, Option[Any]) = {
    implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContexts.synchronous)

    this.setTransactor(Transactor.fromDriverManager[IO](
      driver = "org.postgresql.Driver",             // driver classname
      url    = s"jdbc:postgresql:${func.database}", // connect URL (driver-specific)
      user   = s"${func.login}",                    // user
      pass   = s"${func.password}",                 // password
      ExecutionContexts.synchronous                 // just for testing
    ))

    ("OK", None)
  }
}

case class Leader(timestamp: Long,
                   password: String,
                     member: Long) extends ApiCall[Leader]

object Leader extends ApiCall[Leader] {
  def perform(func: Leader): (String, Option[Any]) = {
    this.addMember(func.member, func.password, func.timestamp, isLeader = true)

    ("OK", None)
  }
}

case class Support(timestamp: Long,
                      member: Long,
                    password: String,
                      action: Long,
                     project: Long,
                   authority: Option[Long]) extends ApiCall[Support]

object Support extends ApiCall[Support] {
  def perform(func: Support): (String, Option[Any]) = {

    if(handleMember(func.member, func.password, func.timestamp)) {
      if (this.addAction(actionType = "support", func.timestamp, func.member, func.password,
        func.action, func.project, func.authority))
        ("OK", None)
      else ("ERROR", None)
    }
    else
      ("ERROR", None)
  }
}

case class Protest(timestamp: Long,
                      member: Long,
                    password: String,
                      action: Long,
                     project: Long,
                   authority: Option[Long]) extends ApiCall[Protest]

object Protest extends ApiCall[Protest] {
  def perform(func: Protest): (String, Option[Any]) = {

    if(handleMember(func.member, func.password, func.timestamp)) {
      if(this.addAction(actionType = "protest", func.timestamp, func.member, func.password,
        func.action, func.project, func.authority))
        ("OK", None)
      else ("ERROR", None)
    }
    else
      ("ERROR", None)
  }
}

case class Upvote(timestamp: Long,
                     member: Long,
                   password: String,
                     action: Long) extends ApiCall[Upvote]

object Upvote extends ApiCall[Upvote] {
  def perform(func: Upvote): (String, Option[Any]) = {
    if(handleMember(func.member, func.password, func.timestamp)) {
      val actionIds = sql"SELECT id FROM action".query[Long].to[List].transact(this.getTransactor).unsafeRunSync

      if (!(actionIds contains func.action))
        if (this.addVote(voteType = "upvote", func.member, func.action))
          ("OK", None)
        else ("ERROR", None)
      else ("ERROR", None)
    }
    else ("ERROR", None)
  }
}

case class Downvote(timestamp: Long,
                       member: Long,
                     password: String,
                       action: Long) extends ApiCall[Downvote]

object Downvote extends ApiCall[Downvote] {
  def perform(func: Downvote): (String, Option[Any]) = {
    if(handleMember(func.member, func.password, func.timestamp)) {
      val actionIds = sql"SELECT id FROM action".query[Long].to[List].transact(this.getTransactor).unsafeRunSync

      if (!(actionIds contains func.action))
        if (this.addVote(voteType = "downvote", func.member, func.action))
          ("OK", None)
        else ("ERROR", None)
      else ("ERROR", None)
    }
    else ("ERROR", None)
  }
}

case class Actions(timestamp: Long,
                      member: Long,
                    password: String,
                       type1: Option[String],
                     project: Option[Long],
                   authority: Option[Long]) extends ApiCall[Actions]

object Actions extends ApiCall[Actions] {
  def perform(func: Actions): Unit = {}
}

case class Projects(timestamp: Long,
                       member: Long,
                     password: String,
                    authority: Option[Long]) extends ApiCall[Projects]

object Projects extends ApiCall[Projects] {
  def perform(func: Projects): Unit = {}
}

case class Votes(timestamp: Long,
                    member: Long,
                  password: String,
                    action: Option[Long],
                   project: Option[Long]) extends ApiCall[Votes]

object Votes extends ApiCall[Votes] {
  def perform(func: Votes): Unit = {}
}

case class Trolls(timestamp: Long) extends ApiCall[Trolls]

object Trolls extends ApiCall[Trolls] {
  def perform(func: Trolls): Unit = {}
}


class PartyManager(implicit val initMode: Boolean) {

  def call(jsonString: String): String = {
    val json = parse(jsonString)
    val JObject(List((funcName, _))) = json
    val argsJson = json \ funcName

    implicit val formats: Formats = DefaultFormats.withStrictOptionParsing

    val (status, data) = try {
      funcName match {
        case "open"     =>     Open.perform(argsJson.extract[Open])
        case "leader"   =>   Leader.perform(argsJson.extract[Leader])
        case "support"  =>  Support.perform(argsJson.extract[Support])
        case "protest"  =>  Protest.perform(argsJson.extract[Protest])
        case "upvote"   =>   Upvote.perform(argsJson.extract[Upvote])
        case "downvote" => Downvote.perform(argsJson.extract[Downvote])
        case "actions"  =>  Actions.perform(argsJson.extract[Actions])
        case "projects" => Projects.perform(argsJson.extract[Projects])
        case "votes"    =>    Votes.perform(argsJson.extract[Votes])
        case "trolls"   =>   Trolls.perform(argsJson.extract[Trolls])
        case _ => ("ERROR", None)
      }
    } catch {
      case e: org.postgresql.util.PSQLException => ("ERROR", None); println(e)
    }

    data match {
      case None    => s"""{"status": "$status"}"""
      case Some(d) => s"""{"status": "$status", "data": $d}"""
    }
  }
}