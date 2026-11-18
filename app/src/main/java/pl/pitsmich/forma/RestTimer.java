package pl.pitsmich.forma;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

public final class RestTimer {
    private static final String PREFS = "forma_timer";
    private static final String KEY_END = "restUntil"; // Keep compatible with 0.5.1.
    private static final String CHANNEL_ID = "forma_rest_finished_v1";
    private static final int ALARM_CODE = 6651;
    private static final int NOTIFICATION_ID = 6652;
    private RestTimer() { }

    public static long endTime(Context context) {
        return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getLong(KEY_END,0);
    }
    public static long secondsRemaining(Context context) {
        long end=endTime(context);
        return end<=0?0:Math.max(0,(end-System.currentTimeMillis()+999)/1000);
    }
    public static boolean canScheduleExact(Context context) {
        AlarmManager manager=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        return manager!=null && (Build.VERSION.SDK_INT<31 || manager.canScheduleExactAlarms());
    }
    public static boolean canNotify(Context context) {
        return Build.VERSION.SDK_INT<33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;
    }

    private static PendingIntent alarm(Context context) {
        Intent intent=new Intent(context,RestAlarmReceiver.class);
        intent.setAction("pl.pitsmich.forma.REST_FINISH");
        return PendingIntent.getBroadcast(context,ALARM_CODE,intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void start(Context context,int seconds) {
        if(seconds<1 || seconds>3600)throw new IllegalArgumentException("Invalid rest duration");
        stop(context);
        long end=System.currentTimeMillis()+seconds*1000L;
        SharedPreferences preferences=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        preferences.edit().putLong(KEY_END,end).commit();
        schedule(context,end);
    }

    public static void reschedule(Context context) {
        long end=endTime(context);
        if(end==0)return;
        if(end<=System.currentTimeMillis())finishIfExpired(context);
        else schedule(context,end);
    }

    private static void schedule(Context context,long end) {
        AlarmManager manager=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if(manager==null)return;
        PendingIntent action=alarm(context);
        // Exact while permitted; otherwise inexact background alarm to avoid
        // requiring the user to grant special access just to use the timer.
        try {
            if(canScheduleExact(context))manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,end,action);
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,end,action);
        } catch(SecurityException denied) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,end,action);
        }
    }

    public static void stop(Context context) {
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().remove(KEY_END).commit();
        AlarmManager manager=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if(manager!=null)manager.cancel(alarm(context));
        NotificationManager notifications=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(notifications!=null)notifications.cancel(NOTIFICATION_ID);
    }

    // Idempotent: both the visible ticker and the alarm receiver can call this.
    public static void finishIfExpired(Context context) {
        synchronized(RestTimer.class) {
            long end=endTime(context);
            if(end==0 || System.currentTimeMillis()<end)return;
            context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().remove(KEY_END).commit();
            if(canNotify(context))showFinished(context);
        }
    }

    private static void showFinished(Context context) {
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null)return;
        NotificationChannel channel=new NotificationChannel(CHANNEL_ID,"FORMA — koniec przerwy",
            NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Powiadomienie i wibracja po zakończeniu przerwy między seriami");
        channel.enableVibration(true);
        manager.createNotificationChannel(channel);
        Intent activity=new Intent(context,MainActivity.class);
        activity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open=PendingIntent.getActivity(context,ALARM_CODE,activity,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(context,CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("FORMA • Przerwa zakończona")
            .setContentText("Możesz rozpocząć następną serię.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .build();
        manager.notify(NOTIFICATION_ID,notification);
    }
}
