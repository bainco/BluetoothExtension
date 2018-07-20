# BluetoothExtension
This extension provides Bluetooth support to NetLogo using the BlueCove library and provides a similar interface used by the Arduino extension

To add this extension, download the 2 jar files found under releases and put them in the extensions folder in NetLogo

## Connecting to the computer
Before using the Bluetooth extension, the bluetooth device must be connected your computer or else your devices can't be found in NetLogo.
### Mac
1. Go to System Preferences and click on Bluetooth
2. Find your device on the list of Bluetooth devices and click connect
3. Enter the passkey if needed
### Windows

## Converting code from the Arduino extension for the Bluetooth extension 
The setup process for using the Bluetooth extension is fairly similar to the process of setting up the Arduino extension. If a project is already compatible with the Arduino extension, a find and replace of `arduino` to `bluetooth` will work for the most part.

An example is provided in the sample folder.

### Notes
* `bluetooth:devices` is not a primitive, the equivalient of `arduino:ports` is `bluetooth:devices`
* One must call `bluetooth:devices` in order to call `bluetooth:open` 
* If you get a timeout for calling `bluetooth:open` on Mac, try reconnecting the Bluetooth device to your computer via System Preferences
* If you get an error saying the you can't access the a port with `bluetooth:open` on Windows, try reconnecting the Bluetooth device to your computer