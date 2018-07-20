import org.nlogo.api.*;
import org.nlogo.core.Syntax;
import org.nlogo.core.SyntaxJ;

import javax.bluetooth.BluetoothStateException;
import java.util.*;
import java.util.concurrent.LinkedBlockingDeque;

public class BluetoothExtension extends DefaultClassManager {

    private BluetoothManager manager;

    public BluetoothExtension() throws ExtensionException {
        try {
            manager = new BluetoothManager();
            manager.setBluetoothEventListener(new ConcreteBluetoothEventListener(values, inboundErrors, previousValues));
        } catch (BluetoothStateException e) {
            throw new ExtensionException("Bluetooth state exception : " + e.getMessage());
        }
    }

    private Map<String, Object> values =
            Collections.<String, Object>synchronizedMap(new HashMap<String, Object>());

    private Deque<BluetoothErrorRecord> inboundErrors = new LinkedBlockingDeque<>();

    private LinkedList<String> outboundMessages = new LinkedList<>();

    private Deque<BluetoothValuePair> previousValues = new LinkedBlockingDeque<>();

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
        System.gc();
        manager.cleanUp();
        System.gc();
        manager = null;
        for (int i = 0; i < 10; i++) {
            System.gc();
        }
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
//    public static class GetDump implements Reporter {
//
//        @Override
//        public Object report(Argument[] args, Context context) throws ExtensionException {
//            return null;
//        }
//
//        @Override
//        public Syntax getSyntax() {
//            return null;
//        }
//    }
}