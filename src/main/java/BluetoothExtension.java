import bluetooth.implementation.BluetoothErrorRecord;
import bluetooth.implementation.BluetoothValuePair;
import bluetooth.interfaces.BluetoothEventListener;
import bluetooth.interfaces.BluetoothManager;
import org.nlogo.api.*;
import org.nlogo.core.Syntax;
import org.nlogo.core.SyntaxJ;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.LinkedBlockingDeque;

public class BluetoothExtension extends DefaultClassManager {

    private BluetoothManager manager;

    public static String PATH = System.getProperty("netlogo.extensions.dir", "extensions") + File.separator + "bluetooth" + File.separator;

    public BluetoothExtension() throws ExtensionException {
        try {
            String className = "bluetooth.implementation.ConcreteBluetoothManager";
            File file = new File(PATH + "BluetoothImplementation.jar");
            File file2 = new File(PATH + "BluetoothInterfaces.jar");
            File file3 = new File(PATH + "bluecove-2.1.2.jar");

            if (file.exists() && file2.exists() && file3.exists()) {
                try {
                    Class test = ClassLoader.getSystemClassLoader().loadClass("bluetooth.implementation.ConcreteBluetoothManager");
                } catch (ClassNotFoundException ex) {
                    ClassLoader cl = ClassLoader.getSystemClassLoader();
                    if ( cl instanceof URLClassLoader ) {
                        URLClassLoader ul = (URLClassLoader)cl;
                        Class<?>[] paramTypes = new Class<?>[] { URL.class };
                        try {
                            Method method = null;
                            method = URLClassLoader.class.getDeclaredMethod("addURL", paramTypes);
                            method.setAccessible(true);
                            method.invoke(ClassLoader.getSystemClassLoader(), file.toURI().toURL());
                            method.invoke(ClassLoader.getSystemClassLoader(), file2.toURI().toURL());
                            method.invoke(ClassLoader.getSystemClassLoader(), file3.toURI().toURL());
                        } catch (Exception e1) {

                        }
                    } else {
                        throw new ExtensionException("Extension exception : SystemClassLoader doesn't extend URLClassLoader");
                    }
                } finally {
                    Class<?> concreteBluetoothManager = ClassLoader.getSystemClassLoader().loadClass("bluetooth.implementation.ConcreteBluetoothManager");
                    Class<?> concreteBluetoothEventListener = ClassLoader.getSystemClassLoader().loadClass("bluetooth.implementation.ConcreteBluetoothEventListener");
                    Object _manager = concreteBluetoothManager.newInstance();
                    manager = (BluetoothManager)_manager;

                    values = Collections.<String, Object>synchronizedMap(new HashMap<String, Object>());
                    inboundErrors = new LinkedBlockingDeque<>();
                    outboundMessages = new LinkedList<>();
                    previousValues = new LinkedBlockingDeque<>();

                    Object _listener = concreteBluetoothEventListener.getDeclaredConstructor(Map.class, Deque.class, Deque.class).newInstance(values, inboundErrors, previousValues);
                    manager.setBluetoothEventListener((BluetoothEventListener) _listener);
                }
            }
        } catch (Exception e) {
            throw new ExtensionException("Extension exception : " + e.getMessage());
        }
    }

    private Map<String, Object> values = null;

    private Deque<BluetoothErrorRecord> inboundErrors = null;

    private LinkedList<String> outboundMessages = null;

    private Deque<BluetoothValuePair> previousValues = null;

    private static void addMessage(LinkedList<String> messages, String messageType, String messageValue) {
        while (messages.size() >= 5) {
            messages.removeLast();
        }
        messages.addFirst(messageType + ":" + messageValue);
    }

    @Override
    public void load(PrimitiveManager primManager) {
        primManager.addPrimitive("primitives", new Primitives());
        primManager.addPrimitive("devices", new Devices(manager));
        primManager.addPrimitive("open", new Open(manager));
        primManager.addPrimitive("close", new Close(manager));
        primManager.addPrimitive("get", new Get(values));
        primManager.addPrimitive("write-string", new WriteString(manager, outboundMessages));
        primManager.addPrimitive("write-int", new WriteInt(manager, outboundMessages));
        primManager.addPrimitive("write-byte", new WriteInt(manager, outboundMessages));
        primManager.addPrimitive("is-open?", new IsOpen(manager));
        primManager.addPrimitive("is-setup?", new IsSetup(manager));
        primManager.addPrimitive("debug-to-bluetooth", new DebugToBluetooth(outboundMessages));
        primManager.addPrimitive("debug-from-bluetooth", new DebugFromBluetooth(inboundErrors));
        primManager.addPrimitive("get-previous-values", new GetPreviousValues(previousValues));
        primManager.addPrimitive("reset-previous-values", new ResetPreviousValues(previousValues));
    }

