package pl.pitsmich.forma;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/**
 * User-initiated foreground notification for an active workout.
 * No background polling: Android's own chronometer counts down rest time.
 */
public final class WorkoutStatusService extends Service {
    public static final String CHANNEL="forma_workout_status_v2";
    private static final int ID=6653;
    private static volatile boolean running;

    @Override public IBinder onBind(Intent intent){return null;}

    public static void start(Context context) {
        Intent i=new Intent(context,WorkoutStatusService.class);
        if(Build.VERSION.SDK_INT>=26)context.startForegroundService(i);
        else context.startService(i);
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context,WorkoutStatusService.class));
    }

    public static void refresh(Context context) {
        if(!running)return;
        NotificationManager manager=(NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager!=null) {
            try {manager.notify(ID,build(context));}
            catch(SecurityException ignored){/* User may have disabled notifications. */}
        }
    }

    private static void channel(Context context) {
        NotificationManager manager=(NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null)return;
        NotificationChannel channel=new NotificationChannel(CHANNEL,
            "FORMA — aktywny trening",NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Cichy status treningu i odliczanie przerwy na ekranie blokady.");
        channel.setSound(null,null);
        channel.enableVibration(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(channel);
    }

    private static Notification build(Context context) {
        long until=RestTimer.endTime(context);
        boolean rest=until>System.currentTimeMillis();
        Intent open=new Intent(context,MainActivity.class);
        open.setAction(RestTimer.ACTION_OPEN_SESSION);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent tap=PendingIntent.getActivity(context,6653,open,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=new Notification.Builder(context,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(rest?"FORMA • Przerwa":"FORMA • Trening trwa")
            .setContentText(rest?"Pozostały czas":"Dotknij, aby wrócić do treningu")
            .setContentIntent(tap)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(rest);
        if(rest) b.setWhen(until).setUsesChronometer(true).setChronometerCountDown(true);
        if(Build.VERSION.SDK_INT>=31)
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        return b.build();
    }

    @Override public void onCreate(){
        super.onCreate();
        running=true;
        channel(this);
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        WorkoutDb db=new WorkoutDb(this);
        WorkoutDb.Session active;
        try {active=db.active();}
        finally {db.close();}
        if(active==null||active.pausedAt>0){
            stopSelf();
            return START_NOT_STICKY;
        }
        try {startForeground(ID,build(this));}
        catch(RuntimeException e){
            running=false;
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override public void onDestroy(){
        running=false;
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}
