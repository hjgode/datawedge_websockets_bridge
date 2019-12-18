package com.darryncampbell.datawedge_websockets_bridge;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.util.Log;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;

import java.net.InetSocketAddress;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.java_websocket.server.WebSocketServer;
import org.json.JSONException;
import org.json.JSONObject;


public class MySocketServer extends WebSocketServer
{

    private WebSocket mSocket = null;
    private static final String TAG = "Datawedge So Srv";
    private List<String> config = null;
    private Context context;
    // Play media (doesn't work)
    private static final String media_play_action = "media.play";

    // Allows enable/dsiable of scanner
    private static final String scanner_action = "SCANNER";

    // RFID command
    private static final String rfid_scanned  = "onRFID";

    // External Scanner commands from BG App
    private static final String ext_scan_avail_cmd = "ExtScannerAvail";
    private static final String ext_scan_list_cmd = "ScannerList";
    private static final String ext_scan_error = "ExtScannerError";
    private static final String ext_scan_connect_delay = "ExtScannerConnectDelay";

    // External Scanner commands from browser
    private static final String ext_scanner_action = "EXT_SCANNER";
    private static final String ext_enquire_key = "ENQUIRE";
    private static final String ext_connect_name_key = "CONNECT_FROM_NAME";
    private static final String ext_connect_barcode_key = "CONNECT_FROM_BARCODE";
    private static final String ext_disconnect_key = "DISCONNECT";

    // MyTimerTypes
    private static final String timer_enable_int_scanner = "EnableIntScanner";
    private static final String timer_delay_RFID_scan = "DelayRFIDScan";
    private static final String timer_delay_btDisc_reset = "DelaybtDiscReset";


    private String sita_conf_path = "/mnt/sdcard/";
    private String exceptionString = "";
    private int scannerDisableCount = 0;

    private int intMaxDisableScanTimeout = 5000;
    private int intRFIDScanDelayTimeout = 1000;

    public int getIntMaxScannerSuspendLevel() {
        return intMaxScannerSuspendLevel;
    }

    public void setIntMaxScannerSuspendLevel(int intMaxScannerSuspendLevel) {
        this.intMaxScannerSuspendLevel = intMaxScannerSuspendLevel;
    }

    private int intMaxScannerSuspendLevel = 2;
    private Timer scanTimer = null;
    private Timer extScanTimerRFIDDelay = null;
    private Timer extScanTimerDelayRestart = null;

    private static BarcodeHandler clientBarcode;

    private static BTDiscoveryFragment btDisc;

    private String extScannerName = "Disconnected";

    private String lastExtScannerCmd = "";

    public void setIntMaxDisableScanTimeout(int intMaxDisableScanTimeout) {
        this.intMaxDisableScanTimeout = intMaxDisableScanTimeout;
    }

    public void setIntRFIDScanDelayTimeout(int intRFIDScanDelayTimeout) {
        this.intRFIDScanDelayTimeout = intRFIDScanDelayTimeout;
    }

    public List<String> getConfig() {
        return config;
    }

    public void setConfig(List<String> config) {
        this.config = config;
    }