    @Override
    public void unload(ExtensionManager em) throws ExtensionException {
        super.unload(em);
        manager.cleanUp();
        manager = null;
    }

    public static class Primitives implements Reporter {

        @Override
        public Object report(Argument[] args, Context context) {
            LogoListBuilder llist = new LogoListBuilder();
            String[] prims = {"reporter:primitives",
                    "reporter:devices",
                    "reporter:get[Name:String(case-insensitive)]",
                    "reporter:is-open?",
                    "reporter:is-setup?",
                    "reporter:debug-to-bluetooth",
                    "reporter:debug-from-bluetooth",
                    "reporter:get-previous-values",
                    "",
                    "command:open[DeviceName:String]",
                    "command:close",
                    "command:write-string[Message:String]",
                    "command:write-int[Message:int]",
                    "command:write-byte[Message:byte]",
                    "command:reset-previous-values"};
            for (String prim : prims) {
                llist.add(prim);
            }
            return llist.toLogoList();
        }

        @Override
        public Syntax getSyntax() {
            return SyntaxJ.reporterSyntax(Syntax.ListType());
        }
    }

    public static class Devices implements Reporter {

        private BluetoothManager _manager;

        public Devices(BluetoothManager manager) {
            _manager = manager;
        }

        @Override
        public Object report(Argument[] args, Context context) {
            LogoListBuilder llist = new LogoListBuilder();
            List<String> devices = _manager.getDevices();
            for (String device : devices) {
                llist.add(device);
            }
            return llist.toLogoList();
        }

        @Override
        public Syntax getSyntax() {
            return SyntaxJ.reporterSyntax(Syntax.ListType());
        }
    }

    public static class Open implements Command {

        private BluetoothManager _manager;

        public Open(BluetoothManager manager) {
            _manager = manager;
        }

                @Override
                public void perform(Argument[] args, Context context) throws ExtensionException {
                    try {
                boolean result = false;
                result = _manager.openDevice(args[0].getString());
                if (!result) {
                    throw new ExtensionException("Error opening " + args[0].getString() + ", please try reconnecting the bluetooth device to your machine");
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new ExtensionException("Exception thrown : " + e.getMessage());
            }
        }

        @Override
        public Syntax getSyntax() {
            return SyntaxJ.commandSyntax(new int[]{Syntax.StringType()});
        }
    }

    public static class Close implements Command {

        private BluetoothManager _manager;

        public Close(BluetoothManager manager) {
            _manager = manager;
        }

        @Override
        public void perform(Argument[] args, Context context) throws ExtensionException {
            if (!_manager.deviceIsOpen()) {
                throw new ExtensionException("No device has been open");
            }
            _manager.close();
        }

        @Override
        public Syntax getSyntax() {
            return SyntaxJ.commandSyntax();
        }
    }

    public static class Get implements Reporter {

        private Map<String, Object> _values;

        public Get(Map<String, Object> values) {
            _values = values;
        }

        @Override
        public Object report(Argument[] args, Context context) throws ExtensionException {
            return get(args[0].getString());
        }

        public Object get(String key) {
            String lcKey = key.toLowerCase();
            return _values.getOrDefault(lcKey, Boolean.FALSE);
        }

        @Override
        public Syntax getSyntax() {
            return SyntaxJ.reporterSyntax(new int[] {Syntax.StringType()},
                    Syntax.StringType() | Syntax.BooleanType() | Syntax.NumberType());
        }
    }

    public static class WriteString implements Command {

        private BluetoothManager _manager;
        private LinkedList<String> _outboundMessages;

        public WriteString(BluetoothManager manager, LinkedList<String> outboundMessages) {
            _manager = manager;
            _outboundMessages = outboundMessages;
        }

        @Override
        public void perform(Argument[] args, Context context) throws ExtensionException {
            String arg = args[0].getString();
            _manager.writeString(arg);
            addMessage(_outboundMessages, "s", arg);
        }

        @Override
        public Syntax getSyntax() {
            return SyntaxJ.commandSyntax(new int[]{ Syntax.StringType() });
        }
    }

    public static class WriteInt implements Command {

        private BluetoothManager _manager;
        private LinkedList<String> _outboundMessages;

