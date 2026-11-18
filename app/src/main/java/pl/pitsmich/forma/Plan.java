package pl.pitsmich.forma;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Plan {
    public static final int UPPER_A = 0;
    public static final int LEGS = 1;
    public static final int UPPER_B = 2;

    public static final class Exercise {
        public final String id, name, description;
        public final int sets, minReps, maxReps, restSeconds;
        public final boolean eachSide;
        public Exercise(String id, String name, String description,
                        int sets, int minReps, int maxReps, int restSeconds, boolean eachSide) {
            this.id = id; this.name = name; this.description = description;
            this.sets = sets; this.minReps = minReps; this.maxReps = maxReps;
            this.restSeconds = restSeconds; this.eachSide = eachSide;
        }
        public String goal() {
            return sets + " × " + minReps + "–" + maxReps
                    + (eachSide ? " / stronę" : "") + "  •  " + restSeconds + " s przerwy";
        }
    }

    public static String title(int workout) {
        if (workout == UPPER_A) return "GÓRA A";
        if (workout == LEGS) return "NOGI";
        return "GÓRA B";
    }
    public static String focus(int workout) {
        if (workout == UPPER_A) return "Klatka • szerokość pleców • barki • ramiona";
        if (workout == LEGS) return "Uda • pośladki • łydki • stabilizacja";
        return "Górna klatka • najszersze grzbietu • ramiona";
    }
    public static List<Exercise> exercises(int workout) {
        if (workout == UPPER_A) return Arrays.asList(
            e("db_flat","Wyciskanie hantli płasko","Leżysz na płaskiej ławce. Opuszczasz hantle obok klatki i wyciskasz do góry.",3,8,12,120,false),
            e("lat_wide","Ściąganie drążka wyciągu górnego","Ściągasz drążek do górnej klatki, prowadząc łokcie w dół. Bez odchylania tułowia.",3,8,12,90,false),
            e("row_cable","Wiosłowanie siedząc na wyciągu dolnym","Siedzisz z nogami opartymi. Przyciągasz wąski uchwyt do brzucha bez kołysania tułowiem.",2,10,12,90,false),
            e("butterfly","Butterfly na maszynie","Zbliżasz uchwyty przed klatką, a następnie powoli otwierasz ramiona.",2,12,15,60,false),
            e("cable_lateral","Unoszenie ramion bokiem na bramie","Stoisz bokiem do bramy i unosisz ramię do wysokości barku. Bez szarpania.",3,12,20,60,true),
            e("facepull","Face pull","Przyciągasz linę do czoła, rozprowadzając dłonie na boki. Pracuje tył barków.",2,12,15,60,false),
            e("tri_push","Triceps — prostowanie ramion z liną","Łokcie blisko tułowia, prostujesz ramiona w dół przy bramie.",2,10,15,60,false),
            e("bi_cable","Biceps — uginanie na bramie","Dolny bloczek; uginasz łokcie bez bujania tułowiem.",2,10,15,60,false)
        );
        if (workout == LEGS) return Arrays.asList(
            e("leg_press","Suwnica siedząca","Wypychasz platformę nogami z podpartymi plecami. Nie odrywaj miednicy od oparcia.",4,10,12,120,false),
            e("leg_curl","Uginanie nóg siedząc","Zginasz kolana, przesuwając wałek w dół. Kontrolujesz powrót.",4,10,15,75,false),
            e("leg_ext","Prostowanie nóg siedząc","Prostujesz kolana, unosząc wałek. Powoli wracasz.",3,10,15,75,false),
            e("rev_lunge","Zakroki z hantlami","Cofasz jedną nogę i wracasz do stania ze stabilnym tułowiem. Jeśli nasilają ból lędźwi, pomiń lub zastąp.",2,8,10,90,true),
            e("calf","Wspięcia na palce — łydki","Unosisz pięty, robisz krótką pauzę i powoli opuszczasz.",4,12,20,60,false),
            e("pallof","Pallof press na bramie","Stoisz bokiem do bramy i wypychasz uchwyt przed siebie, utrzymując nieruchomy tułów.",2,10,12,60,true)
        );
        return Arrays.asList(
            e("smith_incline","Smith — skos dodatni 15–30°","Opuszczasz gryf do górnej części klatki i wyciskasz. Dobierz ławkę do wygodnego toru prowadnic.",3,8,12,120,false),
            e("lat_one","Ściąganie linki górnej jednorącz do biodra","Przyciągasz uchwyt z wysokiej bramy w stronę biodra. Łokieć prowadź w dół bez rotacji tułowia.",3,10,12,75,true),
            e("chest_press","Chest press na maszynie","Siedzisz z plecami opartymi i wypychasz uchwyty przed siebie.",2,8,12,90,false),
            e("lat_close","Ściąganie drążka wąskim neutralnym chwytem","Wąski uchwyt, dłonie do siebie. Ściągasz w stronę górnej klatki.",2,8,12,90,false),
            e("straight_arm","Narciarz na bramie","Lekko ugięte ramiona prowadzisz łukiem z wysokiego bloczka w stronę ud. Bez zamachu tułowiem.",2,12,15,60,false),
            e("db_lateral","Unoszenie hantli bokiem","Unosisz hantle do wysokości barków, bez odchylania się do tyłu.",2,12,20,60,false),
            e("tri_overhead","Triceps znad głowy na bramie","Stoisz plecami do bramy. Lina za głową, prostujesz łokcie do przodu.",2,10,15,60,false),
            e("bi_cable","Biceps — uginanie na wyciągu dolnym","Dolny bloczek. Uginasz łokcie z drążkiem lub liną, bez bujania.",2,10,15,60,false)
        );
    }
    private static Exercise e(String id, String name, String description, int sets,
                              int min, int max, int rest, boolean eachSide) {
        return new Exercise(id,name,description,sets,min,max,rest,eachSide);
    }
    public static Exercise byId(String id) {
        for (int day=0;day<3;day++) for (Exercise ex : exercises(day))
            if (ex.id.equals(id)) return ex;
        return null;
    }
    public static List<Exercise> uniqueExercises() {
        ArrayList<Exercise> list = new ArrayList<>();
        for(int w=0;w<3;w++) for(Exercise ex:exercises(w)) {
            boolean found=false;
            for(Exercise prev:list) if(prev.id.equals(ex.id)) {found=true;break;}
            if(!found) list.add(ex);
        }
        return list;
    }
}
