import java.util.List;

public interface BluetoothManager {
    List<String> getDevices();
    List<String> getDevices(Runnable loadingCallback);
    boolean isSetup();
    boolean openDevice(String name) throws Exception;
    boolean openDevice(String name, Runnable loadingCallback) throws Exception;
    boolean deviceIsOpen();
    void close();
    void writeString(String input);
    void writeInt(int input);
    BluetoothEventListener getBluetoothEventListener();
    void setBluetoothEventListener(BluetoothEventListener newBluetoothEventListener);
    void cleanUp();
}