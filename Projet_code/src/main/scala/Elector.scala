package upmc.akka.leader

import akka.actor._
import scala.util.Random

class ElectorActor(myId: Int, allTerminals: List[Terminal]) extends Actor {

  def receive: Receive = {
    case ElectionRequest(aliveSet) =>
    // afficher les candidats
      println(s"[Elector-$myId] => ElectionRequest($aliveSet)")
      if (aliveSet.isEmpty) {
        println(s"[Elector-$myId] => Personne vivant => pas de Chef possible.")
      } else {
        // ID le plus petit comme nouveau chef (peut être random?)
        val newChef = aliveSet.min
        println(s"[Elector-$myId] => Nouveau Chef = $newChef")

        aliveSet.foreach { id =>
          allTerminals.find(_.id == id) match {
            case Some(t) =>
              val path = s"akka.tcp://MozartSystem${t.id}@${t.ip}:${t.port}/user/Musicien${t.id}"
              context.actorSelection(path) ! YouAreElected(newChef)
              println(s"[Elector-$myId] => Envoi YouAreElected($newChef) à Musicien${t.id}")
            case None =>
              println(s"[Elector-$myId] => Impossible de trouver Terminal($id) pour YouAreElected")
          }
        }
      }
    case other =>
      println(s"[Elector-$myId] => Message inconnu: $other")
  }
}