    public MySocketServer(InetSocketAddress address, Context context) {
        super(address);
        this.context = context;

        if (btDisc==null) {
            btDisc = MainActivity.getBTDiscovery();
            if (btDisc!=null) {
                extScannerName = btDisc.getCurrDevice();
            }
        }
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {

        // NOTE:  Don't know why, but, Toast message below was fine on the Zebra TC56, but, causes
        //        an exception in the Honeywell???  So, we cannot use this for debugging.
        //Toast.makeText(MySocketServer.this.context.getApplicationContext(), "onOpen", Toast.LENGTH_SHORT).show();
        WebSocket oldConn = mSocket;
        mSocket = conn;
        Log.d(TAG, "onOpen - new WSebSocket is '" + conn.toString() + "'");
        if (clientBarcode==null) {
            clientBarcode = BarcodeHandler.getInstance(context.getApplicationContext());// new BarcodeHandler(context.getApplicationContext());
            Log.d(TAG, "onOpen - getting barcodeHandler instance");
        }
        if (oldConn!=null && oldConn.isOpen())
        {
            if (conn == oldConn)
            {
                Log.d(TAG, "onOpen - same socket is opened twice??  Skipping cleanup.'");
            }
            else
            {
                Log.d(TAG, "onOpen - old WSebSocket is '" + oldConn.toString() + "'");
                oldConn.close();
            }
        }
        Log.i(TAG, "onOpen from " + mSocket.getRemoteSocketAddress().getAddress().toString());
        enableScanner();
        String message = "ConfPath (" + sita_conf_path + ")";
        mSocket.send(message);
        if (btDisc==null)
        {
            btDisc = MainActivity.getBTDiscovery();
        }
        if (btDisc==null || config==null)
        {
            message = "StartApp (" + "com.darryncampbell.datawedge_websockets_bridge" + ")";
            mSocket.send(message);
        } else {
            extScannerName = btDisc.getCurrDevice();
        }
        sendExtConnectedScanner(extScannerName);
        if (btDisc!=null)
        {
            btDisc.sendDeviceList();
        }
        Log.i(TAG, message);
        /*
        message = "Config {" + config.toString() + "}";
        mSocket.send(message);
        Log.i(TAG, message);
        */
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Log.i(TAG, "onClose");
        Log.d(TAG, "onClose - saved WebSocket is '" + mSocket.toString() + "' param WebSocket is '" + conn.toString() + "'");
        if ( conn == mSocket )
        {
            Log.d(TAG, "onClose - connected Socket failed.");
            //disableScanner();
            clientBarcode.close(); //cleanUp
            clientBarcode = null;
            mSocket = null;
        }
        else
        {
            Log.d(TAG, "onClose - cleaning up old Socket.");
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        Log.i(TAG, "message: " + message);
        Log.d(TAG, "onMessage - saved WebSocket is '" + mSocket.toString() + "' param WebSocket is '" + conn.toString() + "'");
        try {
            JSONObject jsonObject = new JSONObject(message);
            String action = jsonObject.getString("action");
            String key = jsonObject.getString("extra_key");
            String value = jsonObject.getString("extra_value");
            if ( action.equals(media_play_action ) )
            {
                Log.d(TAG, "Playing file " + key);
                playSound ( key );
            } else if (action.equals(scanner_action)) {
                Log.d(TAG, "SCANNER: " + key);
                scannerAction(key);
            } else if (action.equals(ext_scanner_action)) {
                Log.d(TAG, "EXT SCANNER: " + key);
                extScannerAction(key, value);
            } else {
                Log.d(TAG, "Rcv from Socket: Action: " + action + "Key: " + key + "Value: " + value);
            }
        } catch (JSONException e) {
            Log.w(TAG, "Unable to parse JSON message from page");
            e.printStackTrace();
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        Log.d(TAG, "onError - saved WebSocket is '" + mSocket.toString() + "' param WebSocket is '" + conn.toString() + "'");
        Log.e(TAG, "error: " + ex.getMessage());
    }

    public void sendScanToBrowser(Intent intent) {
        if (mSocket != null)
        {
            Log.d(TAG, "on sendScanToBrowser(Intent) - saved WebSocket is '" + mSocket.toString() + "'");
            //  A barcode has been scanned
            // Honeywell Scanner intent
            int version = intent.getIntExtra("version", 0);
            if (version >= 1)
            {
                String aimId = intent.getStringExtra("aimId");
                String charset = intent.getStringExtra("charset");
                String codeId = intent.getStringExtra("codeId");
                String data = intent.getStringExtra("data");
                byte[] dataBytes = intent.getByteArrayExtra("dataBytes");
                String dataBytesStr="";
                if(dataBytes!=null && dataBytes.length>0)
                    dataBytesStr = bytesToHexString(dataBytes);
                String timestamp = intent.getStringExtra("timestamp");
                String text = String.format(
                        "Data:%s\n" +
                                "Charset:%s\n" +
                                "Bytes:%s\n" +
                                "AimId:%s\n" +
                                "CodeId:%s\n" +
                                "Timestamp:%s\n",
                        data, charset, dataBytesStr, aimId, codeId, timestamp);
                String message = "Barcode (" + data + ")";
//            String message = "onRFID (1," + data + ")";
                Log.d(TAG, "sendScantoBrowser (Intent): " + message);
                mSocket.send(message);
            }
        }
        else {
            //  If the client has not yet connected to us but we have received a scan then buffer it
            //  and send it when we next connect
            Log.w(TAG, "error - socket is null, buffering scan");
        }
    }
    public void sendScanToBrowser(String data) {
        if (mSocket != null)
        {
            Log.d(TAG, "on sendScanToBrowser(String) - saved WebSocket is '" + mSocket.toString() + "'");
            Log.d(TAG, "sendScantoBrowser: " + data);
            //  A barcode has been scanned
            String message = "Barcode (" + data + ")";
//            String message = "onRFID (1," + data + ")";
//            String message = "onRFID (3,0000000000|" + data + "|0123456789)";
            mSocket.send(message);
        }
        else
        {
            Log.d(TAG, "sendScantoBrowser but no socket to send to: " + data);
        }
    }

    public boolean isConnected() {
        if (mSocket!=null &&
                mSocket.isOpen()) {
            return true;
        }
        return false;
    }

    private String bytesToHexString(byte[] arr) {
        String s = "[]";
        if (arr != null) {
            s = "[";
            for (int i = 0; i < arr.length; i++) {
                s += "0x" + Integer.toHexString(arr[i]) + ", ";
            }
            s = s.substring(0, s.length() - 2) + "]";
        }
        return s;
    }

    MediaPlayer mpSound = null;
    File file = null;


    public void playSound( String path )
    {
        try {
            FileDescriptor fd = null;

            file = new File ( path );
            fd = new FileInputStream( file ).getFD();

            if ( mpSound != null ) {
                mpSound.release();
                mpSound = null;
            }
            if ( mpSound == null ) {
                mpSound = new MediaPlayer();
                mpSound.setDataSource( fd );
            }
            mpSound.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mpSound.prepare();
            mpSound.start();
        }
        catch (Exception e)
        {
            Log.e(TAG, "MediaPlayer failed " + e.toString(), e);
        }
    }

    /*************************************************/
    /* Scanner private functions */

    /*************************************************/

    private void scannerAction(String key) {
        try {
            if (key.equals("SUSPEND")) {
                disableScanner();
            } else if (key.equals("RESUME")) {
                enableScanner();
            }
        } catch (Exception e) {
            exceptionString = exceptionString + e.toString();
            Log.e(TAG, "Scanner Action failed " + e.toString(), e);
        }
    }

    private void disableScanner() {
        if (intMaxDisableScanTimeout > 0) {
            if (scannerDisableCount == 0) {
                try {
                    sendDisableScannerIntent();
                    Log.i(TAG, "Scanner disabled.");
                } catch (Exception e) {
                    exceptionString = exceptionString + e.toString();
                    Log.e(TAG, "Scanner disable failed " + e.toString(), e);
                }
            }
            if (intMaxScannerSuspendLevel > scannerDisableCount) {
                scannerDisableCount++;
            }
            if (scanTimer != null) {
                scanTimer.cancel();
            }
            scanTimer = new Timer();
            scanTimer.schedule(new MyTimerTask(timer_enable_int_scanner), intMaxDisableScanTimeout);
        }
    }

    private void enableScanner() {
        scannerDisableCount--;
        if (scannerDisableCount <= 0) {
            scannerDisableCount = 0;
            if (scanTimer != null) {
                scanTimer.cancel();
                scanTimer = null;
            }
            if (mSocket != null) {
                Log.d(TAG, "on enableScanner - saved WebSocket is '" + mSocket.toString() + "'");
                try {
                    sendEnableScannerIntent();
                    Log.i(TAG, "Scanner enabled.");
                } catch (Exception e) {
                    exceptionString = exceptionString + e.toString();
                    Log.e(TAG, "Scanner enable failed " + e.toString(), e);
                }
            }
        }
    }

    private void sendDisableScannerIntent() {
        clientBarcode.disableScanner();
    }

    private void sendEnableScannerIntent() {
        clientBarcode.enableScanner();
    }


    /*************************************************/
    /* Scanner private functions */

    /*************************************************/

    public void setBTDiscoveryFragment(BTDiscoveryFragment btDisc) {
        this.btDisc = btDisc;
        extScannerName = btDisc.getCurrDevice();
    }

    private void extScannerAction(String key, String data) {
        if (mSocket != null) {
            Log.d(TAG, "on extScannerAction - saved WebSocket is '" + mSocket.toString() + "'");
        }
        try {
            if (btDisc==null) {
                Log.d(TAG, "btDisc is null, trying to get from MainActivity");
                btDisc = MainActivity.getBTDiscovery();
                if (btDisc!=null) {
                    extScannerName = btDisc.getCurrDevice();
                }
            }
            if (btDisc==null) {
                Log.d(TAG, "btDisc is still null, trying to have browser kickstart the app");
                mSocket.send( "StartApp (com.darryncampbell.datawedge_websockets_bridge)");
            }
            else
            {
                if (ext_enquire_key.equals(key)) {
                    // sendExtScannerList(extScannerName);
                    Log.d(TAG, "processing ext equire");
                    btDisc.enquireBTDeviceList();
                } else if (ext_connect_name_key.equals(key)) {
                    if (ext_connect_name_key.equals(lastExtScannerCmd)) {
                        // 2 connect request in a row, so we restart the BTDiscovery object.
                        long lapseTime = MainActivity.restartBTDiscovery();
                        if (lapseTime == 0) {
                            Log.d(TAG, "2 connect request back-to-back, restarting BTDiscoveryObject.");
                            mSocket.send("StartApp (com.darryncampbell.datawedge_websockets_bridge)");
                        }
                        else {
                            Long lDelay = new Long( 10*1000 - lapseTime);
                            Log.d(TAG, "2 connect request back-to-back, restarting BTDiscoveryObject in " + lDelay.toString() + " sec.");
                            if (extScanTimerDelayRestart != null) {
                                extScanTimerDelayRestart.cancel();
                            }
                            extScanTimerDelayRestart = new Timer();
                            extScanTimerDelayRestart.schedule(new MyTimerTask(timer_delay_btDisc_reset), lDelay.intValue());
                        }
                        btDisc = null;
                    }
                    else {
                        //extScannerName = data;
                        //sendExtConnectedScanner(data);
                        sendExtScanneConnectDelay("0");
                        Log.d(TAG, "processing ext connect by name");
                        btDisc.connectBTDevice(data);
                    }
                } else if (ext_connect_barcode_key.equals(key)) {
                    extScannerName = data;
                    Log.d(TAG, "processing ext connect by barcode");
                    sendExtConnectedScanner(data);
                } else if (ext_disconnect_key.equals(key)) {
                    if (extScannerName.length()<1)
                    {
                        Log.d(TAG, "processing ext disconnect but no device");
                        sendExtScannerError("No Device Connected");
                    }
                    else
                    {
                        //extScannerName = "";
                        //sendExtConnectedScanner(data);
                        Log.d(TAG, "processing ext disconnect");
                        btDisc.disconnectBTDevice();
                    }
                }
                sendExtScannerError(" ");
            }
        } catch (Exception e) {
            exceptionString = exceptionString + e.toString();
            Log.e(TAG, "Extern Scanner Action failed " + e.toString(), e);
            sendExtScannerError("Action failed.");
        }
        lastExtScannerCmd = key;
    }

    public void sendExtConnectedScanner(String data) {
        extScannerName = data;
        if (mSocket != null)
        {
            Log.d(TAG, "sendExtConnectedScanner: " + data);
            //  A barcode has been scanned
//            String message = ext_scan_avail_cmd + " (IP3012345678901)";
            String message = ext_scan_avail_cmd + " (" + data + ")";
            Log.d(TAG, "on sendExtConnectedScanner - saved WebSocket is '" + mSocket.toString() + "'");
            mSocket.send(message);
        }
        else
        {
            Log.d(TAG, "sendExtConnectedScanner but no socket to send to: " + data);
        }
    }

    public void sendExtScannerList(String data) {
        if (mSocket != null)
        {
            Log.d(TAG, "sendExtScannerList: " + data);
            //  A barcode has been scanned
            //String message = ext_scan_list_cmd + " (IP3012345678901|IP30333334444|IP3011111000001)";
            String message = ext_scan_list_cmd + " ("+ data + ")";
            Log.d(TAG, "on sendExtScannerList - saved WebSocket is '" + mSocket.toString() + "'");
            mSocket.send(message);
        }
        else
        {
            Log.d(TAG, "sendExtScannerList but no socket to send to: " + data);
        }
    }

    public void sendExtScannerError(String data) {
        if (mSocket != null)
        {
            Log.d(TAG, "sendExtScannerError: " + data);
            //  A barcode has been scanned
            String message = ext_scan_error + " (" + data + ")";
            Log.d(TAG, "on sendExtScannerError - saved WebSocket is '" + mSocket.toString() + "'");
            mSocket.send(message);
        }
        else
        {
            Log.d(TAG, "sendExtScannerError but no socket to send to: " + data);
        }
    }

    public  void sendRFIDTag(String data) {
        if (mSocket != null)
        {
            Log.d(TAG, "sendRFIDTag: " + data);
            //  A barcode has been scanned
            String message = rfid_scanned + " (" + data + ")";

            //TODO: Added delay before sending tags?
            if (extScanTimerRFIDDelay != null) {
                extScanTimerRFIDDelay.cancel();
            }
            extScanTimerRFIDDelay = new Timer();
            extScanTimerRFIDDelay.schedule(new MyTimerTask(timer_delay_RFID_scan, message), intRFIDScanDelayTimeout);
        }
        else
        {
            Log.d(TAG, "sendRFIDTag but no socket to send to: " + data);
        }
    }

    public void sendExtScanneConnectDelay(String data) {
        if (mSocket != null)
        {
            Log.d(TAG, "sendExtScanneConnectDelay: " + data);
            //  A barcode has been scanned
            String message = ext_scan_connect_delay + " (" + data + ")";
            Log.d(TAG, "on sendExtScanneConnectDelay - saved WebSocket is '" + mSocket.toString() + "'");
            mSocket.send(message);
        }
        else
        {
            Log.d(TAG, "sendExtScanneConnectDelay but no socket to send to: " + data);
        }
    }

    /*************************************************/
    /* Timer Functions */

    /*************************************************/

    private class MyTimerTask extends TimerTask {

        private String timerType=null;
        private String data=null;

        public MyTimerTask (String timerType) {
            super();
            this.timerType = timerType;
        }

        public MyTimerTask (String timerType, String data) {
            super();
            this.timerType = timerType;
            this.data = data;
        }

        @Override
        public void run() {
            // NOTE: If we're forcing the scanner to work, we must set the count to 1.

            if (timer_enable_int_scanner.compareTo(timerType)==0) {
                scannerDisableCount = 1;
                enableScanner();
            } else if (timer_delay_RFID_scan.compareTo(timerType)==0) {
                mSocket.send(data);
            } else if (timer_delay_btDisc_reset.compareTo(timerType)==0) {
                MainActivity.restartBTDiscovery();
                Log.d(TAG, "2 connect request back-to-back, delayed restart of BTDiscoveryObject.");
                Log.d(TAG, "in MyTimerTask - saved WebSocket is '" + mSocket.toString() + "'");
                mSocket.send("StartApp (com.darryncampbell.datawedge_websockets_bridge)");
            }
        }
    }

}