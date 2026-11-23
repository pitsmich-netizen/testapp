package pl.pitsmich.forma;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/**
 * A NORMAL notification, not the foreground-service notification.
 * Experiment for One UI 9 lock screens which group FGS/background activity status.
 * Android itself renders the countdown; no per-second background polling.
 */
public final class RestCountdownNotification {
    private static final String CHANNEL="forma_rest_live_v1";
    private static final int ID=6654;
    private RestCountdownNotification(){}

    public static void show(Context context,long end){
        if(end<=System.currentTimeMillis()||!RestTimer.canNotify(context))return;
        NotificationManager manager=(NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null)return;
        NotificationChannel channel=new NotificationChannel(CHANNEL,
            "FORMA — odliczanie przerwy",NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Widoczne odliczanie przerwy między seriami, bez dodatkowych przycisków.");
        channel.setSound(null,null);
        channel.enableVibration(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(channel);

        Intent intent=new Intent(context,MainActivity.class);
        intent.setAction(RestTimer.ACTION_OPEN_SESSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open=PendingIntent.getActivity(context,6654,intent,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(context,CHANNEL)
            .setSmallIcon(R.drawable.ic_forma_notification)
            .setContentTitle("FORMA • Przerwa")
            .setContentText("Pozostały czas do następnej serii")
            .setWhen(end)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setContentIntent(open)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setTimeoutAfter(Math.max(1L,end-System.currentTimeMillis())+3000L)
            .build();
        try {manager.notify(ID,notification);}
        catch(SecurityException ignored){/* Notification access may be revoked. */}
    }

    public static void cancel(Context context){
        NotificationManager manager=(NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager!=null)manager.cancel(ID);
    }
}
