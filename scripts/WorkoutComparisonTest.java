package pl.pitsmich.forma;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Executable Java regression checks for read-only session comparisons. */
public final class WorkoutComparisonTest {
    private static WorkoutComparison.SetValue s(String id,int i,double kg,int reps,int rir,int mult){
        return new WorkoutComparison.SetValue(id,i,kg,reps,rir,mult,false);
    }
    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError(message);
    }
    private static boolean close(double a,double b){return Math.abs(a-b)<0.00001;}
    public static void main(String[] args){
        List<WorkoutComparison.SetValue> current=Arrays.asList(
            s("db_flat",0,32.5,12,2,2), // improvement over same reps at 30
            s("db_flat",1,30,10,-1,2),  // decline: fewer reps; absent RIR is NOT zero
            s("db_flat",2,35,8,3,2),   // mixed: higher weight and fewer reps
            s("db_flat",3,20,9,-1,2),  // extra set without comparison
            s("tri_push",0,20,12,2,1),
            new WorkoutComparison.SetValue("db_flat",50,100,100,0,2,true)
        );
        List<WorkoutComparison.SetValue> previous=Arrays.asList(
            s("db_flat",0,30,12,1,2),
            s("db_flat",1,30,12,2,2),
            s("db_flat",2,30,12,-1,2),
            s("db_flat",4,25,8,2,2),  // previous-only set
            s("tri_push",0,20,12,1,1),
            s("calf_standing",0,30,15,2,1) // exercise not done yet
        );
        WorkoutComparison.Result r=WorkoutComparison.compare(current,previous,
            Arrays.asList("db_flat","tri_push","calf_standing"));
        check(r.exercises.size()==3,"Should keep previous-only and active exercises");
        check(r.currentWorkingSets==5,"Warmups excluded from count");
        check(r.previousWorkingSets==6,"Previous set count");
        check(r.matchedSets==4,"Only the same set indexes match");
        check(r.exercises.get(0).id.equals("db_flat"),"Plan order respected");

        WorkoutComparison.Exercise db=r.exercises.get(0);
        check(db.currentSets==4 && db.previousSets==4,"Working-set count");
        check(db.matched==3,"Only identical indices match");
        check(db.improvements==1 && db.declines==1 && db.mixed==1 && db.stable==0,
            "Mixed performance must not count as a gain");
        check(db.currentRirCount==2 && db.previousRirCount==3,
            "Missing RIR excluded, not treated as 0");
        check(close(db.currentAverageRir(),2.5),"Current RIR average");
        check(close(db.previousAverageRir(),(1.0+2.0+2.0)/3.0),"Previous RIR average");
        check(close(db.matchedCurrentVolume,2*(32.5*12+30*10+35*8)),
            "Matched current volume");
        check(close(db.matchedPreviousVolume,2*(30*12+30*12+30*12)),
            "Matched previous volume");
        check(db.rows.size()==5,"Extra and missing series retained");
        check(db.rows.get(3).status==WorkoutComparison.MISSING &&
            db.rows.get(4).status==WorkoutComparison.MISSING,
            "Unmatched series must not become a decline");

        WorkoutComparison.Exercise tri=r.exercises.get(1);
        check(tri.matched==1 && tri.stable==1,"Identical weight and reps are stable");
        check(tri.currentAverageRir()==2.0 && tri.previousAverageRir()==1.0,
            "RIR not used for classifying improvement");
        WorkoutComparison.Exercise calf=r.exercises.get(2);
        check(calf.currentSets==0 && calf.previousSets==1 && calf.matched==0,
            "Exercise not yet done must stay ungraded");
        check(calf.currentAverageRir()==-1,"Missing RIR is distinct from measured RIR 0");

        check(WorkoutComparison.classify(s("x",0,30,12,0,1),
              s("x",0,30,12,4,1))==WorkoutComparison.STABLE,
            "RIR-only change is not automatic improvement");
        check(WorkoutComparison.classify(s("x",0,30,12,1,1),
              s("x",0,32.5,10,1,1))==WorkoutComparison.MIXED,
            "Less weight and more reps is mixed");
        check(WorkoutComparison.classify(s("x",0,30,11,1,1),
              s("x",0,30,12,1,1))==WorkoutComparison.DECLINED,
            "Fewer reps is a decline");
        check(WorkoutComparison.classify(null,s("x",0,30,12,2,1))==
            WorkoutComparison.MISSING,"Null row is missing");

        WorkoutComparison.Result empty=WorkoutComparison.compare(
            Collections.emptyList(),Collections.emptyList(),Arrays.asList("db_flat"));
        check(empty.exercises.isEmpty(),"No fabricated exercise results");
        check(empty.currentVolume==0 && empty.previousVolume==0,"Empty totals");
        System.out.println("FORMA workout comparison: 20+ Java assertions passed");
    }
}
