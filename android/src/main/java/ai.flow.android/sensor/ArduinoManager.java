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

// TODO: Remove this and create generic manager for Panda and Arduino 
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
        System.loadLibrary("arduinod");

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

        activity.runOnUiThread(new Runnable() {
            public void run() {
                // Toast.makeText(ctx, "Testing toast message", Toast.LENGTH_LONG).show();
                new AlertDialog.Builder(activity)
                .setTitle("Title")
                .setMessage("Number of devices: " + deviceList.size())
                .setPositiveButton("OK", (dialog, which) -> { /* handle */ })
                .show();
            }
        });

        for (UsbDevice usbDevice : deviceList.values())
        {
            maybeRequestUSBPermission(usbDevice, ctx);
        }
        Log.i("FlowPilot", "testing");
    }

    private BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            System.out.println("RECEIVING INTENT: " + action);

            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                synchronized (this) {
                    UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    maybeRequestUSBPermission(usbDevice, context);
                }
            } else if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null) {
                            UsbManager usbManager = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);
                            UsbDeviceConnection usbDeviceConnection = usbManager.openDevice(device);
                            Log.i(TAG, "Permission granted for serial "+usbDeviceConnection.getSerial());
                            ArduinoInstance arduinoInstance = new ArduinoInstance(usbDeviceConnection.getFileDescriptor());
                            new Thread(arduinoInstance).start();
                        }
                    }
                    else {
                        Log.i(TAG, "Permission denied for device " + device);
                    }
                }
            }
        }
    };

    private void maybeRequestUSBPermission(UsbDevice device, Context context) {
        if (device == null) {
            Log.w(TAG, "maybeRequestUSBPermission got a null device");
            return;
        }

        // Check for both panda vendor IDs and product IDs
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

class ArduinoInstance implements Runnable {
    private int fd;
    public ArduinoInstance(int fd) {
        this.fd = fd;
    }
    @Override
    public void run(){
        ArduinoManager.nativeStart(this.fd);
    }
}