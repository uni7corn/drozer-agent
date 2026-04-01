package com.reversec.dz.services;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Observable;
import java.util.Observer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;

import com.reversec.dz.Agent;
import com.reversec.dz.BuildConfig;
import com.reversec.dz.R;
import com.reversec.dz.activities.MainActivity;
import com.reversec.dz.models.ServerSettings;
import com.reversec.dz.util.PentestPasswordManager;
import com.reversec.jsolar.api.connectors.Connector;
import com.reversec.jsolar.api.links.Server;

public class ServerService extends ConnectorService {

	private static final String FGS_CHANNEL_ID      = "drozer_server_fgs";
	private static final int    FGS_NOTIFICATION_ID = 1001;
	private static final long   FGS_UPDATE_INTERVAL = 15_000;

	private Handler notificationUpdateHandler;
	private Runnable notificationUpdateRunnable;

	public static final int MSG_GET_DETAILED_SERVER_STATUS = 21;
	public static final int MSG_GET_SERVER_STATUS = 22;
	public static final int MSG_GET_SSL_FINGERPRINT = 23;
	public static final int MSG_START_SERVER = 24;
	public static final int MSG_STOP_SERVER = 25;
	
	private Server server = null;
	private com.reversec.jsolar.api.connectors.Server server_parameters = new com.reversec.jsolar.api.connectors.Server();
	
	public Bundle getDetailedStatus() {
		Bundle data = new Bundle();
		
		data.putBoolean(Connector.CONNECTOR_ENABLED, server_parameters.isEnabled());
		data.putBoolean(com.reversec.jsolar.api.connectors.Server.SERVER_PASSWORD, server_parameters.hasPassword());
    	data.putBoolean(com.reversec.jsolar.api.connectors.Server.SERVER_SSL, server_parameters.isSSL());
    	
    	switch(server_parameters.getStatus()) {
    	case ACTIVE:
    		data.putBoolean(Connector.CONNECTOR_CONNECTED, true);
    		data.putBoolean(Connector.CONNECTOR_OPEN_SESSIONS, true);
    		break;
    		
    	case CONNECTING:
    		data.putBoolean(Connector.CONNECTOR_CONNECTED, false);
    		data.putBoolean(Connector.CONNECTOR_OPEN_SESSIONS, false);
    		break;
    		
    	case ONLINE:
    		data.putBoolean(Connector.CONNECTOR_CONNECTED, true);
    		data.putBoolean(Connector.CONNECTOR_OPEN_SESSIONS, false);
    		break;
    		
    	default:
    		data.putBoolean(Connector.CONNECTOR_CONNECTED, false);
    		data.putBoolean(Connector.CONNECTOR_OPEN_SESSIONS, false);
    		break;
    	}
    	
    	return data;
	}
	
	public Bundle getServerFingerprint() {
		Bundle data = new Bundle();

		if(this.server != null)
			data.putString(Connector.CONNECTOR_SSL_FINGERPRINT, this.server.getHostCertificateFingerprint());
		else
			data.putString(Connector.CONNECTOR_SSL_FINGERPRINT, "No running server.");
		
		return data;
	}
	
	public Bundle getStatus() {
		Bundle data = new Bundle();
		
		data.putInt("server", this.server_parameters.getStatus().ordinal());
		
		return data;
	}
	
