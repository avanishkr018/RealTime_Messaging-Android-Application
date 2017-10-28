package ibt.ortc.ortclib;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.text.format.DateFormat;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import ibt.ortc.api.ChannelPermissions;
import ibt.ortc.api.OnDisablePresence;
import ibt.ortc.api.OnEnablePresence;
import ibt.ortc.api.OnPresence;
import ibt.ortc.api.Ortc;
import ibt.ortc.api.Presence;
import ibt.ortc.extensibility.OnConnected;
import ibt.ortc.extensibility.OnDisconnected;
import ibt.ortc.extensibility.OnException;
import ibt.ortc.extensibility.OnMessage;
import ibt.ortc.extensibility.OnMessageWithPayload;
import ibt.ortc.extensibility.OnReconnected;
import ibt.ortc.extensibility.OnReconnecting;
import ibt.ortc.extensibility.OnRegistrationId;
import ibt.ortc.extensibility.OnSubscribed;
import ibt.ortc.extensibility.OnUnsubscribed;
import ibt.ortc.extensibility.OrtcClient;
import ibt.ortc.extensibility.OrtcFactory;


public class MainActivity extends ActionBarActivity {

    private static final boolean defaultNeedsAuthentication = false;

    private OrtcClient client;
    private int reconnectingTries = 0;
    private static final int RESULT_SETTINGS = 1;

    private String server;
    private String appKey;
    private String privateKey;
    private String authToken;
    private String connectionMetadata;
    private boolean isCluster = true;
    private String channel;
    private String message;
    private static boolean inForeground;

    public static boolean isInForeground() {
        return inForeground;
    }

    public static void setInForeground(boolean inForeground) {
        MainActivity.inForeground = inForeground;
    }

