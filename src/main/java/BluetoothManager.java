
import javax.bluetooth.*;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

class BluetoothManager {

    private static final Object lock = new Object();

    private BluetoothEventListener bluetoothEventListener;
    private boolean isOpen;
    private Thread readDataThread;

    private LocalDevice localDevice;
    private DiscoveryAgent discoveryAgent;
    private RemoteDevice openedDevice;

    private StreamConnection streamConnection;
    private OutputStream os;
    private InputStream is;


    private Map<String, RemoteDevice> deviceMap;
    private List<String> devices;


    private static final int timeout = 25; // In seconds

    public BluetoothManager() throws BluetoothStateException {
//        try {
//            localDevice = LocalDevice.getLocalDevice();
//        } catch (BluetoothStateException | NullPointerException e) {
//            e.printStackTrace();
//        }
        localDevice = LocalDevice.getLocalDevice();
        discoveryAgent = localDevice.getDiscoveryAgent();
    }

    public synchronized List<String> getDevices() {
        return getDevices(() -> {});
    }

    public synchronized List<String> getDevices(Runnable loadingCallback) {

        Callable<Boolean> fillDevices = this::fillListWithDevices;
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<Boolean> future = executorService.submit(fillDevices);

        int timeoutSeconds = timeout;

        while(!future.isDone() && timeoutSeconds > 0) {
            try {
                loadingCallback.run();
                Thread.sleep(1000);
                timeoutSeconds--;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (future.isDone()) {
            try {
                boolean result = future.get();
                if (result) {
                    System.out.println("\nSuccessful in retrieving devices");
                } else {
                    System.err.println("\nUnable to retrieve devices");
                }
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        } else {
            System.err.println("\nTimeout occurred");
        }
        executorService.shutdown();

        return devices;
    }

    private Boolean fillListWithDevices() {
        if (discoveryAgent != null) {
            List<String> _devices = new ArrayList<>();
            Map<String, RemoteDevice> _deviceMap = new HashMap<>();
            try {
                discoveryAgent.startInquiry(DiscoveryAgent.GIAC, new DiscoveryListener() {
                    @Override
                    public void deviceDiscovered(RemoteDevice remoteDevice, DeviceClass deviceClass) {
                        try {
                            String friendlyName = remoteDevice.getFriendlyName(false);
                            _devices.add(friendlyName);
                            _deviceMap.put(friendlyName, remoteDevice);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void servicesDiscovered(int transID, ServiceRecord[] serviceRecords) {

                    }

                    @Override
                    public void serviceSearchCompleted(int transID, int respCode) {

                    }

                    @Override
                    public void inquiryCompleted(int discType) {
                        synchronized (lock) {
                            lock.notify();
                        }
                    }
                });
                synchronized (lock) {
                    lock.wait();
                }
            } catch (BluetoothStateException | InterruptedException e) {
                e.printStackTrace();
                return false;
            }

            devices = _devices;
            deviceMap = _deviceMap;

            return true;
        }
        return false;
    }

    public boolean isSetup() {
        return (devices != null && deviceMap != null);
    }

    public synchronized boolean openDevice(String name) throws BluetoothStateException, InterruptedException, Exception {
        return openDevice(name, () -> {});
    }

    /**
     *
     * @param name Friendly name of the bluetooth device
     * @param loadingCallback Function that runs while waiting for the device to open
     * @return True if device was successfully opened, else false
     * @throws BluetoothStateException Reports bluetooth status
     * @throws InterruptedException Thread exception
     * @throws Exception
     */
    public synchronized boolean openDevice(String name, Runnable loadingCallback) throws BluetoothStateException, InterruptedException, Exception {
        isOpen = false;
        if (isSetup()) {
            if (deviceMap.containsKey(name)) {
                RemoteDevice remoteDevice = deviceMap.get(name);
                Callable<Exception> deviceOpened = () -> deviceOpened(remoteDevice);
                ExecutorService executorService = Executors.newSingleThreadExecutor();
                Future<Exception> future = executorService.submit(deviceOpened);


                int timeoutSeconds = timeout;

                while(!future.isDone() && timeoutSeconds > 0) {
                    try {
                        loadingCallback.run();
                        Thread.sleep(1000);
                        timeoutSeconds--;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        return false;
                    }
                }

                Exception result = new Exception();

                if (future.isDone()) {
                    try {
                        result = future.get();
                        if (result == null) {
                            isOpen = true;
                            startReading();
                            System.out.println("\nSuccessful in opening " + name);
                        } else {
                            isOpen = false;
                            System.err.println("\nUnable to open " + name);
                            throw result;
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                        return false;
                    }
                } else {
                    System.err.println("\nTimeout occurred, is " + name + " connected to your computer?");
                }
                executorService.shutdown();
                return (result == null);
            }
            return false; // The device wasn't found
        }
        throw new Exception("Haven't searched for devices yet, please search for available devices before opening one");
    }

    /**
     * Opens bluetooth device
     * @param remoteDevice The device that is wished to be connected to
     * @return null if no exception has occurred, else returns an exception
     */
    private Exception deviceOpened(RemoteDevice remoteDevice) {
        if (discoveryAgent != null) {
            try {
                StreamConnection _streamConnection;
                OutputStream _os;
                InputStream _is;

                UUID uuid = new UUID(4353);
                UUID[] searchUuidSet = new UUID[]{uuid};
                final String[] url = new String[1];
                discoveryAgent.searchServices(null, searchUuidSet, remoteDevice, new DiscoveryListener() {
                    @Override
                    public void deviceDiscovered(RemoteDevice remoteDevice, DeviceClass deviceClass) {

                    }

                    @Override
                    public void servicesDiscovered(int transID, ServiceRecord[] serviceRecords) {
                        for (int i = 0; i < serviceRecords.length; i++) {
                            url[0] = serviceRecords[i].getConnectionURL(ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false);
                            if (url[0] != null) {
                                break;
                            }
                        }
                    }

                    @Override
                    public void serviceSearchCompleted(int transID, int respCode) {
                        synchronized (lock) {
                            lock.notify();
                        }
                    }

                    @Override
                    public void inquiryCompleted(int discType) {

                    }
                });
                synchronized (lock) {
                    lock.wait();
                }

                try {
                    // The program lags on this function if the device isn't connected to the computer properly
                    // If this happens, a timeout happens on Mac and a BluetoothStateException occurs on Windows
                    // Windows message -> Doesn't have access to socket
                    _streamConnection = (StreamConnection) Connector.open(url[0]);
                    _os = _streamConnection.openOutputStream();
                    _is = _streamConnection.openInputStream();

                    openedDevice = remoteDevice;
                    streamConnection = _streamConnection;
                    os = _os;
                    is = _is;
                } catch(IOException e) {
                    e.printStackTrace();
                    return e;
                }

                return null;
            } catch (BluetoothStateException | InterruptedException e) {
                e.printStackTrace();
                return e;
            }
        }
        return new Exception("This machine may not be able to detect bluetooth devices");
    }

    public boolean deviceIsOpen() {
        return isOpen;
    }

    public void close() {
        if (isOpen) {
            try {
                if (streamConnection != null) {
                    streamConnection.close();
                }
                if (os != null) {
                    os.close();
                }
                if (is != null) {
                    is.close();
                }
            } catch(IOException e) {
                e.printStackTrace();
            } finally {
                openedDevice = null;
                streamConnection = null;
                os = null;
                is = null;
                isOpen = false;
            }
        }
    }

    public void writeString(String input) {
        if (os != null) {
            try {
                os.write(input.getBytes());
            } catch (IOException e) {
                close();
            }
        }
    }

    public void writeInt(int input) {
        if (os != null) {
            try {
                os.write((byte) input);
            } catch (IOException e) {
                close();
            }
        }
    }

    public BluetoothEventListener getBluetoothEventListener() {
        return bluetoothEventListener;
    }

    public void setBluetoothEventListener(BluetoothEventListener newBluetoothEventListener) {
        bluetoothEventListener = newBluetoothEventListener;
    }

    private synchronized void startReading() {
        if (is != null) {
            readDataThread = new Thread(() -> {
                while (deviceIsOpen()) {
                    try {
                        byte[] bytes = new byte[32]; // Arbitrary size
                        int result = is.read(bytes);
                        if (result != -1) {
                            String output = new String(bytes);
                            if (bluetoothEventListener != null) {
                                output = output.replace("\0", "");
                                bluetoothEventListener.onValueUpdated(output);
                            }
                        } else {
                            close();
                        }
                    } catch (IOException | NullPointerException e) {
                        close();
                    }
                }
            });
            readDataThread.start();
        }
    }

    void cleanUp() {
        close();
        localDevice = null;
        discoveryAgent = null;
        deviceMap = null;
        devices = null;
        bluetoothEventListener = null;
        System.gc();
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
    }
}