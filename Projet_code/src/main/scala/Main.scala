package upmc.akka.ppc

import akka.actor._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global  // pour les futures

object Main extends App {
  val system = ActorSystem("mozartGame")
  
  // Création de l'acteur Conductor
  val conductorActor = system.actorOf(Props(new ConductorActor()), "conductorActor")

  conductorActor ! ConductorActor.StartGame  

  // Arrêter le système après une certaine période
  // system.scheduler.scheduleOnce(60.seconds, new Runnable {
  //   def run(): Unit = {
  //     println("Arrêt du jeu après 1 minute.")
  //     system.terminate()
  //   }
  // })
}
