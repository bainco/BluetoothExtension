package bluetooth.implementation

case class BluetoothErrorRecord(inboundString: String,
                       errorDescription: String,
                       exception: Option[Exception])