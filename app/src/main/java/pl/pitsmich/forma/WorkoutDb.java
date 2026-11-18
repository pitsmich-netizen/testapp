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
        public final int workout, sets;
        public final double volume;
        Session(long id, int workout, long startedAt, long finishedAt, int sets, double volume) {
            this.id=id; this.workout=workout; this.startedAt=startedAt;
            this.finishedAt=finishedAt; this.sets=sets; this.volume=volume;
        }
    }
    public static final class Progress {
        public final long date;
        public double maxWeight=0;
        public int repsAtMax=0, totalReps=0, numberOfSets=0;
        Progress(long date) { this.date=date; }
    }

    public WorkoutDb(Context context) { super(context, "forma_workouts.db", null, 1); }
    @Override public void onConfigure(SQLiteDatabase db) { super.onConfigure(db); db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, workout INTEGER NOT NULL, started_at INTEGER NOT NULL, finished_at INTEGER)");
        db.execSQL("CREATE TABLE exercise_sets (session_id INTEGER NOT NULL, exercise_id TEXT NOT NULL, set_index INTEGER NOT NULL, weight REAL NOT NULL, reps INTEGER NOT NULL, PRIMARY KEY(session_id,exercise_id,set_index), FOREIGN KEY(session_id) REFERENCES sessions(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX idx_sets_exercise ON exercise_sets(exercise_id)");
        db.execSQL("CREATE INDEX idx_sessions_time ON sessions(started_at)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

    public long start(int workout) {
        ContentValues v=new ContentValues();
        v.put("workout", workout);
        v.put("started_at", System.currentTimeMillis());
        return getWritableDatabase().insertOrThrow("sessions", null, v);
    }
    public void finish(long id) {
        ContentValues v=new ContentValues(); v.put("finished_at", System.currentTimeMillis());
        getWritableDatabase().update("sessions", v, "id=?", new String[]{Long.toString(id)});
    }
    public Session active() {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,workout,started_at FROM sessions WHERE finished_at IS NULL ORDER BY id DESC LIMIT 1",null)) {
            if(!c.moveToFirst()) return null;
            return new Session(c.getLong(0),c.getInt(1),c.getLong(2),0,countSets(c.getLong(0)),0);
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
        String q="SELECT s.id,s.workout,s.started_at,s.finished_at,COUNT(e.set_index),COALESCE(SUM(e.weight*e.reps),0) " +
                "FROM sessions s LEFT JOIN exercise_sets e ON e.session_id=s.id WHERE s.finished_at IS NOT NULL " +
                "GROUP BY s.id ORDER BY s.started_at DESC LIMIT 100";
        try(Cursor c=getReadableDatabase().rawQuery(q,null)) {
            while(c.moveToNext())
                result.add(new Session(c.getLong(0),c.getInt(1),c.getLong(2),c.getLong(3),c.getInt(4),c.getDouble(5)));
        }
        return result;
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
