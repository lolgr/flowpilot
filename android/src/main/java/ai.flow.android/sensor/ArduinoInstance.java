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
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
import messaging.ZMQSubHandler;

import ai.flow.app.CloudLogConsole;

public class ArduinoInstance implements SerialInputOutputManager.Listener {
    private static final String TAG = "FlowPilot";
    private static final String CAN_REPLAY_ASSET = "can_replay.bin";
    private static final int CAN_REPLAY_RECORD_SIZE = 16;
    private Context ctx;
    private Activity activity;
    private volatile boolean replayRunning = false;
    private UsbSerialPort port = null;
    private static ZMQPubHandler ph = new ZMQPubHandler();
    private static ZMQSubHandler sh = new ZMQSubHandler(true);

    public ArduinoInstance(Context ctx, Activity activity) {
        this.ctx = ctx;
        this.activity = activity;
        ph.createPublishers(Arrays.asList("can", "pandaStates", "gpsLocationExternal", "accelerometer", "gyroscope", "peripheralState", "driverState", "driverMonitoringState"));
        sh.createSubscribers(Arrays.asList("sendcan"));

        ArduinoInstance.DummyPandaInstance dummyPanda = this.new DummyPandaInstance();
        dummyPanda.start();

        startCanReplay();
        startSendCanThread();
    }

    public ArduinoInstance(Context ctx, Activity activity, UsbSerialPort port) {
        this(ctx, activity);
        this.port = port;
    }

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    private void startCanReplay() {
        if (replayRunning) return;

        replayRunning = true;
        Thread replayThread = new Thread(new Runnable() {
            @Override
            public void run() {
                replayCanAsset();
            }
        }, "ArduinoCanReplay");
        replayThread.start();
    }

    private void replayCanAsset() {
        while (replayRunning) {
            try (InputStream in = ctx.getAssets().open(CAN_REPLAY_ASSET)) {
                byte[] record = new byte[CAN_REPLAY_RECORD_SIZE];
                int sent = 0;

                while (replayRunning && readFullRecord(in, record)) {
                    int dlc = record[5] & 0xFF;
                    int delayMs = ((record[7] & 0xFF) << 8) | (record[6] & 0xFF);
                    if (dlc > 8) {
                        CloudLogConsole.println("Skipping CAN replay record with invalid DLC: " + dlc);
                        continue;
                    }

                    if (delayMs > 0) {
                        Thread.sleep(delayMs);
                    }

                    publishReplayCan(record, dlc);
                    sent++;
                }

                CloudLogConsole.println("Finished Android CAN replay pass, published " + sent + " frames");
            } catch (IOException e) {
                CloudLogConsole.println("No Android CAN replay asset found at assets/" + CAN_REPLAY_ASSET + ": " + e);
                replayRunning = false;
            } catch (Exception e) {
                CloudLogConsole.println("Exception in Android CAN replay: " + e);
                replayRunning = false;
            }
        }
    }

