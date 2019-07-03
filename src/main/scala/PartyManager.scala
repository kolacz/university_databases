package scala

import doobie._
import doobie.implicits._
import cats.effect.IO

class PartyManager(implicit val init_mode: Boolean) {
  def call(line: String): Unit = println(line)
  def exit(): Unit = println("exit!")
}


object PartyManager {
  val mapping1 = Map("1" -> "3")
}