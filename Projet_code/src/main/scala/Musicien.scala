package upmc.akka.leader

import akka.actor._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

case class UpdatedAliveSet(alive: Set[Int])
case object RequestChefId

class Musicien(myId: Int, allTerminals: List[Terminal]) extends Actor {
  import ConductorActor._
  import PlayerActor._
  import DataBaseActor._

  val playerActor   = context.actorOf(Props[PlayerActor], s"player_$myId")
  //val providerActor = context.actorOf(Props(new ProviderActor(self)), "provider")
  val listenerActor = context.actorOf(Props(new ListenerActor(myId)), s"listener_$myId")
  val electorActor  = context.actorOf(Props(new ElectorActor(myId, allTerminals)), s"elector_$myId")
  val shouterActor  = context.actorOf(Props(new ShouterActor(myId)), s"shouter_$myId")

  //chef
  var isChef = false
  var localChefId: Option[Int] = None
  var conductorActor: Option[ActorRef] = None
  var showStarted = false

  //vivants
  var aliveSet: Set[Int] = Set(myId)

  def afficherRole(): String = {
    if (isChef) "Chef" else "Musicien"
  }

  def stopConductor(): Unit = {
    conductorActor.foreach(context.stop)
    conductorActor = None
  }

  def broadcastSetChefId(newChefId: Int): Unit = {
     //on l’envoie à tous
     for(t <- allTerminals) {
       val path = s"akka.tcp://MozartSystem${t.id}@${t.ip}:${t.port}/user/Musicien${t.id}"
       context.actorSelection(path) ! SetChefId(newChefId)
     }
  }

  override def preStart(): Unit = {
    println(s"[$afficherRole-$myId] => Démarrage (façade). isChef=$isChef (initialement faux).")
    for(t <- allTerminals if t.id != myId) {
      val path = s"akka.tcp://MozartSystem${t.id}@${t.ip}:${t.port}/user/Musicien${t.id}"
      context.actorSelection(path) ! RequestChefId
    }
     
    context.system.scheduler.scheduleOnce(7.seconds, self, "CheckAlone")
  }

  def receive: Receive = {

    case Start =>
      println(s"[$afficherRole-$myId] => Reçoit Start => on lance le ShouterActor.")
      shouterActor ! "StartShouting"

     // 1 seconde après le démarrage, on check si on est seul
    case "CheckAlone" =>
      if (!isChef && localChefId.isEmpty && aliveSet.size == 1) {
        println(s"[$afficherRole-$myId] => Je suis seul, aucun Chef => je deviens Chef.")
        isChef = true
        localChefId = Some(myId)
        listenerActor ! SetChefId(myId)
        broadcastSetChefId(myId)
      }

     case SetChefId(id) =>
      println(s"[$afficherRole-$myId] => Reçu SetChefId($id). localChefId=$localChefId => devient $id.")
      localChefId = Some(id)
      listenerActor ! SetChefId(id) // pour le ListenerActor

      if(id == myId) {
        //on me déclare Chef => isChef = true
        if(!isChef) {
          println(s"[$afficherRole-$myId] => On me désigne Chef => isChef=true.")
          isChef = true
        }
      } else {
        //un autre est Chef => if j'étais Chef, j'arrête
        if(isChef && id != myId) {
          println(s"[$afficherRole-$myId] => On me retire Chef => isChef=false.")
          isChef = false
          stopConductor()
        }
      }

    case RequestChefId =>
      localChefId.foreach { id =>
        sender() ! SetChefId(id)
      }

    // si on est Chef => démarrer conductor
    case "StartShow" if isChef =>
      println(s"[$afficherRole-$myId] => Chef => création du Conductor et StartConductor.")
      
      listenerActor ! SetChefId(myId)

    //on relaie son état vers les autres et on met à jour le ListenerActor
    case AliveFromShouter(id) =>
      //maj locale
      listenerActor ! StillAlive(id)
      //transmission aux autres musiciens
      for (t <- allTerminals if t.id != myId) {
          val path = s"akka.tcp://MozartSystem${t.id}@${t.ip}:${t.port}/user/Musicien${t.id}"
          context.actorSelection(path) ! ForwardAlive(myId)
      }

    //reçu des autres musiciens
    case ForwardAlive(senderId) =>
      listenerActor ! StillAlive(senderId)

    //listenerActor nous envoie la liste des vivants
    case UpdatedAliveSet(newSet) =>
      aliveSet = newSet
      if (isChef && aliveSet.size > 1 && conductorActor.isEmpty && !showStarted) {
       println(s"[$afficherRole-$myId] => Un autre player est arrivé, je démarre le show.")
       
       if (conductorActor.isEmpty) {
          println(s"[$afficherRole-$myId] => Je crée mon ConductorActor.")
          val c = context.actorOf(Props(new ConductorActor()), s"Conductor-$myId")
          conductorActor = Some(c)
      }
       println(s"[$afficherRole-$myId] => Envoi StartConductor à ConductorActor.")
       conductorActor.foreach(_ ! StartConductor)
       self ! "StartShow"
       showStarted = true
      }

    // Élection
    case StartElection(candidats) =>
      println(s"[$afficherRole-$myId] => Reçu StartElection => je relaie l'ElectionRequest à ElectorActor avec candidats=$candidats.")
      electorActor ! ElectionRequest(candidats)

    case ElectionRequest(aliveSet) =>
      electorActor ! ElectionRequest(aliveSet)

    //recu de Elector local
    case YouAreElectedFromElector(newChefId) =>
      for (t <- allTerminals if t.id != myId) {
            val path = s"akka.tcp://MozartSystem${t.id}@${t.ip}:${t.port}/user/Musicien${t.id}"
            context.actorSelection(path) ! YouAreElected(newChefId)
        }

    case YouAreElected(newChefId) =>
      println(s"[$afficherRole-$myId] => Nouveau Chef élu: $newChefId")
      localChefId = Some(newChefId)
      isChef = (newChefId == myId)

      //maj pour tous les autres musiciens
      broadcastSetChefId(newChefId)

      if (isChef) {
        println(s"[$afficherRole-$myId] => Je suis le nouveau Chef.")
        self ! "StartShow"
      } else {
        stopConductor()
      }


    //choisir un player vivant (différent de soi) au hasard
    case DistributeMeasure(chords) if isChef =>
    
      val possiblePlayers = aliveSet - myId 
      println(s"[$afficherRole-$myId] => DistributeMeasure => $possiblePlayers")
      if (possiblePlayers.isEmpty) {
        println(s"[$afficherRole-$myId] => Aucun player vivant")
      } else {
        val arr = possiblePlayers.toArray
        val target = arr(scala.util.Random.nextInt(arr.length))

        allTerminals.find(_.id == target) match {
               case Some(t) =>
               val path = s"akka.tcp://MozartSystem${t.id}@${t.ip}:${t.port}/user/Musicien${t.id}"
               println(s"[$afficherRole-$myId] => Envoi Measure à Musicien${t.id}")
               context.actorSelection(path) ! Measure(chords)
               case None =>
               println(s"[$afficherRole-$myId] => Impossible de trouver Terminal($target)")
          }
      }

    //recu des autres musiciens
    case Measure(chords) =>
      println(s"[$afficherRole-$myId] => Je reçois Measure => PlayerActor.")
      playerActor ! Measure(chords)

    // Arret
    case Stop(reason) =>
      println(s"[$afficherRole-$myId] => STOP($reason)")
      stopConductor()
      context.stop(self)
      context.system.terminate()

    case other =>
      println(s"[$afficherRole-$myId] => Message inconnu: $other")
  }
}
