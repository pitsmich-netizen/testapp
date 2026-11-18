package pl.pitsmich.forma;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
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
import android.widget.ProgressBar;
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
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int BG=Color.BLACK;
    private static final int CARD=Color.rgb(18,18,18);
    private static final int CARD2=Color.rgb(28,28,28);
    private static final int TEAL=Color.rgb(99,229,198);
    private static final int TEXT=Color.rgb(250,253,255);
    private static final int MUTED=Color.rgb(198,198,198);
    private static final int BORDER=Color.rgb(58,58,58);
    private static final int EXPORT_REQUEST=1901;

    private WorkoutDb db;
    private Updater updater;
    private CloudSync cloud;
    private LinearLayout root, content;
    private String page="plan";
    private long openSession=-1, detailSession=-1;
    private int openWorkout=Plan.UPPER_A;
    private String selectedExercise="db_flat";
    private String expandedExerciseId="";
    private LinearLayout expandedBody;
    private TextView expandedChevron;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private long restUntil=0, sessionStartedAt=0;
    private TextView timerLabel, sessionCount, elapsedLabel;
    private ProgressBar sessionProgress;
    private int plannedSets=0;

    private final Runnable ticker=new Runnable() {
        @Override public void run() {
            if(timerLabel!=null) {
                long remaining=Math.max(0,(restUntil-System.currentTimeMillis()+999)/1000);
                timerLabel.setText(restUntil==0?"00:00":
                    (remaining>0?formatTime(remaining):"00:00"));
                if(restUntil>0 && remaining==0) {
                    restUntil=0;
                    getSharedPreferences("forma_timer",0).edit().remove("restUntil").apply();
                }
            }
            if(elapsedLabel!=null && sessionStartedAt>0) {
                WorkoutDb.Session active=db.active();
                if(active!=null) elapsedLabel.setText(formatTime(db.durationMillis(active)/1000));
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
            expandedExerciseId=state.getString("expandedExerciseId","");
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
        state.putString("expandedExerciseId",expandedExerciseId);
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
        LinearLayout c=col(); c.setPadding(dp(18),dp(18),dp(18),dp(18));
        c.setBackground(rounded(CARD,18,BORDER));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(14);
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
        b.setAllCaps(false); b.setText(s); b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setTextColor(primary?BG:TEXT);
        b.setBackgroundTintList(ColorStateList.valueOf(primary?TEAL:CARD2));
        b.setMinimumHeight(0); b.setMinHeight(0);
        b.setPadding(dp(12),dp(12),dp(12),dp(12));
        b.setMinHeight(dp(48));
        return b;
    }

    private void render() {
        timerLabel=null; sessionCount=null; elapsedLabel=null; sessionProgress=null; expandedBody=null; expandedChevron=null;
        root=col();
        root.setBackgroundColor(BG);
        root.setPadding(dp(18),dp(12),dp(18),dp(10));
        root.setOnApplyWindowInsetsListener((v,insets)->{
            root.setPadding(dp(18),insets.getSystemWindowInsetTop()+dp(12),
                dp(18),insets.getSystemWindowInsetBottom()+dp(10));
            return insets;
        });
        setContentView(root);

        LinearLayout brand=row();
        TextView logo=text("FORMA",20,TEAL,true);
        logo.setLetterSpacing(0.10f);
        brand.addView(logo,new LinearLayout.LayoutParams(0,dp(39),1));
        TextView version=text("0.4.0",12,MUTED,true);
        version.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        brand.addView(version,new LinearLayout.LayoutParams(dp(54),dp(44)));
        TextView gear=text(page.equals("settings")?"‹":"⚙",26,TEAL,true);
        gear.setGravity(Gravity.CENTER);
        gear.setContentDescription(page.equals("settings")?"Wróć do planu":"Ustawienia aplikacji");
        brand.addView(gear,new LinearLayout.LayoutParams(dp(47),dp(44)));
        gear.setOnClickListener(v->{page=page.equals("settings")?"plan":"settings";render();});
        add(root,brand);
        space(root,5);

        if(page.equals("session")) {
            WorkoutDb.Session active=db.active();
            if(active==null || active.id!=openSession || active.pausedAt>0) page="plan";
            else showFixedSessionHeader();
        }
        ScrollView scroll=new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        content=col();
        content.setPadding(0,dp(7),0,dp(16));
        scroll.addView(content);
        if(page.equals("plan")) showPlan();
        else if(page.equals("session")) showSession();
        else if(page.equals("history")) showHistory();
        else if(page.equals("detail")) showDetail();
        else if(page.equals("progress")) showProgress();
        else if(page.equals("edit")) showCompletedEditor();
        else showSettings();

        if(!page.equals("session") && !page.equals("detail") && !page.equals("edit")) showNavigation();
    }

    private void showNavigation() {
        LinearLayout nav=row();
        nav.setPadding(dp(4),dp(8),dp(4),0);
        String[] names={"Plan","Historia","Progres"};
        String[] pages={"plan","history","progress"};
        for(int i=0;i<names.length;i++) {
            final String selected=pages[i];
            boolean active=page.equals(selected);
            LinearLayout item=col();
            item.setGravity(Gravity.CENTER);
            TextView name=text(names[i],14,active?TEAL:MUTED,active);
            name.setGravity(Gravity.CENTER);
            add(item,name);
            space(item,5);
            View indicator=new View(this);
            indicator.setBackground(rounded(active?TEAL:BG,3,0));
            item.addView(indicator,new LinearLayout.LayoutParams(dp(active?29:14),dp(3)));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(55),1);
            nav.addView(item,lp);
            item.setContentDescription("Otwórz "+names[i]);
            item.setOnClickListener(v->{page=selected;render();});
        }
        add(root,nav);
    }

    private void smallLabel(LinearLayout target,String value) {
        TextView t=text(value,12,TEAL,true);
        t.setLetterSpacing(0.08f);
        add(target,t);
    }
    private void summaryLine(LinearLayout target,String title,String value) {
        LinearLayout r=row();
        TextView l=text(title,14,MUTED,false);
        r.addView(l,new LinearLayout.LayoutParams(0,dp(33),1));
        TextView v=text(value,14,TEXT,true);
        v.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        r.addView(v,new LinearLayout.LayoutParams(-2,dp(33)));
        add(target,r);
    }
    private void showPlan() {
        space(content,8);
        headline(content,"Twój plan treningowy");
        add(content,text("Góra A  •  Nogi  •  Góra B",16,MUTED,false));
        space(content,17);

        WorkoutDb.Session active=db.active();
        if(active!=null) {
            LinearLayout resume=card(content);
            smallLabel(resume,active.pausedAt>0?"TRENING WSTRZYMANY":"TRENING W TOKU");
            space(resume,7);
            add(resume,text(Plan.title(active.workout),22,TEXT,true));
            space(resume,4);
            add(resume,text(active.sets+" zapisanych serii",15,MUTED,false));
            space(resume,10);
            Button b=button(active.pausedAt>0?"Wznów trening →":"Wróć do treningu →",true);
            add(resume,b);
            b.setOnClickListener(v->{
                if(active.pausedAt>0)db.resume(active.id);
                openSession=active.id;
                openWorkout=active.workout;
                expandedExerciseId="";
                page="session";
                render();
            });
        }

        String[] days={"PONIEDZIAŁEK","ŚRODA","PIĄTEK"};
        String[] tags={"KLATKA + PLECY","NOGI","PLECY + KLATKA"};
        for(int w=0;w<3;w++) {
            final int workout=w;
            LinearLayout c=card(content);
            LinearLayout tag=row();
            TextView day=text(days[w],12,TEAL,true);
            tag.addView(day,new LinearLayout.LayoutParams(0,dp(27),1));
            TextView no=text("0"+(w+1),20,MUTED,true);
            tag.addView(no);
            add(c,tag);
            space(c,8);
            add(c,text(Plan.title(w),26,TEXT,true));
            space(c,5);
            add(c,text(tags[w],14,MUTED,false));
            space(c,12);
            int sets=0;for(Plan.Exercise e:Plan.exercises(w))sets+=e.sets;
            add(c,text(Plan.exercises(w).size()+" ćwiczeń     •     "+sets+" serii",14,MUTED,false));
            space(c,14);
            Button open=button(active!=null?"Otwórz plan →":"Rozpocznij trening →",true);
            add(c,open);
            open.setOnClickListener(v->startWorkout(workout));
        }
        TextView info=text("Seria po serii zapisujesz ciężar i powtórzenia. Poprzednie wyniki podpowiadamy automatycznie.",14,MUTED,false);
        info.setLineSpacing(dp(3),1f);
        add(content,info);
    }

    private void showSettings() {
        space(content,8);
        headline(content,"Ustawienia");
        add(content,text("Aktualizacje, kopia zapasowa i dane",15,MUTED,false));
        space(content,17);

        LinearLayout cloudCard=card(content);
        smallLabel(cloudCard,"KOPIA W CHMURZE");
        space(cloudCard,8);
        add(cloudCard,text(cloud.signedIn()?"Konto Supabase połączone":"Twoje treningi są zapisane na telefonie",18,TEXT,true));
        space(cloudCard,6);
        add(cloudCard,text(cloud.signedIn()?"Możesz synchronizować historię z chmurą.":"Logowanie jest opcjonalne. Aplikacja działa też offline.",15,MUTED,false));
        space(cloudCard,12);
        Button syncButton=button(cloud.signedIn()?"Synchronizuj teraz":"Zaloguj się / utwórz konto",true);
        add(cloudCard,syncButton);
        syncButton.setOnClickListener(v->{if(cloud.signedIn())runCloudSync();else showCloudLogin();});
        if(cloud.signedIn()) {
            space(cloudCard,6);
            Button logout=button("Wyloguj",false);
            add(cloudCard,logout);
            logout.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle("Wylogować?")
                .setMessage("Treningi zapisane lokalnie pozostaną na telefonie.")
                .setNegativeButton("Anuluj",null)
                .setPositiveButton("Wyloguj",(d,w)->{cloud.signOut();render();}).show());
        }

        LinearLayout ota=card(content);
        smallLabel(ota,"AKTUALIZACJE APLIKACJI");
        space(ota,7);
        add(ota,text("FORMA 0.4.0",20,TEXT,true));
        space(ota,4);
        add(ota,text("Sprawdzanie OTA odbywa się automatycznie przy uruchomieniu.",14,MUTED,false));
        space(ota,12);
        Button check=button("Sprawdź aktualizacje",true);
        add(ota,check);
        check.setOnClickListener(v->updater.check(true));
        space(ota,6);
        Button config=button("Zaawansowane ustawienia OTA",false);
        add(ota,config);
        config.setOnClickListener(v->updater.configure());

        LinearLayout backup=card(content);
        smallLabel(backup,"EKSPORT DANYCH");
        space(backup,7);
        add(backup,text("Kopia historii treningów",18,TEXT,true));
        space(backup,5);
        add(backup,text("Zapisz serie jako plik CSV, który możesz przechować poza telefonem.",14,MUTED,false));
        space(backup,12);
        Button export=button("Eksportuj CSV",false);
        add(backup,export);
        export.setOnClickListener(v->exportCsv());
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
                .setTitle(active.pausedAt>0?"Wstrzymany trening":"Masz rozpoczęty trening")
                .setMessage("Możesz wrócić do niezakończonej sesji "+Plan.title(active.workout)+".")
                .setPositiveButton("Wznów",(d,w)->{
                    if(active.pausedAt>0)db.resume(active.id);
                    openSession=active.id;openWorkout=active.workout;page="session";render();
                })
                .setNegativeButton("Anuluj",null).show();
            return;
        }
        openWorkout=workout;
        stopRest();
        openSession=db.start(workout);
        expandedExerciseId="";
        page="session";render();
    }

    private EditText field(String value,String hint,boolean decimal) {
        EditText e=new EditText(this);
        e.setSingleLine(true);
        e.setTextSize(21);e.setTextColor(TEXT);
        e.setSelectAllOnFocus(true);
        e.setHintTextColor(MUTED); e.setHint(hint);
        e.setText(value);
        e.setGravity(Gravity.CENTER);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | (decimal?InputType.TYPE_NUMBER_FLAG_DECIMAL:0));
        e.setBackground(rounded(CARD2,10,BORDER));
        e.setPadding(dp(10),dp(13),dp(10),dp(13));
        e.setMinHeight(dp(57));
        return e;
    }
    private String weight(double x) {
        return x==(long)x?Long.toString((long)x):String.format(Locale.getDefault(),"%.2f",x).replaceAll("0+$","").replaceAll("[.,]$","");
    }

    private String formatTime(long seconds) {
        return String.format(Locale.ROOT,"%02d:%02d",seconds/60,seconds%60);
    }
    private void updateSessionProgress() {
        if(openSession<0) return;
        int done=db.countSets(openSession);
        if(sessionCount!=null) sessionCount.setText(done+" / "+plannedSets+" serii");
        if(sessionProgress!=null) sessionProgress.setProgress(Math.min(done,plannedSets));
    }
    private void showFixedSessionHeader() {
        if(openSession<0) return;
        plannedSets=0;
        for(Plan.Exercise e:Plan.exercises(openWorkout)) plannedSets+=e.sets;

        LinearLayout top=row();
        TextView back=text("‹  PLAN",15,TEAL,true);
        back.setPadding(dp(1),0,dp(12),0);
        back.setOnClickListener(v->{page="plan";render();});
        top.addView(back,new LinearLayout.LayoutParams(dp(88),dp(44)));
        TextView title=text(Plan.title(openWorkout),21,TEXT,true);
        title.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        top.addView(title,new LinearLayout.LayoutParams(0,dp(44),1));
        TextView controls=text("⋮",29,TEAL,true);
        controls.setGravity(Gravity.CENTER);
        controls.setContentDescription("Opcje treningu: wstrzymaj, zakończ lub anuluj");
        top.addView(controls,new LinearLayout.LayoutParams(dp(43),dp(44)));
        controls.setOnClickListener(v->showSessionActions());
        add(root,top);

        LinearLayout count=row();
        TextView small=text("POSTĘP TRENINGU",12,MUTED,true);
        count.addView(small,new LinearLayout.LayoutParams(0,dp(29),1));
        sessionCount=text("0 / "+plannedSets+" serii",14,TEAL,true);
        sessionCount.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        count.addView(sessionCount);
        add(root,count);

        sessionProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        sessionProgress.setMax(Math.max(1,plannedSets));
        sessionProgress.setProgressTintList(ColorStateList.valueOf(TEAL));
        sessionProgress.setProgressBackgroundTintList(ColorStateList.valueOf(BORDER));
        add(root,sessionProgress);
        updateSessionProgress();

        WorkoutDb.Session currentActive=db.active();
        sessionStartedAt=(currentActive!=null&&currentActive.id==openSession)?currentActive.startedAt:0;

        LinearLayout timerShell=col();
        timerShell.setBackground(rounded(CARD,17,BORDER));
        timerShell.setPadding(dp(13),dp(10),dp(13),dp(11));
        LinearLayout.LayoutParams shellLp=new LinearLayout.LayoutParams(-1,-2);
        shellLp.topMargin=dp(10);shellLp.bottomMargin=dp(8);
        root.addView(timerShell,shellLp);

        LinearLayout times=row();
        LinearLayout elapsed=col();
        add(elapsed,text("CZAS TRENINGU",11,MUTED,true));
        elapsedLabel=text("00:00",22,TEXT,true);
        add(elapsed,elapsedLabel);
        times.addView(elapsed,new LinearLayout.LayoutParams(0,dp(51),1));

        LinearLayout rest=col();
        add(rest,text("PRZERWA",11,MUTED,true));
        timerLabel=text("00:00",24,TEAL,true);
        add(rest,timerLabel);
        times.addView(rest,new LinearLayout.LayoutParams(0,dp(51),1));

        TextView skip=text("Pomiń  ×",13,TEAL,true);
        skip.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        skip.setPadding(dp(7),0,0,0);
        times.addView(skip,new LinearLayout.LayoutParams(dp(73),dp(51)));
        skip.setOnClickListener(v->stopRest());
        add(timerShell,times);

        LinearLayout buttons=row();
        int[] seconds={60,90,120};
        for(int i=0;i<seconds.length;i++) {
            final int sec=seconds[i];
            TextView t=pill(sec+" s",false);
            t.setTextSize(14);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(36),1);
            if(i>0)p.leftMargin=dp(6);
            buttons.addView(t,p);
            t.setOnClickListener(v->startRest(sec));
        }
        TextView plus=pill("+15 s",false);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(36),1);
        p.leftMargin=dp(6);
        buttons.addView(plus,p);
        plus.setOnClickListener(v->{
            long left=restUntil>System.currentTimeMillis()?
                Math.max(0,(restUntil-System.currentTimeMillis()+999)/1000):0;
            startRest((int)left+15);
        });
        add(timerShell,buttons);
    }

    private void showSession() {
        if(openSession<0) {page="plan";render();return;}
        List<Plan.Exercise> exercises=Plan.exercises(openWorkout);
        if(expandedExerciseId.isEmpty() && !exercises.isEmpty()) {
            for(Plan.Exercise e:exercises) {
                if(db.countExerciseSets(openSession,e.id)<e.sets) {
                    expandedExerciseId=e.id;break;
                }
            }
            if(expandedExerciseId.isEmpty())expandedExerciseId=exercises.get(0).id;
        }

        space(content,5);
        add(content,text("Ćwiczenia",23,TEXT,true));
        space(content,6);
        add(content,text("Dotknij ćwiczenia, aby wprowadzić serie. Zapisywane są od razu.",14,MUTED,false));
        space(content,16);

        for(int i=0;i<exercises.size();i++) {
            final Plan.Exercise ex=exercises.get(i);
            final int order=i+1;
            LinearLayout c=card(content);
            LinearLayout header=row();
            TextView number=text(String.format(Locale.ROOT,"%02d",order),15,TEAL,true);
            number.setGravity(Gravity.CENTER);
            number.setBackground(rounded(CARD2,11,0));
            header.addView(number,new LinearLayout.LayoutParams(dp(40),dp(44)));

            LinearLayout titleBox=col();
            LinearLayout.LayoutParams tb=new LinearLayout.LayoutParams(0,-2,1);
            tb.leftMargin=dp(12);
            header.addView(titleBox,tb);
            TextView title=text(ex.name,18,TEXT,true);
            title.setMaxLines(3);
            add(titleBox,title);
            space(titleBox,4);
            TextView done=text(db.countExerciseSets(openSession,ex.id)+"/"+ex.sets+" serii",13,TEAL,true);
            add(titleBox,done);

            TextView chevron=text(expandedExerciseId.equals(ex.id)?"⌃":"⌄",23,MUTED,false);
            chevron.setGravity(Gravity.CENTER);
            header.addView(chevron,new LinearLayout.LayoutParams(dp(31),dp(44)));
            add(c,header);
            LinearLayout body=col();
            boolean expanded=expandedExerciseId.equals(ex.id);
            body.setVisibility(expanded?View.VISIBLE:View.GONE);
            if(expanded) {expandedBody=body;expandedChevron=chevron;}
            space(body,13);
            add(body,text(ex.goal(),14,TEAL,true));
            space(body,9);
            TextView desc=text(ex.description,15,MUTED,false);
            desc.setLineSpacing(dp(3),1f);
            add(body,desc);
            space(body,13);

            LinearLayout rows=col();
            add(body,rows);
            final int[] setNumber={db.setRows(openSession,ex.id,ex.sets)};
            for(int j=0;j<setNumber[0];j++) addSetRow(rows,ex,j,done);
            Button addExtra=button("+  Dodaj serię",false);
            add(body,addExtra);
            addExtra.setOnClickListener(v->addSetRow(rows,ex,setNumber[0]++,done));
            add(c,body);

            header.setPadding(0,dp(3),0,dp(3));
            header.setOnClickListener(v->{
                if(expandedBody!=null) {
                    expandedBody.setVisibility(View.GONE);
                    if(expandedChevron!=null) expandedChevron.setText("⌄");
                }
                if(expandedBody==body) {
                    expandedExerciseId="";
                    expandedBody=null;expandedChevron=null;
                } else {
                    expandedExerciseId=ex.id;
                    body.setVisibility(View.VISIBLE);
                    chevron.setText("⌃");
                    expandedBody=body;expandedChevron=chevron;
                }
            });
        }

        space(content,6);
        Button finish=button("Zakończ trening ✓",true);
        add(content,finish);
        finish.setOnClickListener(v->finishWorkout());
        space(content,8);
        Button pause=button("Wstrzymaj i wróć do planu",false);
        add(content,pause);
        pause.setOnClickListener(v->pauseWorkout());
    }

    private void addSetRow(LinearLayout target,Plan.Exercise ex,int index,TextView exerciseStatus) {
        WorkoutDb.Entry current=db.get(openSession,ex.id,index);
        WorkoutDb.Entry previous=db.previous(ex.id,index);
        LinearLayout block=col();
        block.setPadding(dp(13),dp(12),dp(13),dp(13));
        block.setBackground(rounded(CARD2,13,0));
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);
        bp.bottomMargin=dp(10);
        target.addView(block,bp);

        LinearLayout header=row();
        TextView series=text("SERIA "+(index+1),14,TEXT,true);
        header.addView(series,new LinearLayout.LayoutParams(0,dp(30),1));
        TextView status=text(current!=null?"✓ Zapisana":"Do wykonania",13,current!=null?TEAL:MUTED,true);
        status.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        header.addView(status);
        add(block,header);
        if(previous!=null) {
            TextView prev=text("Poprzednio: "+weight(previous.weight)+" kg  ×  "+previous.reps,13,MUTED,false);
            add(block,prev);
            space(block,7);
        } else space(block,6);

        LinearLayout inputs=row();
        LinearLayout left=col();
        add(left,text("CIĘŻAR  •  KG",12,MUTED,true));
        space(left,5);
        EditText kg=field(current!=null?weight(current.weight):(previous!=null?weight(previous.weight):""),"0",true);
        kg.setContentDescription("Ciężar w kilogramach, seria "+(index+1)+" "+ex.name);
        add(left,kg);
        inputs.addView(left,new LinearLayout.LayoutParams(0,-2,1));

        LinearLayout right=col();
        add(right,text("POWTÓRZENIA",12,MUTED,true));
        space(right,5);
        EditText reps=field(current!=null?Integer.toString(current.reps):
            (previous!=null?Integer.toString(previous.reps):""),"0",false);
        reps.setContentDescription("Powtórzenia, seria "+(index+1)+" "+ex.name);
        add(right,reps);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-2,1);
        rp.leftMargin=dp(10);
        inputs.addView(right,rp);
        add(block,inputs);
        space(block,10);

        Button save=button(current!=null?"Zmień zapisaną serię":"✓  Zapisz serię",current==null);
        add(block,save);
        save.setContentDescription("Zapisz lub edytuj serię "+(index+1)+" ćwiczenia "+ex.name);
        save.setOnClickListener(v->{
            try {
                String input=kg.getText().toString().trim().replace(',','.');
                if(input.isEmpty()) throw new NumberFormatException();
                double w=Double.parseDouble(input);
                int rep=Integer.parseInt(reps.getText().toString().trim());
                if(!Double.isFinite(w)||w<0||w>2000||rep<1||rep>1000)
                    throw new NumberFormatException();
                boolean first=db.get(openSession,ex.id,index)==null;
                db.save(openSession,ex.id,index,w,rep);
                status.setText("✓ Zapisana");
                status.setTextColor(TEAL);
                save.setText("Zmień zapisaną serię");
                save.setTextColor(TEXT);
                save.setBackgroundTintList(ColorStateList.valueOf(CARD));
                exerciseStatus.setText(db.countExerciseSets(openSession,ex.id)+"/"+ex.sets+" serii");
                updateSessionProgress();
                if(first)startRest(ex.restSeconds);
                Toast.makeText(this,"Seria zapisana ✓",Toast.LENGTH_SHORT).show();
            }catch(Exception error) {
                Toast.makeText(this,"Sprawdź ciężar i liczbę powtórzeń",Toast.LENGTH_LONG).show();
            }
        });
        save.setOnLongClickListener(v->{
            new AlertDialog.Builder(this).setTitle("Usunąć tę serię?")
                .setMessage("Dłuższe przytrzymanie pozwala usunąć zapis.")
                .setNegativeButton("Anuluj",null)
                .setPositiveButton("Usuń",(d,w)->{
                    db.delete(openSession,ex.id,index);
                    status.setText("Do wykonania");
                    status.setTextColor(MUTED);
                    save.setText("✓  Zapisz serię");
                    save.setTextColor(BG);
                    save.setBackgroundTintList(ColorStateList.valueOf(TEAL));
                    exerciseStatus.setText(db.countExerciseSets(openSession,ex.id)+"/"+ex.sets+" serii");
                    updateSessionProgress();
                }).show();
            return true;
        });
    }

    private void showSessionActions() {
        String[] options={"Wstrzymaj trening","Zakończ i zapisz","Anuluj i usuń trening"};
        new AlertDialog.Builder(this)
            .setTitle("Opcje treningu")
            .setItems(options,(dialog,which)->{
                if(which==0)pauseWorkout();
                else if(which==1)finishWorkout();
                else cancelWorkout();
            })
            .setNegativeButton("Wróć",null).show();
    }
    private void pauseWorkout() {
        if(openSession<0)return;
        db.pause(openSession);
        stopRest();
        expandedExerciseId="";
        openSession=-1;
        page="plan";
        render();
        Toast.makeText(this,"Trening wstrzymany — możesz go wznowić później",Toast.LENGTH_LONG).show();
    }
    private void cancelWorkout() {
        if(openSession<0)return;
        final long id=openSession;
        new AlertDialog.Builder(this)
            .setTitle("Anulować trening?")
            .setMessage("Wszystkie serie zapisane w tej rozpoczętej sesji zostaną usunięte. Ta czynność jest nieodwracalna.")
            .setNegativeButton("Zostaw trening",null)
            .setPositiveButton("Usuń trening",(d,w)->{
                db.deleteSession(id);
                stopRest();
                openSession=-1;
                expandedExerciseId="";
                page="plan";
                render();
                if(cloud.signedIn())runCloudSync();
            }).show();
    }
    private void finishWorkout() {
        if(openSession<0)return;
        final long id=openSession;
        final int count=db.countSets(id);
        if(count==0) {
            new AlertDialog.Builder(this)
                .setTitle("Ten trening jest pusty")
                .setMessage("Nie zapisano żadnej serii. Zamiast tworzyć pusty wpis w historii, możesz usunąć sesję.")
                .setNegativeButton("Wróć do treningu",null)
                .setPositiveButton("Usuń pustą sesję",(d,w)->{
                    db.deleteSession(id);
                    stopRest();
                    openSession=-1;
                    expandedExerciseId="";
                    page="plan";
                    render();
                    if(cloud.signedIn())runCloudSync();
                }).show();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Zakończyć trening?")
            .setMessage("Zapisane serie: "+count+". Trening trafi do historii i można go będzie później edytować.")
            .setNegativeButton("Wróć",null)
            .setPositiveButton("Zakończ",(d,w)->{
                db.finish(id);
                stopRest();
                openSession=-1;
                expandedExerciseId="";
                page="history";
                render();
                if(cloud.signedIn())runCloudSync();
            }).show();
    }

    private void startRest(int seconds) {
        restUntil=System.currentTimeMillis()+seconds*1000L;
        getSharedPreferences("forma_timer",0).edit().putLong("restUntil",restUntil).apply();
        if(timerLabel!=null) timerLabel.setText(formatTime(seconds));
    }
    private void stopRest() {
        restUntil=0;
        getSharedPreferences("forma_timer",0).edit().remove("restUntil").apply();
        if(timerLabel!=null) timerLabel.setText("00:00");
    }
    private String date(long millis) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT,Locale.forLanguageTag("pl-PL"))
                .format(new Date(millis));
    }

    private void showHistory() {
        space(content,8);
        headline(content,"Historia treningów");
        add(content,text("Twoje zapisane sesje",15,MUTED,false));
        space(content,16);
        List<WorkoutDb.Session> list=db.completed();
        if(list.isEmpty()) {
            LinearLayout empty=card(content);
            add(empty,text("Jeszcze nie masz zakończonych sesji.",18,TEXT,true));
            space(empty,9);
            add(empty,text("Wybierz dzień z zakładki Plan i zapisz pierwsze serie.",15,MUTED,false));
            return;
        }
        for(WorkoutDb.Session session:list) {
            LinearLayout c=card(content);
            LinearLayout cardTop=row();
            TextView name=text(Plan.title(session.workout),13,TEAL,true);
            cardTop.addView(name,new LinearLayout.LayoutParams(0,dp(42),1));
            TextView more=text("⋮",27,TEAL,true);
            more.setGravity(Gravity.CENTER);
            more.setContentDescription("Opcje: edytuj lub usuń trening");
            cardTop.addView(more,new LinearLayout.LayoutParams(dp(44),dp(42)));
            more.setOnClickListener(v->showCompletedActions(session));
            add(c,cardTop);
            space(c,4);
            boolean imported=session.notes!=null&&session.notes.startsWith("Import z notatek 18.11.2026");
            String when=imported?
                new SimpleDateFormat("dd.MM.yyyy",Locale.forLanguageTag("pl-PL")).format(new Date(session.startedAt))+" • import":
                date(session.startedAt);
            add(c,text(when,18,TEXT,true));
            space(c,12);
            summaryLine(c,"Zapisane serie",Integer.toString(session.sets));
            long mins=Math.max(0,(session.finishedAt-session.startedAt)/60000);
            summaryLine(c,"Czas",imported?"Nieznany":formatTime(db.durationMillis(session)/1000));
            space(c,10);
            Button details=button(session.sets==0?"Edytuj pustą sesję →":"Zobacz serie →",false);
            add(c,details);
            details.setOnClickListener(v->{detailSession=session.id;page="detail";render();});
        }
    }

    private void showDetail() {
        TextView back=text("‹  Wróć do historii",15,TEAL,true);
        add(content,back);
        back.setOnClickListener(v->{page="history";render();});
        space(content,13);
        headline(content,"Zapisane serie");
        WorkoutDb.Session selected=db.byId(detailSession);
        if(selected==null){page="history";render();return;}
        LinearLayout actions=row();
        Button edit=button("Edytuj trening",true);
        actions.addView(edit,new LinearLayout.LayoutParams(0,dp(53),1));
        edit.setOnClickListener(v->{page="edit";render();});
        Button del=button("Usuń",false);
        LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(dp(91),dp(53));
        dlp.leftMargin=dp(8);actions.addView(del,dlp);
        del.setOnClickListener(v->confirmDeleteCompleted(selected));
        add(content,actions);
        space(content,13);
        String notes=db.sessionNotes(detailSession);
        if(notes!=null&&!notes.isEmpty()) {
            LinearLayout c=card(content);
            add(c,text(notes,14,MUTED,false));
        }
        List<WorkoutDb.Entry> entries=db.sets(detailSession);
        if(entries.isEmpty()) {
            add(content,text("Nie zapisano żadnych serii.",15,MUTED,false));
            return;
        }
        String previous="";
        LinearLayout current=null;
        for(WorkoutDb.Entry entry:entries) {
            if(!previous.equals(entry.exerciseId)) {
                Plan.Exercise ex=Plan.byId(entry.exerciseId);
                current=card(content);
                add(current,text(ex==null?entry.exerciseId:ex.name,18,TEXT,true));
                space(current,10);
                previous=entry.exerciseId;
            }
            if(current!=null) {
                LinearLayout row=row();
                TextView left=text("Seria "+(entry.setIndex+1),14,MUTED,false);
                row.addView(left,new LinearLayout.LayoutParams(0,dp(35),1));
                TextView right=text(weight(entry.weight)+" kg  ×  "+entry.reps,16,TEAL,true);
                row.addView(right);
                add(current,row);
            }
        }
    }


    private void showCompletedActions(WorkoutDb.Session session) {
        new AlertDialog.Builder(this)
            .setTitle(Plan.title(session.workout))
            .setItems(new String[]{"Zobacz serie","Edytuj trening","Usuń trening"},(d,index)->{
                if(index==0){detailSession=session.id;page="detail";render();}
                else if(index==1){detailSession=session.id;page="edit";render();}
                else confirmDeleteCompleted(session);
            })
            .setNegativeButton("Anuluj",null).show();
    }

    private void confirmDeleteCompleted(WorkoutDb.Session session) {
        new AlertDialog.Builder(this)
            .setTitle("Usunąć trening?")
            .setMessage(Plan.title(session.workout)+" • "+date(session.startedAt)+
                "\n\nUsunięte zostaną wszystkie serie oraz ich wyniki w progresie. "+
                "Jeżeli korzystasz z chmury, usunięcie zostanie przesłane także do Supabase.")
            .setNegativeButton("Anuluj",null)
            .setPositiveButton("Usuń na stałe",(d,w)->{
                db.deleteSession(session.id);
                detailSession=-1;
                page="history";
                render();
                if(cloud.signedIn())runCloudSync();
            }).show();
    }

    private void completeEditing() {
        detailSession=-1;
        page="history";
        render();
        if(cloud.signedIn())runCloudSync();
    }

    private void changeWorkoutDate(WorkoutDb.Session session) {
        Calendar cal=Calendar.getInstance();
        cal.setTimeInMillis(session.startedAt);
        DatePickerDialog dialog=new DatePickerDialog(this,(picker,year,month,day)->{
            Calendar changed=Calendar.getInstance();
            changed.setTimeInMillis(session.startedAt);
            changed.set(Calendar.YEAR,year);
            changed.set(Calendar.MONTH,month);
            changed.set(Calendar.DAY_OF_MONTH,day);
            db.updateCompleted(session.id,changed.getTimeInMillis(),session.notes);
            render();
        },cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    private void changeWorkoutNote(WorkoutDb.Session session) {
        EditText note=new EditText(this);
        note.setText(session.notes==null?"":session.notes);
        note.setTextSize(16);
        note.setTextColor(TEXT);
        note.setHint("Notatka do treningu");
        note.setHintTextColor(MUTED);
        note.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        note.setMinLines(2);
        note.setMaxLines(6);
        LinearLayout wrap=col();
        wrap.setPadding(dp(19),dp(10),dp(19),dp(3));
        add(wrap,note);
        new AlertDialog.Builder(this)
            .setTitle("Notatka")
            .setView(wrap)
            .setNegativeButton("Anuluj",null)
            .setPositiveButton("Zapisz",(d,w)->{
                db.updateCompleted(session.id,session.startedAt,note.getText().toString());
                render();
            }).show();
    }

    private void editSavedSet(WorkoutDb.Session session,Plan.Exercise ex,int index,WorkoutDb.Entry previous) {
        LinearLayout form=col();
        form.setPadding(dp(20),dp(12),dp(20),0);
        add(form,text("CIĘŻAR (KG)",13,MUTED,true));
        space(form,5);
        EditText kg=field(previous==null?"":weight(previous.weight),"kg",true);
        add(form,kg);
        space(form,12);
        add(form,text("POWTÓRZENIA",13,MUTED,true));
        space(form,5);
        EditText reps=field(previous==null?"":Integer.toString(previous.reps),"powt.",false);
        add(form,reps);
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle(ex.name+" • seria "+(index+1))
            .setView(form)
            .setNegativeButton("Anuluj",null)
            .setPositiveButton("Zapisz",null)
            .create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try {
                String raw=kg.getText().toString().trim().replace(',','.');
                if(raw.isEmpty())throw new NumberFormatException();
                double value=Double.parseDouble(raw);
                int repeats=Integer.parseInt(reps.getText().toString().trim());
                if(!Double.isFinite(value)||value<0||value>2000||repeats<1||repeats>1000)
                    throw new NumberFormatException();
                db.save(session.id,ex.id,index,value,repeats);
                dialog.dismiss();
                render();
            } catch(Exception e) {
                Toast.makeText(this,"Wpisz poprawny ciężar i powtórzenia",Toast.LENGTH_LONG).show();
            }
        }));
        dialog.show();
    }

    private void showCompletedEditor() {
        WorkoutDb.Session session=db.byId(detailSession);
        if(session==null){page="history";render();return;}
        space(content,5);
        TextView back=text("‹  Historia",15,TEAL,true);
        add(content,back);
        back.setOnClickListener(v->completeEditing());
        space(content,11);
        headline(content,"Edytuj • "+Plan.title(session.workout));
        add(content,text("Zmieniaj serie, ciężary, datę i notatkę. Zmiany zapisują się lokalnie.",14,MUTED,false));
        space(content,15);
        LinearLayout dateCard=card(content);
        smallLabel(dateCard,"SZCZEGÓŁY SESJI");
        space(dateCard,7);
        add(dateCard,text(new SimpleDateFormat("dd.MM.yyyy",Locale.forLanguageTag("pl-PL"))
            .format(new Date(session.startedAt)),20,TEXT,true));
        space(dateCard,10);
        Button changeDate=button("Zmień datę treningu",false);
        add(dateCard,changeDate);
        changeDate.setOnClickListener(v->changeWorkoutDate(session));
        space(dateCard,7);
        Button note=button(session.notes==null||session.notes.isEmpty()?"Dodaj notatkę":"Edytuj notatkę",false);
        add(dateCard,note);
        note.setOnClickListener(v->changeWorkoutNote(session));

        for(Plan.Exercise ex:Plan.exercises(session.workout)) {
            LinearLayout c=card(content);
            add(c,text(ex.name,18,TEXT,true));
            space(c,9);
            int count=0;
            for(WorkoutDb.Entry entry:db.sets(session.id)) {
                if(!entry.exerciseId.equals(ex.id))continue;
                count++;
                LinearLayout line=row();
                TextView value=text((entry.setIndex+1)+".  "+weight(entry.weight)+" kg × "+entry.reps,16,TEXT,true);
                line.addView(value,new LinearLayout.LayoutParams(0,dp(49),1));
                Button edit=button("Edytuj",false);
                line.addView(edit,new LinearLayout.LayoutParams(dp(91),dp(49)));
                edit.setOnClickListener(v->editSavedSet(session,ex,entry.setIndex,entry));
                TextView remove=text("×",23,MUTED,true);
                remove.setGravity(Gravity.CENTER);
                line.addView(remove,new LinearLayout.LayoutParams(dp(43),dp(49)));
                remove.setContentDescription("Usuń serię "+(entry.setIndex+1));
                remove.setOnClickListener(v->new AlertDialog.Builder(this)
                    .setTitle("Usunąć serię?")
                    .setMessage("Seria "+(entry.setIndex+1)+" • "+weight(entry.weight)+" kg × "+entry.reps)
                    .setNegativeButton("Zostaw",null)
                    .setPositiveButton("Usuń",(d,w)->{
                        db.delete(session.id,ex.id,entry.setIndex);
                        render();
                    }).show());
                add(c,line);
            }
            if(count==0) {
                add(c,text("Nie zapisano serii",14,MUTED,false));
                space(c,7);
            }
            Button addSet=button("+ Dodaj serię",false);
            add(c,addSet);
            addSet.setOnClickListener(v->{
                int next=db.setRows(session.id,ex.id,0);
                editSavedSet(session,ex,next,null);
            });
        }
        Button done=button("Zapisz zmiany i wróć",true);
        add(content,done);
        done.setOnClickListener(v->completeEditing());
        space(content,9);
        Button remove=button("Usuń cały trening",false);
        add(content,remove);
        remove.setOnClickListener(v->confirmDeleteCompleted(session));
    }

    private void showProgress() {
        space(content,8);
        headline(content,"Progres");
        add(content,text("Porównaj wyniki z poprzednich treningów",15,MUTED,false));
        space(content,17);
        List<Plan.Exercise> exercises=Plan.uniqueExercises();
        String[] options=new String[exercises.size()];
        String display="";
        for(int i=0;i<exercises.size();i++) {
            options[i]=exercises.get(i).name;
            if(exercises.get(i).id.equals(selectedExercise)) display=options[i];
        }
        LinearLayout selector=card(content);
        smallLabel(selector,"ĆWICZENIE");
        space(selector,7);
        Button choose=button((display.isEmpty()?"Wybierz ćwiczenie":display)+"  ⌄",false);
        add(selector,choose);
        choose.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle("Wybierz ćwiczenie")
            .setItems(options,(dialog,index)->{
                selectedExercise=exercises.get(index).id;
                render();
            }).show());
        LinearLayout results=col();
        add(content,results);
        drawProgress(results,selectedExercise);
    }

    private void drawProgress(LinearLayout target,String ex) {
        target.removeAllViews();
        List<WorkoutDb.Progress> list=db.progress(ex);
        if(list.isEmpty()) {
            LinearLayout empty=card(target);
            add(empty,text("Brak zapisanych wyników",18,TEXT,true));
            space(empty,9);
            add(empty,text("Wykonaj to ćwiczenie w treningu, a pojawią się tutaj ciężary i powtórzenia.",15,MUTED,false));
            return;
        }
        WorkoutDb.Progress latest=list.get(0);
        LinearLayout hero=card(target);
        smallLabel(hero,"OSTATNIO");
        space(hero,8);
        add(hero,text(weight(latest.maxWeight)+" kg",29,TEXT,true));
        space(hero,3);
        add(hero,text("× "+latest.repsAtMax+" powtórzeń  •  najlepsza seria",15,TEAL,true));
        space(hero,5);
        add(hero,text(date(latest.date),13,MUTED,false));
        space(target,6);
        add(target,text("Poprzednie sesje",20,TEXT,true));
        space(target,10);
        for(int i=0;i<Math.min(list.size(),20);i++) {
            WorkoutDb.Progress entry=list.get(i);
            LinearLayout c=card(target);
            add(c,text(date(entry.date),14,MUTED,false));
            space(c,7);
            add(c,text(weight(entry.maxWeight)+" kg  ×  "+entry.repsAtMax,19,TEXT,true));
            space(c,4);
            add(c,text(entry.numberOfSets+" serii  •  "+entry.totalReps+" powt. łącznie",13,TEAL,false));
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
        if(page.equals("session")||page.equals("detail")||page.equals("progress")||page.equals("history")||page.equals("settings")) {
            page=page.equals("detail")?"history":"plan";
            render();
        } else super.onBackPressed();
    }
}
