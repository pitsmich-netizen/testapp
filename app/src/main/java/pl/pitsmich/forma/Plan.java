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
        public final int sets, minReps, maxReps, restSeconds, rirMin, rirMax;
        public final boolean eachSide;
        public Exercise(String id, String name, String description,
                        int sets, int minReps, int maxReps, int restSeconds, boolean eachSide) {
            this(id,name,description,sets,minReps,maxReps,restSeconds,eachSide,-1,-1);
        }
        public Exercise(String id,String name,String description,int sets,int minReps,int maxReps,
                        int restSeconds,boolean eachSide,int rirMin,int rirMax){
            this.id = id; this.name = name; this.description = description;
            this.sets = sets; this.minReps = minReps; this.maxReps = maxReps;
            this.restSeconds = restSeconds; this.eachSide = eachSide;
            this.rirMin=rirMin;this.rirMax=rirMax;
        }
        public String goal() {
            return sets + " × " + minReps + "–" + maxReps
                    + (eachSide ? " / stronę" : "") + "  •  " +
                    (restSeconds>0?restSeconds+" s przerwy":"ostatnia seria") +
                    (rirMin>=0?"  •  RIR "+rirMin+(rirMax>rirMin?"–"+rirMax:""):"");
        }
    }

    public static String title(int workout) {
        if (workout == UPPER_A) return "FBW A";
        if (workout == LEGS) return "FBW B";
        return "FBW C";
    }
    public static String focus(int workout) {
        if (workout == UPPER_A) return "Klatka • plecy • nogi • barki • ramiona";
        if (workout == LEGS) return "Plecy • górna klatka • nogi • barki • ramiona";
        return "Klatka • najszersze • nogi • barki • triceps";
    }
    /** FBW 1.0 — exactly 19 working sets for each weekday. */
    public static List<Exercise> exercises(int workout) {
        if(workout==UPPER_A) return Arrays.asList(
            f("db_flat","Wyciskanie hantli płasko","Łopatki stabilnie; kontroluj opuszczanie, bez odbijania.",3,8,12,120,false,2,2),
            f("lat_wide","Ściąganie drążka wyciągu górnego","Średni lub nieco szerszy chwyt; łokcie w dół bez odchylania tułowia.",3,8,12,120,false,2,2),
            f("leg_press","Suwnica pozioma","Stopy na szerokości barków; miednica stabilna w całym ruchu.",2,10,12,120,false,2,3),
            f("leg_curl","Uginanie nóg siedząc","Oś kolana zgodna z osią maszyny; bez szarpania.",2,10,15,75,false,1,2),
            f("cable_lateral","Unoszenie ramienia bokiem na wyciągu","Jednorącz z dołu; lekko ugięty łokieć, nie unoś barku.",2,12,20,60,true,1,2),
            f("tri_push","Prostowanie ramion z liną — triceps","Łokcie blisko tułowia, pracują przedramiona.",2,10,15,75,false,1,2),
            f("bi_cable","Uginanie ramion na bramie — biceps","Stabilne łokcie, bez bujania tułowia.",2,10,15,75,false,1,2),
            f("calf_standing","Wspięcia na palce stojąc","Rozciągnij łydkę bez odbijania w dolnej pozycji.",2,12,20,60,false,1,2),
            f("ab_crunch","Abdominal crunch na maszynie","Zginaj tułów pracą brzucha, bez szarpania ciężaru rękami.",1,12,15,0,false,2,2)
        );
        if(workout==LEGS) return Arrays.asList(
            f("lat_close","Ściąganie drążka neutralnym chwytem","Dłonie do siebie; łokcie kieruj w dół i lekko do tyłu.",2,8,12,120,false,2,2),
            f("smith_incline","Wyciskanie na Smithie — skos dodatni","Ławka 15–30°; kontroluj opuszczanie i utrzymaj pośladki na ławce.",2,8,12,120,false,2,2),
            f("rev_lunge","Zakroki z hantlami","Krok do tyłu, stabilny tułów; wracaj przez nogę wykroczną.",2,8,10,120,true,2,3),
            f("leg_ext","Prostowanie nóg na maszynie","Kolana zgodnie z osią maszyny, wyprost bez zamachu.",2,10,15,75,false,1,2),
            f("leg_curl","Uginanie nóg siedząc","Biodra i uda przy poduszkach; kontroluj powrót.",2,10,15,75,false,1,2),
            f("row_cable","Wiosłowanie na wyciągu dolnym","Stabilny tułów, przyciągaj uchwyt do dolnych żeber.",3,10,12,120,false,2,2),
            f("facepull","Face pull","Ciągnij linę do czoła, prowadź łokcie na boki.",2,12,15,75,false,1,2),
            f("bi_cable","Uginanie ramion na wyciągu dolnym — biceps","Łokcie nieruchomo przy tułowiu, bez odchylania.",2,10,15,75,false,1,2),
            f("tri_overhead","Prostowanie ramion znad głowy — triceps","Lina nad głową, prostuj łokcie bez bujania.",2,10,15,75,false,1,2)
        );
        return Arrays.asList(
            f("chest_press","Chest press na maszynie","Uchwyty na wysokości klatki, plecy przy oparciu.",2,8,12,120,false,2,2),
            f("straight_arm","Narciarz — straight-arm pulldown","Lekko ugięte łokcie; prowadź ramiona łukiem w kierunku bioder.",3,12,15,75,false,1,2),
            f("leg_press","Suwnica pozioma","Stabilna miednica; kontroluj ugięcie kolan i wyprost.",2,12,15,120,false,2,3),
            f("leg_curl","Uginanie nóg siedząc","Bez szarpania; kontroluj fazę powrotną.",2,10,15,75,false,1,2),
            f("butterfly","Butterfly na maszynie","Lekko ugięte łokcie; nie wypychaj barków do przodu.",2,12,15,75,false,1,2),
            f("cable_lateral","Unoszenie ramienia bokiem na wyciągu","Jednorącz, kontroluj ruch, bez pomagania tułowiem.",3,12,20,60,true,1,2),
            f("tri_push","Prostowanie ramion z liną — triceps","Łokcie przy tułowiu, pełen kontrolowany wyprost.",2,10,15,75,false,1,2),
            f("calf_seated","Wspięcia na palce siedząc","Krótka pauza na dole, następnie unieś pięty wysoko.",2,12,20,60,false,1,2),
            f("pallof","Pallof press na wyciągu","Bokiem do bloczka; wypychaj uchwyt bez rotacji tułowia.",1,10,15,0,true,2,3)
        );
    }
    private static Exercise f(String id,String name,String desc,int sets,int min,int max,
             int rest,boolean side,int rirLow,int rirHigh) {
        return new Exercise(id,name,desc,sets,min,max,rest,side,rirLow,rirHigh);
    }
    /** Historical identifiers used by sessions created before FBW 1.0. */
    private static List<Exercise> legacyExercises(int workout) {
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
        for(int day=0;day<3;day++)for(Exercise ex:legacyExercises(day))
            if(ex.id.equals(id))return ex;
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
