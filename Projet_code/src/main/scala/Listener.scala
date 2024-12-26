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
            context.parent ! StartElection
          }
        }
      }

      // Informer le parent de la liste des vivants
      context.parent ! UpdatedAliveSet(alive.keySet)

    case StartElection =>
      context.parent ! ElectionRequest(lastBeat.keySet)


    case other =>
      println(s"[Listener-$myId] => Reçu message inconnu: $other")
  }
}