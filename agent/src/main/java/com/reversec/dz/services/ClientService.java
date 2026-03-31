package com.reversec.dz.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.reversec.dz.Agent;
import com.reversec.dz.BuildConfig;
import com.reversec.dz.R;
import com.reversec.dz.activities.MainActivity;
import com.reversec.dz.models.EndpointManager;
import com.reversec.jsolar.api.connectors.Endpoint;
import com.reversec.jsolar.api.links.Client;

public class ClientService extends ConnectorService {

	private static final String FGS_CHANNEL_ID      = "drozer_client_fgs";
	private static final int    FGS_NOTIFICATION_ID = 1002;

	public static final int MSG_GET_DETAILED_ENDPOINT_STATUS = 11;
	public static final int MSG_GET_ENDPOINTS_STATUS = 12;
	public static final int MSG_GET_SSL_FINGERPRINT = 13;
	public static final int MSG_START_ENDPOINT = 14;
	public static final int MSG_STOP_ENDPOINT = 15;

	private SparseArray<Client> clients = new SparseArray<Client>();
	private final EndpointManager endpoint_manager = new EndpointManager(this);
	
	public Bundle getEndpointDetailedStatus(int endpoint_id) {
		Bundle data = new Bundle();
		Endpoint endpoint = this.endpoint_manager.get(endpoint_id);
		
		data.putInt(Endpoint.ENDPOINT_ID, endpoint.getId());
		data.putBoolean(Endpoint.CONNECTOR_ENABLED, endpoint.isEnabled());
		data.putBoolean(Endpoint.ENDPOINT_PASSWORD, endpoint.hasPassword());
    	data.putBoolean(Endpoint.ENDPOINT_SSL, endpoint.isSSL());
    	
    	switch(endpoint.getStatus()) {
    	case ACTIVE:
    		data.putBoolean(Endpoint.CONNECTOR_CONNECTED, true);
    		data.putBoolean(Endpoint.CONNECTOR_OPEN_SESSIONS, true);
    		break;
    		
    	case CONNECTING:
    		data.putBoolean(Endpoint.CONNECTOR_CONNECTED, false);
    		data.putBoolean(Endpoint.CONNECTOR_OPEN_SESSIONS, false);
    		break;
    		
    	case ONLINE:
    		data.putBoolean(Endpoint.CONNECTOR_CONNECTED, true);
    		data.putBoolean(Endpoint.CONNECTOR_OPEN_SESSIONS, false);
    		break;
    		
    	default:
    		data.putBoolean(Endpoint.CONNECTOR_CONNECTED, false);
    		data.putBoolean(Endpoint.CONNECTOR_OPEN_SESSIONS, false);
    		break;
    	}
    	
    	return data;
	}
	
	public Bundle getEndpointFingerprint(int id) {
		Bundle data = new Bundle();
		
		Client client = this.clients.get(id);
		
		if(client != null)
			data.putString(Endpoint.CONNECTOR_SSL_FINGERPRINT, client.getPeerCertificateFingerprint());
		else
			data.putString(Endpoint.CONNECTOR_SSL_FINGERPRINT, "No running client.");
		
		return data;
	}
	
	public Bundle getEndpointsStatus() {
		Bundle data = new Bundle();
		
		for(Endpoint e : this.endpoint_manager.all())
			data.putInt("endpoint-" + e.getId(), e.getStatus().ordinal());
		
		return data;
	}
	
	@Override
	public void handleMessage(Message msg) {
		Bundle data = msg.getData();
		
		switch(msg.what) {
		case MSG_GET_DETAILED_ENDPOINT_STATUS:
			try {
				Message message = Message.obtain(null, MSG_GET_DETAILED_ENDPOINT_STATUS);
				message.setData(this.getEndpointDetailedStatus(data.getInt(Endpoint.ENDPOINT_ID)));
				
				msg.replyTo.send(message);
			}
			catch(RemoteException e) {
				Log.e(this.getString(R.string.log_tag_client_service), e.getMessage());
			}
			break;
			
		case MSG_GET_ENDPOINTS_STATUS:
			try {
				Message message = Message.obtain(null, MSG_GET_ENDPOINTS_STATUS);
				message.setData(this.getEndpointsStatus());
				
				msg.replyTo.send(message);
			}
			catch(RemoteException e) {
				Log.e(this.getString(R.string.log_tag_client_service), e.getMessage());
			}
			break;
			
		case MSG_GET_SSL_FINGERPRINT:
			try {
				Message message = Message.obtain(null, MSG_GET_SSL_FINGERPRINT);
				message.setData(this.getEndpointFingerprint(data.getInt(Endpoint.ENDPOINT_ID)));
				
				msg.replyTo.send(message);
			}
			catch(RemoteException e) {
				Log.e(this.getString(R.string.log_tag_client_service), e.getMessage());
			}
			break;
			
		case MSG_START_ENDPOINT:
			try {
				this.startEndpoint(data.getInt(Endpoint.ENDPOINT_ID));
				
				Message message = Message.obtain(null, MSG_GET_ENDPOINTS_STATUS);
				message.setData(this.getEndpointsStatus());
				
				msg.replyTo.send(message);
			}
			catch(RemoteException e) {
				Log.e(this.getString(R.string.log_tag_client_service), e.getMessage());
			}
			break;
			
		case MSG_STOP_ENDPOINT:
			try {
				this.stopEndpoint(data.getInt(Endpoint.ENDPOINT_ID));
				
				Message message = Message.obtain(null, MSG_GET_ENDPOINTS_STATUS);
				message.setData(this.getEndpointsStatus());
				
				msg.replyTo.send(message);
			}
			catch(RemoteException e) {
				Log.e(this.getString(R.string.log_tag_client_service), e.getMessage());
			}
			break;
		}
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId){
		int ret_val = super.onStartCommand(intent, flags, startId);
		
		if(intent != null && intent.getCategories() != null && intent.getCategories().contains("com.reversec.dz.CREATE_ENDPOINT")) {
			Agent.getInstance().setContext(this.getApplicationContext());
			
			if(intent.getExtras() != null) {
				Bundle endpoint_data = intent.getExtras();
				String name = endpoint_data.getString("name");
				String host = endpoint_data.getString("host");
				int port = endpoint_data.getInt("port");
				boolean ssl = endpoint_data.getBoolean("ssl");
				String password = endpoint_data.getString("password");
				String ts_path = endpoint_data.getString("ts_path");
				String ts_password = endpoint_data.getString("ts_password");
				
				if(name != null && host != null){
					Endpoint new_endpoint = new Endpoint(name, host, port, ssl, ts_path != null ? ts_path : "", ts_password != null ? ts_password : "", password != null ? password : "");
					this.endpoint_manager.add(new_endpoint);
					
					if(intent.getCategories().contains("com.reversec.dz.START_ENDPOINT"))
						this.startEndpoint(new_endpoint.getId());
				}
			}
		}
		
		return ret_val;
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		Agent.getInstance().setContext(this);
		ClientService.running = true;
		
		this.endpoint_manager.setOnEndpointStatusChangeListener(new EndpointManager.OnEndpointStatusChangeListener() {
			
			@Override
			public void onEndpointStopped(Endpoint endpoint) {}
			
			@Override
			public void onEndpointStarted(Endpoint endpoint) {}
			
			@Override
			public void onEndpointStatusChanged(Endpoint endpoint) {
				Message message = Message.obtain(null, MSG_GET_ENDPOINTS_STATUS);
				message.setData(ClientService.this.getEndpointsStatus());
				
				ClientService.this.sendToAllMessengers(message);
			}
			
		});
		
		for(Endpoint e : this.endpoint_manager.all())
			e.setStatus(Endpoint.Status.OFFLINE);
	}
	
