package com.reversec.dz.service_connectors;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import com.reversec.dz.Agent;
import com.reversec.dz.BuildConfig;
import com.reversec.dz.services.ServerService;
import com.reversec.jsolar.api.connectors.Server;

public class ServerServiceConnection implements ServiceConnection {

	private Messenger service = null;
	private boolean bound = false;
	// True only for the first bind in this process lifetime. Used by the pentest
	// flavor to auto-start the server on cold launch without restarting it every
	// time the activity resumes (onPause unbinds, onResume rebinds).
	private boolean firstBind = true;
	
	public void getDetailedServerStatus(Messenger replyTo) throws RemoteException {
		Message msg = Message.obtain(null, ServerService.MSG_GET_DETAILED_SERVER_STATUS);
		msg.replyTo = replyTo;
		
		this.send(msg);
	}
	
	public void getHostFingerprint(Messenger replyTo) throws RemoteException {
		Bundle data = new Bundle();
		data.putBoolean("ctrl:no_cache_messenger", true);
		
		Message msg = Message.obtain(null, ServerService.MSG_GET_SSL_FINGERPRINT);
		msg.replyTo = replyTo;
		msg.setData(data);
		
		this.send(msg);
	}
	
	public void getServerStatus(Messenger replyTo) throws RemoteException {
		Message msg = Message.obtain(null, ServerService.MSG_GET_SERVER_STATUS);
		msg.replyTo = replyTo;
		
		this.send(msg);
	}
	
	public boolean isBound() {
		return this.bound;
	}
	
	@Override
	public void onServiceConnected(ComponentName className, IBinder service) {
		this.service = new Messenger(service);
		this.bound = true;
		// Pentest flavor: auto-start only on the first bind of this process lifetime
		// (i.e. cold launch). Subsequent binds happen every time the activity resumes
		// from background and must not restart a server the user explicitly stopped.
		// Store flavor: use the saved preference as before.
		boolean shouldStart = (BuildConfig.IS_PENTEST && firstBind)
				|| (!BuildConfig.IS_PENTEST
						&& Agent.getInstance().getSettings().getBoolean("localServerEnabled", false)
						&& Agent.getInstance().getSettings().getBoolean("restore_after_crash", true));
		firstBind = false;
		if(shouldStart){
			try {
				ServerServiceConnection ssc = Agent.getInstance().getServerService();
				Server server = Agent.getInstance().getServerParameters();
				Messenger messenger = Agent.getInstance().getMessenger();

				ssc.startServer(server, messenger);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
		// Always sync UI with actual service state on bind, regardless of how the
		// server was started (e.g. auto-started at boot in the pentest flavor).
		try {
			this.getServerStatus(Agent.getInstance().getMessenger());
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void onServiceDisconnected(ComponentName className) {
		this.service = null;
		this.bound = false;
	}
	
	protected void send(Message msg) throws RemoteException {
		this.service.send(msg);
	}
	
	public void startServer(Server server, Messenger replyTo) throws RemoteException {
		Message msg = Message.obtain(null, ServerService.MSG_START_SERVER);
		msg.replyTo = replyTo;
		
		this.send(msg);
		
		Editor edit = Agent.getInstance().getSettings().edit();
		edit.putBoolean("localServerEnabled", true);
		edit.apply();
		
		server.enabled = true;
		server.notifyObservers();
	}
	
	public void stopServer(Server server, Messenger replyTo) throws RemoteException {
		Message msg = Message.obtain(null, ServerService.MSG_STOP_SERVER);
		msg.replyTo = replyTo;
		
		this.send(msg);
		
		Editor edit = Agent.getInstance().getSettings().edit();
		edit.putBoolean("localServerEnabled", false);
		edit.apply();
		
		server.enabled = false;
		server.notifyObservers();
	}
	
	public void unbind(Context context) {
		if(this.bound) {
			context.unbindService(this);
			this.bound = false;
		}
	}
	
}
