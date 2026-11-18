package pl.pitsmich.forma;

import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.UUID;

/** Complete offline workout backup. Never exports Supabase tokens or Android signing secrets. */
public final class WorkoutBackup {
    private static final String FORMAT="forma-workout-backup";
    private WorkoutBackup(){}

    public static JSONObject exportData(WorkoutDb db,SharedPreferences settings) throws Exception {
        JSONObject root=new JSONObject();
        root.put("format",FORMAT);
        root.put("schema",1);
        root.put("exported_at",System.currentTimeMillis());
        JSONObject options=new JSONObject();
        options.put("auto_next",settings.getBoolean("auto_next",true));
        options.put("timer_sound",settings.getBoolean("timer_sound",true));
        root.put("settings",options);
        SQLiteDatabase sql=db.getReadableDatabase();
        JSONArray sessions=new JSONArray();
        try(Cursor c=sql.rawQuery("SELECT id,cloud_id,workout,started_at,finished_at,notes,"+
                "paused_at,paused_total_ms,audit_resolution FROM sessions ORDER BY started_at",null)) {
            while(c.moveToNext()) {
                JSONObject session=new JSONObject();
                long id=c.getLong(0);
                session.put("cloud_id",c.isNull(1)?UUID.randomUUID().toString():c.getString(1));
                session.put("workout",c.getInt(2));
                session.put("started_at",c.getLong(3));
                session.put("finished_at",c.isNull(4)?JSONObject.NULL:c.getLong(4));
                session.put("notes",c.getString(5));
                session.put("paused_total_ms",c.getLong(7));
                session.put("audit_resolution",c.getString(8));
                JSONArray sets=new JSONArray();
                try(Cursor entries=sql.rawQuery(
                    "SELECT exercise_id,set_index,weight,reps,rir,note,warmup FROM exercise_sets "+
                    "WHERE session_id=? ORDER BY rowid",new String[]{Long.toString(id)})) {
                    while(entries.moveToNext()) {
                        JSONObject entry=new JSONObject();
                        entry.put("exercise_id",entries.getString(0));
                        entry.put("index",entries.getInt(1));
                        entry.put("weight",entries.getDouble(2));
                        entry.put("reps",entries.getInt(3));
                        entry.put("rir",entries.getInt(4));
                        entry.put("note",entries.getString(5));
                        entry.put("warmup",entries.getInt(6)!=0);
                        sets.put(entry);
                    }
                }
                session.put("sets",sets);
                sessions.put(session);
            }
        }
        root.put("sessions",sessions);

        JSONArray plans=new JSONArray();
        for(int workout=0;workout<3;workout++) {
            JSONObject day=new JSONObject();
            day.put("workout",workout);
            day.put("custom",db.customPlan(workout));
            JSONArray list=new JSONArray();
            for(Plan.Exercise e:db.workoutPlan(workout)) {
                JSONObject item=new JSONObject();
                item.put("exercise_id",e.id);
                item.put("sets",e.sets);
                item.put("min_reps",e.minReps);
                item.put("max_reps",e.maxReps);
                item.put("rest_seconds",e.restSeconds);
                list.put(item);
            }
            day.put("items",list);
            plans.put(day);
        }
        root.put("plans",plans);
        JSONArray tombstones=new JSONArray();
        for(String cloudId:db.deletedCloudIds())tombstones.put(cloudId);
        root.put("pending_remote_deletes",tombstones);
        return root;
    }

    public static String preview(JSONObject backup) throws Exception {
        validateHeader(backup);
        JSONArray sessions=backup.getJSONArray("sessions");
        if(sessions.length()>20000)throw new Exception("Zbyt dużo sesji w pliku.");
        int entries=0;
        for(int i=0;i<sessions.length();i++) {
            JSONObject session=sessions.getJSONObject(i);
            entries+=session.getJSONArray("sets").length();
            if(entries>250000)throw new Exception("Zbyt dużo serii w pliku.");
        }
        return "Sesje: "+sessions.length()+"\nSerie: "+entries+
            "\nPlany treningowe: "+backup.optJSONArray("plans").length()+
            "\n\nImport połączy brakujące treningi z historią na telefonie. "+
            "Istniejące sesje o tych samych identyfikatorach zostaną pominięte. "+
            "Plany i preferencje aplikacji zostaną przywrócone z kopii. "+
            "Nie usuwa to istniejących treningów.";
    }

    private static void validateHeader(JSONObject backup) throws Exception {
        if(!FORMAT.equals(backup.optString("format")) || backup.optInt("schema",-1)!=1)
            throw new Exception("Plik nie jest obsługiwaną kopią FORMA.");
        if(backup.optJSONArray("sessions")==null || backup.optJSONArray("plans")==null)
            throw new Exception("Kopia nie zawiera pełnych danych treningowych.");
    }

