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

  // Sous-acteurs
  val playerActor   = context.actorOf(Props[PlayerActor], s"player_$myId")
  //val providerActor = context.actorOf(Props(new ProviderActor(self)), "provider")
  val listenerActor = context.actorOf(Props(new ListenerActor(myId)), s"listener_$myId")
  val electorActor  = context.actorOf(Props(new ElectorActor(myId, allTerminals)), s"elector_$myId")
  val shouterActor  = context.actorOf(Props(new ShouterActor(myId)), s"shouter_$myId")

  // Chef
  var isChef = false
  var localChefId: Option[Int] = None
  var conductorActor: Option[ActorRef] = None
  var showStarted = false

  // Ensemble des vivants
  var aliveSet: Set[Int] = Set(myId)

  def afficherRole(): String = {
    if (isChef) "Chef" else "Musicien"
  }

  def stopConductor(): Unit = {
    conductorActor.foreach(context.stop)
    conductorActor = None
  }

  def broadcastSetChefId(newChefId: Int): Unit = {
     // On l’envoie à tous (y compris soi-même, ou on peut s’exclure si on veut)
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

    // ----------------------------------------------------------------
    // 1) Démarrage
    // ----------------------------------------------------------------
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
        // On me déclare Chef => isChef = true
        if(!isChef) {
          println(s"[$afficherRole-$myId] => On me désigne Chef => isChef=true.")
          isChef = true
        }
      } else {
        // Un autre est Chef => if j'étais Chef, j'arrête
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

    // ----------------------------------------------------------------
    // 2) Si on est Chef => démarrer le show
    // ----------------------------------------------------------------
    case "StartShow" if isChef =>
      println(s"[$afficherRole-$myId] => Chef => création du Conductor et StartConductor.")
      
      
      listenerActor ! SetChefId(myId)


    // ----------------------------------------------------------------
    // 3) ShouterActor => AliveFromShouter(myId)
    //    => on relaie vers les autres et on met à jour le ListenerActor
    // ----------------------------------------------------------------
    case AliveFromShouter(id) =>
      // Mise à jour locale
      listenerActor ! StillAlive(id)
      // Forward aux autres
      for (t <- allTerminals if t.id != myId) {
          val path = s"akka.tcp://MozartSystem${t.id}@${t.ip}:${t.port}/user/Musicien${t.id}"
          context.actorSelection(path) ! ForwardAlive(myId)
      }

    case ForwardAlive(senderId) =>
      // Reçu de l'extérieur => on le passe au Listener
      listenerActor ! StillAlive(senderId)

    // ListenerActor nous envoie la liste des vivants
    case UpdatedAliveSet(newSet) =>
      aliveSet = newSet
      // On lance la musique
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

    // ----------------------------------------------------------------
    // 4) Élection
    // ----------------------------------------------------------------
    case StartElection(candidats) =>
      println(s"[$afficherRole-$myId] => Reçu StartElection => je relaie l'ElectionRequest à ElectorActor avec candidats=$candidats.")
      electorActor ! ElectionRequest(candidats)

    case ElectionRequest(aliveSet) =>
      electorActor ! ElectionRequest(aliveSet)

    case YouAreElected(newChefId) =>
      println(s"[$afficherRole-$myId] => Nouveau Chef élu: $newChefId")
      localChefId = Some(newChefId)
      isChef = (newChefId == myId)

      // Mettre à jour tous les autres musiciens
      broadcastSetChefId(newChefId)

      if (isChef) {
        println(s"[$afficherRole-$myId] => Je suis le nouveau Chef.")
        self ! "StartShow"
      } else {
        stopConductor()
      }


    // ----------------------------------------------------------------
    // 5) ConductorActor => DistributeMeasure(chords)
    //    => On choisit un player vivant (différent de soi) au hasard
    // ----------------------------------------------------------------
    case DistributeMeasure(chords) if isChef =>
    
      val possiblePlayers = aliveSet - myId  // tous sauf moi
      println(s"[$afficherRole-$myId] => DistributeMeasure => $possiblePlayers")
      if (possiblePlayers.isEmpty) {
        println(s"[$afficherRole-$myId] => Aucun player vivant")
      } else {
        val arr = possiblePlayers.toArray
        val target = arr(scala.util.Random.nextInt(arr.length))
        // Envoi
        allTerminals.find(_.id == target) match {
               case Some(t) =>
               val path = s"akka.tcp://MozartSystem${t.id}@${t.ip}:${t.port}/user/Musicien${t.id}"
               println(s"[$afficherRole-$myId] => Envoi Measure à Musicien${t.id}")
               context.actorSelection(path) ! Measure(chords)
               case None =>
               println(s"[$afficherRole-$myId] => Impossible de trouver Terminal($target)")
          }
      }

    // ----------------------------------------------------------------
    // 6) Réception Measure => je la joue localement
    // ----------------------------------------------------------------
    case Measure(chords) =>
      println(s"[$afficherRole-$myId] => Je reçois Measure => PlayerActor.")
      playerActor ! Measure(chords)

    // ----------------------------------------------------------------
    // 7) Stop
    // ----------------------------------------------------------------
    case Stop(reason) =>
      println(s"[$afficherRole-$myId] => STOP($reason)")
      stopConductor()
      context.stop(self)
      context.system.terminate()

    case other =>
      println(s"[$afficherRole-$myId] => Message inconnu: $other")
  }
}
