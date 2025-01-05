package upmc.akka.leader

import scala.concurrent.duration._
import DataBaseActor.Chord

case object Start             //pour démarrer dans le main
case object StartConductor    //conductorActor => lancer la boucle
case class Stop(reason: String)

case class GetMeasure(result: Int)

//Conductor
case class DistributeMeasure(chords: List[Chord])

//vie des musiciens
case class AliveFromShouter(id: Int)
case class ForwardAlive(senderId: Int)
case class StillAlive(senderId: Int)

//election
case class StartElection(candidats: Set[Int])
case class ElectionRequest(aliveSet: Set[Int])
case class YouAreElected(newChefId: Int)
case class YouAreElectedFromElector(newChefId: Int)


//setChefId pour informer le ListenerActor
case class SetChefId(id: Int)