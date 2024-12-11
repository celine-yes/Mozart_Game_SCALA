package upmc.akka.ppc

import akka.actor._
import scala.util.Random
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global  

object ConductorActor {
  case object StartGame
}

class ConductorActor() extends Actor {
  import ConductorActor._
  import DataBaseActor._
  import PlayerActor._
  import ProviderActor._

  val player = context.actorOf(Props[PlayerActor], "theplayer")
  val mozart = context.actorOf(Props(new ProviderActor(self)), "provider")
  val dice1 = scala.util.Random
  val dice2 = scala.util.Random
  val scheduler = context.system.scheduler
  val TIME_BASE = 1800.milliseconds

  var result = 0
  def receive = {
    case StartGame =>
    //   println("Conductor started")
      result = dice1.nextInt(6) + dice2.nextInt(6) + 2
    //   println(s"Result is $result")
      mozart ! GetMeasure(result)
    
    case Measure(chordslist) =>
      player ! Measure(chordslist)
      scheduler.scheduleOnce(TIME_BASE, self, StartGame)
    }
}