    public static String restore(WorkoutDb db,SharedPreferences prefs,JSONObject backup) throws Exception {
        validateHeader(backup);
        JSONArray sessions=backup.getJSONArray("sessions");
        JSONArray plans=backup.getJSONArray("plans");
        if(sessions.length()>20000 || plans.length()>3)throw new Exception("Kopia jest zbyt duża.");
        SQLiteDatabase sql=db.getWritableDatabase();
        int added=0,skipped=0;
        boolean hasActive=db.active()!=null;
        sql.beginTransaction();
        try {
            for(int i=0;i<sessions.length();i++) {
                JSONObject src=sessions.getJSONObject(i);
                String cloudId=src.getString("cloud_id");
                if(cloudId.length()<8||cloudId.length()>100)throw new Exception("Błędny identyfikator sesji");
                if(db.hasCloudId(cloudId)) {skipped++;continue;}
                int workout=src.getInt("workout");
                long start=src.getLong("started_at");
                if(workout<0||workout>2||start<=0)throw new Exception("Błędne dane sesji");
                boolean active=src.isNull("finished_at");
                if(active&&hasActive) {skipped++;continue;}
                ContentValues row=new ContentValues();
                row.put("cloud_id",cloudId);row.put("workout",workout);
                row.put("started_at",start);
                if(!active) {
                    long end=src.getLong("finished_at");
                    if(end<start)throw new Exception("Błędny czas zakończenia treningu");
                    row.put("finished_at",end);
                } else {
                    // Restored unfinished sessions are paused until user explicitly resumes.
                    row.put("paused_at",System.currentTimeMillis());
                    hasActive=true;
                }
                String notes=src.optString("notes","");
                if(notes.length()>1000)throw new Exception("Zbyt długa notatka sesji");
                row.put("notes",notes);
                long pause=src.optLong("paused_total_ms",0);
                if(pause<0)throw new Exception("Błędny czas przerwy");
                row.put("paused_total_ms",pause);
                String audit=src.optString("audit_resolution","pending");
                if(!audit.equals("pending")&&!audit.equals("confirmed")&&!audit.equals("dismissed"))
                    throw new Exception("Błędny status audytu");
                row.put("audit_resolution",audit);
                long sessionId=sql.insertOrThrow("sessions",null,row);

                JSONArray entries=src.getJSONArray("sets");
                if(entries.length()>500)throw new Exception("Za dużo serii w jednej sesji");
                HashSet<String> seen=new HashSet<>();
                for(int j=0;j<entries.length();j++) {
                    JSONObject original=entries.getJSONObject(j);
                    String ex=original.getString("exercise_id");
                    int index=original.getInt("index");
                    double kg=original.getDouble("weight");
                    int reps=original.getInt("reps");
                    int rir=original.optInt("rir",-1);
                    String note=original.optString("note","");
                    boolean warmup=original.optBoolean("warmup",false);
                    if(Plan.byId(ex)==null || index<0||index>100 || !seen.add(ex+"#"+index) ||
                       !Double.isFinite(kg)||kg<0||kg>2000||reps<1||reps>1000||
                       rir < -1||rir>4||note.length()>500)
                        throw new Exception("Nieprawidłowa seria w kopii");
                    ContentValues entry=new ContentValues();
                    entry.put("session_id",sessionId);entry.put("exercise_id",ex);
                    entry.put("set_index",index);entry.put("weight",kg);
                    entry.put("reps",reps);entry.put("rir",rir);
                    entry.put("note",note);entry.put("warmup",warmup?1:0);
                    sql.insertOrThrow("exercise_sets",null,entry);
                }
                added++;
            }

            HashSet<Integer> seenWorkouts=new HashSet<>();
            for(int i=0;i<plans.length();i++) {
                JSONObject day=plans.getJSONObject(i);
                int workout=day.getInt("workout");
                if(workout<0||workout>2||!seenWorkouts.add(workout))
                    throw new Exception("Błędny dzień treningowy w kopii");
                JSONArray items=day.getJSONArray("items");
                if(items.length()==0||items.length()>18)throw new Exception("Błędny plan");
                HashSet<String> seenIds=new HashSet<>();
                java.util.ArrayList<ContentValues> toSave=new java.util.ArrayList<>();
                for(int pos=0;pos<items.length();pos++) {
                    JSONObject item=items.getJSONObject(pos);
                    String id=item.getString("exercise_id");
                    int sets=item.getInt("sets"),min=item.getInt("min_reps"),
                        max=item.getInt("max_reps"),rest=item.getInt("rest_seconds");
                    if(Plan.byId(id)==null||!seenIds.add(id)||sets<1||sets>12||
                        min<1||max<min||max>40||rest<15||rest>600)
                        throw new Exception("Nieprawidłowe ćwiczenie w planie");
                    ContentValues row=new ContentValues();
                    row.put("workout",workout);row.put("position",pos);
                    row.put("exercise_id",id);row.put("sets",sets);
                    row.put("min_reps",min);row.put("max_reps",max);row.put("rest_seconds",rest);
                    toSave.add(row);
                }
                sql.delete("plan_items","workout=?",new String[]{Integer.toString(workout)});
                if(day.optBoolean("custom",false))
                    for(ContentValues item:toSave)sql.insertOrThrow("plan_items",null,item);
            }

            JSONArray deleted=backup.optJSONArray("pending_remote_deletes");
            if(deleted!=null) for(int i=0;i<deleted.length();i++) {
                String id=deleted.getString(i);
                if(id.length()<8||id.length()>100)throw new Exception("Błędny identyfikator usunięcia");
                ContentValues row=new ContentValues();row.put("cloud_id",id);
                sql.insertWithOnConflict("pending_remote_deletes",null,row,SQLiteDatabase.CONFLICT_IGNORE);
            }
            sql.setTransactionSuccessful();
        } finally {sql.endTransaction();}

        JSONObject settings=backup.optJSONObject("settings");
        if(settings!=null)
            prefs.edit()
                .putBoolean("auto_next",settings.optBoolean("auto_next",true))
                .putBoolean("timer_sound",settings.optBoolean("timer_sound",true))
                .apply();
        return "Przywrócono "+added+" sesji. Pominięto "+skipped+
            " już istniejących lub konfliktujących. Plany odtworzone.";
    }
}
