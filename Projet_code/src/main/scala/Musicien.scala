package upmc.akka.leader

import akka.actor._

case class Start ()
case class SendPlay(id: Int, measure: Measure)
case class Alive(id: Int)
case class Elected(id: Int)
case class Message(content: String)
case class PromoteToConductor()

class Musicien (val id:Int, val terminaux:List[Terminal]) extends Actor {
     import DataBaseActor._

     // Les differents acteurs du systeme
     val displayActor = context.actorOf(Props[DisplayActor], name = "displayActor")

     def receive = {

          // Initialisation
          case Start => {
               displayActor ! Message ("Musicien " + this.id + " is created")
          }

          // Gestion de l'instruction Elected
          case Elected(newLeaderId) =>
               if (newLeaderId != id) {
               displayActor ! Message(s"Musicien $id acknowledges Musicien $newLeaderId as the new leader.")
               terminaux.find(_.id == newLeaderId) match {
                    case Some(terminal) =>
                    val actorPath = s"akka.tcp://MozartSystem${newLeaderId}@${terminal.ip}:${terminal.port}/user/Musicien$newLeaderId"
                    context.actorSelection(actorPath) ! PromoteToConductor()
                    displayActor ! Message(s"Musicien $id promoted Musicien $newLeaderId to Conductor.")
                    case None =>
                    displayActor ! Message(s"Musicien $id could not find Musicien $newLeaderId to promote to Conductor.")
               }
               } else {
               displayActor ! Message(s"Musicien $id has been elected as the new leader.")
               // Logic to take on the role of leader
               becomeLeader()
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
