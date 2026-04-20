package ai.flow.android.sensor;

import android.content.Context;

import ai.flow.flowy.PythonRunner;
import ai.flow.sensor.SensorInterface;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;

import android.app.Service;
import android.os.IBinder;
import android.content.Intent;
import android.util.Log;

import android.app.Activity;

import android.util.Log;

import android.widget.Toast;
import android.app.AlertDialog;
import java.util.HashMap;
import java.util.List;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import ai.flow.sensor.messages.MsgCanData;
import ai.flow.sensor.messages.MsgPandaState;
import ai.flow.sensor.messages.MsgPeripheralState;
import ai.flow.sensor.messages.MsgDriverMonitoringState;
import ai.flow.sensor.messages.MsgGpsLocationExternal;
import ai.flow.sensor.messages.MsgGyroscope;
import ai.flow.sensor.messages.MsgAccelerometer;
import ai.flow.sensor.messages.MsgDriverState;

import ai.flow.definitions.Definitions;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import messaging.ZMQPubHandler;

public class ArduinoManager implements SensorInterface {
    private Context ctx;
    private Activity activity;

    private static final String TAG = "FlowPilot";
    private Thread applicationThread = null;

	private static final String ACTION_USB_PERMISSION = "ai.flow.flowy.USB_PERMISSION";

    public ArduinoManager(Context ctx, Activity activity) {
        this.ctx = ctx;
        this.activity = activity;
    }

    @Override
    public void dispose() {}

    @Override
    public void stop() {}

    public void start() {
        IntentFilter attachFilter = new IntentFilter();
        // Receiver for attached devices, used to request permission when plugging in a device
        attachFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        // Receiver for extended permissions, called when the user accepts USB permissions
        attachFilter.addAction(ACTION_USB_PERMISSION);
        ctx.registerReceiver(usbReceiver, attachFilter, Context.RECEIVER_EXPORTED);

        // Request permission for already plugged devices
        UsbManager manager = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
		Log.i(TAG, "Number of USB devices found: "+deviceList.size());
        final int deviceCount = deviceList.size();

        for (UsbDevice usbDevice : deviceList.values())
        {
            maybeRequestUSBPermission(usbDevice, ctx);
        }
    }

    private BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public synchronized void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            System.out.println("RECEIVING INTENT: " + action);

            // If newly connected USB device, request permission from android
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                maybeRequestUSBPermission(usbDevice, context);
                return;
            } else if (!ACTION_USB_PERMISSION.equals(action))
                // Some other usb message return
                return;

            // Permission denied return
            UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (!intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                Log.i(TAG, "Permission denied for device " + device);
                return;
            }

            if(device == null)
                return;
            
            UsbManager usbManager = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);
            UsbDeviceConnection usbDeviceConnection = usbManager.openDevice(device);

            if (usbDeviceConnection == null) {
                Log.i(TAG, "Failed to open device");
                return;
            }

            try {
                List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
                UsbSerialDriver driver = availableDrivers.get(0);

                UsbSerialPort port = driver.getPorts().get(0);
                port.open(usbDeviceConnection);
                port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

                ArduinoInstance arduinoinstance = new ArduinoInstance(activity);
                
                SerialInputOutputManager usbIoManager = new SerialInputOutputManager(port, arduinoinstance);
                usbIoManager.start();


            } catch (Exception e) {
                Log.i(TAG, "Exception in onReceive usbReceiver: " + e);
            }
        }
    };

    private void maybeRequestUSBPermission(UsbDevice device, Context context) {
        if (device == null) {
            Log.w(TAG, "maybeRequestUSBPermission got a null device");
            return;
        }

        // Using arduino vendor ID: 0x1a86 and product ID: 0x7523
        // This may be different if the arudino is not an uno or has a different usb chip.
        if ((device.getVendorId() == 0x1a86) &&
            (device.getProductId() == 0x7523)) {
            Log.i(TAG, "Found an Arduino (VID: " + device.getVendorId() +  ", PID: " + device.getProductId() + ")");

            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_MUTABLE);
            ((UsbManager) context.getSystemService(Context.USB_SERVICE)).requestPermission(device, pendingIntent);
        } else {
            Log.w(TAG, "Found a USB device that's not a Arduino (VID: " + device.getVendorId() + ", PID: " + device.getProductId() + ")");
        }
    }

    public static native void nativeStart(int fd);
    public static native void nativeStop();
}

class ArduinoInstance implements SerialInputOutputManager.Listener {
    private static final String TAG = "FlowPilot";
    private Activity activity;
    private static ZMQPubHandler ph = new ZMQPubHandler();

