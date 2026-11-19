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

    private static final String TEST_MARKER="[FORMA:TEST]";
    public static boolean isTestSession(Session session) {
        return session!=null && session.notes!=null && session.notes.contains(TEST_MARKER);
    }
    public boolean isTestSession(long id) { return isTestSession(byId(id)); }
    public void setTestSession(long id,boolean test) {
        Session session=byId(id);
        if(session==null || session.finishedAt<=0)
            throw new IllegalArgumentException("Tylko zakończony trening może być testowy");
        String notes=session.notes==null?"":session.notes.replace(TEST_MARKER,"").trim();
        if(test)notes=(notes.isEmpty()?"":notes+"\n")+TEST_MARKER;
        ContentValues update=new ContentValues();
        update.put("notes",notes);
        getWritableDatabase().update("sessions",update,"id=?",new String[]{Long.toString(id)});
    }
    public WorkoutDb(Context context) { super(context, "forma_workouts.db", null, 9); }
    @Override public void onConfigure(SQLiteDatabase db) { super.onConfigure(db); db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, workout INTEGER NOT NULL, started_at INTEGER NOT NULL, finished_at INTEGER, notes TEXT NOT NULL DEFAULT '', cloud_id TEXT, paused_at INTEGER, paused_total_ms INTEGER NOT NULL DEFAULT 0, audit_resolution TEXT NOT NULL DEFAULT 'pending', workout_label TEXT)");
        db.execSQL("CREATE TABLE exercise_sets (session_id INTEGER NOT NULL, exercise_id TEXT NOT NULL, set_index INTEGER NOT NULL, weight REAL NOT NULL, reps INTEGER NOT NULL, rir INTEGER NOT NULL DEFAULT -1, note TEXT NOT NULL DEFAULT '', warmup INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(session_id,exercise_id,set_index), FOREIGN KEY(session_id) REFERENCES sessions(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX idx_sets_exercise ON exercise_sets(exercise_id)");
        db.execSQL("CREATE INDEX idx_sessions_time ON sessions(started_at)");
        db.execSQL("CREATE UNIQUE INDEX idx_sessions_cloud_id ON sessions(cloud_id)");
        db.execSQL("CREATE TABLE pending_remote_deletes (cloud_id TEXT PRIMARY KEY)");
        createPlanTable(db);
        createPersonalTables(db);
        installFbwPlan(db);
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
        if(oldVersion<8) createPersonalTables(db);
        if(oldVersion<9) migrateFbwPlan(db);
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

    private static void ensureFbwColumns(SQLiteDatabase sql) {
        // Existing v8 plans have no RIR targets.
        boolean hasRir=false;
        try(Cursor c=sql.rawQuery("PRAGMA table_info(plan_items)",null)){
            while(c.moveToNext())if("rir_min".equals(c.getString(1)))hasRir=true;
        }
        if(!hasRir){
            sql.execSQL("ALTER TABLE plan_items ADD COLUMN rir_min INTEGER NOT NULL DEFAULT -1");
            sql.execSQL("ALTER TABLE plan_items ADD COLUMN rir_max INTEGER NOT NULL DEFAULT -1");
        }
    }
    private static void migrateFbwPlan(SQLiteDatabase sql) {
        ensureFbwColumns(sql);
        // Archive existing customised days and exercises; never modify exercise_sets.
        sql.execSQL("CREATE TABLE IF NOT EXISTS pre_fbw_plan_items AS SELECT * FROM plan_items WHERE 0");
        sql.execSQL("INSERT INTO pre_fbw_plan_items SELECT * FROM plan_items");
        sql.execSQL("CREATE TABLE IF NOT EXISTS pre_fbw_schedule AS SELECT * FROM workout_schedule");
        // Preserve titles for existing sessions; they retain their historical workout type.
        sql.execSQL("ALTER TABLE sessions ADD COLUMN workout_label TEXT");
        sql.execSQL("UPDATE sessions SET workout_label=CASE workout "+
            "WHEN 0 THEN 'GÓRA A' WHEN 1 THEN 'NOGI' ELSE 'GÓRA B' END");
        installFbwPlan(sql);
    }
    private static void installFbwPlan(SQLiteDatabase sql) {
        ensureFbwColumns(sql);
        sql.delete("plan_items",null,null);
        for(int w=0;w<3;w++){
            int position=0;
            for(Plan.Exercise ex:Plan.exercises(w)){
                ContentValues v=new ContentValues();
                v.put("workout",w);v.put("position",position++);
                v.put("exercise_id",ex.id);v.put("sets",ex.sets);
                v.put("min_reps",ex.minReps);v.put("max_reps",ex.maxReps);
                v.put("rest_seconds",ex.restSeconds);
                v.put("rir_min",ex.rirMin);v.put("rir_max",ex.rirMax);
                sql.insertOrThrow("plan_items",null,v);
            }
        }
        sql.delete("workout_schedule",null,null);
        for(int i=0;i<3;i++){
            ContentValues v=new ContentValues();
            v.put("workout",i);v.put("position",i);
            v.put("weekday",i==0?1:i==1?3:5);
            v.put("title",Plan.title(i));
            sql.insertOrThrow("workout_schedule",null,v);
        }
    }
    public String sessionWorkoutLabel(Session session){
        if(session==null)return "";
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT workout_label FROM sessions WHERE id=?",
            new String[]{Long.toString(session.id)})){
            if(c.moveToFirst()&&!c.isNull(0))return c.getString(0);
        }
        return workoutLabel(session.workout);
    }
    public static final class ScheduleDay {
        public final int workout,position,weekday;
        public final String title;
        ScheduleDay(int workout,int position,int weekday,String title) {
            this.workout=workout;this.position=position;this.weekday=weekday;this.title=title;
        }
    }

    private static void createPersonalTables(SQLiteDatabase sql) {
        sql.execSQL("CREATE TABLE IF NOT EXISTS workout_schedule (workout INTEGER PRIMARY KEY, "+
            "position INTEGER NOT NULL UNIQUE, weekday INTEGER NOT NULL UNIQUE, "+
            "title TEXT NOT NULL)");
        sql.execSQL("CREATE TABLE IF NOT EXISTS personal_exercises ("+
            "id TEXT PRIMARY KEY, name TEXT NOT NULL, description TEXT NOT NULL DEFAULT '', "+
            "region TEXT NOT NULL DEFAULT '', equipment TEXT NOT NULL DEFAULT '', "+
            "sets INTEGER NOT NULL, min_reps INTEGER NOT NULL, max_reps INTEGER NOT NULL, "+
            "rest_seconds INTEGER NOT NULL, each_side INTEGER NOT NULL DEFAULT 0)");
        sql.execSQL("CREATE TABLE IF NOT EXISTS load_settings ("+
            "exercise_id TEXT PRIMARY KEY, step_kg REAL NOT NULL DEFAULT 2.5, "+
            "multiplier INTEGER NOT NULL DEFAULT 1)");
        // Only seed on the first migration. Existing user customization stays untouched.
        try(Cursor c=sql.rawQuery("SELECT COUNT(*) FROM workout_schedule",null)) {
            if(c.moveToFirst() && c.getInt(0)==0) {
                String[] names={"GÓRA A","NOGI","GÓRA B"};
                int[] days={1,3,5};
                for(int i=0;i<3;i++) {
                    ContentValues values=new ContentValues();
                    values.put("workout",i);values.put("position",i);
                    values.put("weekday",days[i]);values.put("title",names[i]);
                    sql.insertOrThrow("workout_schedule",null,values);
                }
            }
        }
    }

    public List<ScheduleDay> schedule() {
        ArrayList<ScheduleDay> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT workout,position,weekday,title FROM workout_schedule ORDER BY position",null)) {
            while(c.moveToNext())result.add(new ScheduleDay(c.getInt(0),c.getInt(1),c.getInt(2),c.getString(3)));
        }
        return result;
    }

    public String workoutLabel(int workout) {
        for(ScheduleDay d:schedule())if(d.workout==workout)return d.title;
        return Plan.title(workout);
    }
    public void updateSchedule(List<ScheduleDay> days) {
        if(days==null||days.size()!=3)throw new IllegalArgumentException("Potrzebne trzy dni treningowe");
        java.util.HashSet<Integer> exercises=new java.util.HashSet<>();
        java.util.HashSet<Integer> weekdays=new java.util.HashSet<>();
        for(ScheduleDay day:days) {
            if(day.workout<0||day.workout>2||!exercises.add(day.workout) ||
                day.weekday<1||day.weekday>7||!weekdays.add(day.weekday) ||
                day.title==null||day.title.trim().isEmpty()||day.title.length()>32)
                throw new IllegalArgumentException("Dni tygodnia muszą być różne");
        }
        SQLiteDatabase sql=getWritableDatabase();
        sql.beginTransaction();
        try {
            sql.delete("workout_schedule",null,null);
            for(int i=0;i<days.size();i++) {
                ScheduleDay day=days.get(i);
                ContentValues row=new ContentValues();
                row.put("workout",day.workout);row.put("position",i);
                row.put("weekday",day.weekday);row.put("title",day.title.trim());
                sql.insertOrThrow("workout_schedule",null,row);
            }
            sql.setTransactionSuccessful();
        } finally {sql.endTransaction();}
    }
    public void moveScheduledWorkout(int sourceWorkout,int targetWorkout) {
        ArrayList<ScheduleDay> days=new ArrayList<>(schedule());
        int source=-1,target=-1;
        for(int i=0;i<days.size();i++) {
            if(days.get(i).workout==sourceWorkout)source=i;
            if(days.get(i).workout==targetWorkout)target=i;
        }
        if(source<0||target<0||source==target)return;
        ScheduleDay moved=days.remove(source);
        days.add(target,moved);
        updateSchedule(days);
    }
    public void assignWeekday(int workout,int weekday) {
        ArrayList<ScheduleDay> days=new ArrayList<>(schedule());
        int oldDay=-1;
        for(ScheduleDay d:days)if(d.workout==workout)oldDay=d.weekday;
        if(oldDay<0)return;
        for(int i=0;i<days.size();i++) {
            ScheduleDay d=days.get(i);
            if(d.workout==workout)days.set(i,new ScheduleDay(d.workout,i,weekday,d.title));
            else if(d.weekday==weekday)days.set(i,new ScheduleDay(d.workout,i,oldDay,d.title));
        }
        updateSchedule(days);
    }
    public void renameWorkout(int workout,String title) {
        ArrayList<ScheduleDay> days=new ArrayList<>(schedule());
        for(int i=0;i<days.size();i++) {
            ScheduleDay d=days.get(i);
            if(d.workout==workout)days.set(i,new ScheduleDay(workout,i,d.weekday,title));
        }
        updateSchedule(days);
    }

    public Plan.Exercise exerciseById(String id) {
        Plan.Exercise existing=Plan.byId(id);
        if(existing!=null)return existing;
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT name,description,sets,min_reps,max_reps,rest_seconds,each_side "+
            "FROM personal_exercises WHERE id=?",new String[]{id})) {
            if(c.moveToFirst())return new Plan.Exercise(id,c.getString(0),c.getString(1),
                c.getInt(2),c.getInt(3),c.getInt(4),c.getInt(5),c.getInt(6)!=0);
        }
        return null;
    }
    public List<Plan.Exercise> exerciseCatalog() {
        ArrayList<Plan.Exercise> list=new ArrayList<>(Plan.uniqueExercises());
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM personal_exercises ORDER BY name",null)) {
            while(c.moveToNext()) {
                Plan.Exercise ex=exerciseById(c.getString(0));
                if(ex!=null)list.add(ex);
            }
        }
        return list;
    }
    public Plan.Exercise createExercise(String name,String description,String region,String equipment,
            int sets,int min,int max,int rest,boolean eachSide) {
        if(name==null||name.trim().isEmpty()||name.length()>70||
            description==null||description.length()>500 || region==null||region.length()>70 ||
            equipment==null||equipment.length()>70 ||
            sets<1||sets>12||min<1||max<min||max>40||rest<15||rest>600)
            throw new IllegalArgumentException("Sprawdź nazwę, serie i zakres powtórzeń");
        String id="custom_"+UUID.randomUUID().toString();
        ContentValues values=new ContentValues();
        values.put("id",id);values.put("name",name.trim());values.put("description",description.trim());
        values.put("region",region);values.put("equipment",equipment);
        values.put("sets",sets);values.put("min_reps",min);values.put("max_reps",max);
        values.put("rest_seconds",rest);values.put("each_side",eachSide?1:0);
        getWritableDatabase().insertOrThrow("personal_exercises",null,values);
        return exerciseById(id);
    }
    public double weightStep(String exercise) {
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT step_kg FROM load_settings WHERE exercise_id=?",new String[]{exercise})) {
            if(c.moveToFirst())return c.getDouble(0);
        }
        return exercise.contains("lateral")||exercise.equals("facepull")?1.0:2.5;
    }
    public int loadMultiplier(String exercise) {
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT multiplier FROM load_settings WHERE exercise_id=?",new String[]{exercise})) {
            if(c.moveToFirst())return c.getInt(0);
        }
        return exercise.equals("db_flat")||exercise.equals("db_lateral")||exercise.equals("rev_lunge")?2:1;
    }
    public void saveLoadSettings(String exercise,double step,int multiplier) {
        if(exerciseById(exercise)==null||!Double.isFinite(step)||step<0.25||step>50||
            multiplier<1||multiplier>2)throw new IllegalArgumentException("Nieprawidłowy skok lub przelicznik");
        ContentValues row=new ContentValues();
        row.put("exercise_id",exercise);row.put("step_kg",step);row.put("multiplier",multiplier);
        getWritableDatabase().insertWithOnConflict("load_settings",null,row,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public double sessionVolume(long session) {
        double total=0;
        for(Entry entry:sets(session))if(!entry.warmup)total+=entry.weight*entry.reps*loadMultiplier(entry.exerciseId);
        return total;
    }
    public Session previousCompletedSameWorkout(Session current) {
        // The plan migrated from GÓRA A/NOGI/GÓRA B to FBW A/B/C in 0.8.1.
        // Workout integer IDs remained the same. A comparable session must retain
        // the SAME saved plan label and a reasonably similar exercise selection.
        String label=sessionWorkoutLabel(current);
        java.util.Set<String> currentExercises=new java.util.HashSet<>();
        for(Entry e:sets(current.id))if(!e.warmup)currentExercises.add(e.exerciseId);
        for(Session candidate:completed()) {
            if(isTestSession(candidate)||candidate.workout!=current.workout||candidate.id==current.id||
                candidate.startedAt>=current.startedAt||
                !label.equals(sessionWorkoutLabel(candidate)))continue;
            java.util.Set<String> previousExercises=new java.util.HashSet<>();
            for(Entry e:sets(candidate.id))if(!e.warmup)previousExercises.add(e.exerciseId);
            if(currentExercises.isEmpty()||previousExercises.isEmpty())continue;
            int common=0;
            for(String id:currentExercises)if(previousExercises.contains(id))common++;
            if(common*2>=Math.max(currentExercises.size(),previousExercises.size()))
                return candidate;
        }
        return null;
    }
    public boolean isPersonalRecord(long session,String exercise,Entry candidate) {
        if(candidate==null||candidate.warmup)return false;
        Session own=byId(session);
        if(own==null || isTestSession(own))return false;
        boolean previous=false;
        for(Progress p:progress(exercise)) {
            if(p.date>=own.startedAt)continue;
            previous=true;
            if(p.maxWeight>candidate.weight ||
                (p.maxWeight==candidate.weight&&p.repsAtMax>=candidate.reps))return false;
        }
        return previous;
    }
    public int prCount(long session) {
        int count=0;
        java.util.Map<String,Entry> best=new java.util.HashMap<>();
        for(Entry entry:sets(session)) {
            if(entry.warmup)continue;
            Entry previous=best.get(entry.exerciseId);
            if(previous==null||entry.weight>previous.weight||
                (entry.weight==previous.weight&&entry.reps>previous.reps))
                best.put(entry.exerciseId,entry);
        }
        for(java.util.Map.Entry<String,Entry> e:best.entrySet())
            if(isPersonalRecord(session,e.getKey(),e.getValue()))count++;
        return count;
    }

    /** User plan customization; existing completed sessions are never rewritten. */
    public List<Plan.Exercise> workoutPlan(int workout) {
        if(workout<0||workout>2)throw new IllegalArgumentException("Nieznany trening");
        ArrayList<Plan.Exercise> list=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT exercise_id,sets,min_reps,max_reps,rest_seconds,rir_min,rir_max FROM plan_items "+
            "WHERE workout=? ORDER BY position",new String[]{Integer.toString(workout)})) {
            while(c.moveToNext()) {
                Plan.Exercise source=exerciseById(c.getString(0));
                if(source!=null)list.add(new Plan.Exercise(
                    source.id,source.name,source.description,
                    c.getInt(1),c.getInt(2),c.getInt(3),c.getInt(4),source.eachSide,
                    c.getInt(5),c.getInt(6)));
            }
        }
        return list.isEmpty()?new ArrayList<>(Plan.exercises(workout)):list;
    }

    public void savePlan(int workout,List<Plan.Exercise> list) {
        if(workout<0||workout>2 || list==null||list.isEmpty()||list.size()>18)
            throw new IllegalArgumentException("Nieprawidłowy plan");
        java.util.HashSet<String> ids=new java.util.HashSet<>();
        for(Plan.Exercise e:list) {
            if(exerciseById(e.id)==null || !ids.add(e.id)||e.sets<1||e.sets>12||
                e.minReps<1||e.maxReps<e.minReps||e.maxReps>40||
                e.restSeconds<0||e.restSeconds>600)
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
                row.put("rir_min",e.rirMin);row.put("rir_max",e.rirMax);
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
            "WHERE s.workout=? AND e.exercise_id=? AND s.finished_at IS NOT NULL AND s.notes NOT LIKE '%[FORMA:TEST]%' "+
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
        v.put("workout_label",workoutLabel(workout));
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
        String q="SELECT s.id,s.workout,s.started_at,s.finished_at,COUNT(e.set_index),COALESCE(SUM(CASE WHEN e.warmup=0 THEN e.weight*e.reps*COALESCE((SELECT l.multiplier FROM load_settings l WHERE l.exercise_id=e.exercise_id),CASE WHEN e.exercise_id IN ('db_flat','db_lateral','rev_lunge') THEN 2 ELSE 1 END) ELSE 0 END),0),s.notes,s.cloud_id,s.paused_at,s.paused_total_ms "+
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
                "WHERE e.exercise_id=? AND e.set_index=? AND e.warmup=0 AND s.finished_at IS NOT NULL AND s.notes NOT LIKE '%[FORMA:TEST]%' ORDER BY s.started_at DESC LIMIT 1",
                new String[]{exercise,Integer.toString(index)})) {
            if(!c.moveToFirst()) return null;
            return new Entry(exercise,index,c.getDouble(0),c.getInt(1),c.getInt(2),c.getString(3),c.getInt(4)!=0);
        }
    }
    /** Conservative per-set guidance. Never automatically changes the saved load. */
    public String loadGuidance(Plan.Exercise exercise,int workout,int setIndex) {
        Entry previous=previousWorkout(workout,exercise.id,setIndex);
        if(previous==null)return "Pierwszy wynik — dobierz ciężar do zakresu powtórzeń i docelowego RIR.";
        String load=String.format(Locale.forLanguageTag("pl-PL"),"%.2f",previous.weight).replaceAll("0+$","").replaceAll("\\.$","");
        if(previous.rir<0)return "Ostatnio: "+load+" kg × "+previous.reps+
            ". Bez zapisanego RIR nie proponuję automatycznego zwiększania.";
        if(exercise.rirMin>=0 && previous.rir<exercise.rirMin)
            return "Ostatnio RIR "+previous.rir+" — najpierw zadbaj o technikę i zapas powtórzeń.";
        if(previous.reps<exercise.minReps)
            return "Ostatnio poniżej zakresu ("+previous.reps+" powt.). Rozważ mniejszy ciężar.";
        if(previous.reps<exercise.maxReps)
            return "Utrzymaj około "+load+" kg i spróbuj zwiększyć liczbę powtórzeń.";
        if(progressionTip(exercise)!=null && !progressionTip(exercise).startsWith("Osiągnięto"))
            return "Gotowość do progresji: rozważ +"+weightStep(exercise.id)+" kg, zachowując RIR i technikę.";
        return "Górny zakres osiągnięty; utrzymaj ciężar, aż wszystkie serie spełnią założenia RIR.";
    }

    /** Only suggest progression after ALL planned working sets hit the rep ceiling at one weight. */
    public String progressionTip(Plan.Exercise exercise) {
        // Suggest the next small increment only when the entire planned working set
        // block reached its rep target at a constant load, with enough reps in reserve.
        String q="SELECT e.reps,e.weight,e.rir FROM exercise_sets e "+
            "WHERE e.exercise_id=? AND e.warmup=0 AND e.session_id=("+
            "SELECT x.id FROM sessions x JOIN exercise_sets y ON y.session_id=x.id "+
            "WHERE y.exercise_id=? AND y.warmup=0 AND x.finished_at IS NOT NULL AND x.notes NOT LIKE '%[FORMA:TEST]%' "+
            "ORDER BY x.started_at DESC,x.id DESC LIMIT 1) "+
            "ORDER BY e.set_index ASC LIMIT ?";
        int count=0;double constantWeight=-1;
        boolean unknownRir=false,insufficientRir=false;
        try(Cursor c=getReadableDatabase().rawQuery(q,
            new String[]{exercise.id,exercise.id,Integer.toString(exercise.sets)})) {
            while(c.moveToNext()) {
                count++;
                int reps=c.getInt(0),rir=c.getInt(2);
                double loaded=c.getDouble(1);
                if(count==1)constantWeight=loaded;
                if(reps<exercise.maxReps||Math.abs(loaded-constantWeight)>0.001)return null;
                if(rir<0)unknownRir=true;
                else if(exercise.rirMin>=0&&rir<exercise.rirMin)insufficientRir=true;
            }
        }
        if(count<exercise.sets||insufficientRir)return null;
        if(unknownRir)return "Osiągnięto górny zakres we wszystkich seriach. "+
            "Przed zwiększeniem ciężaru sprawdź, czy zachowałeś docelowy RIR i technikę.";
        return "Wszystkie serie po "+exercise.maxReps+" powtórzeń przy "+
            String.format(Locale.forLanguageTag("pl-PL"),"%.1f",constantWeight)+
            " kg z odpowiednim RIR. Następnym razem spróbuj "+
            String.format(Locale.forLanguageTag("pl-PL"),"%.1f",constantWeight+weightStep(exercise.id))+
            " kg, jeśli technika i dostępne obciążenia na to pozwalają.";
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
        String q="SELECT s.id,s.workout,s.started_at,s.finished_at,COUNT(e.set_index),COALESCE(SUM(CASE WHEN e.warmup=0 THEN e.weight*e.reps*COALESCE((SELECT l.multiplier FROM load_settings l WHERE l.exercise_id=e.exercise_id),CASE WHEN e.exercise_id IN ('db_flat','db_lateral','rev_lunge') THEN 2 ELSE 1 END) ELSE 0 END),0),s.notes,s.cloud_id,s.paused_at,s.paused_total_ms " +
                "FROM sessions s LEFT JOIN exercise_sets e ON e.session_id=s.id WHERE s.finished_at IS NOT NULL " +
                "GROUP BY s.id ORDER BY s.started_at DESC";
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
                "WHERE e.exercise_id=? AND e.warmup=0 AND s.finished_at IS NOT NULL AND s.notes NOT LIKE '%[FORMA:TEST]%' ORDER BY s.started_at DESC,e.set_index ASC";
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
                Plan.Exercise ex=exerciseById(c.getString(2));
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
