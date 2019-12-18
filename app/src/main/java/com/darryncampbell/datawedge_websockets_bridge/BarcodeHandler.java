package com.darryncampbell.datawedge_websockets_bridge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.util.Log;

import android.content.Context;

import com.honeywell.aidc.*;

public class BarcodeHandler  implements BarcodeReader.BarcodeListener, BarcodeReader.TriggerListener {

    private static final String TAG = "Datawedge BC Activity";
    private static final String BROWSER_APP_NAME = "sita";

    private AidcManager manager;
    private static com.honeywell.aidc.BarcodeReader barcodeReader; //normally this should be wrapped as a singleton
    private static MySocketServer mServer;
    private Timer barcodeTimer = null;
    private Context inContext = null;
    private static boolean bIsClaimed=false;

    private boolean scannerDisable = false;

    private static BarcodeHandler barcodeHandler=null;

    public static BarcodeHandler getInstance(Context context){
        if(barcodeHandler==null){
            Log.d(TAG, "return new BarcodeHandler()");
            barcodeHandler=new BarcodeHandler(context);
        }else{
            Log.d(TAG, "return existing BarcodeHandler()");
        }
        return barcodeHandler;
    }
    //BarcodeHandler is another candidate for a singleton wrapper, to avoid getting multiple instances
    //@Override
    private BarcodeHandler (Context context) {
        Log.d(TAG, "In Constructor");
        checkMServer();
        barcodeTimer = new Timer();
        barcodeTimer.schedule(new MyTimerTask(), 200, 200);
        AidcManager.create(context, new AidcManager.CreatedCallback() {
            @Override
            public void onCreated(AidcManager aidcManager) {
                manager = aidcManager;
                try {
                    if(barcodeReader!=null)
                        barcodeReader = manager.createBarcodeReader();
                    else{
                        Log.d(TAG, "no manager.createBarcodeReader() as barcodeReader not NUL");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to create barcode reader" + e.toString(), e);
                }
                setupBarcodeReader();
            }
        });
    }

    private void setupBarcodeReader() {
        if (barcodeReader != null) {
            barcodeReaderClaim();
/*
            try {
                barcodeReader.claim();
            } catch (ScannerUnavailableException e) {
                e.printStackTrace();
                Log.e(TAG, "Scanner unavailable" + e.toString(), e);
            }

*/
            // set the trigger mode to client control
            try {
                barcodeReader.setProperty(BarcodeReader.PROPERTY_TRIGGER_CONTROL_MODE,
                        BarcodeReader.TRIGGER_CONTROL_MODE_CLIENT_CONTROL);
            } catch (UnsupportedPropertyException e) {
                Log.e(TAG, "Failed to apply properties" + e.toString(), e);
            }
            /*
            // register trigger state change listener
            barcodeReader.addTriggerListener(this);
            */
            Map<String, Object> properties = new HashMap<String, Object>();
            // Set Symbologies On/Off
            properties.put(BarcodeReader.PROPERTY_CODE_128_ENABLED, true);
            properties.put(BarcodeReader.PROPERTY_GS1_128_ENABLED, true);
            properties.put(BarcodeReader.PROPERTY_QR_CODE_ENABLED, true);
            properties.put(BarcodeReader.PROPERTY_CODE_39_ENABLED, true);
            properties.put(BarcodeReader.PROPERTY_DATAMATRIX_ENABLED, true);
            properties.put(BarcodeReader.PROPERTY_UPC_A_ENABLE, true);
            properties.put(BarcodeReader.PROPERTY_EAN_13_ENABLED, false);
            properties.put(BarcodeReader.PROPERTY_AZTEC_ENABLED, false);
            properties.put(BarcodeReader.PROPERTY_CODABAR_ENABLED, false);
            properties.put(BarcodeReader.PROPERTY_INTERLEAVED_25_ENABLED, true);
            properties.put(BarcodeReader.PROPERTY_PDF_417_ENABLED, false);
            // Set Max Code 39 barcode length
            properties.put(BarcodeReader.PROPERTY_CODE_39_MAXIMUM_LENGTH, 17);
            // Turn on center decoding
            properties.put(BarcodeReader.PROPERTY_CENTER_DECODE, true);
            // Disable bad read response, handle in onFailureEvent
            properties.put(BarcodeReader.PROPERTY_NOTIFICATION_BAD_READ_ENABLED, false);
            // Apply the settings
            barcodeReader.setProperties(properties);
            Log.d(TAG, "set Barcode Properties");
        }

    }

    private void checkMServer() {
        if (mServer == null)
        {
            Log.d(TAG, "Starting WebSocket Server");
            //  Start the WebSocket Server
            mServer = WebSocketIntentService.getMySocketServer();
            Log.d(TAG, "WebSocket Server started");
        }
        else
        {
            Log.v(TAG, "Did not start server as it is already started");
        }

        /*
        if (barcodeReader != null) {
            try {
                barcodeReader.claim();
            } catch (ScannerUnavailableException e) {
                e.printStackTrace();
                Log.e(TAG, "Scanner unavailable" + e.toString(), e);
            }
        }
        */
    }

    @Override
    public void onBarcodeEvent(final BarcodeReadEvent event) {
        if (mServer==null)
        {
            checkMServer();
        }
        if (mServer!=null)
        {
            Log.d(TAG, "on Barcode Event - " + event.getBarcodeData());
            mServer.sendScanToBrowser(event.getBarcodeData());
        }
        else
        {
            Log.d(TAG, "on Barcode Event but mServer is null - " + event.getBarcodeData());
        }
    }

    public void disableScanner() {
        scannerDisable = true;
        barcodeReaderRelease();
        /*
        if (barcodeReader != null) {
                barcodeReader.release();
                Log.d(TAG, "Scanner released.");
        }
        */
    }

    public void enableScanner() {
        scannerDisable = false;
        // Make sure we claim the barcode reader on Scanner enable in case somebody else
        // claimed it on us in the background...
        checkMServer();
        /*
        if (barcodeReader != null) {
            try {
                barcodeReader.claim();
                Log.d(TAG, "Scanner claimed.");
            } catch (ScannerUnavailableException e) {
                e.printStackTrace();
                Log.e(TAG, "Scanner.claim(): " + e.toString(), e);
            }
        }
        */
        barcodeReaderClaim();
    }

    // When using Automatic Trigger control do not need to implement the
    // onTriggerEvent function
    @Override
    public void onTriggerEvent(TriggerStateChangeEvent event) {
        try {
            if (scannerDisable)
            {
                if (event.getState())
                {
                    Log.d(TAG, "on Trigger Event - Scanner disabled - Trigger engagged");
                }
                else
                {
                    Log.d(TAG, "on Trigger Event - Scanner disabled - Trigger disengagged");
                }
            }
            else
            {
                if (event.getState())
                {
                    Log.d(TAG, "on Trigger Event - Scanner ensabled - Trigger engagged");
                }
                else
                {
                    Log.d(TAG, "on Trigger Event - Scanner ensabled - Trigger disegagged");
                }
            }
            // only handle trigger presses
            // turn on/off aimer, illumination and decoding

            if (!scannerDisable || !event.getState())
            {
                barcodeReader.aim(event.getState());
                barcodeReader.light(event.getState());
                barcodeReader.decode(event.getState());
            }

        } catch (ScannerNotClaimedException e) {
            e.printStackTrace();
            Log.e(TAG, "Scanner is not claimed" + e.toString(), e);
        } catch (ScannerUnavailableException e) {
            e.printStackTrace();
            Log.e(TAG, "Scanner unavailable" + e.toString(), e);
        }
    }

    @Override
    public void onFailureEvent(BarcodeFailureEvent arg0) {
    }

    public boolean isBagManInForeground() {
        final ActivityManager activityManager = (ActivityManager) inContext.getSystemService(Context.ACTIVITY_SERVICE);
        try {
            final List<ActivityManager.RunningTaskInfo> tasks = activityManager.getRunningTasks(1);
            if (!tasks.isEmpty()) {
                final ComponentName topActivity = tasks.get(0).topActivity;
                if (topActivity.getPackageName().contains(BROWSER_APP_NAME)) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Get tasks list error: " + e.toString(), e);
        }
        return false;
    }

    private class MyTimerTask extends TimerTask {

        public MyTimerTask () {
            super();
        }

        @Override
        public void run() {
            // NOTE: If we're forcing the scanner to work, we must set the count to 1.
            if (barcodeReader != null) {
                try {
                    if (isBagManInForeground() && !scannerDisable) {
                        // if BagManager is in Foreground and scanner is enabled.  Make sure
                        // barcodeReader is claimed.
                        barcodeReaderClaim();
                        /*
                        if (!isBarcodeReaderClaimed) {
                            Log.d(TAG, "Timer thread: browser in foreground, enabled, and not claimed.  Calling claim code.");
                            barcodeReader.claim();
                            isBarcodeReaderClaimed = true;
                        }
                        */
                    } else {
                        // if BagManager is in the Background or scanner is disabled,
                        // release the barcodeReader if it is claimed.
                        barcodeReaderRelease();
                        /*
                        if (isBarcodeReaderClaimed) {
                            Log.d(TAG, "Timer thread: either browser is not in foreground or disabled, and it is claimed.  Calling release code.");
                            barcodeReader.release();
                            isBarcodeReaderClaimed = false;
                        }
                        */
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e(TAG, "MyTimerTask exception: " + e.getMessage());
                }
            }
        }
    }

    /// do it like singleton use
    private void barcodeReaderClaim(){
        if(bIsClaimed) {
            Log.d(TAG, "barcodeReaderClaim(): already claimed");
            return;
        }
        if(barcodeReader==null){
            Log.d(TAG, "barcodeReaderClaim(): barcodeReader is NUL");
            bIsClaimed=false;
            return;
        }
        try{
            Log.d(TAG, "barcodeReaderClaim(): about to claim()");
            this.barcodeReader.claim();
            // register bar code event listener
            barcodeReader.addBarcodeListener(this);
            // register trigger state change listener
            barcodeReader.addTriggerListener(this);
            Log.d(TAG, "barcodeReaderClaim(): claim() success");
            bIsClaimed=true;
        }catch(Exception ex)
        {
            Log.d(TAG, "barcodeReaderClaim(): claim() Exception: " +ex.getMessage());
        }
    }
    private void barcodeReaderRelease(){
        if(!bIsClaimed){
            Log.d(TAG, "barcodeReaderRelease(): not claimed()");
            return;
        }
        if(barcodeReader==null){
            Log.d(TAG, "barcodeReaderRelease(): barcodeReader is NUL");
            bIsClaimed=false;
            return;
        }
        try{
            Log.d(TAG, "barcodeReaderRelease(): about to release()");
            barcodeReader.release();
            Log.d(TAG, "barcodeReaderRelease(): release() success");
            bIsClaimed=false;
        }catch(Exception ex){
            Log.d(TAG, "barcodeReaderRelease(): release() Exception: " + ex.getMessage());
        }
    }

    public void close(){
        Log.d(TAG, "barcodeReader close(): about to close()");
        if(barcodeTimer!=null)
            barcodeTimer.cancel();
        if(barcodeReader!=null){
            try{
                enableTrigger();
                barcodeReaderRelease();
                barcodeReader.removeBarcodeListener(this);
                barcodeReader.removeTriggerListener(this);
                barcodeReader=null;
            }catch(Exception ex){
                Log.d(TAG, "barcodeReader close(): close() Exception: " + ex.getMessage());
            }
        }else{
            Log.d(TAG, "barcodeReader close(): barcodeReader was NUL");
        }
    }

    public void disableTrigger(){
        if(barcodeReader!=null){
            Map<String, Object> properties = new HashMap<String, Object>();
            // Set Symbologies On/Off
            properties.put("TRIG_ENABLE", false);
            barcodeReader.setProperties(properties);
        }
    }

    public void enableTrigger(){
        if(barcodeReader!=null){
            Map<String, Object> properties = new HashMap<String, Object>();
            // Set Symbologies On/Off
            properties.put("TRIG_ENABLE", true);
            barcodeReader.setProperties(properties);
        }
    }
}
