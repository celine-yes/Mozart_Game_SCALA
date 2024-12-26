package upmc.akka.leader

import akka.actor._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class ShouterActor(myId: Int) extends Actor {

  def receive: Receive = {
    case "StartShouting" =>
      context.system.scheduler.schedule(
        2.seconds,  // initial delay
        3.seconds,  // interval
        self,
        "ShoutNow"
      )

    case "ShoutNow" =>
      context.parent ! AliveFromShouter(myId)

    case other =>
      println(s"[ShouterActor-$myId] => Message inconnu: $other")
  }
}