// Start/stop psql manually
// pg_ctl -D /usr/local/var/postgres start
// pg_ctl -D /usr/local/var/postgres stop

// Start psql server now and relaunch at login / stop
// brew services start postgresql
// brew services stop postgresql



object Main extends App {
  implicit val init_mode: Boolean = args.length == 1 && args(0) == "--init"

  val apiCaller = new PartyManager()

  try {
    Iterator.continually(scala.io.StdIn.readLine).
      takeWhile(_.nonEmpty).
      foreach(line => apiCaller.call(line))
  }
  catch {
    case e: Exception => println("catch!")
  }
  finally {
    apiCaller.exit()
    println("finally!")
  }
}
