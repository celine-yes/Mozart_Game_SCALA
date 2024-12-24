package upmc.akka.leader

import akka.actor._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global


object ShouterActor {
  case object StartShouter
}

class ShouterActor(musicien: ActorRef, id: Int) extends Actor {
    import ShouterActor._

    val scheduler = context.system.scheduler
    val TIME_BASE = 600.milliseconds


    def receive: Receive = {
    case StartShouter =>
        musicien ! Alive
        scheduler.scheduleOnce(TIME_BASE, self, StartShouter)
  }
}
