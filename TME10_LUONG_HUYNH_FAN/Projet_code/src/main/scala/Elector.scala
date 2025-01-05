package upmc.akka.leader

import akka.actor._
import scala.util.Random

class ElectorActor(myId: Int, allTerminals: List[Terminal]) extends Actor {

  def receive: Receive = {
    case ElectionRequest(aliveSet) =>
      if (aliveSet.isEmpty) {
        println(s"[Elector-$myId] => Personne vivant => pas de Chef possible.")
      } else {
        //elu le plus petit id comme nouveau chef
        val newChef = aliveSet.min
        println(s"[Elector-$myId] => Nouveau Chef = $newChef")

        context.parent ! YouAreElectedFromElector(newChef)
        println(s"[Elector-$myId] => Envoi YouAreElected($newChef) à Musicien$myId")
      }
    case other =>
      println(s"[Elector-$myId] => Message inconnu: $other")
  }
}