    public ArduinoInstance(Activity activity) {
        this.activity = activity;
        ph.createPublishers(Arrays.asList("can", "pandaStates", "gpsLocationExternal", "accelerometer", "gyroscope", "peripheralState", "driverState", "driverMonitoringState"));

        ArduinoInstance.DummyPandaInstance dummyPanda = this.new DummyPandaInstance();
        dummyPanda.start();
    }

    @Override
    public void onNewData(byte[] data) {

        if (data.length < 8) return;

        // First 4 bytes represent canid
        ByteBuffer buffer = ByteBuffer.wrap(data, 0, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        int canId = buffer.getInt();

        // DLC is 5th byte
        int dlc = data[4] & 0xFF;
        if (data.length < 8 + dlc) return;
        
        // 2 padding bytes then rest is data
        byte[] canData = new byte[dlc];
        buffer = ByteBuffer.wrap(data, 8, dlc);
        buffer.get(canData);

        // activity.runOnUiThread(new Runnable() {
        //     public void run() {
        //         new AlertDialog.Builder(activity)
        //         .setTitle("Serial Message Received")
        //         // .setMessage("canId: " + Integer.toString(canId) + "\n DLC: " + Integer.toString(dlc) + "\n Data: " + Integer.toString((int)canData[0]))
        //         .setPositiveButton("OK", (dialog, which) -> { })
        //         .show();
        //     }
        // });

        MsgCanData msgCanData = new MsgCanData(dlc);

        msgCanData.canData.get(0).setAddress(canId);
        msgCanData.canData.get(0).setSrc((byte)0);
        msgCanData.canData.get(0).setBusTime((short)0);
        // msgCanData.canData.get(0).setDat(canData);
        ph.publishBuffer("can", msgCanData.serialize(true));
    }

    @Override
    public void onRunError(Exception e) {
        Log.i(TAG, "onRunError exception in ArduinoInstance: " + e);
    }

    class DummyPandaInstance implements Runnable {
        private MsgPandaState msgPandaState;
        private MsgPeripheralState msgPeripheralState;
        private MsgGpsLocationExternal msgGpsLocationExternal;
        private MsgAccelerometer msgAccelerometer;
        private MsgGyroscope msgGyroscope;
        private MsgDriverState msgDriverState;
        private MsgDriverMonitoringState msgDriverMonitoringState;
        long lastCanDataTime;

        public DummyPandaInstance() { }

        public void start() {
            try {
                msgPandaState = new MsgPandaState();
                msgPeripheralState = new MsgPeripheralState();
                msgGpsLocationExternal = new MsgGpsLocationExternal();
                msgAccelerometer = new MsgAccelerometer();
                msgGyroscope = new MsgGyroscope();
                msgDriverState = new MsgDriverState();
                msgDriverMonitoringState = new MsgDriverMonitoringState();

                initPandaState();
                initPeripheralState();
                initGpsLocationExternal();
                initAccelerometer();
                initGyroscope();
                initDriverState();
                initDriverMonitoringState();

                Thread dummyPandaState = new Thread(this);
                dummyPandaState.start();

            } catch (Exception e) {
                Log.i(TAG, "Exception in DummyPandaInstance start: " + e);
            }

        }

        public void run() {
            try {
                while (true) {
                    //TODO: fix frequency issue - doesn't account for function runtime or modulus (could skip messages)

                    if (System.currentTimeMillis() % 500L == 0) {
                        // Runs at 2hz which is 500ms
                        ph.publishBuffer("pandaStates", msgPandaState.serialize(true));
                        ph.publishBuffer("peripheralState", msgPeripheralState.serialize(true));
                    }

                    if (System.currentTimeMillis() % 100L == 0) {
                        // Runs at 10hz which is 100ms
                        ph.publishBuffer("gpsLocationExternal", msgGpsLocationExternal.serialize(true));
                        ph.publishBuffer("driverState", msgDriverState.serialize(true));
                        ph.publishBuffer("driverMonitoringState", msgDriverMonitoringState.serialize(true));
                    }

                    if (System.currentTimeMillis() % 10L == 0) {
                        // Runs at 100hz which is 10ms
                        msgAccelerometer.accelerometer.setTimestamp(System.currentTimeMillis());
                        msgGyroscope.gyroscope.setTimestamp(System.currentTimeMillis());

                        ph.publishBuffer("accelerometer", msgAccelerometer.serialize(true));
                        ph.publishBuffer("gyroscope", msgGyroscope.serialize(true));
                    }
                }
            } catch (Exception e) {
                Log.i(TAG, "Exception in DummyPandaInstance run: " + e);
            }
        }

        public void initPandaState() {
            msgPandaState.pandaStates.get(0).setPandaType(Definitions.PandaState.PandaType.BLACK_PANDA);
            msgPandaState.pandaStates.get(0).setControlsAllowed(true);
            msgPandaState.pandaStates.get(0).setSafetyModel(ai.flow.definitions.CarDefinitions.CarParams.SafetyModel.HONDA_NIDEC);
            msgPandaState.pandaStates.get(0).setIgnitionLine(true);

            // msgPandaState.pandaStates.get(0).setHeartbeatLost(false);
            // msgPandaState.pandaStates.get(0).setHarnessStatus(ai.flow.definitions.Definitions.PandaState.HarnessStatus.NORMAL);
            // msgPandaState.pandaStates.get(0).setIgnitionCan(true);
            // msgPandaState.pandaStates.get(0).setFaultStatus(ai.flow.definitions.Definitions.PandaState.FaultStatus.NONE);

            // msgPandaState.pandaStates.get(0).setCurrent(3);
            // msgPandaState.pandaStates.get(0).setVoltage(0);
        }

        public void initPeripheralState() {
            msgPeripheralState.peripheralState.setPandaType(ai.flow.definitions.Definitions.PandaState.PandaType.BLACK_PANDA);
            msgPeripheralState.peripheralState.setVoltage(12000);
            msgPeripheralState.peripheralState.setCurrent(5678);
            msgPeripheralState.peripheralState.setFanSpeedRpm((short)1000);
        }

        public void initGpsLocationExternal() {
            msgGpsLocationExternal.gpsLocationExternal.setUnixTimestampMillis((long)(System.currentTimeMillis()));
            msgGpsLocationExternal.gpsLocationExternal.setFlags((short)1);
            msgGpsLocationExternal.gpsLocationExternal.setAccuracy(1.0f);
            msgGpsLocationExternal.gpsLocationExternal.setVerticalAccuracy(1.0f);
            msgGpsLocationExternal.gpsLocationExternal.setSpeedAccuracy(0.1f);
            msgGpsLocationExternal.gpsLocationExternal.setBearingAccuracyDeg(0.1f);

            msgGpsLocationExternal.gpsLocationExternal.initVNED(3);
            msgGpsLocationExternal.gpsLocationExternal.getVNED().set(0, 0.0f);
            msgGpsLocationExternal.gpsLocationExternal.getVNED().set(1, 0.0f);
            msgGpsLocationExternal.gpsLocationExternal.getVNED().set(2, 0.0f);

            msgGpsLocationExternal.gpsLocationExternal.setBearingDeg(0.0f);
            msgGpsLocationExternal.gpsLocationExternal.setLatitude(20.0);
            msgGpsLocationExternal.gpsLocationExternal.setLongitude(30.0);
            msgGpsLocationExternal.gpsLocationExternal.setAltitude(1000.0);
            msgGpsLocationExternal.gpsLocationExternal.setSpeed(20.0f);
            msgGpsLocationExternal.gpsLocationExternal.setSource(Definitions.GpsLocationData.SensorSource.UBLOX);
        }

        public void initAccelerometer() {
            msgAccelerometer.accelerometer.setSensor((byte)4);
            msgAccelerometer.accelerometer.setType((byte)0x10);
            msgAccelerometer.accelerometer.getAcceleration().initV(3);
            msgAccelerometer.accelerometer.getAcceleration().getV().set(0, 0.0f);
            msgAccelerometer.accelerometer.getAcceleration().getV().set(1, 0.0f);
            msgAccelerometer.accelerometer.getAcceleration().getV().set(2, 0.0f);
        }

        public void initGyroscope() {
            msgGyroscope.gyroscope.setSensor((byte)5);
            msgGyroscope.gyroscope.setType((byte)0x10);
            msgGyroscope.gyroscope.getGyro().initV(3);
            msgGyroscope.gyroscope.getGyro().getV().set(0, 0.0f);
            msgGyroscope.gyroscope.getGyro().getV().set(1, 0.0f);
            msgGyroscope.gyroscope.getGyro().getV().set(2, 0.0f);
        }

        public void initDriverState() {
            msgDriverState.driverState.getLeftDriverData().setFaceProb(1.0f);
        }

        public void initDriverMonitoringState() {
            msgDriverMonitoringState.driverMonitoringState.setFaceDetected(true);
            msgDriverMonitoringState.driverMonitoringState.setIsDistracted(false);
            msgDriverMonitoringState.driverMonitoringState.setAwarenessStatus(1.0f);
        }
    }
}