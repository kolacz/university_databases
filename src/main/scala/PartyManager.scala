package scala

class PartyManager(implicit val init_mode: Boolean) {
  def call(line: String): Unit = println(line)
}


object PartyManager {
  val mapping1 = Map("1" -> "2")
}