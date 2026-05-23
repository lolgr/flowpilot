package ai.flow.android.sensor;

import android.content.Context;

import ai.flow.flowy.PythonRunner;
import ai.flow.sensor.SensorInterface;
import ai.flow.android.sensor.ArduinoInstance;
import ai.flow.flowy.ServicePandad;

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

import ai.flow.definitions.Definitions;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import messaging.ZMQPubHandler;

import ai.flow.app.CloudLogConsole;

public class USBManager implements SensorInterface {
    private Context ctx;
    private Activity activity;

    private static final String TAG = "FlowPilot";
    private Thread applicationThread = null;

	private static final String ACTION_USB_PERMISSION = "ai.flow.flowy.USB_PERMISSION";

    public USBManager(Context ctx, Activity activity) {
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
		CloudLogConsole.println("Number of USB devices found: "+deviceList.size());
        final int deviceCount = deviceList.size();

        for (UsbDevice usbDevice : deviceList.values())
        {
            maybeRequestUSBPermission(usbDevice, ctx);
        }

        // ArduinoInstance arduinoinstance = new ArduinoInstance(ctx, activity);
    }

    private BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public synchronized void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            CloudLogConsole.println("RECEIVING INTENT: " + action);

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
                CloudLogConsole.println("Permission denied for device " + device);
                return;
            }

            if(device == null)
                return;
            
            UsbManager usbManager = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);
            UsbDeviceConnection usbDeviceConnection = usbManager.openDevice(device);

            if (usbDeviceConnection == null) {
                CloudLogConsole.println("Failed to open device");
                return;
            }

            try {
                // Arduino found
                if (device.getVendorId() == 0x1a86 || device.getVendorId() == 0x2341) {
                    List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
                    UsbSerialDriver driver = availableDrivers.get(0);

                    UsbSerialPort port = driver.getPorts().get(0);
                    port.open(usbDeviceConnection);
                    port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

                    ArduinoInstance arduinoinstance = new ArduinoInstance(ctx, activity, port);
                    byte[] initMsg = new byte[] { (byte)0x0F, (byte)0xF1, (byte)0x0B, (byte)0x07, (byte)0xF2, (byte)0xBE, (byte)0x0D, (byte)0x05 };
                    arduinoinstance.sendSerial(initMsg);

                    SerialInputOutputManager usbIoManager = new SerialInputOutputManager(port, arduinoinstance);
                    usbIoManager.start();
                }
                // Panda found
                else {
                    CloudLogConsole.println("Permission granted for serial " + usbDeviceConnection.getSerial());

                    ServicePandad.start(ctx, usbDeviceConnection.getFileDescriptor()); // TODO: Check if panda service starts correctly
                }
            } catch (Exception e) {
                CloudLogConsole.println("Exception in onReceive usbReceiver: " + e);
            }
        }
    };

    private void maybeRequestUSBPermission(UsbDevice device, Context context) {
        if (device == null) {
            CloudLogConsole.println("maybeRequestUSBPermission got a null device");
            return;
        }

        // Using arduino vendor IDs: 0x1a86, 0x2341 and product IDs: 0x7523, 0x43
        // This may be different if the arudino is not an uno or has a different usb chip.
        // Checks the vendor and product IDs for arduino and panda
        if ((device.getVendorId() == 0xbbaa || device.getVendorId() == 0x3801 || device.getVendorId() == 0x1a86 || device.getVendorId() == 0x2341) &&
            (device.getProductId() == 0xddcc || device.getProductId() == 0xddee || device.getProductId() == 0x7523 || device.getProductId() == 0x43)) {
            CloudLogConsole.println("Found a recognized USB device (VID: " + device.getVendorId() +  ", PID: " + device.getProductId() + ")");

            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_MUTABLE);
            ((UsbManager) context.getSystemService(Context.USB_SERVICE)).requestPermission(device, pendingIntent);
        } else {
            CloudLogConsole.println("Found a USB device that's not recognized (VID: " + device.getVendorId() + ", PID: " + device.getProductId() + ")");
        }
    }
}
