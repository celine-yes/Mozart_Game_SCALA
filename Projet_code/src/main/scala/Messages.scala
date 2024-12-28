package upmc.akka.leader

import scala.concurrent.duration._
import DataBaseActor.Chord

/** Messages pour la logique du jeu Mozart */
case object Start             // Pour démarrer dans le main
case object StartConductor    // ConductorActor => lancer la boucle
case class Stop(reason: String)

case class GetMeasure(result: Int)

// Pour le Conductor
case class DistributeMeasure(chords: List[Chord])

// Pour Heartbeats
case class AliveFromShouter(id: Int)
case class ForwardAlive(senderId: Int)
case class StillAlive(senderId: Int)

// Pour Election
case class StartElection(candidats: Set[Int])
case class ElectionRequest(aliveSet: Set[Int])
case class YouAreElected(newChefId: Int)

// SetChefId pour informer le ListenerActor
case class SetChefId(id: Int)