	@Override
	public void onDestroy() {
		ClientService.running = false;
		if (BuildConfig.IS_PENTEST) stopForeground(true);
	}

	// -------------------------------------------------------------------------
	// Foreground service helpers (pentest flavor only)
	// -------------------------------------------------------------------------

	private Notification buildForegroundNotification(String contentText) {
		PendingIntent pi = PendingIntent.getActivity(this, 0,
			new Intent(this, MainActivity.class),
			PendingIntent.FLAG_IMMUTABLE);

		return new NotificationCompat.Builder(this, FGS_CHANNEL_ID)
			.setSmallIcon(R.drawable.ic_notification)
			.setContentTitle("drozer Agent")
			.setContentText(contentText)
			.setContentIntent(pi)
			.setOngoing(true)
			.setPriority(NotificationCompat.PRIORITY_LOW)
			.build();
	}

	/** Promote to foreground and show which hosts are currently connected. */
	private void updateForegroundNotification() {
		if (!BuildConfig.IS_PENTEST) return;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel ch = new NotificationChannel(
				FGS_CHANNEL_ID, "drozer Endpoints", NotificationManager.IMPORTANCE_LOW);
			ch.setDescription("Keeps outgoing drozer connections alive");
			((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
				.createNotificationChannel(ch);
		}

		List<String> active = new ArrayList<>();
		for (Endpoint e : this.endpoint_manager.all()) {
			if (e.isEnabled()) active.add(e.toConnectionString());
		}

		String text = active.isEmpty()
			? "No active connections"
			: "Connected to: " + android.text.TextUtils.join(", ", active);

		startForeground(FGS_NOTIFICATION_ID, buildForegroundNotification(text));
	}

	/** Drop foreground status when no endpoints remain active. */
	private void updateForegroundState() {
		if (!BuildConfig.IS_PENTEST) return;
		boolean anyActive = false;
		for (Endpoint e : this.endpoint_manager.all()) {
			if (e.isEnabled()) { anyActive = true; break; }
		}
		if (anyActive) {
			updateForegroundNotification();
		} else {
			stopForeground(true);
		}
	}
	
	public static void startAndBindToService(Context context, ServiceConnection serviceConnection) {
		if(!ClientService.running)
			context.startService(new Intent(context, ClientService.class));
		
		Intent intent = new Intent(context, ClientService.class);
    	context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
	}
	
	public void startEndpoint(int id) {
		if(this.clients.get(id) == null) {
			Endpoint endpoint = this.endpoint_manager.get(id, true);

			Agent.getInstance().getEndpointManager().setActive(endpoint.getId(), true);

			Client client = new Client(endpoint, Agent.getInstance().getDeviceInfo());
			client.setLogger(endpoint.getLogger());
			endpoint.getLogger().addOnLogMessageListener(this);

			this.clients.put(id, client);

			endpoint.enabled = true;
			client.start();

			updateForegroundNotification();

			Toast.makeText(this, String.format(Locale.ENGLISH, this.getString(R.string.endpoint_started), endpoint.toConnectionString()), Toast.LENGTH_SHORT).show();
		}
	}

	public void stopEndpoint(int id) {
		Endpoint endpoint = this.endpoint_manager.get(id);
		Client client = this.clients.get(id);

		Agent.getInstance().getEndpointManager().setActive(endpoint.getId(), false);

		if(client != null) {
			client.stopConnector();
			endpoint.enabled = false;

			this.clients.remove(id);

			updateForegroundState();

			Toast.makeText(this, String.format(Locale.ENGLISH, this.getString(R.string.endpoint_stopped), endpoint.toConnectionString()), Toast.LENGTH_SHORT).show();
		}
	}

}
