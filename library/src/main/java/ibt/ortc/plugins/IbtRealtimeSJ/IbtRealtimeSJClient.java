package ibt.ortc.plugins.IbtRealtimeSJ;

import android.os.CountDownTimer;

import org.json.JSONException;
import org.json.simple.JSONValue;

import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import ibt.ortc.api.Strings;
import ibt.ortc.extensibility.EventEnum;
import ibt.ortc.extensibility.OnPublishResult;
import ibt.ortc.extensibility.OrtcClient;
import ibt.ortc.extensibility.exception.OrtcNotConnectedException;
import ibt.ortc.plugins.IbtRealtimeSJ.OrtcServerErrorException.OrtcServerErrorOperation;
import ibt.ortc.plugins.websocket.WebSocket;
import ibt.ortc.plugins.websocket.WebSocketEventHandler;
import ibt.ortc.plugins.websocket.WebSocketException;
import ibt.ortc.plugins.websocket.WebSocketMessage;

public final class IbtRealtimeSJClient extends OrtcClient {
  private static final Integer HEARTBEAT_TIMEOUT = 30;
  
	private WebSocket socket;
	
	private Thread heartBeatThread;	
	private Date lastHeartBeat;
	private String sessionId = "";
	private long sessionIdTimesamp = 0;
	private final int SESSION_TIME = 30;
	private final int SESSION_ID_SIZE = 16;


	@Override
	protected void connect() {
		Random randomGenerator = new Random();
		int port = this.uri.getPort();
		if (port == -1) {
			port = "https".equals(uri.getScheme()) ? 443 : 80;
		}

		String connectionUrl = String.format(
				"%s://%s:%s/broadcast/%s/%s/websocket",
				this.protocol.getProtocol(), this.uri.getHost(), port,
				randomGenerator.nextInt(1000), Strings.randomString(8));
		
		boolean ex = false;
		try {
			URI connectionUri = new URI(connectionUrl);
			socket = new WebSocket(connectionUri);
			addSocketEventsListener();

			socket.connect();
		} catch (Exception e) {
			ex = true;
		} finally {
			if (ex) {
				raiseOrtcEvent(
						EventEnum.OnException,
						(OrtcClient) this,
						new OrtcNotConnectedException(
								"Could not connect. Check if the server is running correctly."));
				if (isConnecting || isReconnecting) {
					isReconnecting = true;
					raiseOrtcEvent(EventEnum.OnReconnecting, (OrtcClient) this);
				}
			}
		}
	}
	
	private void initializeHeartBeatThread(){
	  if(heartBeatThread != null){
	    heartBeatThread.interrupt();
	  }
	  
	  heartBeatThread = new Thread(new Runnable() {      
      @Override
      public void run() {
        while(isConnected){
          try {
            Thread.sleep(HEARTBEAT_TIMEOUT*1000);
            Date currentDate = new Date();
            if(lastHeartBeat == null || ((currentDate.getTime() - lastHeartBeat.getTime())/1000 > HEARTBEAT_TIMEOUT)){
              try {
                socket.close(true);
              } catch (WebSocketException e) {}
            }            
          } catch (InterruptedException e) { }
        }
      }
    });
	  
	  heartBeatThread.start();
	}