    @Override
    protected void onResume() {
        super.onResume();
        MainActivity.setInForeground(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        MainActivity.setInForeground(false);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent intent = getIntent();

        String channel = intent.getStringExtra("channel");
        String message = intent.getStringExtra("message");
        String payload = intent.getStringExtra("payload");

        if (channel != null && message != null){
            if (payload != null) {
                log(String.format("Push on channel %s: %s payload: %s", channel, message, payload));
            }else{
                log(String.format("Push on channel %s: %s", channel, message));
            }
        }


        init();

        try {
            Ortc ortc = new Ortc();

            OrtcFactory factory;

            factory = ortc.loadOrtcFactory("IbtRealtimeSJ");

            client = factory.createClient();
            client.setApplicationContext(getApplicationContext());

            // Use this method if you have implemented a backend to store your user's GCM registration ids
            //RegistrationIdRemoteStore.getRegistrationIdFromBackend(getApplicationContext(), client);

            OrtcClient.setOnRegistrationId(new OnRegistrationId() {
                @Override
                public void run(String registrationId) {
                    Log.i("REG", "GCM Registration ID: " + registrationId);

                    // Use this method if you have implemented a backend to store your user's GCM registration ids
                    //RegistrationIdRemoteStore.setRegistrationIdToBackend(getApplicationContext(), registrationId);
                }
            });

            client.setGoogleProjectId("[enter your GCM project id]");

            Ortc.setOnPushNotification(new OnMessageWithPayload() {
                @Override
                public void run(OrtcClient sender, String channel, String message, Map payload) {
                    final String subscribedChannel = channel;
                    final String messageReceived = message;
                    final Map<String, Object> payloadReceived = payload;
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (payloadReceived != null) {
                                log(String.format("Push on channel %s: %s payload: %s", subscribedChannel, messageReceived, payloadReceived.toString()));
                            }else{
                                log(String.format("Push on channel %s: %s", subscribedChannel, messageReceived));
                            }
                        }
                    });
                }
            });
        } catch (Exception e) {
            log(String.format("ORTC CREATE ERROR: %s", e.toString()));
        }



        if (client != null) {
            try {
                client.onConnected = new OnConnected() {

                    public void run(final OrtcClient sender) {
                        runOnUiThread(new Runnable() {

                            public void run() {
                                log(String.format("Connected to: %s", ((OrtcClient) sender).getUrl()));
                            }
                        });
                    }
                };

                client.onDisconnected = new OnDisconnected() {

                    public void run(OrtcClient arg0) {
                        runOnUiThread(new Runnable() {

                            public void run() {
                                log("Disconnected");
                            }
                        });
                    }
                };

                client.onSubscribed = new OnSubscribed() {

                    public void run(OrtcClient sender, String channel) {
                        final String subscribedChannel = channel;
                        runOnUiThread(new Runnable() {

                            public void run() {
                                log(String.format("Subscribed to channel: %s", subscribedChannel));
                            }
                        });
                    }
                };

                client.onUnsubscribed = new OnUnsubscribed() {

                    public void run(OrtcClient sender, String channel) {
                        final String subscribedChannel = channel;
                        runOnUiThread(new Runnable() {

                            public void run() {
                                log(String.format("Unsubscribed from channel: %s", subscribedChannel));
                            }
                        });
                    }
                };

                client.onException = new OnException() {

                    public void run(OrtcClient send, Exception ex) {
                        final Exception exception = ex;
                        runOnUiThread(new Runnable() {

                            public void run() {
                                log(String.format("Error: %s", exception.getMessage()));
                            }
                        });
                    }
                };

                client.onReconnected = new OnReconnected() {

                    public void run(final OrtcClient sender) {
                        runOnUiThread(new Runnable() {

                            public void run() {
                                reconnectingTries = 0;

                                log(String.format("Reconnected to: %s", ((OrtcClient) sender).getUrl()));
                            }
                        });
                    }
                };

                client.onReconnecting = new OnReconnecting() {

                    public void run(OrtcClient sender) {
                        runOnUiThread(new Runnable() {

                            public void run() {
                                reconnectingTries++;

                                log(String.format("Reconnecting %s...", reconnectingTries));
                            }
                        });
                    }
                };
            } catch (Exception e) {
                log(String.format("ORTC EXCEPTION: %s", e.toString()));
            }
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            startActivityForResult(new Intent(this,SettingsActivity.class),RESULT_SETTINGS);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case RESULT_SETTINGS:
                updateUserSettings();
                break;
        }
    }

    private void init(){

        SharedPreferences sharedPrefs = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());

        server = sharedPrefs.getString("server",getString(R.string.DefaultServer));
        isCluster = sharedPrefs.getBoolean("isCluster",true);
        authToken = sharedPrefs.getString("authToken",getString(R.string.DefaultAuthenticationToken));
        appKey = sharedPrefs.getString("appKey",getString(R.string.DefaultApplicationKey));
        privateKey = sharedPrefs.getString("privateKey",getString(R.string.DefaultPrivateKey));
        connectionMetadata = sharedPrefs.getString("server",getString(R.string.DefaultConnectionMetadata));
        channel = sharedPrefs.getString("channel",getString(R.string.DefaultChannel));
        message = sharedPrefs.getString("message",getString(R.string.DefaultText));
    }

    private void updateUserSettings() {

        SharedPreferences sharedPrefs = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());

        server = sharedPrefs.getString("server",getString(R.string.DefaultServer));
        isCluster = sharedPrefs.getBoolean("isCluster",true);
        authToken = sharedPrefs.getString("authToken",getString(R.string.DefaultAuthenticationToken));
        appKey = sharedPrefs.getString("appKey",getString(R.string.DefaultApplicationKey));
        privateKey = sharedPrefs.getString("privateKey",getString(R.string.DefaultPrivateKey));
        connectionMetadata = sharedPrefs.getString("connMetada",getString(R.string.DefaultConnectionMetadata));
        channel = sharedPrefs.getString("channel",getString(R.string.DefaultChannel));
        message = sharedPrefs.getString("message",getString(R.string.DefaultText));

    }

    public void clearClickEventHandler(View v) {
        TextView textViewLog = (TextView) findViewById(R.id.TextViewLog);
        textViewLog.setText("");
        textViewLog.scrollTo(0, 0);
    }

    private void subscribe() {
        log("Subscribing...");

//        HashMap options = new HashMap();
//        options.put("channel", channel);
//        options.put("withNotifications",true);
//        options.put("subscriberId","testeph");
//
//        client.subscribeWithOptions(options, new OnMessageWithOptions() {
//            @Override
//            public void run(OrtcClient sender, Map msgOptions) {
//                if (msgOptions.containsKey("channel") && msgOptions.containsKey("message")){
//                    final String channel = (String)msgOptions.get("channel");
//                    final String message = (String)msgOptions.get("message");
//                    final String seqId = (String)msgOptions.get("seqId");
//
//                    runOnUiThread(new Runnable() {
//                        public void run() {
//                            log(String.format("Message on channel %s: %s: seqId:%s", channel, message, seqId));
//                        }
//                    });
//                }
//            }
//        });

//        client.subscribeWithBuffer(channel, "teste1", new OnMessageWithBuffer() {
//            @Override
//            public void run(OrtcClient sender, final String channel, final String seqId, final String message) {
//                runOnUiThread(new Runnable() {
//                    public void run() {
//                        log(String.format("Message on channel %s: %s: seqId:%s", channel, message, seqId));
//                    }
//                });
//            }
//        });
//
//        client.subscribe(channel, true, new OnMessage() {
//            @Override
//            public void run(OrtcClient sender, String channel, String message) {
//                runOnUiThread(new Runnable() {
//                    public void run() {
//                        log(String.format("Message on channel %s: %s:", channel, message));
//                    }
//                });
//            }
//        });

        client.subscribeWithNotifications(channel, true,
                new OnMessage() {
                    public void run(OrtcClient sender, String channel,
                                    String message) {
                        final String subscribedChannel = channel;
                        final String messageReceived = message;
                        runOnUiThread(new Runnable() {
                            public void run() {
                                log(String.format("Message on channel %s: %s", subscribedChannel, messageReceived));
                            }
                        });
                    }
                });
    }

    private void unsubscribe() {
        log("Unsubscribing...");
        client.unsubscribe(channel);
    }

    public void sendClickEventHandler(View v) {

        log(String.format("Sending %s to %s...", message, channel));

        client.send(channel, message);
    }

    private void connect() {


        if (defaultNeedsAuthentication) {
            try {
                TextView textViewLog = (TextView) findViewById(R.id.TextViewLog);
                textViewLog.setMovementMethod(new ScrollingMovementMethod());

                HashMap<String, ChannelPermissions> permissions = new HashMap<String, ChannelPermissions>();

                permissions.put("yellow:*", ChannelPermissions.Write);
                permissions.put("yellow", ChannelPermissions.Write);
                permissions.put("test:*", ChannelPermissions.Write);
                permissions.put("test", ChannelPermissions.Write);

                log("Authenticating...");

                if (!Ortc.saveAuthentication(server, isCluster, authToken, false, appKey, 14000, privateKey, permissions)) {
                    log("Unable to authenticate");
                }
                else {
                    log("Authentication successfull");
                }
            } catch (Exception e) {
                log(String.format("ORTC AUTHENTICATION ERROR: %s", e.toString()));
            }
        }

        if (isCluster) {
            client.setClusterUrl(server);
        }
        else {
            client.setUrl(server);
        }

        client.setConnectionMetadata(connectionMetadata);

        log("Connecting...");
        client.connect(appKey, authToken);
    }

    private void disconnect() {
        log("Disconnecting...");
        client.disconnect();
    }

    public void presenceClickEventHandler(View v) {

        Ortc.presence(
                server,
                isCluster,
                appKey,
                authToken, channel, new OnPresence() {
                    @Override
                    public void run(Exception error, Presence presence) {
                        final Exception exception = error;
                        final Presence presenceData = presence;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if(exception != null){
                                    log(String.format("Error: %s", exception.getMessage()));
                                }else{
                                    Iterator<?> metadataIterator = presenceData.getMetadata().entrySet().iterator();
                                    while(metadataIterator.hasNext()){
                                        @SuppressWarnings("unchecked")
                                        Map.Entry<String, Long> entry = (Map.Entry<String, Long>) metadataIterator.next();
                                        log(entry.getKey() + " - " + entry.getValue());
                                    }
                                    log("Subscriptions - " + presenceData.getSubscriptions());
                                }
                            }
                        });
                    }
                });
    }

    private void enablePresence() {

        Ortc.enablePresence(
                server,
                isCluster,
                appKey,
                privateKey,
                channel,
                true,
                new OnEnablePresence() {
                    @Override
                    public void run(Exception error, String result) {
                        final Exception exception = error;
                        final String resultText = result;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if(exception != null){
                                    log(String.format("Error: %s", exception.getMessage()));
                                }else{
                                    log(resultText);
                                }
                            }
                        });
                    }
                });
    }

    private void disablePresence() {

        Ortc.disablePresence(
                server,
                isCluster,
                appKey,
                privateKey,
                channel,
                new OnDisablePresence() {
                    @Override
                    public void run(Exception error, String result) {
                        final Exception exception = error;
                        final String resultText = result;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (exception != null) {
                                    log(String.format("Error: %s", exception.getMessage()));
                                } else {
                                    log(resultText);
                                }
                            }
                        });
                    }
                });
    }


    private void log(String text) {
        TextView t = ((TextView) findViewById(R.id.TextViewLog));
        t.setText(String.format("%s - %s\n%s", DateFormat.format("HH:mm:ss", new java.util.Date()), text, t.getText()));
    }


    public void connect(View view) {
        connect();
    }

    public void disconnect(View view) {
        disconnect();
    }

    public void subscribe(View view) {
        subscribe();
    }

    public void unsubscribe(View view) {
        unsubscribe();
    }

    public void enablePresence(View view) {
        enablePresence();
    }

    public void disablePresence(View view) {
        disablePresence();
    }
}