	@Override
	public void handleMessage(Message msg) {
		switch(msg.what) {
		case MSG_GET_DETAILED_SERVER_STATUS:
			try {
				Message message = Message.obtain(null, MSG_GET_DETAILED_SERVER_STATUS);
				message.setData(this.getDetailedStatus());
				
				msg.replyTo.send(message);
			}
			catch(RemoteException e) {
				Log.e(this.getString(R.string.log_tag_server_service), e.getMessage());
			}
			break;
			
		case MSG_GET_SERVER_STATUS:
			try {
				Message message = Message.obtain(null, MSG_GET_SERVER_STATUS);
				message.setData(this.getStatus());
				
				msg.replyTo.send(message);
			}
			catch(RemoteException e) {
				Log.e(this.getString(R.string.log_tag_server_service), e.getMessage());
			}
			break;
			
		case MSG_GET_SSL_FINGERPRINT:
			try {
				Message message = Message.obtain(null, MSG_GET_SSL_FINGERPRINT);
				message.setData(this.getServerFingerprint());
				
				msg.replyTo.send(message);
			}
			catch(RemoteException e) {
				Log.e(this.getString(R.string.log_tag_server_service), e.getMessage());
			}
			break;
			
		case MSG_START_SERVER:
			try {
				this.startServer();
				
				Message message = Message.obtain(null, MSG_GET_SERVER_STATUS);
				message.setData(this.getStatus());
				
				msg.replyTo.send(message);
			}
			catch(RemoteException e) {
				Log.e(this.getString(R.string.log_tag_server_service), e.getMessage());
			}
			break;
			
		case MSG_STOP_SERVER:
			try {
				this.stopServer();

				Message message = Message.obtain(null, MSG_GET_SERVER_STATUS);
				message.setData(this.getStatus());
				
				msg.replyTo.send(message);
			}
			catch(RemoteException e) {
				Log.e(this.getString(R.string.log_tag_server_service), e.getMessage());
			}
			break;
		}	
	
	}	
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId){
		int ret_val = super.onStartCommand(intent, flags, startId);

		if(intent != null && intent.getCategories() != null && intent.getCategories().contains("com.reversec.dz.START_EMBEDDED")) {
			Agent.getInstance().setContext(this.getApplicationContext());
			PentestPasswordManager.ensurePasswordGenerated(this.getApplicationContext());
			this.startServer();
		}

		return ret_val;
	}

	private void ensureForeground() {
		if (!BuildConfig.IS_PENTEST) return;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel ch = new NotificationChannel(
				FGS_CHANNEL_ID, "drozer Server", NotificationManager.IMPORTANCE_LOW);
			ch.setDescription("Keeps the drozer server running in the background");
			((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
				.createNotificationChannel(ch);
		}

		startForeground(FGS_NOTIFICATION_ID, buildForegroundNotification("Server stopped"));
	}

	private Notification buildForegroundNotification(String contentText) {
		PendingIntent pi = PendingIntent.getActivity(this, 0,
			new Intent(this, MainActivity.class),
			PendingIntent.FLAG_IMMUTABLE);

		return new NotificationCompat.Builder(this, FGS_CHANNEL_ID)
			.setSmallIcon(R.drawable.ic_notification)
			.setContentTitle("drozer Agent")
			.setContentText(contentText)
			.setStyle(new NotificationCompat.BigTextStyle().bigText(contentText))
			.setContentIntent(pi)
			.setOngoing(true)
			.setPriority(NotificationCompat.PRIORITY_LOW)
			.build();
	}

	private void updateForegroundNotification(String contentText) {
		if (!BuildConfig.IS_PENTEST) return;
		((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
			.notify(FGS_NOTIFICATION_ID, buildForegroundNotification(contentText));
	}

	private String buildNotificationText() {
		String text = "Listening on " + getLocalAddressesString(server_parameters.getPort());
		if (BuildConfig.IS_PENTEST && server_parameters.hasPassword()) {
			text += "\nPassword: " + server_parameters.getPassword();
		}
		return text;
	}

	private String getLocalAddressesString(int port) {
		List<String> addresses = new ArrayList<>();
		addresses.add("127.0.0.1:" + port);
		try {
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements()) {
				NetworkInterface iface = interfaces.nextElement();
				if (iface.isLoopback() || !iface.isUp()) continue;
				Enumeration<InetAddress> addrs = iface.getInetAddresses();
				while (addrs.hasMoreElements()) {
					InetAddress addr = addrs.nextElement();
					if (addr instanceof Inet4Address) {
						addresses.add(addr.getHostAddress() + ":" + port);
					}
				}
			}
		} catch (SocketException e) {
			// ignore
		}
		return TextUtils.join(", ", addresses);
	}
	
	
	
	@Override
	public void onCreate() {
		super.onCreate();

		ServerService.running = true;

		notificationUpdateHandler = new Handler(Looper.getMainLooper());
		notificationUpdateRunnable = new Runnable() {
			@Override public void run() {
				if (server != null && BuildConfig.IS_PENTEST) {
					updateForegroundNotification(buildNotificationText());
					notificationUpdateHandler.postDelayed(this, FGS_UPDATE_INTERVAL);
				}
			}
		};

		this.server_parameters.addObserver(new Observer() {

			@Override
			public void update(Observable arg0, Object arg1) {
				Message message = Message.obtain(null, MSG_GET_SERVER_STATUS);
				message.setData(ServerService.this.getStatus());
						
				ServerService.this.sendToAllMessengers(message);
			}
			
		});
		
		this.server_parameters.setStatus(Connector.Status.OFFLINE);
	}
	
	@Override
	public void onDestroy() {
		ServerService.running = false;
		if (BuildConfig.IS_PENTEST) {
			stopForeground(true);
		}
	}

	public static void startAndBindToService(Context context, ServiceConnection serviceConnection) {
		if (!ServerService.running)
			context.startService(new Intent(context, ServerService.class));

		context.bindService(new Intent(context, ServerService.class), serviceConnection, Context.BIND_AUTO_CREATE);
	}
	
	public void startServer() {
		if(this.server == null) {
			// Promote to foreground service before doing any work so the OS knows
			// this service is user-visible. Must happen before the 5-second window
			// expires when started via startForegroundService() (e.g. from BootReceiver).
			ensureForeground();

			// Persist the intent to keep the server running so that onServiceConnected()
			// can restart it after the app is killed and relaunched, regardless of which
			// code path started the server (UI toggle, BootReceiver, etc.).
			getSharedPreferences(getPackageName() + "_preferences", Context.MODE_MULTI_PROCESS)
					.edit().putBoolean("localServerEnabled", true).apply();

			(new ServerSettings()).load(getBaseContext(), this.server_parameters);

			this.server_parameters.enabled = true;
			this.server = new Server(this.server_parameters, Agent.getInstance().getDeviceInfo());
			this.server.setLogger(this.server_parameters.getLogger());
			this.server_parameters.getLogger().addOnLogMessageListener(this);

			this.server.start();

			updateForegroundNotification(buildNotificationText());
			notificationUpdateHandler.postDelayed(notificationUpdateRunnable, FGS_UPDATE_INTERVAL);
			Toast.makeText(this, String.format(Locale.ENGLISH, this.getString(R.string.embedded_server_started), this.server_parameters.getPort()), Toast.LENGTH_SHORT).show();
		}
	}

	public void stopServer() {
		if(this.server != null) {
			// Null first to prevent re-entry if stopConnector() triggers any callbacks.
			Server serverToStop = this.server;
			this.server = null;

			notificationUpdateHandler.removeCallbacks(notificationUpdateRunnable);
			this.server_parameters.enabled = false;
			getSharedPreferences(getPackageName() + "_preferences", Context.MODE_MULTI_PROCESS)
					.edit().putBoolean("localServerEnabled", false).apply();
			serverToStop.stopConnector();

			if (BuildConfig.IS_PENTEST) stopForeground(true);
			Toast.makeText(this, String.format(Locale.ENGLISH, this.getString(R.string.embedded_server_stopped), this.server_parameters.getPort()), Toast.LENGTH_SHORT).show();
		}
	}

}
