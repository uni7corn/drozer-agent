package com.reversec.dz.receivers;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

public class StartServiceReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		Intent start_service = new Intent();
		start_service.putExtras(intent);
		
		if(intent.getCategories().contains("com.reversec.dz.START_EMBEDDED")) {
			start_service.addCategory("com.reversec.dz.START_EMBEDDED");
			start_service.setComponent(new ComponentName(context.getPackageName(), "com.reversec.dz.services.ServerService"));
		}
		else {
			if(intent.getCategories().contains("com.reversec.dz.CREATE_ENDPOINT"))
				start_service.addCategory("com.reversec.dz.CREATE_ENDPOINT");
			if(intent.getCategories().contains("com.reversec.dz.START_ENDPOINT"))
				start_service.addCategory("com.reversec.dz.START_ENDPOINT");
			
			start_service.setComponent(new ComponentName(context.getPackageName(), "com.reversec.dz.services.ClientService"));
		}

		context.startService(start_service);
	}

}
