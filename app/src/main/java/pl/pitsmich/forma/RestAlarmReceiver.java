package pl.pitsmich.forma;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Receives rest alarms even when the app screen is no longer open. */
public final class RestAlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        if(intent!=null && "pl.pitsmich.forma.REST_FINISH".equals(intent.getAction()))
            RestTimer.finishIfExpired(context.getApplicationContext());
    }
}
