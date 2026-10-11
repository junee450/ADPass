package io.adpass.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

final class Notice {
    private static final int ID = 100;
    static void show(Context context) {
        if (!AppState.enabled(context) || !AppState.accessible(context)) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("running", "运行状态", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent pause = PendingIntent.getBroadcast(context, 0, new Intent(context, PauseReceiver.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(context, "running").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("ADSkip 已开启").setContentText("仅在红果前台识别广告 · 可随时暂停")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(new Notification.Action.Builder(null, "暂停", pause).build()).build();
        try { manager.notify(ID, n); } catch (SecurityException ignored) { }
    }
    static void cancel(Context c) { c.getSystemService(NotificationManager.class).cancel(ID); }
}
