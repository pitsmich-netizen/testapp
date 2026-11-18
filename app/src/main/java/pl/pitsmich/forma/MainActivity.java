package pl.pitsmich.forma;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int BG=Color.rgb(10,16,22);
    private static final int CARD=Color.rgb(23,33,42);
    private static final int CARD2=Color.rgb(29,42,53);
    private static final int TEAL=Color.rgb(46,215,188);
    private static final int TEXT=Color.rgb(240,247,250);
    private static final int MUTED=Color.rgb(154,174,188);
    private static final int BORDER=Color.rgb(48,64,75);
    private static final int EXPORT_REQUEST=1901;

    private WorkoutDb db;
    private Updater updater;
    private CloudSync cloud;
    private LinearLayout root, content;
    private String page="plan";
    private long openSession=-1, detailSession=-1;
    private int openWorkout=Plan.UPPER_A;
    private String selectedExercise="db_flat";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private long restUntil=0, sessionStartedAt=0;
    private TextView timerLabel, sessionCount, elapsedLabel;

    private final Runnable ticker=new Runnable() {
        @Override public void run() {
            if(timerLabel!=null) {
                long remaining=Math.max(0,(restUntil-System.currentTimeMillis()+999)/1000);
                timerLabel.setText(restUntil==0?"Timer przerwy: gotowy":
                    (remaining>0?"Przerwa: "+remaining+" s":"Przerwa zakończona ✓"));
                if(restUntil>0 && remaining==0) {
                    restUntil=0;
                    getSharedPreferences("forma_timer",0).edit().remove("restUntil").apply();
                }
            }
            if(elapsedLabel!=null && sessionStartedAt>0) {
                long secs=Math.max(0,(System.currentTimeMillis()-sessionStartedAt)/1000);
                elapsedLabel.setText("Czas treningu: "+String.format(Locale.ROOT,"%02d:%02d",secs/60,secs%60));
            }
            handler.postDelayed(this,1000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        db=new WorkoutDb(this);
        updater=new Updater(this);
        cloud=new CloudSync(this,db);
        restUntil=getSharedPreferences("forma_timer",0).getLong("restUntil",0);
        if(state!=null) {
            page=state.getString("page","plan");
            openSession=state.getLong("openSession",-1);
            openWorkout=state.getInt("openWorkout",Plan.UPPER_A);
            detailSession=state.getLong("detailSession",-1);
            selectedExercise=state.getString("selectedExercise","db_flat");
        }
        render();
        handler.post(ticker);
        updater.check(false);
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("page",page);
        state.putLong("openSession",openSession);
        state.putInt("openWorkout",openWorkout);
        state.putLong("detailSession",detailSession);
        state.putString("selectedExercise",selectedExercise);
        super.onSaveInstanceState(state);
    }
    @Override protected void onResume() {
        super.onResume();
        if(updater!=null) updater.afterPermissionReturn();
    }
    @Override protected void onDestroy() {
        handler.removeCallbacks(ticker);
        if(cloud!=null)cloud.close();
        db.close();
        super.onDestroy();
    }
    private int dp(float size) { return (int)(size*getResources().getDisplayMetrics().density+0.5f); }

    private GradientDrawable rounded(int bg,int radius,int stroke) {
        GradientDrawable d=new GradientDrawable();
        d.setColor(bg);
        d.setCornerRadius(dp(radius));
        if(stroke!=0) d.setStroke(dp(1),stroke);
        return d;
    }
    private LinearLayout col() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private TextView text(String s,int sp,int color,boolean strong) {
        TextView t=new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if(strong) t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private void space(LinearLayout target,int height) {
        View v=new View(this); target.addView(v,new LinearLayout.LayoutParams(1,dp(height)));
    }
    private LinearLayout card(LinearLayout target) {
        LinearLayout c=col(); c.setPadding(dp(15),dp(15),dp(15),dp(15));
        c.setBackground(rounded(CARD,18,BORDER));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(12);
        target.addView(c,lp);
        return c;
    }
    private void add(LinearLayout target,View child) { target.addView(child,new LinearLayout.LayoutParams(-1,-2)); }
    private void headline(LinearLayout target,String s) {
        add(target,text(s,24,TEXT,true)); space(target,7);
    }
    private TextView pill(String s,boolean active) {
        TextView t=text(s,13,active?BG:MUTED,true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(9),dp(11),dp(9),dp(11));
        t.setBackground(rounded(active?TEAL:CARD,14,active?0:BORDER));
        return t;
    }
    private Button button(String s,boolean primary) {
        Button b=new Button(this);
        b.setAllCaps(false); b.setText(s); b.setTextSize(13);
        b.setTextColor(primary?BG:TEXT);
        b.setBackgroundTintList(ColorStateList.valueOf(primary?TEAL:CARD2));
        b.setMinimumHeight(0); b.setMinHeight(0);
        b.setPadding(dp(10),dp(10),dp(10),dp(10));
        return b;
    }
    private void render() {
        timerLabel=null; sessionCount=null; elapsedLabel=null;
        root=col(); root.setBackgroundColor(BG);
        root.setPadding(dp(17),dp(15),dp(17),dp(10));
        root.setOnApplyWindowInsetsListener((v,insets)->{
            root.setPadding(dp(17),insets.getSystemWindowInsetTop()+dp(12),
                    dp(17),insets.getSystemWindowInsetBottom()+dp(8));
            return insets;
        });
        setContentView(root);
        add(root,text("FORMA  /  TRAINING",14,TEAL,true));
        space(root,4);
        add(root,text("Dziennik treningowy",21,TEXT,true));
        space(root,16);
        if(!page.equals("session") && !page.equals("detail")) {
            LinearLayout tabs=row();
            String[] tabNames={"Plan","Historia","Progres"};
            String[] pages={"plan","history","progress"};
            for(int i=0;i<3;i++) {
                final String destination=pages[i];
                TextView t=pill(tabNames[i],page.equals(destination));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(42),1);
                if(i>0) lp.leftMargin=dp(7);
                tabs.addView(t,lp);
                t.setOnClickListener(v->{page=destination;render();});
            }
            add(root,tabs); space(root,15);
        }
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        content=col();
        content.setPadding(0,0,0,dp(16));
        scroll.addView(content);
        if(page.equals("plan")) showPlan();
        else if(page.equals("session")) showSession();
        else if(page.equals("history")) showHistory();
        else if(page.equals("detail")) showDetail();
        else showProgress();
    }

    private void showPlan() {
        headline(content,"Twój tydzień");
        add(content,text("2 × góra • 1 × nogi • sesje do 60 minut",14,MUTED,false));
        space(content,13);
        LinearLayout intro=card(content);
        add(intro,text("CEL: KLATKA + SZEROKOŚĆ PLECÓW",12,TEAL,true));
        space(intro,8);
        add(intro,text("Ćwicz na hantlach, wyciągach i maszynach. Każdą serię zapisujesz jednym kliknięciem.",14,TEXT,false));
        WorkoutDb.Session active=db.active();
        if(active!=null) {
            space(content,6);
            LinearLayout resume=card(content);
            add(resume,text("ROZPOCZĘTY TRENING",12,TEAL,true)); space(resume,6);
            add(resume,text(Plan.title(active.workout)+" • "+active.sets+" zapisanych serii",16,TEXT,true));
            space(resume,9);
            Button b=button("Wznów trening",true);
            add(resume,b);
            b.setOnClickListener(v->{openSession=active.id;openWorkout=active.workout;page="session";render();});
        }
        LinearLayout ota=card(content);
        add(ota,text("AKTUALIZACJE OTA",12,TEAL,true));
        space(ota,7);
        add(ota,text(updater.feed().isEmpty()
            ? "Kanał aktualizacji nie jest jeszcze skonfigurowany"
            : "Sprawdzanie automatyczne raz dziennie",12,MUTED,false));
        space(ota,9);
        Button check=button("Sprawdź aktualizacje",true);
        add(ota,check);
        check.setOnClickListener(v->updater.check(true));
        space(ota,5);
        Button config=button("Ustawienia kanału OTA",false);
        add(ota,config);
        config.setOnClickListener(v->updater.configure());
        LinearLayout cloudCard=card(content);
        add(cloudCard,text("KOPIA TRENINGÓW W CHMURZE",12,TEAL,true));
        space(cloudCard,6);
        add(cloudCard,text(cloud.signedIn()
            ? "Zalogowano do Supabase • dane na telefonie pozostają dostępne offline"
            : "Opcjonalna kopia danych w Supabase. Bez logowania trening działa offline.",
            13,MUTED,false));
        space(cloudCard,9);
        Button syncButton=button(cloud.signedIn()?"Synchronizuj treningi":"Zaloguj się / utwórz konto",true);
        add(cloudCard,syncButton);
        syncButton.setOnClickListener(v->{
            if(cloud.signedIn()) runCloudSync();
            else showCloudLogin();
        });
        if(cloud.signedIn()) {
            space(cloudCard,5);
            Button logout=button("Wyloguj z chmury",false);
            add(cloudCard,logout);
            logout.setOnClickListener(v->{
                new AlertDialog.Builder(this)
                    .setTitle("Wylogować?")
                    .setMessage("Treningi lokalne pozostaną zapisane. Powtórne logowanie włączy synchronizację.")
                    .setNegativeButton("Anuluj",null)
                    .setPositiveButton("Wyloguj",(d,w)->{cloud.signOut();render();}).show();
            });
        }
        String[] days={"PONIEDZIAŁEK","ŚRODA","PIĄTEK"};
        for(int w=0;w<3;w++) {
            final int workout=w;
            LinearLayout c=card(content);
            add(c,text(days[w],12,TEAL,true)); space(c,6);
            add(c,text(Plan.title(w),23,TEXT,true)); space(c,5);
            add(c,text(Plan.focus(w),13,MUTED,false)); space(c,9);
            int count=0; for(Plan.Exercise ex:Plan.exercises(w))count+=ex.sets;
            add(c,text(Plan.exercises(w).size()+" ćwiczeń • "+count+" serii roboczych",12,MUTED,false));
            space(c,11);
            Button start=button("Otwórz / rozpocznij",true); add(c,start);
            start.setOnClickListener(v->startWorkout(workout));
        }
        add(content,text("Przy bólu lędźwi nie wykonuj ćwiczeń przez narastający ból. Dostosuj zakres ruchu i ciężar.",12,MUTED,false));
    }

    private void showCloudLogin() {
        LinearLayout form=col();
        form.setPadding(dp(18),dp(8),dp(18),0);
        TextView intro=text("Logowanie służy wyłącznie do synchronizacji historii treningowej z Twoim kontem Supabase.",13,MUTED,false);
        add(form,intro);space(form,12);
        EditText email=new EditText(this);
        email.setSingleLine(true);
        email.setHint("Adres e-mail");
        email.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        add(form,email);
        space(form,7);
        EditText pass=new EditText(this);
        pass.setSingleLine(true);
        pass.setHint("Hasło");
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        add(form,pass);
        new AlertDialog.Builder(this)
            .setTitle("Chmura FORMA")
            .setView(form)
            .setNegativeButton("Anuluj",null)
            .setNeutralButton("Utwórz konto",(dialog,which)->{
                cloud.signIn(email.getText().toString(),pass.getText().toString(),true,(ok,msg)->{
                    new AlertDialog.Builder(this).setTitle(ok?"Supabase":"Błąd logowania")
                        .setMessage(msg).setPositiveButton("OK",null).show();
                    if(ok)render();
                });
            })
            .setPositiveButton("Zaloguj",(dialog,which)->{
                cloud.signIn(email.getText().toString(),pass.getText().toString(),false,(ok,msg)->{
                    new AlertDialog.Builder(this).setTitle(ok?"Synchronizacja":"Błąd logowania")
                        .setMessage(msg).setPositiveButton("OK",null).show();
                    if(ok)render();
                });
            })
            .show();
    }
    private void runCloudSync() {
        Toast.makeText(this,"Synchronizowanie treningów…",Toast.LENGTH_SHORT).show();
        cloud.sync((ok,msg)->{
            Toast.makeText(this,(ok?"✓ ":"Błąd: ")+msg,Toast.LENGTH_LONG).show();
            if(ok && (page.equals("history")||page.equals("progress"))) render();
        });
    }
    private void startWorkout(int workout) {
        WorkoutDb.Session active=db.active();
        if(active!=null) {
            new AlertDialog.Builder(this)
                .setTitle("Masz rozpoczęty trening")
                .setMessage("Możesz wrócić do niezakończonej sesji "+Plan.title(active.workout)+".")
                .setPositiveButton("Wznów",(d,w)->{openSession=active.id;openWorkout=active.workout;page="session";render();})
                .setNegativeButton("Anuluj",null).show();
            return;
        }
        openWorkout=workout;
        stopRest();
        openSession=db.start(workout);
        page="session";render();
    }

    private EditText field(String value,String hint,boolean decimal) {
        EditText e=new EditText(this);
        e.setSingleLine(true);
        e.setTextSize(15);e.setTextColor(TEXT);
        e.setHintTextColor(MUTED); e.setHint(hint);
        e.setText(value);
        e.setGravity(Gravity.CENTER);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | (decimal?InputType.TYPE_NUMBER_FLAG_DECIMAL:0));
        e.setBackground(rounded(CARD2,10,BORDER));
        e.setPadding(dp(5),dp(9),dp(5),dp(9));
        return e;
    }
    private String weight(double x) {
        return x==(long)x?Long.toString((long)x):String.format(Locale.getDefault(),"%.2f",x).replaceAll("0+$","").replaceAll("[.,]$","");
    }
    private void showSession() {
        if(openSession<0) {page="plan";render();return;}
        LinearLayout back=row();
        TextView b=text("‹  Wróć do planu",14,TEAL,true);
        add(back,b);add(content,back);
        b.setOnClickListener(v->{page="plan";render();});space(content,9);
        headline(content,Plan.title(openWorkout));
        add(content,text(Plan.focus(openWorkout),13,MUTED,false));space(content,6);
        sessionCount=text("Zapisane serie: "+db.countSets(openSession),13,TEAL,true);
        add(content,sessionCount);
        WorkoutDb.Session currentActive=db.active();
        sessionStartedAt=(currentActive!=null&&currentActive.id==openSession)?currentActive.startedAt:0;
        LinearLayout timerCard=card(content);
        add(timerCard,text("TIMER TRENINGOWY",12,TEAL,true));
        space(timerCard,7);
        elapsedLabel=text("Czas treningu: 00:00",16,TEXT,true);
        add(timerCard,elapsedLabel);
        space(timerCard,6);
        timerLabel=text("Timer przerwy: gotowy",17,TEXT,true);
        add(timerCard,timerLabel);
        space(timerCard,9);
        LinearLayout timerRow=row();
        for(int seconds:new int[]{60,90,120}) {
            final int secs=seconds;
            Button timerButton=button(seconds+" s",false);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(43),1);
            lp.rightMargin=dp(5);timerRow.addView(timerButton,lp);
            timerButton.setOnClickListener(v->startRest(secs));
        }
        Button plus=button("+15 s",false);
        timerRow.addView(plus,new LinearLayout.LayoutParams(0,dp(43),1));
        plus.setOnClickListener(v->{
            long left=restUntil>System.currentTimeMillis()?restUntil-System.currentTimeMillis():0;
            startRest((int)(left/1000)+15);
        });
        add(timerCard,timerRow);
        space(timerCard,5);
        Button skip=button("Zatrzymaj / pomiń przerwę",false);
        add(timerCard,skip);
        skip.setOnClickListener(v->stopRest());
        space(content,3);

        for(Plan.Exercise ex:Plan.exercises(openWorkout)) {
            LinearLayout c=card(content);
            add(c,text(ex.name,17,TEXT,true)); space(c,5);
            add(c,text(ex.goal(),12,TEAL,true)); space(c,7);
            add(c,text(ex.description,13,MUTED,false)); space(c,12);
            LinearLayout rows=col();
            add(c,rows);
            final int[] setNumber={db.setRows(openSession,ex.id,ex.sets)};
            for(int i=0;i<setNumber[0];i++) addSetRow(rows,ex,i);
            Button addExtra=button("+ Dodaj serię",false);
            add(c,addExtra);
            addExtra.setOnClickListener(v->addSetRow(rows,ex,setNumber[0]++));
        }
        Button finish=button("Zakończ i zapisz trening",true);
        add(content,finish);
        finish.setOnClickListener(v->{
            new AlertDialog.Builder(this)
                .setTitle("Zakończyć trening?")
                .setMessage("Zapisane serie zostaną zachowane w historii. Możesz pominąć niewykonane serie.")
                .setNegativeButton("Jeszcze nie",null)
                .setPositiveButton("Zakończ",(d,w)->{
                    db.finish(openSession);openSession=-1;stopRest();page="history";render();
                    if(cloud.signedIn()) runCloudSync();
                }).show();
        });
    }
    private void addSetRow(LinearLayout c,Plan.Exercise ex,int index) {
        WorkoutDb.Entry current=db.get(openSession,ex.id,index);
        WorkoutDb.Entry previous=db.previous(ex.id,index);
        LinearLayout line=row();
        TextView number=text(Integer.toString(index+1)+".",14,MUTED,true);
        LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(dp(27),-2);
        line.addView(number,nlp);
        EditText kg=field(current!=null?weight(current.weight):(previous!=null?weight(previous.weight):""),"kg",true);
        EditText reps=field(current!=null?Integer.toString(current.reps):(previous!=null?Integer.toString(previous.reps):""),"powt.",false);
        LinearLayout.LayoutParams k=new LinearLayout.LayoutParams(0,dp(46),1);
        line.addView(kg,k);
        LinearLayout.LayoutParams r=new LinearLayout.LayoutParams(0,dp(46),1);
        r.leftMargin=dp(7);line.addView(reps,r);
        Button save=button(current!=null?"Zmień":"Zapisz",current==null);
        LinearLayout.LayoutParams s=new LinearLayout.LayoutParams(dp(82),dp(46));
        s.leftMargin=dp(7);line.addView(save,s);
        add(c,line);
        if(previous!=null && current==null) {
            TextView prev=text("Poprzednio: "+weight(previous.weight)+" kg × "+previous.reps,11,MUTED,false);
            LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2);
            pp.leftMargin=dp(27);pp.topMargin=dp(3);c.addView(prev,pp);
        }
        space(c,9);
        save.setOnClickListener(v->{
            try {
                String input=kg.getText().toString().trim().replace(',','.');
                double w=input.isEmpty()?0:Double.parseDouble(input);
                int repeat=Integer.parseInt(reps.getText().toString().trim());
                if(!Double.isFinite(w)||w<0||w>2000||repeat<1||repeat>1000)throw new NumberFormatException();
                db.save(openSession,ex.id,index,w,repeat);
                save.setText("Zmień");save.setTextColor(TEXT);
                save.setBackgroundTintList(ColorStateList.valueOf(CARD2));
                if(sessionCount!=null)sessionCount.setText("Zapisane serie: "+db.countSets(openSession));
                startRest(ex.restSeconds);
                Toast.makeText(this,"Seria "+(index+1)+" zapisana ✓",Toast.LENGTH_SHORT).show();
            } catch(Exception ignored) {
                Toast.makeText(this,"Wpisz poprawny ciężar i liczbę powtórzeń",Toast.LENGTH_LONG).show();
            }
        });
        save.setOnLongClickListener(v->{
            new AlertDialog.Builder(this).setTitle("Usunąć tę serię?")
                .setNegativeButton("Nie",null)
                .setPositiveButton("Usuń",(d,w)->{
                    db.delete(openSession,ex.id,index);
                    save.setText("Zapisz");save.setTextColor(BG);
                    save.setBackgroundTintList(ColorStateList.valueOf(TEAL));
                    if(sessionCount!=null)sessionCount.setText("Zapisane serie: "+db.countSets(openSession));
                }).show();
            return true;
        });
    }

    private void startRest(int seconds) {
        restUntil=System.currentTimeMillis()+seconds*1000L;
        getSharedPreferences("forma_timer",0).edit().putLong("restUntil",restUntil).apply();
        if(timerLabel!=null) timerLabel.setText("Przerwa: "+seconds+" s");
    }
    private void stopRest() {
        restUntil=0;
        getSharedPreferences("forma_timer",0).edit().remove("restUntil").apply();
        if(timerLabel!=null) timerLabel.setText("Timer przerwy: gotowy");
    }
    private String date(long millis) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT,Locale.forLanguageTag("pl-PL"))
                .format(new Date(millis));
    }
    private void showHistory() {
        headline(content,"Historia treningów");
        Button export=button("Eksportuj zapisane serie do CSV",false);
        add(content,export);space(content,13);
        export.setOnClickListener(v->exportCsv());
        List<WorkoutDb.Session> list=db.completed();
        if(list.isEmpty()) {
            LinearLayout c=card(content);
            add(c,text("Nie ma jeszcze zakończonych treningów.",16,TEXT,true));
            space(c,5);
            add(c,text("Otwórz Plan, wykonaj sesję i zapisz serie.",13,MUTED,false));
            return;
        }
        for(WorkoutDb.Session s:list) {
            LinearLayout c=card(content);
            add(c,text(Plan.title(s.workout),18,TEXT,true));space(c,4);
            boolean imported=s.notes!=null&&!s.notes.isEmpty();
            String when=imported?new SimpleDateFormat("dd.MM.yyyy",Locale.forLanguageTag("pl-PL")).format(new Date(s.startedAt))+" • Z notatek":date(s.startedAt);
            add(c,text(when,13,MUTED,false));space(c,5);
            long mins=Math.max(0,(s.finishedAt-s.startedAt)/60000);
            add(c,text(s.sets+" serii • "+(imported?"czas nieznany":mins+" min")+" • "+weight(s.volume)+" kg objętości",13,TEAL,false));
            space(c,9);
            Button details=button("Zobacz zapisane serie",false);add(c,details);
            details.setOnClickListener(v->{detailSession=s.id;page="detail";render();});
        }
    }
    private void showDetail() {
        TextView back=text("‹  Wróć do historii",14,TEAL,true);add(content,back);
        back.setOnClickListener(v->{page="history";render();});space(content,12);
        headline(content,"Zapisane serie");
        String notes=db.sessionNotes(detailSession);
        if(notes!=null&&!notes.isEmpty()) {add(content,text(notes,13,TEAL,false));space(content,10);}
        List<WorkoutDb.Entry> entries=db.sets(detailSession);
        if(entries.isEmpty()) {add(content,text("Nie zapisano serii.",14,MUTED,false));return;}
        String last="";
        for(WorkoutDb.Entry e:entries) {
            if(!last.equals(e.exerciseId)) {
                Plan.Exercise ex=Plan.byId(e.exerciseId);
                space(content,10);
                add(content,text(ex==null?e.exerciseId:ex.name,16,TEXT,true));
                last=e.exerciseId;
            }
            add(content,text("Seria "+(e.setIndex+1)+": "+weight(e.weight)+" kg × "+e.reps,14,MUTED,false));
            space(content,4);
        }
    }

    private void showProgress() {
        headline(content,"Twój progres");
        add(content,text("Sprawdź ciężar i powtórzenia w kolejnych sesjach danego ćwiczenia.",13,MUTED,false));
        space(content,15);
        List<Plan.Exercise> exercises=Plan.uniqueExercises();
        String[] names=new String[exercises.size()];
        int position=0;
        for(int i=0;i<exercises.size();i++) {
            names[i]=exercises.get(i).name;
            if(exercises.get(i).id.equals(selectedExercise))position=i;
        }
        Spinner spinner=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names);
        spinner.setAdapter(adapter);spinner.setSelection(position);
        add(content,spinner);space(content,14);
        LinearLayout results=col();add(content,results);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent,View view,int selected,long id) {
                selectedExercise=exercises.get(selected).id;
                drawProgress(results,selectedExercise);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        drawProgress(results,selectedExercise);
    }
    private void drawProgress(LinearLayout target,String ex) {
        target.removeAllViews();
        List<WorkoutDb.Progress> list=db.progress(ex);
        if(list.isEmpty()) {
            LinearLayout c=card(target);
            add(c,text("Brak zapisanych wyników",16,TEXT,true));
            space(c,6);
            add(c,text("Wykonaj ćwiczenie w treningu, żeby zobaczyć progres.",13,MUTED,false));
            return;
        }
        WorkoutDb.Progress latest=list.get(0);
        LinearLayout top=card(target);
        add(top,text("OSTATNI TRENING",12,TEAL,true));
        space(top,7);
        add(top,text(weight(latest.maxWeight)+" kg × "+latest.repsAtMax,26,TEXT,true));
        add(top,text("Najcięższa zapisana seria • "+date(latest.date),12,MUTED,false));
        space(target,5);
        add(target,text("Historia ćwiczenia",18,TEXT,true));space(target,9);
        for(int i=0;i<Math.min(list.size(),15);i++) {
            WorkoutDb.Progress p=list.get(i);
            LinearLayout c=card(target);
            add(c,text(date(p.date),13,MUTED,false));space(c,6);
            add(c,text(weight(p.maxWeight)+" kg × "+p.repsAtMax+" powt.",18,TEXT,true));
            space(c,3);
            add(c,text(p.numberOfSets+" serii • "+p.totalReps+" powtórzeń łącznie",12,TEAL,false));
        }
    }

    private void exportCsv() {
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/csv");
        i.putExtra(Intent.EXTRA_TITLE,"forma-treningi-"+
                new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date())+".csv");
        startActivityForResult(i,EXPORT_REQUEST);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==EXPORT_REQUEST&&result==RESULT_OK&&data!=null&&data.getData()!=null) {
            Uri uri=data.getData();
            try(OutputStream out=getContentResolver().openOutputStream(uri)) {
                if(out==null)throw new IllegalStateException("Brak dostępu do pliku");
                try(OutputStreamWriter writer=new OutputStreamWriter(out,java.nio.charset.StandardCharsets.UTF_8)) {
                    db.exportCsv(writer);
                }
                Toast.makeText(this,"Eksport CSV zakończony ✓",Toast.LENGTH_LONG).show();
            }catch(Exception e) {
                Toast.makeText(this,"Eksport nieudany: "+e.getMessage(),Toast.LENGTH_LONG).show();
            }
        }
    }
    @Override public void onBackPressed() {
        if(page.equals("session")||page.equals("detail")||page.equals("progress")||page.equals("history")) {
            page=page.equals("detail")?"history":"plan";
            render();
        } else super.onBackPressed();
    }
}
