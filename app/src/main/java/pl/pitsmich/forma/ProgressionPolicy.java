package pl.pitsmich.forma;

import java.util.List;

/**
 * Conservative, deterministic double-progression policy.
 * Works on historical data only; never writes an exercise set.
 */
public final class ProgressionPolicy {
    private ProgressionPolicy() { }

    public static final class WorkingSet {
        public final int index, reps, rir;
        public final double weight;
        public WorkingSet(int index,double weight,int reps,int rir) {
            this.index=index;this.weight=weight;this.reps=reps;this.rir=rir;
        }
    }
    public static final class Suggestion {
        public final double weight;
        public final int reps;
        public final boolean increaseWeight;
        public final String explanation;
        Suggestion(double weight,int reps,boolean increaseWeight,String explanation) {
            this.weight=weight;this.reps=reps;this.increaseWeight=increaseWeight;
            this.explanation=explanation;
        }
    }

    /**
     * Increase weight only after the last completed session has exactly the planned
     * working-set indices, the same load, the upper rep target and recorded
     * sufficient RIR in every series. Missing RIR is not zero.
     *
     * Outside that case, suggest at most one additional rep in the matching set
     * when its recorded RIR supports it. If RIR is missing or too low, hold.
     */
    public static Suggestion recommend(List<WorkingSet> previous,int targetIndex,
            int expectedSets,int minReps,int maxReps,int minRir,double increment) {
        if(previous==null || expectedSets<1 || expectedSets>12 || targetIndex<0 ||
            targetIndex>=expectedSets || minReps<1 || maxReps<minReps ||
            maxReps>1000 || minRir<0 || minRir>4 ||
            !Double.isFinite(increment)||increment<=0)return null;

        WorkingSet target=null;
        boolean complete=previous.size()==expectedSets;
        boolean[] seen=new boolean[expectedSets];
        boolean sameWeight=true, allAtCeiling=true, allSafeRir=true;
        double firstWeight=-1;
        for(WorkingSet entry:previous) {
            if(entry==null || entry.index<0 || entry.index>=expectedSets ||
                !Double.isFinite(entry.weight) || entry.weight<0 || entry.weight>2000 ||
                entry.reps<1 || entry.reps>1000 || entry.rir< -1 || entry.rir>4)
                return null;
            if(seen[entry.index])return null;
            seen[entry.index]=true;
            if(entry.index==targetIndex)target=entry;
            if(firstWeight<0)firstWeight=entry.weight;
            if(Math.abs(firstWeight-entry.weight)>0.001)sameWeight=false;
            if(entry.reps<maxReps)allAtCeiling=false;
            if(entry.rir<minRir)allSafeRir=false;
        }
        for(boolean found:seen)if(!found)complete=false;
        if(target==null)return null;

        if(complete && sameWeight && allAtCeiling && allSafeRir &&
            firstWeight>0 && firstWeight+increment<=2000) {
            return new Suggestion(firstWeight+increment,minReps,true,
                "Wszystkie serie osiągnęły górny zakres przy równym ciężarze i RIR ≥"+
                minRir+". Spróbuj wyższego ciężaru, zaczynając od dolnego zakresu powtórzeń.");
        }
        if(target.rir<0)
            return new Suggestion(target.weight,target.reps,false,
                "Brak RIR przy tej serii. Zachowaj wynik i wpisz RIR przed oceną zwiększenia ciężaru.");
        if(target.rir<minRir)
            return new Suggestion(target.weight,target.reps,false,
                "Ostatnio RIR "+target.rir+" (cel ≥"+minRir+
                "). Nie zwiększaj obciążenia; zadbaj o technikę i zapas.");
        if(target.reps<maxReps)
            return new Suggestion(target.weight,Math.min(maxReps,target.reps+1),false,
                "Utrzymaj ciężar i spróbuj wykonać o jedno powtórzenie więcej z RIR ≥"+
                minRir+".");
        return new Suggestion(target.weight,target.reps,false,
            "Utrzymaj ciężar. Do zwiększenia potrzeba pełnego bloku serii w górnym zakresie i z RIR ≥"+
            minRir+".");
    }
}