    private void publishReplayCan(byte[] record, int dlc) {
        ByteBuffer canIdBuffer = ByteBuffer.wrap(record, 0, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        int canId = canIdBuffer.getInt();
        byte bus = record[4];

        byte[] canData = new byte[dlc];
        System.arraycopy(record, 8, canData, 0, dlc);

        MsgCanData msgCanData = new MsgCanData(dlc);
        msgCanData.canData.get(0).setAddress(canId);
        msgCanData.canData.get(0).setSrc(bus);
        msgCanData.canData.get(0).setBusTime((short)0);
        msgCanData.canData.get(0).getDat().asByteBuffer().put(canData);
        ph.publishBuffer("can", msgCanData.serialize(true));
    }

    private boolean readFullRecord(InputStream in, byte[] record) throws IOException {
        int offset = 0;
        while (offset < record.length) {
            int read = in.read(record, offset, record.length - offset);
            if (read < 0) {
                return false;
            }
            offset += read;
        }
        return true;
    }

    @Override
    public void onNewData(byte[] data) {
        // try {
        //     // CloudLogConsole.println("newData: " + bytesToHex(data));
        //     // CloudLogConsole.println("newData: " + ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getFloat());
        //     CloudLogConsole.println("newData HEX:" + bytesToHex(data) + "  float: " + ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getFloat() + " length: " + data.length);

        //     // byte[] fakeDat = new byte[] { (byte)0x0F, (byte)0xF1 };
        //     // byte[] fakeDat = new byte[] { (byte)0x6E, (byte)0x28, (byte)0x00, (byte)0x80, (byte)0x64, (byte)0x80, (byte)0x1C, (byte)0x63 };
        //     // sendSerial(fakeDat);

        //     // Thread.sleep(1000);
        // } catch (Exception e) {
        //     CloudLogConsole.println("Exception in onNewData: " + e);
        // }
        // return;
        
        if (data.length < 8) {
            // if (true || data.length == 6) {
            //     try {
            //         CloudLogConsole.println("newData HEX:" + bytesToHex(data) + "  float: " + ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getFloat());
            //     } catch (Exception e) { } 
            // }

            return;
        }

        // First 4 bytes represent canid
        ByteBuffer buffer = ByteBuffer.wrap(data, 0, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        Integer canId = buffer.getInt();

        // DLC is 5th byte
        int dlc = data[4] & 0xFF;
        if (data.length < 8 + dlc) return;
        
        // Header is 8 bytes; copy only the CAN payload into the Cap'n Proto dat field.
        byte[] canData = new byte[dlc];
        System.arraycopy(data, 8, canData, 0, dlc);

        // byte[] canData = new byte[dlc];
        // ByteBuffer.wrap(data, 8, dlc).get(canData);

        // byte[] canData = new byte[] { (byte)0x0F, (byte)0xF1, (byte)0x0B, (byte)0x07, (byte)0xF2, (byte)0xBE, (byte)0x5D, (byte)0x0D };
        // MsgCanData msgCanData = new MsgCanData(8);

        // if (canId == 0x02) {
        //     // double raw = ((short)((data[8] & 0xFF) | ((data[9] & 0xFF) << 8))) * 0.1;
        //     double raw = ((short)((canData[0] & 0xFF) | ((canData[1] & 0xFF) << 8))) * 0.1;
        //     CloudLogConsole.println("Found Steering: " + raw);
        // }

        MsgCanData msgCanData = new MsgCanData(dlc);

        msgCanData.canData.get(0).setAddress(canId);
        msgCanData.canData.get(0).setSrc((byte)0);
        msgCanData.canData.get(0).setBusTime((short)0);
        msgCanData.canData.get(0).getDat().asByteBuffer().put(canData);
        ph.publishBuffer("can", msgCanData.serialize(true));
    }

    @Override
    public void onRunError(Exception e) {
        CloudLogConsole.println("onRunError exception in ArduinoInstance: " + e);
    }

    public void startSendCanThread() {
        CloudLogConsole.println("startSendCanThread started");
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                sendCanThread();
            }
        }, "sendCanThread");
        thread.start();
    }

    int counter = 0;
    public void sendCanThread() {
        while (true) {
            // if (true) {
            //     double desiredAngleDeg = 10.0;

            //     byte[] sendcanBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat((float) desiredAngleDeg).put((byte)0x0D).put((byte)0x0A).array();

            //     sendSerial(sendcanBuffer);
            //     try{
            //         Thread.sleep(1000);
            //     } catch (Exception e) { }
            // }
            if (sh.updated("sendcan")) {
                Definitions.Event.Reader event = sh.recv("sendcan");
                ByteBuffer buffer = event.getSendcan().get(0).getDat().asByteBuffer();
                byte[] data = new byte[buffer.remaining()];
                buffer.get(data);

                int byte0 = data[0] & 0xFF;
                int byte1 = data[1] & 0xFF;
                int byte2 = data[2] & 0xFF;
                
                // Extract 18-bit raw value starting at bit 7 (Motorola format)
                // Bits 7-0 from byte0, bits 15-8 from byte1, bits 17-16 from byte2
                int rawValue = (byte0 << 10) | (byte1 << 2) | (byte2 >> 6);
                
                // Apply DBC scale (-0.01) and offset (1310) to get physical angle in degrees
                double desiredAngleDeg = (rawValue * -0.01) + 1310;

                // desiredAngleDeg = 120245.5;

                byte[] sendcanBuffer = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN).putFloat((float) desiredAngleDeg).put((byte)0x0D).put((byte)0x0A).array();
                // byte[] sendcanBuffer = new byte[] { (byte)0x40, (byte)0x60, (byte)0xEA, (byte)0x47, (byte)0x0D, (byte)0x0A };
                sendSerial(sendcanBuffer);

                // CloudLogConsole.println("sendcan: " + bytesToHex(sendcanBuffer));

                try {
                    Thread.sleep(30);
                    // Thread.sleep(100);
                } catch (Exception e) { }

                // counter++;
                // if (counter >= 30) {
                //     // CloudLogConsole.println("desiredDeg: " + desiredAngleDeg);
                //     // desiredAngleDeg = 120245.5;
                //     CloudLogConsole.println("sendcan: " + bytesToHex(sendcanBuffer) + ", DESIRED_ANGLE: " + String.format("%.2f", desiredAngleDeg) + "°");
                //     counter = 0;
                // }
            }
        }
    }

    public void sendSerial(byte[] data) {
        try {
            if (port == null) {
                // CloudLogConsole.println("ArduinoInstance in read-only mode, usbserialport not initialized.");
                return;
            }

            port.write(data, 0);
        } catch (Exception e) {
            CloudLogConsole.println("Exception in sendSerial: " + e);
        }

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
                CloudLogConsole.println("Exception in DummyPandaInstance start: " + e);
            }

        }
        
        public void run() {
            try {
                while (true) {
                    //TODO: fix frequency issue - doesn't account for function runtime or modulus (could skip messages)
                    long time = System.currentTimeMillis();

                    if (time % 500L == 0) {
                        // Runs at 2hz which is 500ms
                        ph.publishBuffer("pandaStates", msgPandaState.serialize(true));
                        // ph.publishBuffer("peripheralState", msgPeripheralState.serialize(true));
                    }

                    if (time % 100L == 0) {
                        // Runs at 10hz which is 100ms
                        ph.publishBuffer("gpsLocationExternal", msgGpsLocationExternal.serialize(true));
                        ph.publishBuffer("driverState", msgDriverState.serialize(true));
                        ph.publishBuffer("driverMonitoringState", msgDriverMonitoringState.serialize(true));
                    }

                    if (time % 10L == 0) {
                        // Runs at 100hz which is 10ms
                        msgAccelerometer.accelerometer.setTimestamp(System.currentTimeMillis());
                        msgGyroscope.gyroscope.setTimestamp(System.currentTimeMillis());

                        ph.publishBuffer("accelerometer", msgAccelerometer.serialize(true));
                        ph.publishBuffer("gyroscope", msgGyroscope.serialize(true));
                    }
                }
            } catch (Exception e) {
                CloudLogConsole.println("Exception in DummyPandaInstance run: " + e);
            }
        }

        public void initPandaState() {
            msgPandaState.pandaStates.get(0).setPandaType(Definitions.PandaState.PandaType.BLACK_PANDA);
            msgPandaState.pandaStates.get(0).setControlsAllowed(true);
            msgPandaState.pandaStates.get(0).setSafetyModel(ai.flow.definitions.CarDefinitions.CarParams.SafetyModel.NISSAN);
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
