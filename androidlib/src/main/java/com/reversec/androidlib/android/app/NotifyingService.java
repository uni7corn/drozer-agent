package com.reversec.androidlib.android.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.os.Build;
import androidx.core.app.NotificationCompat;

public abstract class NotifyingService extends Service {

	private static final String SESSION_CHANNEL_ID = "drozer_session";

	private NotificationManager notification_manager = null;

	protected void hideNotification(String tag, int view_id) {
		this.notification_manager.cancel(tag, view_id);
	}

	@Override
	public void onCreate() {
		this.notification_manager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
	}

	protected void showNotification(String tag, int view_id, int icon_id,
			PendingIntent intent, CharSequence contentText) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel ch = new NotificationChannel(
				SESSION_CHANNEL_ID, "drozer Sessions", NotificationManager.IMPORTANCE_DEFAULT);
			notification_manager.createNotificationChannel(ch);
		}

		Notification notification = new NotificationCompat.Builder(this, SESSION_CHANNEL_ID)
			.setSmallIcon(icon_id)
			.setContentTitle(tag)
			.setContentText(contentText)
			.setContentIntent(intent)
			.setOngoing(true)
			.build();

		this.notification_manager.notify(tag, view_id, notification);
	}

}
