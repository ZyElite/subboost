package org.subboost.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import org.subboost.android.core.LocalConfigServer;

/** Keeps the explicitly started LAN HTTP server alive while other apps use it. */
public final class LocalShareService extends Service {
    public static final String ACTION_START = "org.subboost.android.START_LOCAL_SHARE";
    public static final String ACTION_STOP = "org.subboost.android.STOP_LOCAL_SHARE";
    private static final String CHANNEL_ID = "local-config-share";
    private static final int NOTIFICATION_ID = 17890;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            LocalConfigServer.get().stop();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!LocalConfigServer.get().isRunning()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        createChannel();
        startForeground(NOTIFICATION_ID, notification());
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        LocalConfigServer.get().stop();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "局域网配置链接", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("保持 SubBoost 的局域网 config.yaml 链接可用");
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    private Notification notification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openIntent = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, LocalShareService.class).setAction(ACTION_STOP);
        PendingIntent stopIntent = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("SubBoost 局域网链接运行中")
                .setContentText("其他设备可获取当前 config.yaml")
                .setContentIntent(openIntent)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                        "停止", stopIntent).build())
                .build();
    }
}
