package com.darryncampbell.datawedge_websockets_bridge;

import android.app.IntentService;
import android.content.Intent;

public class WebSocketIntentService extends IntentService {
    static MySocketServer mySocketServer=null;

    public WebSocketIntentService (String s){
        super(s);

    }
    @Override
    protected void onHandleIntent(Intent intent){


    }

    public static MySocketServer getMySocketServer(){
        return mySocketServer;
    }
}