        public WriteInt(BluetoothManager manager, LinkedList<String> outboundMessages) {
            _manager = manager;
            _outboundMessages = outboundMessages;
        }

        @Override
        public void perform(Argument[] args, Context context) throws ExtensionException {
            int arg = args[0].getIntValue();
            _manager.writeInt(arg);
            addMessage(_outboundMessages, "i", Integer.toString(arg));
        }

        @Override
        public Syntax getSyntax() {
            return SyntaxJ.commandSyntax(new int[]{ Syntax.NumberType() });
        }
    }

    public static class IsOpen implements Reporter {

        private BluetoothManager _manager;

        public IsOpen(BluetoothManager manager) {
            _manager = manager;
        }

        @Override
        public Object report(Argument[] args, Context context) throws ExtensionException {
            return _manager.deviceIsOpen();
        }

        @Override
        public Syntax getSyntax() {
            return SyntaxJ.reporterSyntax(Syntax.BooleanType());
        }
    }

    public static class IsSetup implements Reporter {

        private BluetoothManager _manager;

        public IsSetup(BluetoothManager manager) {
            _manager = manager;
        }

        @Override
        public Object report(Argument[] args, Context context) throws ExtensionException {
            return _manager.isSetup();
        }

        @Override
        public Syntax getSyntax() {
            return SyntaxJ.reporterSyntax(Syntax.BooleanType());
        }
    }

    public static class DebugFromBluetooth implements Reporter {

        private Deque<BluetoothErrorRecord> _inboundErrors;

        public DebugFromBluetooth(Deque<BluetoothErrorRecord> inboundErrors) {
            _inboundErrors = inboundErrors;
        }

        @Override
        public Object report(Argument[] args, Context context) throws ExtensionException {
            LogoListBuilder llist = new LogoListBuilder();

            Iterator<BluetoothErrorRecord> iter = _inboundErrors.iterator();

            while (iter.hasNext()) {
                LogoListBuilder itemBuilder = new LogoListBuilder();
                BluetoothErrorRecord record = iter.next();
                itemBuilder.add(record.inboundString());
                itemBuilder.add(record.errorDescription());
                if (record.exception().nonEmpty()) {
                    itemBuilder.add(record.exception().get().getMessage());
                }
                llist.add(itemBuilder.toLogoList());
            }

            return llist.toLogoList();
        }

        @Override
        public Syntax getSyntax() {
            return SyntaxJ.reporterSyntax(Syntax.ListType());
        }
    }

    public static class DebugToBluetooth implements Reporter {

        private LinkedList<String> _outboundMessages;

        public DebugToBluetooth(LinkedList<String> outboundMessages) {
            _outboundMessages = outboundMessages;
        }

        @Override
        public Object report(Argument[] args, Context context) throws ExtensionException {
            LogoListBuilder llist = new LogoListBuilder();

            Iterator<String> iter = _outboundMessages.iterator();

            while(iter.hasNext()) {
                llist.add(iter.next());
            }

            return llist.toLogoList();
        }

        @Override
        public Syntax getSyntax() {
            return SyntaxJ.reporterSyntax(Syntax.ListType());
        }
    }

    public static class GetPreviousValues implements Reporter {

        private Deque<BluetoothValuePair> _previousValues;

        public GetPreviousValues(Deque<BluetoothValuePair> previousValues) {
            _previousValues = previousValues;
        }

        @Override
        public Object report(Argument[] args, Context context) throws ExtensionException {
            LogoListBuilder llist = new LogoListBuilder();
            Iterator<BluetoothValuePair> iter = _previousValues.iterator();
            while (iter.hasNext()) {
                LogoListBuilder itemBuilder = new LogoListBuilder();
                BluetoothValuePair record = iter.next();
                itemBuilder.add(record.key());
                itemBuilder.add(record.value());
                llist.add(itemBuilder.toLogoList());
            }
            return llist.toLogoList();
        }

        @Override
        public Syntax getSyntax() {
            return SyntaxJ.reporterSyntax(Syntax.ListType());
        }
    }

    public static class ResetPreviousValues implements Command {

        private Deque<BluetoothValuePair> _previousValues;

        public ResetPreviousValues(Deque<BluetoothValuePair> previousValues) {
            _previousValues = previousValues;
        }

        @Override
        public void perform(Argument[] args, Context context) throws ExtensionException {
            _previousValues.clear();
        }

        @Override
        public Syntax getSyntax() {
            return SyntaxJ.commandSyntax();
        }
    }
}