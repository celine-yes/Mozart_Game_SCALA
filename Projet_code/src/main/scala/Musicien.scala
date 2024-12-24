package upmc.akka.leader

import akka.actor._
import upmc.akka.leader.DataBaseActor.Measure
import scala.concurrent.duration._ // Pour utiliser 1.second
import scala.concurrent.Future // Pour les opérations asynchrones
import scala.util.{Success, Failure} // Pour traiter les résultats de Future


case class Start()
case class Elect(id: Int) // Message reçu de l'Elector
case class Elected(id: Int) // Message envoyé au musicien correspondant
case class SendPlay(id: Int, measure: Measure)
case class Alive(id: Int)

class Musicien(val id: Int, val terminaux: List[Terminal]) extends Actor {
     import DataBaseActor._
     import ConductorActor._
     import ShouterActor._

     // Les differents acteurs du systeme
     val displayActor = context.actorOf(Props[DisplayActor], name = "displayActor")
     val conductor = context.actorOf(Props[ConductorActor], "ConductorActor")
     val shouter = context.actorOf(Props(new ShouterActor(self, id)), s"Shouter$id")
     val listener = context.actorOf(Props[Listener], s"Listener$id")

     // Variable locale pour suivre si le musicien est chef d'orchestre
     private var isConductor: Boolean = false

     def receive = {

    // Initialisation
     case Start =>
          displayActor ! Message(s"Musicien $id is created")
          shouter ! StartShouter
          // Vérifier si c'est le premier musicien
          if (id == 0) {
               displayActor ! Message(s"Musicien $id is the first to start. Electing itself as leader.")
               self ! Elected
          }


    // Gestion du message Elect
     case Elect(newLeaderId) =>
          displayActor ! Message(s"Musicien $id received Elect($newLeaderId). Forwarding as Elected($newLeaderId).")
          // Chercher le terminal correspondant au nouvel élu
          terminaux.find(_.id == newLeaderId) match {
          case Some(terminal) =>
               val actorPath = s"akka.tcp://MozartSystem${newLeaderId}@${terminal.ip}:${terminal.port}/user/Musicien$newLeaderId"
               context.actorSelection(actorPath) ! Elected(newLeaderId)
               displayActor ! Message(s"Musicien $id forwarded Elected($newLeaderId) to Musicien$newLeaderId.")
          case None =>
               displayActor ! Message(s"Musicien $id could not find Musicien$newLeaderId to forward Elected.")
          }

    // Gestion du message Elected
     case Elected =>
          // Devenir leader directement ici, car ce message est envoyé uniquement au musicien correspondant
          isConductor = true
          displayActor ! Message(s"Musicien $id has been elected as the new leader and is now coordinating the game.")
          // Ajouter ici la logique spécifique au chef d'orchestre
          conductor ! StartConductor

     
     // Réception d'un Alive venant de musicien externe
     case Alive(otherId) =>
          displayActor ! Message(s"Musicien $id received Alive from Musicien $otherId")
          listener ! Alive(otherId)
          

     // from Shouter
     // case Alive =>
     //      displayActor ! Message(s"Musicien $id received Alive from Shouter")
     //      terminaux.filter(_.id != id).foreach { terminal =>
     //           val actorPath = s"akka.tcp://MozartSystem${terminal.id}@${terminal.ip}:${terminal.port}/user/Musicien${terminal.id}"
     //           context.actorSelection(actorPath) ! Alive(id) }
          
     case Alive =>
          displayActor ! Message(s"Musicien $id received Alive from Shouter.")
          Thread.sleep(5000) 
          // Envoyer Alive(id) uniquement aux musiciens vivants
          terminaux.filter(_.id != id).foreach { terminal =>
          val actorPath = s"akka.tcp://system${terminal.id}@${terminal.ip}:${terminal.port}/user/Musicien${terminal.id}"
          context.actorSelection(actorPath).resolveOne(1.second).onComplete {
               case Success(actorRef) =>
               actorRef ! Alive(id)
               displayActor ! Message(s"Musicien $id sent Alive($id) to Musicien ${terminal.id}.")
               case Failure(exception) =>
               displayActor ! Message(s"Musicien $id could not reach Musicien ${terminal.id}: ${exception.getMessage}")
          }(context.dispatcher) // Nécessaire pour exécuter l'opération asynchrone
     }


    // Gestion de l'instruction SendPlay
     case SendPlay(targetId, measure) =>
          if (targetId != id) {
          terminaux.find(_.id == targetId) match {
               case Some(terminal) =>
               val actorPath = s"akka.tcp://MozartSystem${targetId}@${terminal.ip}:${terminal.port}/user/Musicien$targetId"
               context.actorSelection(actorPath) ! SendPlay(id, measure)
               displayActor ! Message(s"Musicien $id sent measure '$measure' to Musicien $targetId.")
               case None =>
               displayActor ! Message(s"Musicien $id could not find Musicien $targetId.")
          }
          } else {
          displayActor ! Message(s"Musicien $id cannot send a measure to itself.")
          }
     }
}
