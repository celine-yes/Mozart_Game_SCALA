package upmc.akka.leader

import akka.actor._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class ListenerActor(myId: Int) extends Actor {

  var lastBeat: Map[Int, Long] = Map(myId -> System.currentTimeMillis())
  var chefId: Option[Int] = None

  override def preStart(): Unit = {
    // on check la vie
    context.system.scheduler.schedule(2.seconds, 3.seconds, self, "CheckAlive")

    // Vérifie toutes les 30 secondes si le chef est seul
    context.system.scheduler.schedule(30.seconds, 30.seconds, self, "CheckSoloChef")
  }
  def receive: Receive = {
    case StillAlive(senderId) =>
      lastBeat += (senderId -> System.currentTimeMillis())

    case SetChefId(id) =>
      chefId = Some(id)
      println(s"[Listener-$myId] => Enregistre Chef=$id")

    case "CheckAlive" =>
      val now = System.currentTimeMillis()
      val (alive, dead) = lastBeat.partition { case (_, t) => (now - t) <= 10000 }
      lastBeat = alive

      if (dead.nonEmpty) {
        val deadIDs = dead.keySet
        println(s"[Listener-$myId] => Morts détectés: $deadIDs")
        // Si le Chef est dedans => StartElection
        chefId.foreach { cid =>
          if (deadIDs.contains(cid)) {
            println(s"[Listener-$myId] => Chef $cid est mort => StartElection.")
            context.parent ! StartElection(alive.keySet - cid)
          }
        }
      }

      // Informer le parent de la liste des vivants
      context.parent ! UpdatedAliveSet(alive.keySet)


    case "CheckSoloChef" =>
      chefId match {
        case Some(id) if id == myId && lastBeat.size == 1 => // Si je suis le chef et seul
          println(s"[Listener-$myId] => Seul chef détecté pendant 30 secondes. Arrêt du programme.")
          context.parent ! Stop("Le chef d'orchestre est seul.")
          context.system.terminate()

        case _ =>
          println(s"[Listener-$myId] => Vérification : pas seul, pas d'arrêt.")
      }

    case StartElection =>
      val cleanedAliveSet = lastBeat.keySet // Nettoyer uniquement les vivants
      println(s"[Listener-$myId] => Nettoyage des morts avant Election: $cleanedAliveSet")
      context.parent ! ElectionRequest(cleanedAliveSet)

    case other =>
      println(s"[Listener-$myId] => Reçu message inconnu: $other")
  }
}
