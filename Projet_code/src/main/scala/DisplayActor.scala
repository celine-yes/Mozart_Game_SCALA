package upmc.akka.leader

import akka.actor._

case class Message (content:String)

class DisplayActor extends Actor {

     def receive = {

          case Message (content) => {
               println(content)
          }

     }
}
