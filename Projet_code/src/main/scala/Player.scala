
package upmc.akka.ppc

import math._

import javax.sound.midi._
import javax.sound.midi.ShortMessage._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global

import akka.actor.{Props, Actor, ActorRef, ActorSystem}


object PlayerActor {
  import DataBaseActor._
  case class MidiNote (pitch:Int, vel:Int, dur:Int, at:Int) 
  val info = MidiSystem.getMidiDeviceInfo().filter(_.getName == "Gervill").headOption
  val device = info.map(MidiSystem.getMidiDevice).getOrElse {
    println("[ERROR] Could not find Gervill synthesizer.")
    sys.exit(1)
}

val rcvr = device.getReceiver()

/////////////////////////////////////////////////
def note_on (pitch:Int, vel:Int, chan:Int): Unit = {
    val msg = new ShortMessage
    msg.setMessage(NOTE_ON, chan, pitch, vel)
    rcvr.send(msg, -1)
}

def note_off (pitch:Int, chan:Int): Unit = {
    val msg = new ShortMessage
    msg.setMessage(NOTE_ON, chan, pitch, 0)
    rcvr.send(msg, -1)
}

}

//////////////////////////////////////////////////


class PlayerActor() extends Actor {
  import DataBaseActor._
  import PlayerActor._
  
  device.open()
  var currentTime: Int = 0
  def receive = {
    case Measure(chords) =>
      chords.foreach { chord =>
        chord.notes.foreach { note =>
          // Calcul du moment de la note en fonction du temps actuel
          val noteAt = currentTime
          self ! MidiNote(note.pitch, note.vol, note.dur, noteAt)
          currentTime += note.dur
        }
      }

    case MidiNote(p, v, d, at) =>
      context.system.scheduler.scheduleOnce(at.milliseconds) {
        note_on(p, v, 10)
      }
      context.system.scheduler.scheduleOnce((at + d).milliseconds) {
        note_off(p, 10)
      }
  }
}
