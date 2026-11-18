package pl.pitsmich.forma;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.UUID;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class WorkoutDb extends SQLiteOpenHelper {
    public static final class Entry {
        public final String exerciseId;
        public final int setIndex, reps;
        public final double weight;
        public final int rir; // -1 if not supplied
        public final String note;
        public final boolean warmup;
        Entry(String exerciseId,int setIndex,double weight,int reps) {
            this(exerciseId,setIndex,weight,reps,-1,"",false);
        }
        Entry(String exerciseId,int setIndex,double weight,int reps,int rir,String note,boolean warmup) {
            this.exerciseId=exerciseId;this.setIndex=setIndex;this.weight=weight;this.reps=reps;
            this.rir=rir;this.note=note==null?"":note;this.warmup=warmup;
        }
    }
    public static final class Session {
        public final long id, startedAt, finishedAt;
        public final String notes, cloudId;
        public final long pausedAt, pausedTotalMs;
        public final int workout, sets;
        public final double volume;
        Session(long id, int workout, long startedAt, long finishedAt, int sets, double volume, String notes, String cloudId) {
            this.id=id; this.workout=workout; this.startedAt=startedAt;
            this.finishedAt=finishedAt; this.sets=sets; this.volume=volume;
            this.notes=notes; this.cloudId=cloudId;
            this.pausedAt=0; this.pausedTotalMs=0;
        }
        Session(long id,int workout,long startedAt,long finishedAt,int sets,double volume,String notes,String cloudId,long pausedAt,long pausedTotalMs) {
            this.id=id;this.workout=workout;this.startedAt=startedAt;this.finishedAt=finishedAt;
            this.sets=sets;this.volume=volume;this.notes=notes;this.cloudId=cloudId;
            this.pausedAt=pausedAt;this.pausedTotalMs=pausedTotalMs;
        }
    }
    public static final class Progress {
        public final long date;
        public double maxWeight=0;
        public int repsAtMax=0, totalReps=0, numberOfSets=0;
        Progress(long date) { this.date=date; }
    }

    public WorkoutDb(Context context) { super(context, "forma_workouts.db", null, 7); }
    @Override public void onConfigure(SQLiteDatabase db) { super.onConfigure(db); db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, workout INTEGER NOT NULL, started_at INTEGER NOT NULL, finished_at INTEGER, notes TEXT NOT NULL DEFAULT '', cloud_id TEXT, paused_at INTEGER, paused_total_ms INTEGER NOT NULL DEFAULT 0, audit_resolution TEXT NOT NULL DEFAULT 'pending')");
        db.execSQL("CREATE TABLE exercise_sets (session_id INTEGER NOT NULL, exercise_id TEXT NOT NULL, set_index INTEGER NOT NULL, weight REAL NOT NULL, reps INTEGER NOT NULL, rir INTEGER NOT NULL DEFAULT -1, note TEXT NOT NULL DEFAULT '', warmup INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(session_id,exercise_id,set_index), FOREIGN KEY(session_id) REFERENCES sessions(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX idx_sets_exercise ON exercise_sets(exercise_id)");
        db.execSQL("CREATE INDEX idx_sessions_time ON sessions(started_at)");
        db.execSQL("CREATE UNIQUE INDEX idx_sessions_cloud_id ON sessions(cloud_id)");
        db.execSQL("CREATE TABLE pending_remote_deletes (cloud_id TEXT PRIMARY KEY)");
        createPlanTable(db);
        seedInitialWorkout(db);
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion<2) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN notes TEXT NOT NULL DEFAULT ''");
        }
        if(oldVersion<4) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN cloud_id TEXT");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_sessions_cloud_id ON sessions(cloud_id)");
            db.execSQL("UPDATE sessions SET cloud_id='forma-import-20261118' WHERE notes LIKE 'Import z notatek 18.11.2026%'");
            db.execSQL("UPDATE sessions SET cloud_id=lower(hex(randomblob(16))) WHERE cloud_id IS NULL");
        }
        if(oldVersion<5) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN paused_at INTEGER");
            db.execSQL("ALTER TABLE sessions ADD COLUMN paused_total_ms INTEGER NOT NULL DEFAULT 0");
            db.execSQL("CREATE TABLE IF NOT EXISTS pending_remote_deletes (cloud_id TEXT PRIMARY KEY)");
        }
        if(oldVersion<6) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN audit_resolution TEXT NOT NULL DEFAULT 'pending'");
            createPlanTable(db);
        }
        if(oldVersion<7) {
            db.execSQL("ALTER TABLE exercise_sets ADD COLUMN rir INTEGER NOT NULL DEFAULT -1");
            db.execSQL("ALTER TABLE exercise_sets ADD COLUMN note TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE exercise_sets ADD COLUMN warmup INTEGER NOT NULL DEFAULT 0");
        }
        if(oldVersion<2) seedInitialWorkout(db);
        if(oldVersion<3) seedAdditionalNovemberSets(db);
    }
    private static void createPlanTable(SQLiteDatabase sql) {
        sql.execSQL("CREATE TABLE IF NOT EXISTS plan_items ("+
            "workout INTEGER NOT NULL,position INTEGER NOT NULL,exercise_id TEXT NOT NULL,"+
            "sets INTEGER NOT NULL,min_reps INTEGER NOT NULL,max_reps INTEGER NOT NULL,"+
            "rest_seconds INTEGER NOT NULL,PRIMARY KEY(workout,position),"+
            "UNIQUE(workout,exercise_id))");
    }

    /** User plan customization; existing completed sessions are never rewritten. */
    public List<Plan.Exercise> workoutPlan(int workout) {
        if(workout<0||workout>2)throw new IllegalArgumentException("Nieznany trening");
        ArrayList<Plan.Exercise> list=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT exercise_id,sets,min_reps,max_reps,rest_seconds FROM plan_items "+
            "WHERE workout=? ORDER BY position",new String[]{Integer.toString(workout)})) {
            while(c.moveToNext()) {
                Plan.Exercise source=Plan.byId(c.getString(0));
                if(source!=null)list.add(new Plan.Exercise(
                    source.id,source.name,source.description,
                    c.getInt(1),c.getInt(2),c.getInt(3),c.getInt(4),source.eachSide));
            }
        }
        return list.isEmpty()?new ArrayList<>(Plan.exercises(workout)):list;
    }

    public void savePlan(int workout,List<Plan.Exercise> list) {
        if(workout<0||workout>2 || list==null||list.isEmpty()||list.size()>18)
            throw new IllegalArgumentException("Nieprawidłowy plan");
        java.util.HashSet<String> ids=new java.util.HashSet<>();
        for(Plan.Exercise e:list) {
            if(Plan.byId(e.id)==null || !ids.add(e.id)||e.sets<1||e.sets>12||
                e.minReps<1||e.maxReps<e.minReps||e.maxReps>40||
                e.restSeconds<15||e.restSeconds>600)
                throw new IllegalArgumentException("Nieprawidłowe parametry ćwiczenia");
        }
        SQLiteDatabase sql=getWritableDatabase();
        sql.beginTransaction();
        try {
            sql.delete("plan_items","workout=?",new String[]{Integer.toString(workout)});
            for(int i=0;i<list.size();i++) {
                Plan.Exercise e=list.get(i);
                ContentValues row=new ContentValues();
                row.put("workout",workout);
                row.put("position",i);
                row.put("exercise_id",e.id);
                row.put("sets",e.sets);
                row.put("min_reps",e.minReps);
                row.put("max_reps",e.maxReps);
                row.put("rest_seconds",e.restSeconds);
                sql.insertOrThrow("plan_items",null,row);
            }
            sql.setTransactionSuccessful();
        } finally {sql.endTransaction();}
    }

    public void resetPlan(int workout) {
        getWritableDatabase().delete("plan_items","workout=?",new String[]{Integer.toString(workout)});
    }

    public boolean customPlan(int workout) {
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT 1 FROM plan_items WHERE workout=? LIMIT 1",
            new String[]{Integer.toString(workout)})) {return c.moveToFirst();}
    }

    public String auditResolution(long sessionId) {
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT audit_resolution FROM sessions WHERE id=?",
            new String[]{Long.toString(sessionId)})) {
            return c.moveToFirst()?c.getString(0):"pending";
        }
    }
    public void setAuditResolution(long sessionId,String status) {
        if(!"pending".equals(status)&&!"confirmed".equals(status)&&!"dismissed".equals(status))
            throw new IllegalArgumentException("Nieprawidłowy wynik audytu");
        ContentValues values=new ContentValues();
        values.put("audit_resolution",status);
        getWritableDatabase().update("sessions",values,
            "id=? AND notes LIKE 'Import z notatek 18.11.2026%'",
            new String[]{Long.toString(sessionId)});
    }

    /** Last completed workout containing this exercise, scoped to the same day type. */
    private long lastExerciseSession(int workout,String exercise) {
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT s.id FROM sessions s JOIN exercise_sets e ON e.session_id=s.id "+
            "WHERE s.workout=? AND e.exercise_id=? AND s.finished_at IS NOT NULL "+
            "GROUP BY s.id ORDER BY s.started_at DESC,s.id DESC LIMIT 1",
            new String[]{Integer.toString(workout),exercise})) {
            return c.moveToFirst()?c.getLong(0):-1L;
        }
    }

    public int expectedSets(int workout,Plan.Exercise exercise) {
        if(customPlan(workout)) return exercise.sets;
        long session=lastExerciseSession(workout,exercise.id);
        if(session<0)return exercise.sets;
        int count=countExerciseSets(session,exercise.id);
        return count>0?Math.min(12,count):exercise.sets;
    }

    public Entry previousWorkout(int workout,String exercise,int index) {
        long session=lastExerciseSession(workout,exercise);
        if(session>=0) {
            Entry exact=get(session,exercise,index);
            if(exact!=null)return exact;
            // For an added set, reuse the last set's load when available.
            try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT set_index,weight,reps,rir,note,warmup FROM exercise_sets WHERE session_id=? AND exercise_id=? "+
                "ORDER BY set_index DESC LIMIT 1",
                new String[]{Long.toString(session),exercise})) {
                if(c.moveToFirst())return new Entry(exercise,c.getInt(0),c.getDouble(1),c.getInt(2),c.getInt(3),c.getString(4),c.getInt(5)!=0);
            }
        }
        return previous(exercise,index);
    }

    private void seedAdditionalNovemberSets(SQLiteDatabase sql) {
        try(Cursor c=sql.rawQuery("SELECT id FROM sessions WHERE notes LIKE 'Import z notatek 18.11.2026%' ORDER BY id LIMIT 1",null)) {
            if(!c.moveToFirst()) return;
            long id=c.getLong(0);
            seedSetsIfAbsent(sql,id,"cable_lateral",5,new int[]{12,12,12});
            seedSetsIfAbsent(sql,id,"facepull",15,new int[]{12,12,12});
        }
    }
    private void seedSetsIfAbsent(SQLiteDatabase sql,long session,String ex,double kg,int[] reps) {
        for(int i=0;i<reps.length;i++) {
            ContentValues row=new ContentValues();
            row.put("session_id",session);
            row.put("exercise_id",ex);
            row.put("set_index",i);
            row.put("weight",kg);
            row.put("reps",reps[i]);
            sql.insertWithOnConflict("exercise_sets",null,row,SQLiteDatabase.CONFLICT_IGNORE);
        }
    }
    private void seedInitialWorkout(SQLiteDatabase sql) {
        // User-reported workout on 2026-11-18, no time provided; noon is a storage placeholder only.
        Calendar date=Calendar.getInstance(TimeZone.getTimeZone("Europe/Warsaw"));
        date.clear();
        date.set(2026, Calendar.NOVEMBER,18,12,0,0);
        long time=date.getTimeInMillis();
        try(Cursor c=sql.rawQuery("SELECT COUNT(*) FROM sessions WHERE notes LIKE 'Import z notatek 18.11.2026%'",null)) {
            if(c.moveToFirst() && c.getInt(0)>0) return;
        }
        ContentValues session=new ContentValues();
        session.put("workout",Plan.UPPER_A);
        session.put("started_at",time);
        session.put("finished_at",time);
        session.put("cloud_id","forma-import-20261118");
        session.put("notes","Import z notatek 18.11.2026. Godzina niepodana. Ćw. 2: 30 kg na maszynie z dwoma linkami (bez przeliczania ciężaru).");
        long id=sql.insertOrThrow("sessions",null,session);
        seedSets(sql,id,"db_flat",22.5,new int[]{12,12,10});
        seedSets(sql,id,"lat_wide",30,new int[]{12,12,12});
        seedSets(sql,id,"row_cable",17.5,new int[]{12,12,12});
        seedSets(sql,id,"butterfly",30,new int[]{12,12,12});
        seedSets(sql,id,"cable_lateral",5,new int[]{12,12,12});
        seedSets(sql,id,"facepull",15,new int[]{12,12,12});
    }
    private void seedSets(SQLiteDatabase sql,long session,String ex,double kg,int[] reps) {
        for(int i=0;i<reps.length;i++) {
            ContentValues row=new ContentValues();
            row.put("session_id",session);
            row.put("exercise_id",ex);
            row.put("set_index",i);
            row.put("weight",kg);
            row.put("reps",reps[i]);
            sql.insertOrThrow("exercise_sets",null,row);
        }
    }

    public long start(int workout) {
        ContentValues v=new ContentValues();
        v.put("workout", workout);
        v.put("started_at", System.currentTimeMillis());
        v.put("cloud_id", UUID.randomUUID().toString());
        return getWritableDatabase().insertOrThrow("sessions", null, v);
    }
    public void finish(long id) {
        resume(id);
        ContentValues v=new ContentValues(); v.put("finished_at", System.currentTimeMillis());
        getWritableDatabase().update("sessions", v, "id=?", new String[]{Long.toString(id)});
    }
    public Session active() {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,workout,started_at,notes,cloud_id,paused_at,paused_total_ms FROM sessions WHERE finished_at IS NULL ORDER BY id DESC LIMIT 1",null)) {
            if(!c.moveToFirst()) return null;
            return new Session(c.getLong(0),c.getInt(1),c.getLong(2),0,countSets(c.getLong(0)),0,c.getString(3),c.getString(4),c.isNull(5)?0:c.getLong(5),c.getLong(6));
        }
    }
    public Session byId(long id) {
        String q="SELECT s.id,s.workout,s.started_at,s.finished_at,COUNT(e.set_index),COALESCE(SUM(CASE WHEN e.warmup=0 THEN e.weight*e.reps ELSE 0 END),0),s.notes,s.cloud_id,s.paused_at,s.paused_total_ms "+
            "FROM sessions s LEFT JOIN exercise_sets e ON e.session_id=s.id WHERE s.id=? GROUP BY s.id";
        try(Cursor c=getReadableDatabase().rawQuery(q,new String[]{Long.toString(id)})) {
            if(!c.moveToFirst()) return null;
            return new Session(c.getLong(0),c.getInt(1),c.getLong(2),c.isNull(3)?0:c.getLong(3),
                c.getInt(4),c.getDouble(5),c.getString(6),c.getString(7),c.isNull(8)?0:c.getLong(8),c.getLong(9));
        }
    }
    public long durationMillis(Session session) {
        if(session==null) return 0;
        long end=session.finishedAt>0?session.finishedAt:System.currentTimeMillis();
        long currentPause=session.pausedAt>0?Math.max(0,end-session.pausedAt):0;
        return Math.max(0,end-session.startedAt-session.pausedTotalMs-currentPause);
    }
    public void pause(long id) {
        ContentValues v=new ContentValues();
        v.put("paused_at",System.currentTimeMillis());
        getWritableDatabase().update("sessions",v,"id=? AND finished_at IS NULL AND paused_at IS NULL",
            new String[]{Long.toString(id)});
    }
    public void resume(long id) {
        SQLiteDatabase sql=getWritableDatabase();
        sql.beginTransaction();
        try {
            long paused=0;
            try(Cursor c=sql.rawQuery("SELECT paused_at FROM sessions WHERE id=? AND finished_at IS NULL",
                new String[]{Long.toString(id)})) {
                if(c.moveToFirst()&&!c.isNull(0)) paused=c.getLong(0);
            }
            if(paused>0) {
                ContentValues v=new ContentValues();
                v.putNull("paused_at");
                sql.execSQL("UPDATE sessions SET paused_total_ms=paused_total_ms+? WHERE id=?",
                    new Object[]{Math.max(0,System.currentTimeMillis()-paused),id});
                sql.update("sessions",v,"id=?",new String[]{Long.toString(id)});
            }
            sql.setTransactionSuccessful();
        } finally {sql.endTransaction();}
    }
    public void deleteSession(long id) {
        SQLiteDatabase sql=getWritableDatabase();
        sql.beginTransaction();
        try {
            try(Cursor c=sql.rawQuery("SELECT cloud_id FROM sessions WHERE id=?",
                    new String[]{Long.toString(id)})) {
                if(c.moveToFirst() && !c.isNull(0)) {
                    ContentValues tombstone=new ContentValues();
                    tombstone.put("cloud_id",c.getString(0));
                    sql.insertWithOnConflict("pending_remote_deletes",null,tombstone,SQLiteDatabase.CONFLICT_IGNORE);
                }
            }
            sql.delete("sessions","id=?",new String[]{Long.toString(id)});
            sql.setTransactionSuccessful();
        } finally {sql.endTransaction();}
    }
    public List<String> deletedCloudIds() {
        ArrayList<String> ids=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT cloud_id FROM pending_remote_deletes",null)) {
            while(c.moveToNext()) ids.add(c.getString(0));
        }
        return ids;
    }
    public void markCloudDeletionDone(String cloudId) {
        getWritableDatabase().delete("pending_remote_deletes","cloud_id=?",new String[]{cloudId});
    }
    public boolean hasCloudId(String cloudId) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM sessions WHERE cloud_id=? LIMIT 1",
                new String[]{cloudId})) {
            return c.moveToFirst();
        }
    }
    public void updateCompleted(long id,long startedAt,String notes) {
        Session s=byId(id);
        if(s==null||s.finishedAt<=0)throw new IllegalArgumentException("Nie znaleziono zakończonego treningu");
        ContentValues v=new ContentValues();
        v.put("started_at",startedAt);
        v.put("finished_at",Math.max(startedAt,s.finishedAt+(startedAt-s.startedAt)));
        v.put("notes",notes==null?"":notes.substring(0,Math.min(1000,notes.length())));
        getWritableDatabase().update("sessions",v,"id=? AND finished_at IS NOT NULL",
            new String[]{Long.toString(id)});
    }

    public void save(long session,String exercise,int index,double weight,int reps) {
        Entry existing=get(session,exercise,index);
        save(session,exercise,index,weight,reps,
            existing==null?-1:existing.rir,
            existing==null?"":existing.note,
            existing!=null&&existing.warmup);
    }
    public void save(long session,String exercise,int index,double weight,int reps,int rir,String note,boolean warmup) {
        if(index<0||index>100 || weight<0||weight>2000 || !Double.isFinite(weight) ||
            reps<1||reps>1000 || rir < -1||rir >4||note==null||note.length()>500)
            throw new IllegalArgumentException("Nieprawidłowe dane serii");
        ContentValues v=new ContentValues();
        v.put("session_id",session);v.put("exercise_id",exercise);
        v.put("set_index",index);v.put("weight",weight);v.put("reps",reps);
        v.put("rir",rir);v.put("note",note);v.put("warmup",warmup?1:0);
        getWritableDatabase().insertWithOnConflict("exercise_sets",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        invalidateAuditAfterSetChange(session);
    }
    private void invalidateAuditAfterSetChange(long session) {
        getWritableDatabase().execSQL(
            "UPDATE sessions SET audit_resolution='pending' WHERE id=? "+
            "AND notes LIKE 'Import z notatek 18.11.2026%'",
            new Object[]{session});
    }
    public void delete(long session, String exercise, int index) {
        getWritableDatabase().delete("exercise_sets","session_id=? AND exercise_id=? AND set_index=?",
                new String[]{Long.toString(session),exercise,Integer.toString(index)});
        invalidateAuditAfterSetChange(session);
    }
    public Entry get(long session,String exercise,int index) {
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT weight,reps,rir,note,warmup FROM exercise_sets WHERE session_id=? AND exercise_id=? AND set_index=?",
                new String[]{Long.toString(session),exercise,Integer.toString(index)})) {
            if(!c.moveToFirst()) return null;
            return new Entry(exercise,index,c.getDouble(0),c.getInt(1),c.getInt(2),c.getString(3),c.getInt(4)!=0);
        }
    }
    public Entry previous(String exercise,int index) {
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT e.weight,e.reps,e.rir,e.note,e.warmup FROM exercise_sets e JOIN sessions s ON s.id=e.session_id " +
                "WHERE e.exercise_id=? AND e.set_index=? AND e.warmup=0 AND s.finished_at IS NOT NULL ORDER BY s.started_at DESC LIMIT 1",
                new String[]{exercise,Integer.toString(index)})) {
            if(!c.moveToFirst()) return null;
            return new Entry(exercise,index,c.getDouble(0),c.getInt(1));
        }
    }
    /** Only suggest progression after ALL planned working sets hit the rep ceiling at one weight. */
    public String progressionTip(Plan.Exercise exercise) {
        String q="SELECT COUNT(*),MIN(e.reps),MIN(e.weight),MAX(e.weight) " +
            "FROM exercise_sets e JOIN sessions s ON s.id=e.session_id " +
            "WHERE e.exercise_id=? AND e.warmup=0 AND e.set_index<? AND s.id=(" +
            "SELECT x.id FROM sessions x JOIN exercise_sets y ON y.session_id=x.id " +
            "WHERE y.exercise_id=? AND x.finished_at IS NOT NULL " +
            "ORDER BY x.started_at DESC,x.id DESC LIMIT 1)";
        try(Cursor c=getReadableDatabase().rawQuery(q,new String[]{
                exercise.id,Integer.toString(exercise.sets),exercise.id})) {
            if(!c.moveToFirst() || c.getInt(0)<exercise.sets ||
                c.isNull(2) || c.getInt(1)<exercise.maxReps ||
                Math.abs(c.getDouble(2)-c.getDouble(3))>0.001) return null;
            double weight=c.getDouble(2);
            return "Ostatnio komplet serii po "+exercise.maxReps+" powt. przy "+
                String.format(Locale.forLanguageTag("pl-PL"),"%.1f",weight)+
                " kg. Jeśli technika jest stabilna, spróbuj niewielkiego zwiększenia ciężaru.";
        }
    }

    public int countExerciseSets(long session,String exercise) {
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM exercise_sets WHERE session_id=? AND exercise_id=? AND warmup=0",
                new String[]{Long.toString(session),exercise})) {
            return c.moveToFirst()?c.getInt(0):0;
        }
    }

    public int countSets(long session) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM exercise_sets WHERE session_id=?",
                new String[]{Long.toString(session)})) {
            return c.moveToFirst()?c.getInt(0):0;
        }
    }
    public List<Entry> sets(long session) {
        ArrayList<Entry> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT exercise_id,set_index,weight,reps,rir,note,warmup FROM exercise_sets WHERE session_id=? ORDER BY rowid",
                new String[]{Long.toString(session)})) {
            while(c.moveToNext()) result.add(new Entry(c.getString(0),c.getInt(1),c.getDouble(2),c.getInt(3),c.getInt(4),c.getString(5),c.getInt(6)!=0));
        }
        return result;
    }
    public List<Session> completed() {
        ArrayList<Session> result=new ArrayList<>();
        String q="SELECT s.id,s.workout,s.started_at,s.finished_at,COUNT(e.set_index),COALESCE(SUM(e.weight*e.reps),0),s.notes,s.cloud_id,s.paused_at,s.paused_total_ms " +
                "FROM sessions s LEFT JOIN exercise_sets e ON e.session_id=s.id WHERE s.finished_at IS NOT NULL " +
                "GROUP BY s.id ORDER BY s.started_at DESC LIMIT 100";
        try(Cursor c=getReadableDatabase().rawQuery(q,null)) {
            while(c.moveToNext())
                result.add(new Session(c.getLong(0),c.getInt(1),c.getLong(2),c.getLong(3),c.getInt(4),c.getDouble(5),c.getString(6),c.getString(7),c.isNull(8)?0:c.getLong(8),c.getLong(9)));
        }
        return result;
    }
    public long insertCloudSession(String cloudId, int workout, long startedAt, long finishedAt, String notes) {
        SQLiteDatabase sql=getWritableDatabase();
        try(Cursor c=sql.rawQuery("SELECT id FROM sessions WHERE cloud_id=?",new String[]{cloudId})) {
            if(c.moveToFirst())return c.getLong(0);
        }
        ContentValues values=new ContentValues();
        values.put("cloud_id",cloudId);
        values.put("workout",workout);
        values.put("started_at",startedAt);
        values.put("finished_at",finishedAt);
        values.put("notes",notes==null?"":notes);
        return sql.insertWithOnConflict("sessions",null,values,SQLiteDatabase.CONFLICT_IGNORE);
    }
    public void insertCloudSet(long sessionId,String exercise,int setIndex,double weight,int reps) {
        insertCloudSet(sessionId,exercise,setIndex,weight,reps,-1,"",false);
    }
    public void insertCloudSet(long sessionId,String exercise,int setIndex,double weight,int reps,
                               int rir,String note,boolean warmup) {
        ContentValues row=new ContentValues();
        row.put("session_id",sessionId);
        row.put("exercise_id",exercise);
        row.put("set_index",setIndex);
        row.put("weight",weight);
        row.put("reps",reps);
        row.put("rir",rir);
        row.put("note",note==null?"":note);
        row.put("warmup",warmup?1:0);
        getWritableDatabase().insertWithOnConflict("exercise_sets",null,row,SQLiteDatabase.CONFLICT_IGNORE);
    }
    /**
     * Compare imported data with the six exercises explicitly reported for 18 Nov.
     * It is diagnostic only: NEVER delete or rewrite a user's exercise sets.
     */
    public String initialImportAudit(long sessionId) {
        Session session=byId(sessionId);
        if(session==null || session.notes==null ||
            !session.notes.startsWith("Import z notatek 18.11.2026")) {
            return "To nie jest początkowy import z 18.11.2026.";
        }
        String[] original={"db_flat","lat_wide","row_cable","butterfly","cable_lateral","facepull"};
        java.util.Map<String,Integer> counts=new java.util.LinkedHashMap<>();
        for(String id:original)counts.put(id,0);
        for(Entry entry:sets(sessionId)) {
            counts.put(entry.exerciseId,counts.containsKey(entry.exerciseId)?
                counts.get(entry.exerciseId)+1:1);
        }
        StringBuilder report=new StringBuilder();
        report.append("W pierwszych notatkach było 6 ćwiczeń po 3 serie, czyli 18 serii.\n");
        report.append("W aplikacji jest teraz ").append(session.sets).append(" serii.\n\n");
        int beyond=0,missing=0;
        for(java.util.Map.Entry<String,Integer> entry:counts.entrySet()) {
            String exercise=entry.getKey();
            int actual=entry.getValue();
            int expected=java.util.Arrays.asList(original).contains(exercise)?3:0;
            Plan.Exercise ex=Plan.byId(exercise);
            String title=ex==null?exercise:ex.name;
            report.append("• ").append(title).append(": ").append(actual);
            if(actual!=expected) {
                report.append(" (w notatkach: ").append(expected).append(")");
                beyond+=Math.max(0,actual-expected);
                missing+=Math.max(0,expected-actual);
            }
            report.append("\n");
        }
        report.append("\n");
        if(beyond==0 && missing==0)report.append("Zapis zgadza się z notatkami.");
        else {
            if(beyond>0)report.append("Dodatkowe serie względem notatek: ").append(beyond).append(". ");
            if(missing>0)report.append("Brakujące serie względem notatek: ").append(missing).append(". ");
            report.append("To nie dowodzi duplikacji — mogłeś później dopisać kolejne serie lub ćwiczenia. Nic nie zostało usunięte.");
        }
        return report.toString().replace("\\\n","\n");
    }

    public String sessionNotes(long id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT notes FROM sessions WHERE id=?",
                new String[]{Long.toString(id)})) {
            return c.moveToFirst()?c.getString(0):"";
        }
    }
    public int setRows(long session,String exercise,int defaultRows) {
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT MAX(set_index) FROM exercise_sets WHERE session_id=? AND exercise_id=?",
                new String[]{Long.toString(session),exercise})) {
            return Math.max(defaultRows,c.moveToFirst() && !c.isNull(0)?c.getInt(0)+1:defaultRows);
        }
    }
    public List<Progress> progress(String exercise) {
        ArrayList<Progress> result=new ArrayList<>();
        String q="SELECT s.id,s.started_at,e.weight,e.reps FROM exercise_sets e JOIN sessions s ON s.id=e.session_id " +
                "WHERE e.exercise_id=? AND e.warmup=0 AND s.finished_at IS NOT NULL ORDER BY s.started_at DESC,e.set_index ASC";
        try(Cursor c=getReadableDatabase().rawQuery(q,new String[]{exercise})) {
            long sessionId=-1; Progress p=null;
            while(c.moveToNext()) {
                long currentId=c.getLong(0);
                if(currentId!=sessionId) {
                    sessionId=currentId;
                    p=new Progress(c.getLong(1));
                    result.add(p);
                }
                double weight=c.getDouble(2); int reps=c.getInt(3);
                if(p!=null) {
                    if(p.numberOfSets==0 || weight>p.maxWeight || (weight==p.maxWeight && reps>p.repsAtMax)) {
                        p.maxWeight=weight; p.repsAtMax=reps;
                    }
                    p.totalReps+=reps; p.numberOfSets++;
                }
            }
        }
        return result;
    }
    public void exportCsv(Writer writer) throws IOException {
        writer.write("\uFEFFData;Trening;Ćwiczenie;Seria;Ciężar kg;Powtórzenia;RIR;Notatka;Rozgrzewka\n");
        String q="SELECT s.started_at,s.workout,e.exercise_id,e.set_index,e.weight,e.reps,e.rir,e.note,e.warmup " +
                "FROM exercise_sets e JOIN sessions s ON s.id=e.session_id ORDER BY s.started_at,e.rowid";
        SimpleDateFormat fmt=new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        try(Cursor c=getReadableDatabase().rawQuery(q,null)) {
            while(c.moveToNext()) {
                Plan.Exercise ex=Plan.byId(c.getString(2));
                String name=ex!=null?ex.name:c.getString(2);
                String line=csv(fmt.format(new Date(c.getLong(0))))+";"+csv(Plan.title(c.getInt(1)))+";"+
                        csv(name)+";"+(c.getInt(3)+1)+";"+
                        String.format(Locale.forLanguageTag("pl-PL"),"%.2f",c.getDouble(4))+";"+c.getInt(5)+";"+
                        (c.getInt(6)<0?"":c.getInt(6))+";"+csv(c.getString(7))+";"+(c.getInt(8)!=0?"TAK":"NIE")+"\n";
                writer.write(line);
            }
        }
        writer.flush();
    }
    private static String csv(String s) { return "\"" + s.replace("\"","\"\"") + "\""; }
}
