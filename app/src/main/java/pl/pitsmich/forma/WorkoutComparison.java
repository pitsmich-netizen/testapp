package pl.pitsmich.forma;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Read-only comparison of exercise working sets. RIR is shown separately,
 * never considered an unqualified performance improvement or regression.
 */
public final class WorkoutComparison {
    private WorkoutComparison() { }

    public static final int DECLINED=-1, STABLE=0, IMPROVED=1, MIXED=2, MISSING=3;

    public static final class SetValue {
        public final String exerciseId;
        public final int index,reps,rir,multiplier;
        public final double weight;
        public final boolean warmup;
        public SetValue(String exerciseId,int index,double weight,int reps,int rir,
                int multiplier,boolean warmup) {
            this.exerciseId=exerciseId;this.index=index;this.weight=weight;this.reps=reps;
            this.rir=rir;this.multiplier=multiplier;this.warmup=warmup;
        }
        public double volume(){return weight*reps*multiplier;}
    }
    public static final class Row {
        public final int index;
        public final SetValue current,previous;
        public final int status;
        Row(int index,SetValue current,SetValue previous) {
            this.index=index;this.current=current;this.previous=previous;
            this.status=classify(current,previous);
        }
    }
    public static final class Exercise {
        public final String id;
        public final List<Row> rows=new ArrayList<>();
        public int currentSets,previousSets,matched,improvements,declines,stable,mixed;
        public double currentVolume,previousVolume,matchedCurrentVolume,matchedPreviousVolume;
        public int currentRirSum,currentRirCount,previousRirSum,previousRirCount;
        Exercise(String id){this.id=id;}
        public double currentAverageRir(){
            return currentRirCount==0?-1:(double)currentRirSum/currentRirCount;
        }
        public double previousAverageRir(){
            return previousRirCount==0?-1:(double)previousRirSum/previousRirCount;
        }
    }
    public static final class Result {
        public final List<Exercise> exercises=new ArrayList<>();
        public int currentWorkingSets,previousWorkingSets,matchedSets;
        public double currentVolume,previousVolume;
    }
    public static int classify(SetValue current,SetValue previous) {
        if(current==null||previous==null)return MISSING;
        int weight=Double.compare(current.weight,previous.weight);
        int reps=Integer.compare(current.reps,previous.reps);
        if(weight==0 && reps==0)return STABLE;
        if(weight>=0 && reps>=0)return IMPROVED;
        if(weight<=0 && reps<=0)return DECLINED;
        return MIXED;
    }
    private static void add(Map<String,TreeMap<Integer,SetValue[]>> data,
                            List<SetValue> sets,int slot) {
        if(sets==null)return;
        for(SetValue set:sets) {
            if(set==null||set.warmup||set.exerciseId==null||set.exerciseId.isEmpty()||
                set.index<0||!Double.isFinite(set.weight)||set.weight<0||
                set.reps<1||set.multiplier<1)continue;
            TreeMap<Integer,SetValue[]> rows=data.get(set.exerciseId);
            if(rows==null){rows=new TreeMap<>();data.put(set.exerciseId,rows);}
            SetValue[] pair=rows.get(set.index);
            if(pair==null){pair=new SetValue[2];rows.put(set.index,pair);}
            pair[slot]=set;
        }
    }
    public static Result compare(List<SetValue> current,List<SetValue> previous,
                                 List<String> plannedOrder) {
        LinkedHashMap<String,TreeMap<Integer,SetValue[]>> groups=new LinkedHashMap<>();
        if(plannedOrder!=null)for(String id:plannedOrder)
            if(id!=null&&!id.isEmpty()&&!groups.containsKey(id))
                groups.put(id,new TreeMap<>());
        add(groups,current,0);
        add(groups,previous,1);
        Result result=new Result();
        for(Map.Entry<String,TreeMap<Integer,SetValue[]>> group:groups.entrySet()) {
            if(group.getValue().isEmpty())continue;
            Exercise ex=new Exercise(group.getKey());
            for(Map.Entry<Integer,SetValue[]> item:group.getValue().entrySet()) {
                Row row=new Row(item.getKey(),item.getValue()[0],item.getValue()[1]);
                ex.rows.add(row);
                if(row.current!=null) {
                    ex.currentSets++;result.currentWorkingSets++;
                    ex.currentVolume+=row.current.volume();
                    result.currentVolume+=row.current.volume();
                    if(row.current.rir>=0){ex.currentRirSum+=row.current.rir;ex.currentRirCount++;}
                }
                if(row.previous!=null) {
                    ex.previousSets++;result.previousWorkingSets++;
                    ex.previousVolume+=row.previous.volume();
                    result.previousVolume+=row.previous.volume();
                    if(row.previous.rir>=0){ex.previousRirSum+=row.previous.rir;ex.previousRirCount++;}
                }
                if(row.status!=MISSING) {
                    ex.matched++;result.matchedSets++;
                    ex.matchedCurrentVolume+=row.current.volume();
                    ex.matchedPreviousVolume+=row.previous.volume();
                    if(row.status==IMPROVED)ex.improvements++;
                    else if(row.status==DECLINED)ex.declines++;
                    else if(row.status==STABLE)ex.stable++;
                    else ex.mixed++;
                }
            }
            result.exercises.add(ex);
        }
        return result;
    }
}
