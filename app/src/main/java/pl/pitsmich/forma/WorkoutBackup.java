package pl.pitsmich.forma;

import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;
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
        root.put("schema",2);
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

        JSONArray custom=new JSONArray();
        try(Cursor c=sql.rawQuery("SELECT id,name,description,region,equipment,sets,min_reps,"+
            "max_reps,rest_seconds,each_side FROM personal_exercises ORDER BY name",null)) {
            while(c.moveToNext()){
                JSONObject item=new JSONObject();
                item.put("id",c.getString(0));item.put("name",c.getString(1));
                item.put("description",c.getString(2));item.put("region",c.getString(3));
                item.put("equipment",c.getString(4));item.put("sets",c.getInt(5));
                item.put("min_reps",c.getInt(6));item.put("max_reps",c.getInt(7));
                item.put("rest_seconds",c.getInt(8));item.put("each_side",c.getInt(9)!=0);
                custom.put(item);
            }
        }
        root.put("personal_exercises",custom);
        JSONArray loads=new JSONArray();
        try(Cursor c=sql.rawQuery("SELECT exercise_id,step_kg,multiplier FROM load_settings",null)){
            while(c.moveToNext()){
                JSONObject item=new JSONObject();
                item.put("exercise_id",c.getString(0));
                item.put("step_kg",c.getDouble(1));item.put("multiplier",c.getInt(2));
                loads.put(item);
            }
        }
        root.put("load_settings",loads);
        JSONArray schedule=new JSONArray();
        for(WorkoutDb.ScheduleDay day:db.schedule()){
            JSONObject item=new JSONObject();
            item.put("workout",day.workout);item.put("position",day.position);
            item.put("weekday",day.weekday);item.put("title",day.title);
            schedule.put(item);
        }
        root.put("workout_schedule",schedule);
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
            "\nWłasne ćwiczenia: "+(backup.optJSONArray("personal_exercises")==null?0:backup.optJSONArray("personal_exercises").length())+
            "\n\nImport połączy brakujące treningi z historią na telefonie. "+
            "Istniejące sesje zostaną pominięte, poza początkowym importem 18.11, który zostanie zastąpiony Twoją kopią. "+
            "Plany i preferencje aplikacji zostaną przywrócone z kopii. "+
            "Nie usuwa to istniejących treningów.";
    }

    private static void validateHeader(JSONObject backup) throws Exception {
        int version=backup.optInt("schema",-1);
        if(!FORMAT.equals(backup.optString("format")) || (version!=1&&version!=2))
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
            // Restore exercise definitions BEFORE referencing them in historical sets.
            JSONArray personal=backup.optJSONArray("personal_exercises");
            if(personal!=null){
                if(personal.length()>500)throw new Exception("Zbyt wiele ćwiczeń w kopii");
                for(int i=0;i<personal.length();i++){
                    JSONObject src=personal.getJSONObject(i);
                    String id=src.getString("id"),name=src.getString("name");
                    String description=src.optString("description","");
                    String region=src.optString("region","");
                    String equipment=src.optString("equipment","");
                    int count=src.getInt("sets"),min=src.getInt("min_reps"),
                        max=src.getInt("max_reps"),rest=src.getInt("rest_seconds");
                    if(!id.startsWith("custom_")||id.length()>90||name.isEmpty()||
                        name.length()>70||description.length()>500||region.length()>70||
                        equipment.length()>70||count<1||count>12||min<1||max<min||max>40||
                        rest<15||rest>600)throw new Exception("Nieprawidłowe własne ćwiczenie");
                    ContentValues row=new ContentValues();
                    row.put("id",id);row.put("name",name);row.put("description",description);
                    row.put("region",region);row.put("equipment",equipment);
                    row.put("sets",count);row.put("min_reps",min);row.put("max_reps",max);
                    row.put("rest_seconds",rest);row.put("each_side",src.optBoolean("each_side",false)?1:0);
                    sql.insertWithOnConflict("personal_exercises",null,row,SQLiteDatabase.CONFLICT_REPLACE);
                }
            }
            JSONArray loadSettings=backup.optJSONArray("load_settings");
            if(loadSettings!=null){
                if(loadSettings.length()>700)throw new Exception("Za dużo ustawień ciężaru");
                for(int i=0;i<loadSettings.length();i++){
                    JSONObject source=loadSettings.getJSONObject(i);
                    String exercise=source.getString("exercise_id");
                    double step=source.getDouble("step_kg");
                    int multiplier=source.getInt("multiplier");
                    if(db.exerciseById(exercise)==null||!Double.isFinite(step)||
                        step<0.25||step>50||multiplier<1||multiplier>2)
                        throw new Exception("Błędny przelicznik ciężaru");
                    ContentValues row=new ContentValues();
                    row.put("exercise_id",exercise);row.put("step_kg",step);
                    row.put("multiplier",multiplier);
                    sql.insertWithOnConflict("load_settings",null,row,SQLiteDatabase.CONFLICT_REPLACE);
                }
            }
            JSONArray schedule=backup.optJSONArray("workout_schedule");
            if(schedule!=null){
                if(schedule.length()!=3)throw new Exception("Błędny harmonogram treningów");
                HashSet<Integer> usedWorkouts=new HashSet<>(),usedWeekdays=new HashSet<>(),
                    usedPositions=new HashSet<>();
                java.util.ArrayList<ContentValues> rows=new java.util.ArrayList<>();
                for(int i=0;i<schedule.length();i++){
                    JSONObject item=schedule.getJSONObject(i);
                    int workout=item.getInt("workout"),position=item.getInt("position"),
                        weekday=item.getInt("weekday");
                    String title=item.getString("title");
                    if(workout<0||workout>2||position<0||position>2||
                        weekday<1||weekday>7||title.trim().isEmpty()||title.length()>32||
                        !usedWorkouts.add(workout)||!usedWeekdays.add(weekday)||
                        !usedPositions.add(position))throw new Exception("Błędna kolejność dni");
                    ContentValues row=new ContentValues();
                    row.put("workout",workout);row.put("position",position);
                    row.put("weekday",weekday);row.put("title",title);
                    rows.add(row);
                }
                sql.delete("workout_schedule",null,null);
                for(ContentValues row:rows)sql.insertOrThrow("workout_schedule",null,row);
            }
            for(int i=0;i<sessions.length();i++) {
                JSONObject src=sessions.getJSONObject(i);
                String cloudId=src.getString("cloud_id");
                if(cloudId.length()<8||cloudId.length()>100)throw new Exception("Błędny identyfikator sesji");
                long existingId=-1;
                try(Cursor existing=sql.rawQuery(
                    "SELECT id FROM sessions WHERE cloud_id=?",new String[]{cloudId})) {
                    if(existing.moveToFirst())existingId=existing.getLong(0);
                }
                // Fresh installs include a seeded 18.11 demo session. A user's
                // edited copy (e.g. 24 sets) must REPLACE that seed on restore.
                boolean replaceSeed=existingId>0 && "forma-import-20261118".equals(cloudId);
                if(existingId>0 && !replaceSeed) {skipped++;continue;}
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
                long sessionId;
                if(replaceSeed) {
                    sql.update("sessions",row,"id=?",new String[]{Long.toString(existingId)});
                    sql.delete("exercise_sets","session_id=?",new String[]{Long.toString(existingId)});
                    sessionId=existingId;
                } else {
                    sessionId=sql.insertOrThrow("sessions",null,row);
                }

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
                    if(db.exerciseById(ex)==null || index<0||index>100 || !seen.add(ex+"#"+index) ||
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
                    if(db.exerciseById(id)==null||!seenIds.add(id)||sets<1||sets>12||
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
