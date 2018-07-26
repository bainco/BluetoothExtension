package bluetooth.implementation;

import java.util
import java.util.{Deque, Map => JMap}

import bluetooth.interfaces.BluetoothEventListener

class ConcreteBluetoothEventListener(values: JMap[String, Object], inboundErrors: Deque[BluetoothErrorRecord], previousValues: Deque[BluetoothValuePair])
  extends BluetoothEventListener{

  private val MaxPreviousValueSize = 500
  private val MaxErrorSize = 10
  private var residue: String = ""
  private var recentString : String = ""

  override def onValueUpdated(newValue: String): Unit = {
    if (newValue != null) {
      recentString = newValue
      residue += newValue
      val (newResidue, results) = BluetoothMessageParser.parseStream(residue)
      residue = newResidue
      results.foreach {
        case Right(BluetoothValuePair(key, value)) =>
          if (values != null) {
            values.put(key, value)
          }
          if (previousValues != null) {
            previousValues.add(BluetoothValuePair(key, value))
            for (i <- 0 until previousValues.size() - MaxPreviousValueSize) {
              previousValues.removeFirst()
            }
          }
        case Left(e: BluetoothErrorRecord) =>
          if (inboundErrors != null) {
            for (i <- 0 until inboundErrors.size - MaxErrorSize) {
              inboundErrors.removeLast()
            }
            inboundErrors.addFirst(e)
          }
      }
    }
  }

  def getRecentString : String = {
    recentString
  }
}
