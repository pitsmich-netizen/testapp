package pl.pitsmich.forma;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Real Java regression tests for the independent next-workout progression policy. */
public final class ProgressionPolicyTest {
    private static ProgressionPolicy.WorkingSet set(int i,double kg,int reps,int rir) {
        return new ProgressionPolicy.WorkingSet(i,kg,reps,rir);
    }
    private static ProgressionPolicy.Suggestion recommend(
            List<ProgressionPolicy.WorkingSet> rows,int idx) {
        return ProgressionPolicy.recommend(rows,idx,3,8,12,2,2.5);
    }
    private static void check(boolean ok,String what) {
        if(!ok)throw new AssertionError(what);
    }
    private static void target(ProgressionPolicy.Suggestion s,double kg,int reps,boolean increase) {
        check(s!=null,"Missing proposal");
        check(Math.abs(s.weight-kg)<0.0001,"Wrong kg: "+s.weight+" != "+kg);
        check(s.reps==reps,"Wrong reps: "+s.reps+" != "+reps);
        check(s.increaseWeight==increase,"Wrong weight progression flag");
    }
    public static void main(String[] args) {
        List<ProgressionPolicy.WorkingSet> ready=Arrays.asList(
            set(0,30,12,2),set(1,30,12,3),set(2,30,12,2));
        target(recommend(ready,0),32.5,8,true);
        target(recommend(ready,1),32.5,8,true);
        target(recommend(ready,2),32.5,8,true);

        List<ProgressionPolicy.WorkingSet> gradual=Arrays.asList(
            set(0,30,12,2),set(1,30,11,2),set(2,30,10,3));
        target(recommend(gradual,0),30,12,false);
        target(recommend(gradual,1),30,12,false);
        target(recommend(gradual,2),30,11,false);

        List<ProgressionPolicy.WorkingSet> missing=Arrays.asList(
            set(0,30,12,2),set(1,30,12,-1),set(2,30,12,2));
        target(recommend(missing,0),30,12,false);
        target(recommend(missing,1),30,12,false);
        check(recommend(missing,1).explanation.contains("Brak RIR"),"Missing RIR warning");

        List<ProgressionPolicy.WorkingSet> low=Arrays.asList(
            set(0,30,12,2),set(1,30,12,0),set(2,30,12,2));
        target(recommend(low,0),30,12,false);
        target(recommend(low,1),30,12,false);
        check(recommend(low,1).explanation.contains("RIR 0"),"Low RIR warning");

        List<ProgressionPolicy.WorkingSet> mixedWeight=Arrays.asList(
            set(0,30,12,2),set(1,32.5,12,2),set(2,30,12,2));
        target(recommend(mixedWeight,0),30,12,false);
        target(recommend(mixedWeight,1),32.5,12,false);

        List<ProgressionPolicy.WorkingSet> incomplete=Arrays.asList(set(0,30,12,2),set(1,30,12,2));
        target(recommend(incomplete,0),30,12,false);
        check(recommend(incomplete,2)==null,"No fabricated set-3 proposal");

        check(recommend(Arrays.asList(set(0,30,12,2),set(0,30,12,2)),0)==null,
              "Duplicate indices must be rejected");
        check(recommend(Arrays.asList(set(0,30,12,2),set(1,30,12,2),
              set(2,30,12,2),set(3,30,12,2)),0)==null,
              "Out-of-plan extra set must not trigger unsafe progression");

        target(ProgressionPolicy.recommend(Arrays.asList(set(0,20,8,2)),
            0,1,8,12,2,1.25),20,9,false);
        target(ProgressionPolicy.recommend(Arrays.asList(set(0,20,12,2)),
            0,1,8,12,2,1.25),21.25,8,true);
        target(ProgressionPolicy.recommend(Arrays.asList(set(0,0,12,2)),
            0,1,8,12,2,2.5),0,12,false);
        target(ProgressionPolicy.recommend(Arrays.asList(set(0,20,7,2)),
            0,1,8,12,2,2.5),20,8,false);
        check(ProgressionPolicy.recommend(Collections.emptyList(),
            0,3,8,12,2,2.5)==null,"No history should not create a recommendation");
        System.out.println("FORMA progression policy: 17 scenarios passed");
    }
}