	private void opAck(String message){
        org.json.JSONObject json = OrtcMessage.parseJSON(message);

        if (json != null && json.has("m") && json.has("seq")){
            try {
                HashMap pendingMsg = (HashMap)pendingPublishMessages.get((String)json.get("m"));
                CountDownTimer timeout = (CountDownTimer)pendingMsg.get("timeout");
                timeout.cancel();

                OnPublishResult callback = (OnPublishResult)pendingMsg.get("callback");
                callback.run(null, (String)json.get("seq"));

                pendingPublishMessages.remove((String)json.get("m"));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
	}

	private void addSocketEventsListener() {
		final OrtcClient sender = this;
		socket.setEventHandler(new WebSocketEventHandler() {

			@Override
			public void onOpen() {
			}

			@Override
			public void onMessage(WebSocketMessage socketMessage) {
				try {
					String message = socketMessage.getText();
					//message = message.replace("\\\"", "\"");
					lastHeartBeat = new Date();
					if ("h".equals(message)) {
						//lastHeartBeat = new Date();
					} else {
						if ("o".equals(message)) {
							performValidate();
						} else {

							OrtcMessage ortcMessage = OrtcMessage
									.parseMessage(message);
							OrtcOperation operation = ortcMessage
									.getOperation();

							switch (operation) {
								case Validated:
									onValidated(ortcMessage);
									break;
								case Subscribed:
									onSubscribed(ortcMessage);
									break;
								case Unsubscribed:
									onUnsubscribed(ortcMessage);
									break;
								case Received:
									onReceived(ortcMessage);
									break;
								case ack:
									opAck(message);
									break;
								case Error:
									onError(ortcMessage);
									break;
								case Close:
									try {
										socket.close(true);
									} catch (WebSocketException e) {
									}
									break;
							}
						}
					}
				} catch (IOException e) {
					raiseOrtcEvent(EventEnum.OnException, sender, e);
				}
			}

			@Override
			public void onClose() {
				if (heartBeatThread != null)
					heartBeatThread.interrupt();
				isReconnecting = false;
				raiseOrtcEvent(EventEnum.OnDisconnected, sender);
			}

			@Override
			public void onForcedClose() {
				if (heartBeatThread != null)
					heartBeatThread.interrupt();
				if (!isReconnecting) {
					isReconnecting = true;
					raiseOrtcEvent(EventEnum.OnDisconnected, sender);
				}
			}

			@Override
			public void onPing() {

			}

			@Override
			public void onPong() {

			}

			@Override
			public void onException(Exception error) {
				raiseOrtcEvent(EventEnum.OnException, sender, error);
			}
		});
	}

	private void performValidate() {
		String lAnnouncementSubChannel = Strings
				.isNullOrEmpty(this.announcementSubChannel) ? ""
				: this.announcementSubChannel;
		String lSessionId = this.getSessionId();
		String heartbeatDetails = heartbeatActive ? ";" + heartbeatTime + ";" + heartbeatFails + ";" : "";
		String validateMessage = String.format("validate;%s;%s;%s;%s;%s%s",
				this.applicationKey, this.authenticationToken,
				lAnnouncementSubChannel, lSessionId,
				replaceCharsSend(this.connectionMetadata),heartbeatDetails);

		sendMessage(validateMessage);
	}

	private static String random(final int MAX_LENGTH) {
		String session = UUID.randomUUID().toString();
		session = session.replace("-","");
		return session.substring(0, MAX_LENGTH);
	}

	private String generateSessionId(){
		this.sessionIdTimesamp = System.currentTimeMillis();
		this.sessionId = IbtRealtimeSJClient.random(SESSION_ID_SIZE);
		return this.sessionId;
	}

	private String getSessionId(){
		long now = System.currentTimeMillis();

		long minutes = TimeUnit.MILLISECONDS.toMinutes(now - this.sessionIdTimesamp);
		if (this.sessionId.equals("") || minutes >= SESSION_TIME)
			return this.generateSessionId();

		return this.sessionId;
	}


	private void onValidated(OrtcMessage message) {

		this.channelsPermissions = message.getPermissions();		
		raiseOrtcEvent(EventEnum.OnConnected, (OrtcClient) this);
		this.initializeHeartBeatThread();
	}

	private void onSubscribed(OrtcMessage message) {
		try {
			raiseOrtcEvent(EventEnum.OnSubscribed, (OrtcClient) this,
					message.channelSubscribed());
		} catch (Exception e) {
			raiseOrtcEvent(EventEnum.OnException, (OrtcClient) this, e);
		}
	}

	private void onUnsubscribed(OrtcMessage message) {
		try {
			raiseOrtcEvent(EventEnum.OnUnsubscribed, (OrtcClient) this,
					message.channelUnsubscribed());
		} catch (Exception e) {
			raiseOrtcEvent(EventEnum.OnException, (OrtcClient) this, e);
		}
	}

	private void onReceived(OrtcMessage message) {
		raiseOrtcEvent(EventEnum.OnReceived, message.getMessageChannel(),
				message.getMessage(), message.getMessageId(),
				message.getMessagePart(), message.getMessageTotalParts(), message.isFiltered(), message.getSeqId());
	}

	@SuppressWarnings("incomplete-switch")
	private void onError(OrtcMessage message) {
		Exception error;
		OrtcServerErrorException serverError = null;

		try {
			serverError = message.serverError();
			error = serverError;
		} catch (Exception e) {
			error = e;
		}

		if (serverError != null) {
			OrtcServerErrorOperation so = serverError.getOperation(); 
			if(so != null){
			switch (serverError.getOperation()) {
			case Validate:
				validateServerError();
				break;
			case Subscribe:
				cancelSubscription(serverError.getChannel());
				break;
			case Subscribe_MaxSize:
				channelMaxSizeError(serverError.getChannel());
				break;
			case Unsubscribe_MaxSize:
				channelMaxSizeError(serverError.getChannel());
				break;
			case Send_MaxSize:
				messageMaxSize();
				break;
			}
			} else {
				//System.out.println("ser err: " + serverError.getMessage());
			}
		}

		raiseOrtcEvent(EventEnum.OnException, (OrtcClient) this, error);
	}

	private void validateServerError() {
		stopReconnecting();

		try {
			socket.close(false);
		} catch (WebSocketException e) {
			raiseOrtcEvent(EventEnum.OnException, (OrtcClient) this, e);
		}
	}

	private void channelMaxSizeError(String channel) {
		cancelSubscription(channel);
		stopReconnecting();

		try {
			socket.close(false);
		} catch (WebSocketException e) {
			raiseOrtcEvent(EventEnum.OnException, (OrtcClient) this, e);
		}
	}

	private void messageMaxSize() {
		stopReconnecting();

		try {
			socket.close(false);
		} catch (WebSocketException e) {
			raiseOrtcEvent(EventEnum.OnException, (OrtcClient) this, e);
		}
	}

	@Override
	protected void disconnectIntern() {
		this.isConnecting = false;
		this.isDisconnecting = true;			
		this.isReconnecting = false;
		try {
			socket.close(false);
		} catch (WebSocketException e) {
			raiseOrtcEvent(EventEnum.OnException, (OrtcClient) this, e);
		}
	}

	@Override
	protected  void publish(String channel, String message, int ttl, String messagePartIdentifier, String permission){
        String escapedMessage = JSONValue.escape(message);

        String messageParsed = String.format("publish;%s;%s;%s;%s;%s;%s",
                this.applicationKey, this.authenticationToken, channel,
                ttl,
                permission,
                String.format("%s_%s", messagePartIdentifier, escapedMessage));
        sendMessage(messageParsed);
	}

	@Override
	protected void send(String channel, String message,
			String messagePartIdentifier, String permission) {
		String escapedMessage = JSONValue.escape(message);

		String messageParsed = String.format("send;%s;%s;%s;%s;%s",
				this.applicationKey, this.authenticationToken, channel,
				permission,
				String.format("%s_%s", messagePartIdentifier, escapedMessage));
		sendMessage(messageParsed);
	}

    @Override
    protected void sendAck(String channel, String messageId, String seqId, String asAllParts){
        String subscribeMessage = String.format("ack;%s;%s;%s;%s;%s",
                this.applicationKey, channel, messageId, seqId, asAllParts);
        sendMessage(subscribeMessage);
    }

	@Override
	protected void _subscribeWithOptions(String channel, String permission, boolean subscribeOnReconnected, boolean withNotifications,
                                         String filter, String subscriberId){
        String subscribeMessage = String.format("subscribeoptions;%s;%s;%s;%s;%s;%s;%s",
                this.applicationKey, this.authenticationToken, channel, subscriberId,
                (withNotifications ? String.format("%s;GCM", this.registrationId) : ""),
                permission,
                String.format("%s", (filter == null?"":filter)));
        sendMessage(subscribeMessage);
	}

	@Override
	protected void subscribe(String channel, String permission, boolean withNotification, boolean withFilter, String filter) {
		String subscribe = ((withFilter == true)? "subscribefilter" : "subscribe");
		String subscribeMessage = String.format("%s;%s;%s;%s;%s%s%s",
                subscribe, this.applicationKey, this.authenticationToken, channel,
				permission == null ? null : permission,
				(withNotification?String.format(";%s;GCM", this.registrationId):""),
                (withFilter?String.format(";%s", filter):""));
		sendMessage(subscribeMessage);
	}

	private void sendMessage(String message) {
		try {
			socket.send(String.format("\"%s\"", message));
		} catch (WebSocketException e) {
			raiseOrtcEvent(EventEnum.OnException, (OrtcClient) this, e);
		}
	}

	@Override
	protected void unsubscribe(String channel, boolean isValid, boolean isWithNotification) {
		if (isValid) {
			String unsubscribeMessage;
			if(isWithNotification)
				unsubscribeMessage = String.format("unsubscribe;%s;%s;%s;GCM", this.applicationKey, channel, this.registrationId);
			else
				unsubscribeMessage = String.format("unsubscribe;%s;%s", this.applicationKey, channel);
			sendMessage(unsubscribeMessage);
		}
	}

	private static String replaceCharsSend(String message) {
		// CAUSE: Assignment to method parameter
		String lMessage = message;
		if (lMessage == null) {
			lMessage = "";
		}

		return lMessage.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	@Override
	protected void sendHeartbeat() {
		if(heartbeatActive){
			sendMessage("b");
		}				
	}	
}
