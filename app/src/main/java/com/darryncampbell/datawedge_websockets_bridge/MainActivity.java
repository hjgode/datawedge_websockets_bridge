package com.darryncampbell.datawedge_websockets_bridge;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Enumeration;

public class MainActivity extends AppCompatActivity {

    static BTDiscoveryFragment btDiscoveryFragment=null;
    MySocketServer mySocketServer;
    private WebSocketClient mWebSocketClient;
    final static String TAG="myWebsocketClientTest";
    Context mContext=this;
    String localIP="127.0.0.1";
    int localPort = 12345;
    WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "###### Starting ####");

//        btDiscoveryFragment=new BTDiscoveryFragment();
//        // Begin the transaction
//        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
//        // Replace the contents of the container with the new fragment
//        ft.replace(R.id.your_placeholder, btDiscoveryFragment);
//        // or ft.add(R.id.your_placeholder, new FooFragment());
//        // Complete the changes added above
//        ft.commit();

        //localIP = getLocalIpAddress();
        try {
            InetSocketAddress inetSocketAddress = new InetSocketAddress(localIP, localPort);
            mySocketServer = MySocketServer.getMySocketServer(inetSocketAddress, mContext);
            mySocketServer.start();
        }catch(Exception ex){
            Log.e(TAG, ex.getMessage());
        }
        Button btn1=findViewById(R.id.button);
        btn1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mySocketServer!=null) {
                    if (!mySocketServer.isConnected())
                        ConnectToWebSocket(localIP);
                }
            }
        });

        setupWebview();

    }


    void setupWebview(){
        webView=findViewById(R.id.webView);
        WebSettings settings=webView.getSettings();
        settings.setDomStorageEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAppCacheEnabled(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }

        // Extras tried for Android 9.0, can be removed if want.
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(true);
        settings.setBlockNetworkImage(false);
        webView.loadUrl("file:///android_asset/index.html");
    }


    public static BTDiscoveryFragment getBTDiscovery (){
        return btDiscoveryFragment;
    }
    public static Long restartBTDiscovery(){
        return 0L;
    }


    public static String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        Log.i(TAG, "***** IP="+ inetAddress.getHostAddress());
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    private void ConnectToWebSocket(String hostIP) {
        URI uri;
        try {
            uri = new URI("ws://" + hostIP + ":8015");
        } catch (URISyntaxException e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
            return;
        }

        mWebSocketClient = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake serverHandshake) {
                Log.i(TAG, "onOpen ");
                mWebSocketClient.send("Hello from " + Build.MANUFACTURER + " " + Build.MODEL);
            }

            @Override
            public void onMessage(String s) {
                final String message = s;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView textView = (TextView)findViewById(R.id.logText);
                        textView.append("\n" + message);
                        Log.i(TAG, "onMessage " + message);
                    }
                });
            }

            @Override
            public void onClose(int i, String s, boolean b) {
                Log.i(TAG, "onClose " + s);
            }

            @Override
            public void onError(Exception e) {
                Log.i(TAG, "onError " + e.getMessage());
            }
        };
        mWebSocketClient.connect();
    }
}
