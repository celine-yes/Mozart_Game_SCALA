package upmc.akka.leader

import akka.actor._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

object ConductorActor {
  case object StartConductor
}

class ConductorActor() extends Actor {
  import ProviderActor._
  import ConductorActor._
  import DataBaseActor._
  
  val dice1 = new Random
  val dice2 = new Random
  val TIME_BASE = 1500.milliseconds

  val providerActor = context.actorOf(Props(new ProviderActor(self)), "provider")

  def receive: Receive = {
    case StartConductor =>
      val r = dice1.nextInt(6) + dice2.nextInt(6) + 2
      println(s"[ConductorActor] => Lance dés => $r")
      providerActor ! GetMeasure(r)

    case Measure(chords) =>
      println(s"[ConductorActor] => Reçu measure => DistributeMeasure au parent (Musicien Chef).")
      context.parent ! DistributeMeasure(chords)
      context.system.scheduler.scheduleOnce(TIME_BASE, self, StartConductor)

    case other =>
      println(s"[ConductorActor] => Message inconnu: $other")
  }
}