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
        Entry(String exerciseId, int setIndex, double weight, int reps) {
            this.exerciseId=exerciseId; this.setIndex=setIndex; this.weight=weight; this.reps=reps;
        }
    }
    public static final class Session {
        public final long id, startedAt, finishedAt;
        public final String notes, cloudId;
        public final int workout, sets;
        public final double volume;
        Session(long id, int workout, long startedAt, long finishedAt, int sets, double volume, String notes, String cloudId) {
            this.id=id; this.workout=workout; this.startedAt=startedAt;
            this.finishedAt=finishedAt; this.sets=sets; this.volume=volume;
            this.notes=notes; this.cloudId=cloudId;
        }
    }
    public static final class Progress {
        public final long date;
        public double maxWeight=0;
        public int repsAtMax=0, totalReps=0, numberOfSets=0;
        Progress(long date) { this.date=date; }
    }

    public WorkoutDb(Context context) { super(context, "forma_workouts.db", null, 4); }
    @Override public void onConfigure(SQLiteDatabase db) { super.onConfigure(db); db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, workout INTEGER NOT NULL, started_at INTEGER NOT NULL, finished_at INTEGER, notes TEXT NOT NULL DEFAULT '', cloud_id TEXT)");
        db.execSQL("CREATE TABLE exercise_sets (session_id INTEGER NOT NULL, exercise_id TEXT NOT NULL, set_index INTEGER NOT NULL, weight REAL NOT NULL, reps INTEGER NOT NULL, PRIMARY KEY(session_id,exercise_id,set_index), FOREIGN KEY(session_id) REFERENCES sessions(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX idx_sets_exercise ON exercise_sets(exercise_id)");
        db.execSQL("CREATE INDEX idx_sessions_time ON sessions(started_at)");
        db.execSQL("CREATE UNIQUE INDEX idx_sessions_cloud_id ON sessions(cloud_id)");
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
        if(oldVersion<2) seedInitialWorkout(db);
        if(oldVersion<3) seedAdditionalNovemberSets(db);
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
        ContentValues v=new ContentValues(); v.put("finished_at", System.currentTimeMillis());
        getWritableDatabase().update("sessions", v, "id=?", new String[]{Long.toString(id)});
    }
    public Session active() {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,workout,started_at,notes,cloud_id FROM sessions WHERE finished_at IS NULL ORDER BY id DESC LIMIT 1",null)) {
            if(!c.moveToFirst()) return null;
            return new Session(c.getLong(0),c.getInt(1),c.getLong(2),0,countSets(c.getLong(0)),0,c.getString(3),c.getString(4));
        }
    }
    public void save(long session, String exercise, int index, double weight, int reps) {
        ContentValues v=new ContentValues();
        v.put("session_id",session); v.put("exercise_id",exercise);
        v.put("set_index",index); v.put("weight",weight); v.put("reps",reps);
        getWritableDatabase().insertWithOnConflict("exercise_sets",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public void delete(long session, String exercise, int index) {
        getWritableDatabase().delete("exercise_sets","session_id=? AND exercise_id=? AND set_index=?",
                new String[]{Long.toString(session),exercise,Integer.toString(index)});
    }
    public Entry get(long session,String exercise,int index) {
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT weight,reps FROM exercise_sets WHERE session_id=? AND exercise_id=? AND set_index=?",
                new String[]{Long.toString(session),exercise,Integer.toString(index)})) {
            if(!c.moveToFirst()) return null;
            return new Entry(exercise,index,c.getDouble(0),c.getInt(1));
        }
    }
    public Entry previous(String exercise,int index) {
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT e.weight,e.reps FROM exercise_sets e JOIN sessions s ON s.id=e.session_id " +
                "WHERE e.exercise_id=? AND e.set_index=? AND s.finished_at IS NOT NULL ORDER BY s.started_at DESC LIMIT 1",
                new String[]{exercise,Integer.toString(index)})) {
            if(!c.moveToFirst()) return null;
            return new Entry(exercise,index,c.getDouble(0),c.getInt(1));
        }
    }
    public int countExerciseSets(long session,String exercise) {
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM exercise_sets WHERE session_id=? AND exercise_id=?",
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
                "SELECT exercise_id,set_index,weight,reps FROM exercise_sets WHERE session_id=? ORDER BY rowid",
                new String[]{Long.toString(session)})) {
            while(c.moveToNext()) result.add(new Entry(c.getString(0),c.getInt(1),c.getDouble(2),c.getInt(3)));
        }
        return result;
    }
    public List<Session> completed() {
        ArrayList<Session> result=new ArrayList<>();
        String q="SELECT s.id,s.workout,s.started_at,s.finished_at,COUNT(e.set_index),COALESCE(SUM(e.weight*e.reps),0),s.notes,s.cloud_id " +
                "FROM sessions s LEFT JOIN exercise_sets e ON e.session_id=s.id WHERE s.finished_at IS NOT NULL " +
                "GROUP BY s.id ORDER BY s.started_at DESC LIMIT 100";
        try(Cursor c=getReadableDatabase().rawQuery(q,null)) {
            while(c.moveToNext())
                result.add(new Session(c.getLong(0),c.getInt(1),c.getLong(2),c.getLong(3),c.getInt(4),c.getDouble(5),c.getString(6),c.getString(7)));
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
        ContentValues row=new ContentValues();
        row.put("session_id",sessionId);
        row.put("exercise_id",exercise);
        row.put("set_index",setIndex);
        row.put("weight",weight);
        row.put("reps",reps);
        getWritableDatabase().insertWithOnConflict("exercise_sets",null,row,SQLiteDatabase.CONFLICT_IGNORE);
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
                "WHERE e.exercise_id=? AND s.finished_at IS NOT NULL ORDER BY s.started_at DESC,e.set_index ASC";
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
        writer.write("\uFEFFData;Trening;Ćwiczenie;Seria;Ciężar kg;Powtórzenia\n");
        String q="SELECT s.started_at,s.workout,e.exercise_id,e.set_index,e.weight,e.reps " +
                "FROM exercise_sets e JOIN sessions s ON s.id=e.session_id ORDER BY s.started_at,e.rowid";
        SimpleDateFormat fmt=new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        try(Cursor c=getReadableDatabase().rawQuery(q,null)) {
            while(c.moveToNext()) {
                Plan.Exercise ex=Plan.byId(c.getString(2));
                String name=ex!=null?ex.name:c.getString(2);
                String line=csv(fmt.format(new Date(c.getLong(0))))+";"+csv(Plan.title(c.getInt(1)))+";"+
                        csv(name)+";"+(c.getInt(3)+1)+";"+
                        String.format(Locale.forLanguageTag("pl-PL"),"%.2f",c.getDouble(4))+";"+c.getInt(5)+"\n";
                writer.write(line);
            }
        }
        writer.flush();
    }
    private static String csv(String s) { return "\"" + s.replace("\"","\"\"") + "\""; }
}
