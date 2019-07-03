import doobie._, doobie.implicits._, doobie.util.ExecutionContexts
import cats._, cats.data._, cats.implicits._, cats.effect.IO

class PartyManager(implicit val init_mode: Boolean) {
  def call(line: String): Unit = {
    println(line)
    val program1 = 42.pure[ConnectionIO]
    implicit val cs = IO.contextShift(ExecutionContexts.synchronous)
    val xa = Transactor.fromDriverManager[IO](
      "org.postgresql.Driver",     // driver classname
      "jdbc:postgresql:student",     // connect URL (driver-specific)
      "init",                  // user
      "",                          // password
      ExecutionContexts.synchronous // just for testing
    )
    val io = program1.transact(xa)
    // io: IO[Int] = Async(
    //   cats.effect.internals.IOBracket$$$Lambda$15047/791253558@10d9c307,
    //   false
    // )
    println(io.unsafeRunSync)
    // res0: Int = 42

  }
  def exit(): Unit = println("exit!")
}


object PartyManager {
  val mapping1 = Map("1" -> "3")
}