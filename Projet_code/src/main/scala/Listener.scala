package upmc.akka.leader

import akka.actor._

class Listener extends Actor {
  private var activeMusicians: Set[Int] = Set()

  def receive: Receive = {
    case Alive(id) =>
      if (!activeMusicians.contains(id)) {
        activeMusicians += id
        context.parent ! Message(s"Listener detected Musicien $id is alive.")
      }
  }
}
