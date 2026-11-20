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
import android.media.AudioAttributes;
import android.media.RingtoneManager;

public final class RestTimer {
    private static final String PREFS = "forma_timer";
    private static final String KEY_END = "restUntil"; // Keep compatible with 0.5.1.
    private static final String CHANNEL_COUNTDOWN = "forma_rest_countdown_v1";
    private static final int COUNTDOWN_ID = 6653;
    public static final String ACTION_OPEN_SESSION = "pl.pitsmich.forma.OPEN_ACTIVE_SESSION";
    private static final String CHANNEL_SOUND = "forma_rest_sound_v2";
    private static final String CHANNEL_SILENT = "forma_rest_vibration_v2";
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
        showCountdown(context,end);
    }

    public static void reschedule(Context context) {
        long end=endTime(context);
        if(end==0)return;
        if(end<=System.currentTimeMillis())finishIfExpired(context);
        else {
            schedule(context,end);
            showCountdown(context,end);
        }
    }

    /** Native Android chronometer: lockscreen seconds tick without per-second app wakeups. */
    private static void showCountdown(Context context,long end) {
        if(!canNotify(context)||end<=System.currentTimeMillis())return;
        NotificationManager manager=(NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null)return;
        NotificationChannel channel=new NotificationChannel(CHANNEL_COUNTDOWN,
            "FORMA — odliczanie przerwy",NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Pozostały czas przerwy w powiadomieniu i na ekranie blokady.");
        channel.enableVibration(false);
        channel.setSound(null,null);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(channel);
        Intent openIntent=new Intent(context,MainActivity.class);
        openIntent.setAction(ACTION_OPEN_SESSION);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open=PendingIntent.getActivity(context,6653,openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(context,CHANNEL_COUNTDOWN)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("FORMA • Przerwa")
            .setContentText("Pozostały czas")
            .setWhen(end)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_STATUS)
            .setContentIntent(open)
            .setTimeoutAfter(Math.max(1,end-System.currentTimeMillis())+2000)
            .build();
        manager.notify(COUNTDOWN_ID,notification);
    }

    private static void clearCountdown(Context context) {
        NotificationManager manager=(NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager!=null)manager.cancel(COUNTDOWN_ID);
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
        if(notifications!=null){
            notifications.cancel(NOTIFICATION_ID);
            notifications.cancel(COUNTDOWN_ID);
        }
    }

    // Idempotent: both the visible ticker and the alarm receiver can call this.
    public static void finishIfExpired(Context context) {
        synchronized(RestTimer.class) {
            long end=endTime(context);
            if(end==0 || System.currentTimeMillis()<end)return;
            context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().remove(KEY_END).commit();
            clearCountdown(context);
            if(canNotify(context))showFinished(context);
        }
    }

    private static void showFinished(Context context) {
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null)return;
        // Android notification channel sound is immutable; distinct IDs let users
        // switch between sound+vibration and vibration only without recreating a channel.
        boolean sound=context.getSharedPreferences("forma_settings",Context.MODE_PRIVATE)
            .getBoolean("timer_sound",true);
        String channelId=sound?CHANNEL_SOUND:CHANNEL_SILENT;
        NotificationChannel channel=new NotificationChannel(channelId,
            sound?"FORMA — dźwięk i wibracja":"FORMA — tylko wibracja",
            NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Koniec przerwy między seriami. Dźwiękiem możesz sterować w ustawieniach FORMA.");
        channel.enableVibration(true);
        if(sound) {
            AudioAttributes attrs=new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
            channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),attrs);
        } else channel.setSound(null,null);
        manager.createNotificationChannel(channel);
        Intent activity=new Intent(context,MainActivity.class);
        activity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open=PendingIntent.getActivity(context,ALARM_CODE,activity,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(context,channelId)
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
