package pl.pitsmich.forma;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.content.Intent;
import android.content.ClipData;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.widget.ImageView;
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
import android.view.DragEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.GridLayout;
import android.widget.NumberPicker;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Switch;
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
    private static final int CARD2=Color.rgb(26,26,26);
    private static final int TEAL=Color.rgb(120,196,177);
    private static final int TEXT=Color.rgb(250,253,255);
    private static final int MUTED=Color.rgb(169,169,169);
    private static final int BORDER=Color.rgb(58,58,58);
    private static final int GREEN=Color.rgb(128,187,146);
    private static final int AMBER=Color.rgb(213,171,112);
    private static final int RED=Color.rgb(247,113,113);
    private static final int SKY=Color.rgb(146,164,181);
    private static final int VIOLET=Color.rgb(165,155,185);
    private static final int EXPORT_REQUEST=1901;
    private static final int BACKUP_REQUEST=1902;
    private static final int RESTORE_REQUEST=1903;
    private int editingWorkout=Plan.UPPER_A;
    private String editingSetExercise="";

    private int editingSetIndex=-1;
    private boolean canUndoSet=false;
    private long undoSession=-1;
    private String undoExercise="";
    private int undoIndex=-1;
    private WorkoutDb.Entry undoPrevious;
    private int calendarMonth=Calendar.getInstance().get(Calendar.MONTH);
    private int calendarYear=Calendar.getInstance().get(Calendar.YEAR);
    private String settingsSection="";
    private final java.util.HashSet<String> collapsedDetailExercises=new java.util.HashSet<>();
    private int weeklyOffset=0;
    private ProgressBar restProgress;

    private WorkoutDb db;
    private Updater updater;
    private CloudSync cloud;
    private LinearLayout root, content;
    private ScrollView contentScroll;
    private LinearLayout expandedCard;
    private String page="plan";
    private long openSession=-1, detailSession=-1;
    private int openWorkout=Plan.UPPER_A;
    private String selectedExercise="db_flat";
    private int selectedProgressWorkout=Plan.UPPER_A;
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
            RestTimer.finishIfExpired(MainActivity.this);
            restUntil=RestTimer.endTime(MainActivity.this);
            if(timerLabel!=null)
                timerLabel.setText(restUntil==0?"00:00":formatTime(RestTimer.secondsRemaining(MainActivity.this)));
            if(restProgress!=null) {
                long remaining=RestTimer.secondsRemaining(MainActivity.this);
                int total=getSharedPreferences("forma_settings",0).getInt("rest_total",90);
                restProgress.setMax(Math.max(1,total));
                restProgress.setProgress((int)Math.max(0,Math.min(total,total-remaining)));
                restProgress.setProgressTintList(ColorStateList.valueOf(remaining>0?TEAL:BORDER));
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
        RestTimer.reschedule(this);
        restUntil=RestTimer.endTime(this);
        if(state!=null) {
            page=state.getString("page","plan");
            openSession=state.getLong("openSession",-1);
            openWorkout=state.getInt("openWorkout",Plan.UPPER_A);
            detailSession=state.getLong("detailSession",-1);
            selectedExercise=state.getString("selectedExercise","db_flat");
            selectedProgressWorkout=state.getInt("selectedProgressWorkout",Plan.UPPER_A);
            expandedExerciseId=state.getString("expandedExerciseId","");
            editingWorkout=state.getInt("editingWorkout",Plan.UPPER_A);
            editingSetExercise=state.getString("editingSetExercise","");
            editingSetIndex=state.getInt("editingSetIndex",-1);
            calendarMonth=state.getInt("calendarMonth",calendarMonth);
            calendarYear=state.getInt("calendarYear",calendarYear);
            settingsSection=state.getString("settingsSection","");
            weeklyOffset=state.getInt("weeklyOffset",0);
        }
        if(RestTimer.ACTION_OPEN_SESSION.equals(getIntent()==null?null:getIntent().getAction()))
            openSessionFromNotification();
        render();
        syncWorkoutNotification();
        handler.post(ticker);
        updater.check(false);
    }

    private void syncWorkoutNotification() {
        WorkoutDb.Session active=db.active();
        if(active!=null && active.pausedAt==0){
            try {WorkoutStatusService.start(this);}
            catch(RuntimeException ignored){
                Toast.makeText(this,"Android zablokował powiadomienie treningu",Toast.LENGTH_SHORT).show();
            }
        } else WorkoutStatusService.stop(this);
    }

    private void askWorkoutNotificationPermission() {
        if(Build.VERSION.SDK_INT>=33&&!RestTimer.canNotify(this)){
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},6071);
        }
    }

    private void openSessionFromNotification() {
        WorkoutDb.Session active=db.active();
        if(active!=null) {
            if(active.pausedAt>0)db.resume(active.id);
            openSession=active.id;
            openWorkout=active.workout;
            page="session";
        } else page="plan";
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if(RestTimer.ACTION_OPEN_SESSION.equals(intent.getAction())){
            openSessionFromNotification();
            render();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==6071 && RestTimer.canNotify(this)){
            RestTimer.reschedule(this);
            syncWorkoutNotification();
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("page",page);
        state.putLong("openSession",openSession);
        state.putInt("openWorkout",openWorkout);
        state.putLong("detailSession",detailSession);
        state.putString("selectedExercise",selectedExercise);
        state.putInt("selectedProgressWorkout",selectedProgressWorkout);
        state.putString("expandedExerciseId",expandedExerciseId);
        state.putInt("editingWorkout",editingWorkout);
        state.putString("editingSetExercise",editingSetExercise);
        state.putInt("editingSetIndex",editingSetIndex);
        state.putInt("calendarMonth",calendarMonth);
        state.putInt("calendarYear",calendarYear);
        state.putString("settingsSection",settingsSection);
        state.putInt("weeklyOffset",weeklyOffset);
        super.onSaveInstanceState(state);
    }
    @Override protected void onResume() {
        super.onResume();
        if(updater!=null) updater.afterPermissionReturn();
        RestTimer.reschedule(this);
        // Only a foreground Activity can safely start an ongoing user-initiated FGS.
        syncWorkoutNotification();
    }
    @Override protected void onDestroy() {
        handler.removeCallbacks(ticker);
        if(cloud!=null)cloud.close();
        db.close();
        super.onDestroy();
    }
    private int dp(float size) { return (int)(size*getResources().getDisplayMetrics().density+0.5f); }
    private boolean compactUi(){
        return getSharedPreferences("forma_settings",0).getBoolean("compact_ui",true);
    }
    private int p(int compact,int standard){return dp(compactUi()?compact:standard);}
    private int typeScale(int size){
        int adjustment=getSharedPreferences("forma_settings",0).getInt("font_scale",0);
        return Math.max(10,size+adjustment);
    }

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
        t.setText(s); t.setTextSize(typeScale(sp)); t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if(strong) t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private void space(LinearLayout target,int height) {
        View v=new View(this); target.addView(v,new LinearLayout.LayoutParams(1,dp(height)));
    }
    private LinearLayout card(LinearLayout target) {
        LinearLayout c=col(); c.setPadding(p(13,18),p(11,17),p(13,18),p(11,17));
        c.setBackground(rounded(CARD,15,BORDER));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=p(8,13);
        target.addView(c,lp);
        return c;
    }
    private void add(LinearLayout target,View child) { target.addView(child,new LinearLayout.LayoutParams(-1,-2)); }
    private void headline(LinearLayout target,String s) {
        add(target,text(s,22,TEXT,true)); space(target,4);
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
        b.setMinHeight(dp(43));
        return b;
    }

    private void render() {
        timerLabel=null; sessionCount=null; elapsedLabel=null; sessionProgress=null; restProgress=null;
        expandedBody=null; expandedChevron=null; expandedCard=null; contentScroll=null;
        root=col();
        root.setBackgroundColor(BG);
        root.setPadding(dp(16),dp(9),dp(16),dp(8));
        root.setOnApplyWindowInsetsListener((v,insets)->{
            root.setPadding(dp(16),insets.getSystemWindowInsetTop()+dp(8),
                dp(16),insets.getSystemWindowInsetBottom()+dp(8));
            return insets;
        });
        setContentView(root);

        LinearLayout brand=row();
        TextView logo=text("FORMA",20,TEAL,true);
        logo.setLetterSpacing(0.10f);
        brand.addView(logo,new LinearLayout.LayoutParams(0,dp(39),1));
        TextView version=text(BuildConfig.VERSION_NAME,12,MUTED,true);
        version.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        brand.addView(version,new LinearLayout.LayoutParams(dp(54),dp(44)));
        if(page.equals("settings")) {
            version.setContentDescription("Wersja "+BuildConfig.VERSION_NAME+". Zaawansowane ustawienia OTA");
            version.setOnClickListener(v->updater.configure());
        }
        if(page.equals("settings")) {
            TextView update=text("↻",29,TEAL,true);
            update.setGravity(Gravity.CENTER);
            update.setContentDescription("Sprawdź aktualizacje OTA");
            update.setOnClickListener(v->updater.check(true));
            brand.addView(update,new LinearLayout.LayoutParams(dp(44),dp(44)));
        }
        TextView gear=text(page.equals("settings")?"‹":"⚙",26,TEAL,true);
        gear.setGravity(Gravity.CENTER);
        gear.setContentDescription(page.equals("settings")?"Wróć do planu":"Ustawienia aplikacji");
        brand.addView(gear,new LinearLayout.LayoutParams(dp(47),dp(44)));
        gear.setOnClickListener(v->{
            if(page.equals("settings")&&!settingsSection.isEmpty())settingsSection="";
            else page=page.equals("settings")?"plan":"settings";
            render();
        });
        if(!page.equals("session")) {
            add(root,brand);
            space(root,5);
        }

        if(page.equals("session")) {
            WorkoutDb.Session active=db.active();
            if(active==null || active.id!=openSession || active.pausedAt>0) page="plan";
            else showFixedSessionHeader();
        }
        ScrollView scroll=new ScrollView(this);
        contentScroll=scroll;
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
        else if(page.equals("exerciseProgress")) showExerciseProgress();
        else if(page.equals("edit")) showCompletedEditor();
        else if(page.equals("planEditor")) showPlanEditor();
        else if(page.equals("planPreview")) showPlanPreview();
        else if(page.equals("calendar")) showCalendar();
        else if(page.equals("summary")) showSessionSummary();
        else if(page.equals("weekly")) showWeeklyAnalysis();
        else showSettings();

        if(!page.equals("session") && !page.equals("edit") && !page.equals("planEditor") && !page.equals("summary")) showNavigation();
    }

    private void showNavigation() {
        LinearLayout nav=row();
        nav.setPadding(dp(4),dp(9),dp(4),dp(2));
        View separator=new View(this);
        separator.setBackgroundColor(BORDER);
        root.addView(separator,new LinearLayout.LayoutParams(-1,dp(1)));
        String[] names={"Plan","Historia","Progres","Kalendarz"};
        String[] icons={"▦","◷","↗","▤"};
        String[] pages={"plan","history","progress","calendar"};
        for(int i=0;i<names.length;i++) {
            final String selected=pages[i];
            boolean active=page.equals(selected) || (selected.equals("history") && page.equals("detail")) || (selected.equals("progress") && (page.equals("exerciseProgress") || page.equals("weekly")));
            LinearLayout item=col();
            item.setGravity(Gravity.CENTER);
            TextView symbol=text(icons[i],21,active?TEAL:MUTED,active);
            symbol.setGravity(Gravity.CENTER);
            symbol.setMinHeight(dp(28));
            add(item,symbol);
            TextView name=text(names[i],12,active?TEAL:MUTED,active);
            name.setGravity(Gravity.CENTER);
            add(item,name);
            space(item,4);
            View indicator=new View(this);
            indicator.setBackground(rounded(active?TEAL:BG,3,0));
            item.addView(indicator,new LinearLayout.LayoutParams(dp(active?29:14),dp(3)));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(57),1);
            nav.addView(item,lp);
            item.setContentDescription("Otwórz "+names[i]+(active?", aktywna zakładka":""));
            item.setClickable(true);
            item.setFocusable(true);
            item.setForeground(getDrawable(android.R.drawable.list_selector_background));
            item.setOnClickListener(v->{if(!page.equals(selected)){page=selected;render();}});
        }
        add(root,nav);
    }

    private void smallLabel(LinearLayout target,String value) {
        TextView t=text(value,12,TEAL,true);
        t.setLetterSpacing(0.08f);
        add(target,t);
    }
    private int dayColor(int workout) {
        return workout==Plan.LEGS?SKY:(workout==Plan.UPPER_B?VIOLET:TEAL);
    }
    private void tintCard(LinearLayout c, int color) {
        c.setBackground(rounded(CARD,15,color));
    }
    private TextView actionLabel(String text,int color) {
        TextView v=text(text,13,color,true);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }
    private void clickCard(LinearLayout c, Runnable action) {
        c.setClickable(true);
        c.setFocusable(true);
        c.setForeground(getDrawable(android.R.drawable.list_selector_background));
        c.setOnClickListener(v->action.run());
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
    private void showActionSheet(String title,String[] options,java.util.function.IntConsumer onSelect) {
        LinearLayout sheet=col();
        sheet.setPadding(dp(15),dp(14),dp(15),dp(14));
        sheet.setBackground(rounded(CARD,20,BORDER));
        TextView heading=text(title,18,TEXT,true);
        heading.setPadding(dp(8),dp(4),dp(8),dp(12));
        add(sheet,heading);
        AlertDialog dialog=new AlertDialog.Builder(this).setView(sheet).create();
        for(int i=0;i<options.length;i++) {
            final int choice=i;
            TextView option=text(options[i],15,TEXT,false);
            option.setMinHeight(dp(54));
            option.setPadding(dp(13),dp(12),dp(13),dp(12));
            option.setBackground(rounded(CARD2,10,0));
            option.setClickable(true);
            option.setFocusable(true);
            LinearLayout.LayoutParams optionLp=new LinearLayout.LayoutParams(-1,-2);
            optionLp.bottomMargin=dp(7);
            sheet.addView(option,optionLp);
            option.setOnClickListener(v->{dialog.dismiss();onSelect.accept(choice);});
        }
        dialog.show();
        Window w=dialog.getWindow();
        if(w!=null){
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(getResources().getDisplayMetrics().widthPixels-dp(16),
                ViewGroup.LayoutParams.WRAP_CONTENT);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes=w.getAttributes();
            attributes.dimAmount=0.62f;
            w.setAttributes(attributes);
        }
    }

    private void showPlan() {
        space(content,4);
        headline(content,"Twój plan");
        add(content,text("FBW 1.0  •  3 dni  •  57 serii tygodniowo",13,MUTED,false));
        space(content,11);

        WorkoutDb.Session active=db.active();
        if(active!=null) {
            LinearLayout resume=card(content);
            int stateColor=active.pausedAt>0?AMBER:GREEN;
            tintCard(resume,stateColor);
            LinearLayout top=row();
            TextView status=actionLabel(active.pausedAt>0?"Ⅱ  WSTRZYMANY":"●  W TOKU",stateColor);
            top.addView(status,new LinearLayout.LayoutParams(0,dp(31),1));
            TextView arrow=actionLabel("WZNÓW  ›",stateColor);
            top.addView(arrow);
            add(resume,top);
            add(resume,text(db.sessionWorkoutLabel(active)+"  ·  "+active.sets+" serii",19,TEXT,true));
            clickCard(resume,()->{
                if(active.pausedAt>0) db.resume(active.id);
                openSession=active.id;openWorkout=active.workout;
                expandedExerciseId="";page="session";render();
            });
        }
        space(content,6);
        for(WorkoutDb.ScheduleDay scheduled:db.schedule()) {
            final int workout=scheduled.workout;
            int accent=dayColor(workout);
            LinearLayout card=card(content);
            LinearLayout first=row();

            TextView day=actionLabel(weekdayName(scheduled.weekday).toUpperCase(Locale.forLanguageTag("pl-PL")),Color.rgb(193,193,193));
            day.setTextSize(17);
            day.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            day.setGravity(Gravity.CENTER_VERTICAL);
            first.addView(day,new LinearLayout.LayoutParams(0,dp(40),1));
            day.setOnClickListener(v->editScheduleDay(workout));

            TextView menu=actionLabel("⋯",MUTED);
            menu.setTextSize(23);menu.setGravity(Gravity.CENTER);
            menu.setContentDescription("Ustaw dzień, nazwę lub edytuj plan");
            first.addView(menu,new LinearLayout.LayoutParams(dp(44),dp(46)));
            menu.setOnClickListener(v->showActionSheet(scheduled.title,
                new String[]{"✎  Zmień dzień lub nazwę","☷  Edytuj ćwiczenia"},choice->{
                    if(choice==0)editScheduleDay(workout);else openPlanEditor(workout);
                }));

            TextView drag=actionLabel("⠿",MUTED);
            drag.setTextSize(24);drag.setGravity(Gravity.CENTER);
            drag.setContentDescription("Przytrzymaj uchwyt, aby przesunąć "+scheduled.title);
            first.addView(drag,new LinearLayout.LayoutParams(dp(49),dp(46)));
            drag.setOnLongClickListener(v->{
                ClipData payload=ClipData.newPlainText("forma-workout",Integer.toString(workout));
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                return v.startDragAndDrop(payload,new View.DragShadowBuilder(card),null,0);
            });
            add(card,first);

            add(card,text(scheduled.title,20,TEXT,true));
            space(card,4);
            int sets=0;
            for(Plan.Exercise ex:db.workoutPlan(workout))
                sets+=db.expectedSets(workout,ex);
            TextView details=text(db.workoutPlan(workout).size()+" ćwiczeń  ·  "+sets+
                " serii",12,MUTED,false);
            details.setMaxLines(2);
            add(card,details);
            space(card,12);
            LinearLayout actions=row();
            TextView preview=pill("☷  Podgląd",false);
            preview.setContentDescription("Zobacz ćwiczenia "+scheduled.title+" bez rozpoczynania treningu");
            LinearLayout.LayoutParams previewLp=new LinearLayout.LayoutParams(0,dp(48),1);
            previewLp.rightMargin=dp(8);
            actions.addView(preview,previewLp);
            preview.setOnClickListener(v->openPlanPreview(workout));
            TextView start=pill("▶  Start treningu",true);
            start.setContentDescription("Rozpocznij trening "+scheduled.title);
            actions.addView(start,new LinearLayout.LayoutParams(0,dp(48),1.3f));
            start.setOnClickListener(v->startWorkout(workout));
            add(card,actions);
            card.setOnClickListener(null);
            card.setOnDragListener((v,event)->{
                if(event.getAction()==DragEvent.ACTION_DRAG_STARTED)
                    return event.getClipDescription()!=null &&
                        event.getClipDescription().hasMimeType("text/plain");
                if(event.getAction()==DragEvent.ACTION_DRAG_ENTERED){
                    tintCard(card,accent);return true;
                }
                if(event.getAction()==DragEvent.ACTION_DRAG_EXITED ||
                    event.getAction()==DragEvent.ACTION_DRAG_ENDED){
                    card.setBackground(rounded(CARD,15,BORDER));return true;
                }
                if(event.getAction()==DragEvent.ACTION_DROP){
                    card.setBackground(rounded(CARD,15,BORDER));
                    try {
                        ClipData clip=event.getClipData();
                        if(clip==null||clip.getItemCount()==0)return false;
                        int source=Integer.parseInt(clip.getItemAt(0).coerceToText(this).toString());
                        if(db.active()!=null){
                            Toast.makeText(this,"Zakończ lub wstrzymaj trening przed zmianą kolejności",Toast.LENGTH_LONG).show();
                            return true;
                        }
                        db.moveScheduledWorkout(source,workout);render();return true;
                    }catch(Exception ignored){return false;}
                }
                return true;
            });
        }

    }

    private void openPlanPreview(int workout){
        editingWorkout=workout;
        page="planPreview";
        render();
    }

    private void appendExerciseIllustration(LinearLayout parent,String exerciseId) {
        Bitmap bitmap=ExerciseIllustrations.forId(this,exerciseId);
        if(bitmap==null)return;
        space(parent,8);
        TextView label=text("POZYCJA STARTOWA  /  KOŃCOWA",10,TEAL,true);
        add(parent,label);
        ImageView image=new ImageView(this);
        image.setImageBitmap(bitmap);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription("Ilustracja ćwiczenia — pozycja początkowa i końcowa");
        image.setBackground(rounded(CARD,10,BORDER));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(5);
        parent.addView(image,lp);
        TextView disclaimer=text("Schemat poglądowy. Szczegóły ustawienia znajdziesz poniżej.",11,MUTED,false);
        disclaimer.setPadding(dp(0),dp(4),dp(0),dp(3));
        add(parent,disclaimer);
    }

    private void showPlanPreview(){
        TextView back=actionLabel("‹  Twój plan",TEAL);
        back.setMinHeight(dp(42));
        add(content,back);
        back.setOnClickListener(v->{page="plan";render();});
        space(content,4);
        headline(content,db.workoutLabel(editingWorkout));
        add(content,text("Podgląd — nie uruchamia treningu, timera ani zapisu serii.",13,MUTED,false));
        space(content,12);
        int number=0;
        for(Plan.Exercise ex:db.workoutPlan(editingWorkout)){
            number++;
            LinearLayout exercise=card(content);
            add(exercise,text(String.format(Locale.ROOT,"%02d  %s",number,ex.name),16,TEXT,true));
            space(exercise,5);
            add(exercise,text(db.expectedSets(editingWorkout,ex)+" × "+ex.minReps+"–"+ex.maxReps+
                "  •  "+ex.restSeconds+" s przerwy"+
                (ex.rirMin>=0?"  •  RIR "+ex.rirMin:""),12,TEAL,false));
            TextView toggle=actionLabel("ⓘ  Technika i ustawienie  ⌄",TEAL);
            toggle.setMinHeight(dp(40));
            add(exercise,toggle);
            LinearLayout guide=col();
            guide.setPadding(dp(10),dp(9),dp(10),dp(9));
            guide.setBackground(rounded(CARD2,10,BORDER));
            appendExerciseIllustration(guide,ex.id);
            TextView instructions=text(ExerciseGuides.forId(ex.id,ex.description),13,TEXT,false);
            instructions.setLineSpacing(dp(4),1.08f);
            add(guide,instructions);
            guide.setVisibility(View.GONE);
            add(exercise,guide);
            toggle.setOnClickListener(v->{
                boolean expanded=guide.getVisibility()==View.GONE;
                guide.setVisibility(expanded?View.VISIBLE:View.GONE);
                toggle.setText(expanded?"▴  Ukryj wskazówki":"ⓘ  Technika i ustawienie  ⌄");
            });
        }
        Button start=button("▶  Start treningu",true);
        add(content,start);
        start.setOnClickListener(v->startWorkout(editingWorkout));
    }

    private static String weekdayName(int weekday){
        String[] names={"Poniedziałek","Wtorek","Środa","Czwartek","Piątek","Sobota","Niedziela"};
        return names[Math.max(0,Math.min(6,weekday-1))];
    }
    private void openPlanEditor(int workout){
        if(db.active()!=null){
            new AlertDialog.Builder(this).setTitle("Trening w toku")
                .setMessage("Zakończ lub anuluj sesję przed zmianą ćwiczeń.")
                .setPositiveButton("OK",null).show();
            return;
        }
        editingWorkout=workout;page="planEditor";render();
    }
    private void editScheduleDay(int workout){
        if(db.active()!=null){
            Toast.makeText(this,"Najpierw zakończ lub wstrzymaj bieżący trening",Toast.LENGTH_LONG).show();
            return;
        }
        WorkoutDb.ScheduleDay chosen=null;
        for(WorkoutDb.ScheduleDay d:db.schedule())if(d.workout==workout)chosen=d;
        if(chosen==null)return;
        final WorkoutDb.ScheduleDay current=chosen;
        new AlertDialog.Builder(this).setTitle("Dzień i rodzaj treningu")
            .setItems(new String[]{"Zmień dzień tygodnia","Zmień nazwę treningu",
                "Przenieś wyżej","Przenieś niżej"},(dialog,index)->{
                if(index==0) {
                    String[] options={"Poniedziałek","Wtorek","Środa","Czwartek","Piątek","Sobota","Niedziela"};
                    new AlertDialog.Builder(this).setTitle("Przypisz dzień tygodnia")
                        .setSingleChoiceItems(options,current.weekday-1,(d,position)->{
                            // A conflicting assignment swaps weekdays between the two plans.
                            db.assignWeekday(workout,position+1);
                            d.dismiss();render();
                        }).setNegativeButton("Anuluj",null).show();
                } else if(index==1) {
                    EditText name=field(current.title,"Nazwa treningu",false);
                    name.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                    new AlertDialog.Builder(this).setTitle("Nazwa rodzaju treningu")
                        .setView(name).setNegativeButton("Anuluj",null)
                        .setPositiveButton("Zapisz",(d,w)->{
                            try{db.renameWorkout(workout,name.getText().toString());render();}
                            catch(Exception e){Toast.makeText(this,"Nazwa: od 1 do 32 znaków",Toast.LENGTH_LONG).show();}
                        }).show();
                } else {
                    List<WorkoutDb.ScheduleDay> days=db.schedule();
                    int target=current.position+(index==2?-1:1);
                    if(target>=0&&target<days.size()){
                        db.moveScheduledWorkout(workout,days.get(target).workout);render();
                    }
                }
            }).show();
    }

    private void showPlanEditor() {
        space(content,4);
        TextView back=actionLabel("‹  Wróć do planu",SKY);
        back.setMinHeight(dp(38));add(content,back);
        back.setOnClickListener(v->{page="plan";render();});
        headline(content,"Edytuj "+db.workoutLabel(editingWorkout));
        add(content,text("Przesuwaj ćwiczenia strzałkami. Dotknij nazwy, aby zmienić serie, zakres lub przerwę.",13,MUTED,false));
        space(content,12);
        List<Plan.Exercise> items=db.workoutPlan(editingWorkout);
        for(int i=0;i<items.size();i++) {
            final int position=i;
            Plan.Exercise exercise=items.get(i);
            LinearLayout card=card(content);
            LinearLayout first=row();
            TextView name=text(exercise.name,16,TEXT,true);
            name.setMaxLines(2);
            first.addView(name,new LinearLayout.LayoutParams(0,dp(42),1));
            TextView more=actionLabel("⋮",SKY);
            more.setGravity(Gravity.CENTER);
            more.setTextSize(23);
            more.setContentDescription("Zamień, ustaw ciężary lub usuń "+exercise.name);
            first.addView(more,new LinearLayout.LayoutParams(dp(42),dp(42)));
            more.setOnClickListener(v->showPlanItemOptions(position));
            add(card,first);
            LinearLayout lower=row();
            TextView target=text(exercise.sets+" × "+exercise.minReps+"–"+exercise.maxReps+
                "  •  "+exercise.restSeconds+" s",13,dayColor(editingWorkout),true);
            lower.addView(target,new LinearLayout.LayoutParams(0,dp(37),1));
            TextView up=actionLabel("↑",position==0?BORDER:SKY);
            up.setGravity(Gravity.CENTER);up.setTextSize(22);
            lower.addView(up,new LinearLayout.LayoutParams(dp(46),dp(39)));
            if(position>0)up.setOnClickListener(v->movePlanExercise(position,-1));
            TextView down=actionLabel("↓",position==items.size()-1?BORDER:SKY);
            down.setTextSize(22);down.setGravity(Gravity.CENTER);
            lower.addView(down,new LinearLayout.LayoutParams(dp(46),dp(39)));
            if(position<items.size()-1)down.setOnClickListener(v->movePlanExercise(position,1));
            add(card,lower);
            clickCard(card,()->editPlanParameters(position));
        }
        Button addExercise=button("+ Dodaj ćwiczenie",true);
        add(content,addExercise);
        addExercise.setOnClickListener(v->showPlanExerciseChooser(-1));
        space(content,7);
        Button reset=button("Przywróć oryginalny plan",false);
        reset.setTextColor(AMBER);
        add(content,reset);
        reset.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle("Przywrócić oryginalną rozpiskę?")
            .setMessage("Zmieniony plan zostanie zresetowany. Historia wykonanych treningów pozostanie bez zmian.")
            .setNegativeButton("Anuluj",null)
            .setPositiveButton("Przywróć",(d,w)->{
                db.resetPlan(editingWorkout);render();
            }).show());
        space(content,12);
        add(content,text("Zmiany dotyczą kolejnych treningów. Istniejąca historia i ciężary nie zostaną usunięte.",12,MUTED,false));
    }

    private void movePlanExercise(int position,int change) {
        java.util.ArrayList<Plan.Exercise> items=new java.util.ArrayList<>(db.workoutPlan(editingWorkout));
        int to=position+change;
        if(to<0||to>=items.size())return;
        java.util.Collections.swap(items,position,to);
        db.savePlan(editingWorkout,items);
        render();
    }
    private void showPlanItemOptions(int position) {
        showActionSheet("Ćwiczenie",new String[]{
                "✎  Edytuj serie i powtórzenia",
                "⇄  Zamień ćwiczenie",
                "⚖  Skok ciężaru i objętość",
                "×  Usuń z planu"
            },choice->{
                    if(choice==0)editPlanParameters(position);
                    else if(choice==1)showPlanExerciseChooser(position);
                    else if(choice==2)editExerciseLoadSettings(db.workoutPlan(editingWorkout).get(position));
                    else {
                        java.util.ArrayList<Plan.Exercise> items=new java.util.ArrayList<>(db.workoutPlan(editingWorkout));
                        if(items.size()<=1) {
                            Toast.makeText(this,"Plan musi zawierać ćwiczenie",Toast.LENGTH_LONG).show();
                            return;
                        }
                        Plan.Exercise removed=items.get(position);
                        new AlertDialog.Builder(this).setTitle("Usunąć "+removed.name+"?")
                            .setMessage("Usunięcie z planu nie zmienia historii treningów.")
                            .setNegativeButton("Anuluj",null)
                            .setPositiveButton("Usuń",(dialog,which)->{
                                items.remove(position);db.savePlan(editingWorkout,items);render();
                            }).show();
                    }
                });
    }

    private void showPlanExerciseChooser(int replaceAt) {
        java.util.ArrayList<Plan.Exercise> items=new java.util.ArrayList<>(db.workoutPlan(editingWorkout));
        java.util.ArrayList<Plan.Exercise> options=new java.util.ArrayList<>();
        java.util.HashSet<String> used=new java.util.HashSet<>();
        for(int i=0;i<items.size();i++)
            if(i!=replaceAt)used.add(items.get(i).id);
        for(Plan.Exercise ex:db.exerciseCatalog())if(!used.contains(ex.id))options.add(ex);
        // "Utwórz własne" must remain available even after using every catalog item.
        String[] titles=new String[options.size()+1];
        titles[0]="+ Utwórz własne ćwiczenie";
        for(int i=0;i<options.size();i++)titles[i+1]=options.get(i).name;
        new AlertDialog.Builder(this)
            .setTitle(replaceAt<0?"Dodaj ćwiczenie":"Zamień ćwiczenie")
            .setItems(titles,(d,choice)->{
                if(choice==0){createCustomExercise(replaceAt);return;}
                choice--;
                if(replaceAt<0&&items.size()>=18){
                    Toast.makeText(this,"Maksymalnie 18 ćwiczeń",Toast.LENGTH_LONG).show();return;
                }
                if(replaceAt<0)items.add(options.get(choice));
                else items.set(replaceAt,options.get(choice));
                db.savePlan(editingWorkout,items);
                render();
            })
            .setNegativeButton("Anuluj",null).show();
    }

    private void createCustomExercise(int replaceAt) {
        LinearLayout form=col();
        form.setPadding(dp(19),dp(9),dp(19),dp(5));
        add(form,text("NAZWA ĆWICZENIA",12,MUTED,true));
        EditText name=new EditText(this);
        name.setSingleLine(true);name.setTextColor(TEXT);name.setHint("Np. wyciskanie neutralne");
        name.setHintTextColor(MUTED);add(form,name);
        add(form,text("SPRZĘT / GRUPA MIĘŚNIOWA",12,MUTED,true));
        EditText equipment=new EditText(this);
        equipment.setSingleLine(true);equipment.setTextColor(TEXT);
        equipment.setHint("Np. hantle • klatka");equipment.setHintTextColor(MUTED);
        add(form,equipment);
        add(form,text("KRÓTKI OPIS",12,MUTED,true));
        EditText description=new EditText(this);
        description.setSingleLine(true);description.setTextColor(TEXT);
        description.setHint("Opcjonalnie");description.setHintTextColor(MUTED);
        add(form,description);
        add(form,text("SERIE / MIN POWT. / MAX POWT.",12,MUTED,true));
        LinearLayout inputs=row();
        EditText sets=field("3","Serie",false);
        EditText min=field("8","Od",false);
        EditText max=field("12","Do",false);
        inputs.addView(sets,new LinearLayout.LayoutParams(0,dp(51),1));
        inputs.addView(min,new LinearLayout.LayoutParams(0,dp(51),1));
        inputs.addView(max,new LinearLayout.LayoutParams(0,dp(51),1));
        add(form,inputs);
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle("Nowe ćwiczenie").setView(form)
            .setNegativeButton("Anuluj",null).setPositiveButton("Utwórz",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(v->{
                try{
                    String label=name.getText().toString().trim();
                    String tool=equipment.getText().toString().trim();
                    String details=description.getText().toString().trim();
                    int count=Integer.parseInt(sets.getText().toString());
                    int low=Integer.parseInt(min.getText().toString());
                    int high=Integer.parseInt(max.getText().toString());
                    if(replaceAt<0&&db.workoutPlan(editingWorkout).size()>=18)
                        throw new IllegalArgumentException("Maksymalnie 18 ćwiczeń w planie");
                    Plan.Exercise exercise=db.createExercise(label,details,tool,tool,
                        count,low,high,90,false);
                    java.util.ArrayList<Plan.Exercise> items=
                        new java.util.ArrayList<>(db.workoutPlan(editingWorkout));
                    if(replaceAt<0)items.add(exercise);else items.set(replaceAt,exercise);
                    db.savePlan(editingWorkout,items);
                    dialog.dismiss();render();
                }catch(Exception e){
                    Toast.makeText(this,e.getMessage()==null?"Sprawdź dane ćwiczenia":e.getMessage(),Toast.LENGTH_LONG).show();
                }
            }));
        dialog.show();
    }
    private void editExerciseLoadSettings(Plan.Exercise exercise) {
        LinearLayout form=col();
        form.setPadding(dp(18),dp(8),dp(18),dp(5));
        add(form,text("SKOK CIĘŻARU (KG)",13,MUTED,true));
        EditText step=field(weight(db.weightStep(exercise.id)),"np. 2,5",true);
        add(form,step);
        space(form,11);
        add(form,text("JAK PODAJESZ CIĘŻAR?",13,MUTED,true));
        String[] modes={"Łączny ciężar / stos maszyny (×1)","Jedna hantla — obie ręce (×2)"};
        Spinner multiplier=new Spinner(this);
        multiplier.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,modes));
        multiplier.setSelection(db.loadMultiplier(exercise.id)-1);
        add(form,multiplier);
        add(form,text("Mnożnik dotyczy wyłącznie statystyk objętości, "+
            "nie zmienia zapisanych kilogramów ani rekordów PR.",12,MUTED,false));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(exercise.name)
            .setView(form).setNegativeButton("Anuluj",null)
            .setPositiveButton("Zapisz",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(v->{
                try{
                    double kg=Double.parseDouble(step.getText().toString().replace(',','.'));
                    db.saveLoadSettings(exercise.id,kg,multiplier.getSelectedItemPosition()+1);
                    dialog.dismiss();render();
                }catch(Exception error){
                    Toast.makeText(this,"Skok 0,25–50 kg. Wybierz sposób podawania obciążenia.",
                        Toast.LENGTH_LONG).show();
                }
            }));
        dialog.show();
    }

    private void editPlanParameters(int position) {
        java.util.ArrayList<Plan.Exercise> items=new java.util.ArrayList<>(db.workoutPlan(editingWorkout));
        Plan.Exercise ex=items.get(position);
        LinearLayout form=col();
        form.setPadding(dp(18),dp(12),dp(18),dp(5));
        String[] labels={"SERIE","MIN. POWTÓRZENIA","MAKS. POWTÓRZENIA","PRZERWA (SEKUNDY)"};
        int[] values={ex.sets,ex.minReps,ex.maxReps,ex.restSeconds};
        EditText[] fields=new EditText[4];
        for(int i=0;i<4;i++) {
            add(form,text(labels[i],12,MUTED,true));
            space(form,3);
            fields[i]=field(Integer.toString(values[i]),labels[i],false);
            add(form,fields[i]);
            space(form,9);
        }
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(ex.name)
            .setView(form).setNegativeButton("Anuluj",null)
            .setPositiveButton("Zapisz",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{
                int sets=Integer.parseInt(fields[0].getText().toString().trim());
                int min=Integer.parseInt(fields[1].getText().toString().trim());
                int max=Integer.parseInt(fields[2].getText().toString().trim());
                int rest=Integer.parseInt(fields[3].getText().toString().trim());
                Plan.Exercise updated=new Plan.Exercise(ex.id,ex.name,ex.description,
                    sets,min,max,rest,ex.eachSide,ex.rirMin,ex.rirMax);
                items.set(position,updated);
                db.savePlan(editingWorkout,items);
                dialog.dismiss();render();
            }catch(Exception error) {
                Toast.makeText(this,"Serie 1–12, powtórzenia 1–40, przerwa 15–600 s",Toast.LENGTH_LONG).show();
            }
        }));
        dialog.show();
    }

    private void showSettings() {
        space(content,6);
        if(settingsSection.isEmpty()) {
            headline(content,"Ustawienia");
            add(content,text("Aktualizacje ↻ w prawym górnym rogu; dotknij wersji, aby otworzyć zaawansowane OTA.",12,MUTED,false));
            space(content,13);
            settingsEntry("♙","Konto i synchronizacja","Supabase, logowanie i stan chmury","cloud");
            settingsEntry("◷","Trening i timer","Przerwy, dźwięki i automatyczne przejście","timer");
            settingsEntry("◐","Wygląd aplikacji","Preferencje wyświetlania","appearance");
            settingsEntry("▣","Kopie zapasowe","Eksport CSV, JSON i przywracanie","backup");
             return;
        }
        TextView back=actionLabel("‹  Wszystkie ustawienia",TEAL);
        back.setMinHeight(dp(40));
        add(content,back);
        back.setOnClickListener(v->{settingsSection="";render();});
        space(content,6);
        if(settingsSection.equals("cloud")) {
            headline(content,"Konto i synchronizacja");
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

        } else if(settingsSection.equals("backup")) {
            headline(content,"Kopie zapasowe");
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
        space(backup,8);
        Button saveBackup=button("Zapisz pełną kopię JSON",true);
        add(backup,saveBackup);
        saveBackup.setOnClickListener(v->exportBackup());
        space(backup,6);
        Button restore=button("Przywróć kopię JSON",false);
        restore.setTextColor(SKY);
        add(backup,restore);
        restore.setOnClickListener(v->importBackup());
        space(backup,5);
        add(backup,text("Kopia JSON zawiera historię, notatki, RIR i plany. Nie zawiera haseł ani tokenów logowania.",12,MUTED,false));

        } else if(settingsSection.equals("timer")) {
            headline(content,"Trening i timer");
        LinearLayout timerSettings=card(content);
        smallLabel(timerSettings,"TIMER W TLE");
        space(timerSettings,6);
        add(timerSettings,text("Ciche, stałe powiadomienie aktywnego treningu. Podczas przerwy wyświetla pozostały czas; na końcu przerwy dźwięk lub wibracja.",14,MUTED,false));
        space(timerSettings,8);
        add(timerSettings,text(RestTimer.canNotify(this)?"Powiadomienia: dostępne":"Powiadomienia: wymagają zezwolenia",13,RestTimer.canNotify(this)?GREEN:AMBER,true));
        space(timerSettings,5);
        add(timerSettings,text(RestTimer.canScheduleExact(this)?"Dokładne alarmy: włączone":
            "Dokładne alarmy: wyłączone (system może opóźnić powiadomienie)",13,
            RestTimer.canScheduleExact(this)?GREEN:AMBER,false));
        space(timerSettings,9);
        Button alerts=button("Ustaw powiadomienia",false);
        add(timerSettings,alerts);
        alerts.setOnClickListener(v->{
            try {
                Intent i=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                i.putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());
                startActivity(i);
            } catch(Exception ignored) {
                Toast.makeText(this,"Otwórz ustawienia powiadomień aplikacji w Androidzie",Toast.LENGTH_LONG).show();
            }
        });
        if(Build.VERSION.SDK_INT>=31 && !RestTimer.canScheduleExact(this)) {
            space(timerSettings,6);
            Button precise=button("Włącz dokładne alarmy",false);
            add(timerSettings,precise);
            precise.setOnClickListener(v->{
                try {
                    Intent i=new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    i.setData(Uri.parse("package:"+getPackageName()));
                    startActivity(i);
                } catch(Exception error) {
                    Toast.makeText(this,"Zezwól na alarmy i przypomnienia w ustawieniach Androida",Toast.LENGTH_LONG).show();
                }
            });
        }
        space(timerSettings,10);
        addSettingsToggle(timerSettings,"Dźwięk końca przerwy","timer_sound",true,
            "Standardowe powiadomienie Androida");
        space(timerSettings,6);
        addSettingsToggle(timerSettings,"Automatyczne następne ćwiczenie","auto_next",true,
            "Po zakończeniu zaplanowanych serii");

        } else if(settingsSection.equals("appearance")) {
            headline(content,"Wygląd aplikacji");
            LinearLayout theme=card(content);
            smallLabel(theme,"CIEMNY INTERFEJS");
            space(theme,6);
            add(theme,text("True Black",17,TEXT,true));
            space(theme,5);
            add(theme,text("Czarne tło i spokojne akcenty. Ustaw wielkość całego interfejsu.",13,MUTED,false));
            space(theme,12);
            addSettingsToggle(theme,"Kompaktowy układ","compact_ui",true,
                "Mniejsze karty, bez utraty czytelności");
            space(theme,13);
            smallLabel(theme,"WIELKOŚĆ TEKSTU");
            space(theme,8);
            int font=getSharedPreferences("forma_settings",0).getInt("font_scale",0);
            addAppearanceChoice(theme,"Mniejszy","Więcej informacji na ekranie","font_scale",-1,font==-1);
            space(theme,6);
            addAppearanceChoice(theme,"Standardowy","Domyślny rozmiar tekstu","font_scale",0,font==0);
            space(theme,6);
            addAppearanceChoice(theme,"Większy","Czytelniejszy tekst","font_scale",2,font==2);

        }
    }

    private void addAppearanceChoice(LinearLayout target,String title,String description,
                                     String key,int value,boolean selected) {
        LinearLayout line=row();
        line.setPadding(p(9,12),p(9,13),p(9,12),p(9,13));
        line.setBackground(rounded(selected?Color.rgb(25,42,36):CARD2,10,selected?TEAL:BORDER));
        LinearLayout left=col();
        line.addView(left,new LinearLayout.LayoutParams(0,-2,1));
        add(left,text(title,15,TEXT,true));
        add(left,text(description,12,MUTED,false));
        TextView check=text(selected?"✓":"○",21,selected?TEAL:MUTED,true);
        check.setGravity(Gravity.CENTER);
        line.addView(check,new LinearLayout.LayoutParams(dp(34),dp(42)));
        line.setOnClickListener(v->{
            getSharedPreferences("forma_settings",0).edit().putInt(key,value).apply();
            render();
        });
        add(target,line);
    }

    private void settingsEntry(String symbol,String title,String description,String section) {
        LinearLayout item=row();
        item.setPadding(dp(13),dp(12),dp(12),dp(12));
        item.setBackground(rounded(CARD,13,BORDER));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(9);
        content.addView(item,lp);
        TextView icon=text(symbol,23,TEAL,true);
        icon.setGravity(Gravity.CENTER);
        item.addView(icon,new LinearLayout.LayoutParams(dp(40),dp(48)));
        LinearLayout words=col();
        LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,-2,1);
        wp.leftMargin=dp(8);
        item.addView(words,wp);
        add(words,text(title,16,TEXT,true));
        space(words,3);
        add(words,text(description,12,MUTED,false));
        TextView arrow=text("›",23,MUTED,false);
        arrow.setGravity(Gravity.CENTER);
        item.addView(arrow,new LinearLayout.LayoutParams(dp(24),dp(45)));
        item.setContentDescription(title+". "+description);
        item.setOnClickListener(v->{settingsSection=section;render();});
    }

    private void addSettingsToggle(LinearLayout target,String title,String key,
                                   boolean defaultValue,String description) {
        LinearLayout entry=row();
        LinearLayout labels=col();
        entry.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        add(labels,text(title,15,TEXT,true));
        add(labels,text(description,12,MUTED,false));
        Switch toggle=new Switch(this);
        toggle.setChecked(getSharedPreferences("forma_settings",0).getBoolean(key,defaultValue));
        toggle.setThumbTintList(ColorStateList.valueOf(TEAL));
        toggle.setContentDescription(title);
        toggle.setOnCheckedChangeListener((button,checked)->
            getSharedPreferences("forma_settings",0).edit().putBoolean(key,checked).apply());
        entry.addView(toggle,new LinearLayout.LayoutParams(dp(63),dp(51)));
        add(target,entry);
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
                .setMessage("Możesz wrócić do niezakończonej sesji "+db.sessionWorkoutLabel(active)+".")
                .setPositiveButton("Wznów",(d,w)->{
                    if(active.pausedAt>0)db.resume(active.id);
                    openSession=active.id;openWorkout=active.workout;page="session";render();
                    syncWorkoutNotification();
                    askWorkoutNotificationPermission();
                })
                .setNegativeButton("Anuluj",null).show();
            return;
        }
        openWorkout=workout;
        stopRest();
        openSession=db.start(workout);
        canUndoSet=false;editingSetExercise="";editingSetIndex=-1;
        expandedExerciseId="";
        page="session";render();
        syncWorkoutNotification();
        askWorkoutNotificationPermission();
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
        int done=0;
        for(Plan.Exercise ex:db.workoutPlan(openWorkout))done+=db.countExerciseSets(openSession,ex.id);
        if(sessionCount!=null) sessionCount.setText(done+" / "+plannedSets+" serii");
        if(sessionProgress!=null) sessionProgress.setProgress(Math.min(done,plannedSets));
    }
    private void showFixedSessionHeader() {
        if(openSession<0) return;
        plannedSets=0;
        for(Plan.Exercise e:db.workoutPlan(openWorkout)) plannedSets+=db.expectedSets(openWorkout,e);

        // The workout header replaces the global brand toolbar: centered day title,
        // unobtrusive back affordance, and the session menu in the upper right.
        LinearLayout top=row();
        TextView back=text("‹",26,MUTED,false);
        back.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
        back.setContentDescription("Wróć do planu");
        top.addView(back,new LinearLayout.LayoutParams(dp(51),dp(42)));
        back.setOnClickListener(v->{page="plan";render();});
        TextView title=text(db.workoutLabel(openWorkout),21,TEXT,true);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(title,new LinearLayout.LayoutParams(0,dp(42),1));
        TextView controls=text("⋮",26,MUTED,true);
        controls.setGravity(Gravity.CENTER);
        controls.setContentDescription("Opcje treningu: wstrzymaj, zakończ lub anuluj");
        top.addView(controls,new LinearLayout.LayoutParams(dp(51),dp(42)));
        controls.setOnClickListener(v->showSessionActions());
        add(root,top);
        space(root,2);

        LinearLayout count=row();
        TextView small=text("SERIE ROBOCZE",11,MUTED,true);
        count.addView(small,new LinearLayout.LayoutParams(0,dp(29),1));
        sessionCount=text("0 / "+plannedSets+" serii",14,TEAL,true);
        sessionCount.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        count.addView(sessionCount);
        TextView undo=actionLabel("↶ Cofnij",canUndoSet&&undoSession==openSession?AMBER:MUTED);
        undo.setGravity(Gravity.CENTER);
        undo.setContentDescription("Cofnij ostatnio zapisaną serię");
        count.addView(undo,new LinearLayout.LayoutParams(dp(83),dp(34)));
        undo.setOnClickListener(v->undoLastSet());
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
        timerShell.setBackground(rounded(CARD,15,BORDER));
        timerShell.setPadding(dp(13),dp(10),dp(13),dp(11));
        LinearLayout.LayoutParams shellLp=new LinearLayout.LayoutParams(-1,-2);
        shellLp.topMargin=dp(7);shellLp.bottomMargin=dp(3);
        root.addView(timerShell,shellLp);

        LinearLayout times=row();
        LinearLayout elapsed=col();
        add(elapsed,text("CZAS TRENINGU",10,MUTED,true));
        elapsedLabel=text("00:00",20,TEXT,true);
        add(elapsed,elapsedLabel);
        times.addView(elapsed,new LinearLayout.LayoutParams(0,dp(47),1));

        LinearLayout rest=col();
        add(rest,text("PRZERWA",10,MUTED,true));
        timerLabel=text(restUntil==0?"00:00":formatTime(RestTimer.secondsRemaining(this)),24,TEAL,true);
        add(rest,timerLabel);
        times.addView(rest,new LinearLayout.LayoutParams(0,dp(47),1));

        TextView skip=text("Pomiń  ×",12,MUTED,true);
        skip.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        skip.setContentDescription("Pomiń aktualną przerwę");
        times.addView(skip,new LinearLayout.LayoutParams(dp(78),dp(47)));
        skip.setOnClickListener(v->stopRest());
        add(timerShell,times);

        restProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        restProgress.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(41,41,41)));
        restProgress.setProgressTintList(ColorStateList.valueOf(TEAL));
        int secondsTotal=getSharedPreferences("forma_settings",0).getInt("rest_total",90);
        long remaining=RestTimer.secondsRemaining(this);
        restProgress.setMax(Math.max(1,secondsTotal));
        restProgress.setProgress((int)Math.max(0,Math.min(secondsTotal,secondsTotal-remaining)));
        LinearLayout.LayoutParams progressLp=new LinearLayout.LayoutParams(-1,dp(5));
        progressLp.topMargin=dp(6);progressLp.bottomMargin=dp(9);
        timerShell.addView(restProgress,progressLp);

        LinearLayout buttons=row();
        int[] seconds={60,90,120};
        TextView[] timerChoices=new TextView[seconds.length];
        for(int i=0;i<seconds.length;i++) {
            final int sec=seconds[i];
            TextView t=pill(sec+" s",remaining>0 && secondsTotal==sec);
            timerChoices[i]=t;
            t.setTextSize(13);
            t.setContentDescription("Rozpocznij przerwę "+sec+" sekund");
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(35),1);
            if(i>0)p.leftMargin=dp(7);
            buttons.addView(t,p);
            t.setOnClickListener(v->{
                startRest(sec);
                for(int j=0;j<seconds.length;j++){
                    boolean selected=seconds[j]==sec;
                    timerChoices[j].setTextColor(selected?BG:MUTED);
                    timerChoices[j].setBackground(rounded(selected?TEAL:CARD,14,selected?0:BORDER));
                }
            });
        }
        TextView plus=pill("+15 s",false);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(35),1);
        p.leftMargin=dp(7);
        buttons.addView(plus,p);
        plus.setContentDescription("Wydłuż przerwę o 15 sekund");
        plus.setOnClickListener(v->{
            long left=RestTimer.secondsRemaining(this);
            startRest((int)left+15);
            for(TextView choice:timerChoices){
                choice.setTextColor(MUTED);
                choice.setBackground(rounded(CARD,14,BORDER));
            }
        });
        add(timerShell,buttons);

    }

    private void changeExerciseComment(long sessionId,String exerciseId,String exerciseName) {
        String existing=db.exerciseComment(sessionId,exerciseId);
        EditText field=new EditText(this);
        field.setText(existing);
        field.setTextColor(TEXT);
        field.setHintTextColor(MUTED);
        field.setHint("Np. Technogym, uchwyt neutralny, ustawienie siedziska 4");
        field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|
            InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        field.setSingleLine(false);
        field.setMinLines(2);
        field.setMaxLines(5);
        field.setTextSize(15);
        field.setPadding(dp(12),dp(12),dp(12),dp(12));
        field.setBackground(rounded(CARD2,12,BORDER));
        LinearLayout wrap=col();
        wrap.setPadding(dp(16),dp(8),dp(16),0);
        add(wrap,field);
        add(wrap,text("Komentarz zapisuje się przy tym ćwiczeniu tylko w tej sesji (maks. 300 znaków).",
            12,MUTED,false));
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle("Komentarz · "+exerciseName)
            .setView(wrap)
            .setNegativeButton("Anuluj",null)
            .setPositiveButton("Zapisz",null)
            .create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(v->{
                String comment=field.getText().toString().trim();
                if(comment.length()>300){
                    field.setError("Maksymalnie 300 znaków");
                    return;
                }
                try {
                    db.saveExerciseComment(sessionId,exerciseId,comment);
                    dialog.dismiss();
                    render();
                } catch(Exception e) {
                    Toast.makeText(this,"Nie udało się zapisać komentarza",Toast.LENGTH_LONG).show();
                }
            }));
        dialog.show();
    }

    private void showSession() {
        if(openSession<0) {page="plan";render();return;}
        List<Plan.Exercise> exercises=db.workoutPlan(openWorkout);
        if(expandedExerciseId.isEmpty() && !exercises.isEmpty()) {
            for(Plan.Exercise e:exercises) {
                if(db.countExerciseSets(openSession,e.id)<db.expectedSets(openWorkout,e)) {
                    expandedExerciseId=e.id;break;
                }
            }
            if(expandedExerciseId.isEmpty())expandedExerciseId=exercises.get(0).id;
        }

        space(content,5);
        add(content,text("Ćwiczenia",19,TEXT,true));
        space(content,6);

        for(int i=0;i<exercises.size();i++) {
            final Plan.Exercise ex=exercises.get(i);
            final int order=i+1;
            LinearLayout c=card(content);
            LinearLayout header=row();
            TextView number=text(String.format(Locale.ROOT,"%02d",order),14,MUTED,true);
            number.setGravity(Gravity.CENTER);
            number.setBackground(rounded(CARD2,11,0));
            header.addView(number,new LinearLayout.LayoutParams(dp(40),dp(44)));

            LinearLayout titleBox=col();
            LinearLayout.LayoutParams tb=new LinearLayout.LayoutParams(0,-2,1);
            tb.leftMargin=dp(12);
            header.addView(titleBox,tb);
            TextView title=text(ex.name,17,TEXT,true);
            title.setMaxLines(3);
            add(titleBox,title);
            String existingComment=db.exerciseComment(openSession,ex.id);
            TextView commentLink=actionLabel(existingComment.isEmpty()?
                "✎  Dodaj komentarz · maszyna / uchwyt":
                "✎  "+existingComment,existingComment.isEmpty()?MUTED:TEAL);
            commentLink.setTextSize(12);
            commentLink.setMaxLines(2);
            commentLink.setMinHeight(dp(30));
            commentLink.setContentDescription("Dodaj lub zmień komentarz do ćwiczenia "+ex.name);
            add(titleBox,commentLink);
            commentLink.setOnClickListener(v->changeExerciseComment(openSession,ex.id,ex.name));
            space(titleBox,4);
            int complete=db.countExerciseSets(openSession,ex.id);
            int target=db.expectedSets(openWorkout,ex);
            boolean finished=complete>=target;
            if(finished && expandedExerciseId.equals(ex.id)
                && !editingSetExercise.equals(ex.id)) {
                // Finished exercises collapse automatically but remain editable on tap.
                expandedExerciseId="";
            }
            TextView done=text((finished?"✓ ":"")+complete+"/"+target+" serii",13,finished?GREEN:TEAL,true);
            add(titleBox,done);

            TextView chevron=text(expandedExerciseId.equals(ex.id)?"⌃":"⌄",23,MUTED,false);
            chevron.setGravity(Gravity.CENTER);
            header.addView(chevron,new LinearLayout.LayoutParams(dp(31),dp(44)));
            add(c,header);
            LinearLayout body=col();
            boolean expanded=expandedExerciseId.equals(ex.id);
            body.setVisibility(expanded?View.VISIBLE:View.GONE);
            if(expanded) {
                expandedBody=body;
                expandedChevron=chevron;
                expandedCard=c;
            }
            space(body,5);
            int expected=db.expectedSets(openWorkout,ex);
            String objective=expected+" × "+ex.minReps+"–"+ex.maxReps+
                (ex.eachSide?" / stronę":"")+"  •  "+ex.restSeconds+" s przerwy";
            add(body,text(objective+"  •  RIR "+(ex.rirMin<0?"—":ex.rirMin==ex.rirMax?Integer.toString(ex.rirMin):ex.rirMin+"–"+ex.rirMax),12,TEAL,true));
            Plan.Exercise proposed=new Plan.Exercise(ex.id,ex.name,ex.description,
                expected,ex.minReps,ex.maxReps,ex.restSeconds,ex.eachSide);
            String tip=db.progressionTip(proposed);
            if(tip!=null) {
                space(body,8);
                TextView suggestion=text("↗  "+tip,13,GREEN,false);
                suggestion.setLineSpacing(dp(3),1f);
                add(body,suggestion);
            }
            space(body,3);
            TextView help=actionLabel("ⓘ  Szybka instrukcja techniki  ⌄",TEAL);
            help.setMinHeight(dp(40));
            help.setContentDescription("Rozwiń instrukcję techniki ćwiczenia "+ex.name);
            add(body,help);
            LinearLayout guide=col();
            guide.setPadding(dp(12),dp(12),dp(12),dp(12));
            guide.setBackground(rounded(CARD2,11,BORDER));
            appendExerciseIllustration(guide,ex.id);
            TextView desc=text(ExerciseGuides.forId(ex.id,ex.description),13,TEXT,false);
            desc.setLineSpacing(dp(4),1.08f);
            add(guide,desc);
            guide.setVisibility(View.GONE);
            add(body,guide);
            help.setOnClickListener(v->{
                boolean show=guide.getVisibility()!=View.VISIBLE;
                guide.setVisibility(show?View.VISIBLE:View.GONE);
                help.setText(show?"▴  Ukryj instrukcję":"ⓘ  Szybka instrukcja techniki  ⌄");
                help.setContentDescription((show?"Zwiń":"Rozwiń")+" instrukcję techniki ćwiczenia "+ex.name);
            });
            space(body,0);
            LinearLayout labels=row();
            String[] headings={"","KG","POWT.",""};
            float[] widths={1.0f,1.6f,1.5f,1.6f};
            for(int label=0;label<4;label++){
                TextView heading=text(headings[label],10,MUTED,true);
                heading.setGravity(Gravity.CENTER);
                labels.addView(heading,new LinearLayout.LayoutParams(0,dp(20),widths[label]));
            }
            add(body,labels);

            LinearLayout rows=col();
            add(body,rows);
            final int[] setNumber={db.setRows(openSession,ex.id,db.expectedSets(openWorkout,ex))};
            for(int j=0;j<setNumber[0];j++) addSetRow(rows,ex,j,done);
            TextView addExtra=actionLabel("+  Dodatkowa seria  ⋯",SKY);
            addExtra.setGravity(Gravity.CENTER);
            addExtra.setMinHeight(dp(36));
            add(body,addExtra);
            addExtra.setOnClickListener(v->showExtraSetOptions(ex,rows,done,setNumber));
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
                    expandedCard=c;
                }
            });
        }

        space(content,6);
        Button finish=button("Zakończ trening ✓",true);
        add(content,finish);
        finish.setOnClickListener(v->finishWorkout());
        space(content,8);
        Button pause=button("Wstrzymaj i wróć do planu",false);
        pause.setTextColor(AMBER);
        add(content,pause);
        pause.setOnClickListener(v->pauseWorkout());
    }

    private void advanceAfterLastSet(Plan.Exercise current) {
        if(openSession<0 || !getSharedPreferences("forma_settings",0).getBoolean("auto_next",true))
            return;
        if(db.countExerciseSets(openSession,current.id)!=db.expectedSets(openWorkout,current))return;
        List<Plan.Exercise> exercises=db.workoutPlan(openWorkout);
        for(int i=0;i<exercises.size()-1;i++) {
            if(!exercises.get(i).id.equals(current.id))continue;
            expandedExerciseId=exercises.get(i+1).id;
            render();
            if(contentScroll!=null && expandedCard!=null) {
                ScrollView scroll=contentScroll;
                LinearLayout card=expandedCard;
                scroll.post(()->scroll.smoothScrollTo(0,Math.max(0,card.getTop()-dp(10))));
            }
            return;
        }
    }

    private void undoLastSet() {
        if(!canUndoSet || undoSession!=openSession)return;
        if(undoPrevious==null)db.delete(undoSession,undoExercise,undoIndex);
        else db.save(undoSession,undoExercise,undoIndex,undoPrevious.weight,
            undoPrevious.reps,undoPrevious.rir,undoPrevious.note,undoPrevious.warmup);
        canUndoSet=false;
        editingSetExercise=undoExercise;editingSetIndex=undoIndex;
        expandedExerciseId=undoExercise;
        render();
        Toast.makeText(this,"Ostatnia zmiana cofnięta",Toast.LENGTH_SHORT).show();
    }

    private void showExtraSetOptions(Plan.Exercise exercise,LinearLayout container,
            TextView status,int[] count) {
        new AlertDialog.Builder(this).setTitle("Dodaj serię")
            .setItems(new String[]{"Tylko w bieżącym treningu","Zapisz również do planu"},
                (dialog,index)->{
                    if(count[0]>=12){
                        Toast.makeText(this,"Maksymalnie 12 serii roboczych na ćwiczenie",Toast.LENGTH_LONG).show();
                        return;
                    }
                    if(index==1) {
                        java.util.ArrayList<Plan.Exercise> plan=
                            new java.util.ArrayList<>(db.workoutPlan(openWorkout));
                        for(int i=0;i<plan.size();i++) {
                            Plan.Exercise e=plan.get(i);
                            if(e.id.equals(exercise.id)) {
                                plan.set(i,new Plan.Exercise(e.id,e.name,e.description,
                                    Math.min(12,db.expectedSets(openWorkout,e)+1),
                                    e.minReps,e.maxReps,e.restSeconds,e.eachSide,e.rirMin,e.rirMax));
                                break;
                            }
                        }
                        db.savePlan(openWorkout,plan);
                    }
                    final int row=count[0]++;
                    editingSetExercise=exercise.id;editingSetIndex=row;
                    if(index==1)render();
                    else addSetRow(container,exercise,row,status);
                }).show();
    }

    private void addSetRow(LinearLayout target,Plan.Exercise ex,int index,TextView exerciseStatus) {
        WorkoutDb.Entry current=db.get(openSession,ex.id,index);
        WorkoutDb.Entry previous=db.previousWorkout(openWorkout,ex.id,index);
        int firstPending=0;
        while(db.get(openSession,ex.id,firstPending)!=null && firstPending<12)
            firstPending++;
        boolean explicit=(editingSetIndex==index && ex.id.equals(editingSetExercise));
        boolean editing=explicit || (editingSetIndex<0 && current==null && index==firstPending);

        LinearLayout block=col();
        block.setPadding(dp(5),dp(editing?6:3),dp(5),dp(editing?7:3));
        block.setBackground(rounded(editing?CARD2:CARD,9,editing?BORDER:0));
        LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);
        layout.bottomMargin=dp(3);
        target.addView(block,layout);

        if(!editing && current==null) {
            LinearLayout waiting=row();
            TextView number=text(String.format(Locale.ROOT,"%02d",index+1),13,MUTED,true);
            number.setGravity(Gravity.CENTER);
            waiting.addView(number,new LinearLayout.LayoutParams(0,dp(36),1));
            TextView prior=text(previous==null?"Oczekuje":weight(previous.weight)+" kg  ×  "+previous.reps,
                13,MUTED,false);
            prior.setGravity(Gravity.CENTER);
            waiting.addView(prior,new LinearLayout.LayoutParams(0,dp(36),3.1f));
            TextView edit=actionLabel("EDYTUJ  ›",MUTED);
            edit.setTextSize(11);edit.setGravity(Gravity.CENTER);
            waiting.addView(edit,new LinearLayout.LayoutParams(0,dp(36),1.6f));
            add(block,waiting);
            block.setOnClickListener(v->{
                editingSetExercise=ex.id;editingSetIndex=index;render();
                scrollToExpandedExercise();
            });
            return;
        }
        if(!editing) {
            LinearLayout saved=row();
            TextView number=text(String.format(Locale.ROOT,"%02d",index+1),14,MUTED,true);
            number.setGravity(Gravity.CENTER);
            saved.addView(number,new LinearLayout.LayoutParams(0,dp(37),1));
            TextView kilos=text(weight(current.weight)+" kg",15,TEXT,true);
            kilos.setGravity(Gravity.CENTER);
            saved.addView(kilos,new LinearLayout.LayoutParams(0,dp(37),1.6f));
            TextView repeats=text("× "+current.reps,15,TEXT,true);
            repeats.setGravity(Gravity.CENTER);
            saved.addView(repeats,new LinearLayout.LayoutParams(0,dp(37),1.5f));
            boolean pr=db.isPersonalRecord(openSession,ex.id,current);
            TextView state=text(current.warmup?"ROZGRZ.":pr?"★ PR":"✓ EDYTUJ",
                12,current.warmup?AMBER:pr?AMBER:GREEN,true);
            state.setGravity(Gravity.CENTER);
            saved.addView(state,new LinearLayout.LayoutParams(0,dp(37),1.6f));
            add(block,saved);
            if(previous!=null&&!current.warmup) {
                double change=current.weight-previous.weight;
                int repChange=current.reps-previous.reps;
                if(Math.abs(change)>0.001 || repChange!=0) {
                    String delta=(change>0?"+":"")+weight(change)+" kg";
                    String repsDelta=(repChange>0?"+":"")+repChange+" powt.";
                    int trend=change>0.001?GREEN:change< -0.001?AMBER:
                        repChange>0?GREEN:AMBER;
                    add(block,text("vs ostatnio: "+delta+"  ·  "+repsDelta,11,trend,false));
                }
            }
            block.setContentDescription("Seria "+(index+1)+", "+weight(current.weight)+
                " kilogramów, "+current.reps+" powtórzeń. Dotknij, aby edytować.");
            block.setOnClickListener(v->{
                editingSetExercise=ex.id;editingSetIndex=index;render();
            });
            block.setOnLongClickListener(v->{
                editSetMetadata(openSession,ex,index,()->render());
                return true;
            });
            return;
        }

        LinearLayout title=row();
        TextView titleText=text("SERIA "+(index+1),12,MUTED,true);
        title.addView(titleText,new LinearLayout.LayoutParams(0,dp(20),1));
        TextView hint=text(previous==null?"Nowa seria":
            "Poprz.: "+weight(previous.weight)+" kg × "+previous.reps,11,MUTED,false);
        title.addView(hint);
        add(block,title);
        if(current==null){
            TextView guidance=text("ⓘ  "+db.loadGuidance(ex,openWorkout,index),11,MUTED,false);
            guidance.setPadding(dp(2),dp(3),dp(2),dp(6));
            add(block,guidance);
        }

        // One horizontal control strip. Flexible stepper groups share remaining width
        // after the fixed-size save and RIR actions; this avoids row overflow.
        LinearLayout controls=row();
        controls.setGravity(Gravity.CENTER_VERTICAL);
        final double increment=db.weightStep(ex.id);

        LinearLayout weightBox=row();
        TextView minus=actionLabel("−",AMBER);
        minus.setTextSize(20);minus.setGravity(Gravity.CENTER);
        minus.setContentDescription("Zmniejsz ciężar o "+weight(increment)+" kg");
        minus.setBackground(rounded(Color.rgb(35,32,27),7,Color.rgb(65,57,47)));
        weightBox.addView(minus,new LinearLayout.LayoutParams(dp(29),dp(42)));
        EditText kg=field(current!=null?weight(current.weight):
            previous!=null?weight(previous.weight):"","kg",true);
        kg.setTextSize(16);kg.setPadding(dp(1),dp(2),dp(1),dp(2));
        kg.setSingleLine(true);
        kg.setContentDescription("Ciężar w kg, seria "+(index+1));
        weightBox.addView(kg,new LinearLayout.LayoutParams(0,dp(42),1));
        TextView plus=actionLabel("+",GREEN);
        plus.setTextSize(20);plus.setGravity(Gravity.CENTER);
        plus.setContentDescription("Zwiększ ciężar o "+weight(increment)+" kg");
        plus.setBackground(rounded(Color.rgb(29,39,33),7,Color.rgb(48,71,58)));
        weightBox.addView(plus,new LinearLayout.LayoutParams(dp(29),dp(42)));
        LinearLayout.LayoutParams weightLp=new LinearLayout.LayoutParams(0,dp(42),1.12f);
        weightLp.rightMargin=dp(5);
        controls.addView(weightBox,weightLp);
        minus.setOnClickListener(v->changeWeight(kg,-increment));
        plus.setOnClickListener(v->changeWeight(kg,increment));

        LinearLayout repsBox=row();
        TextView repMinus=actionLabel("−",MUTED);
        repMinus.setTextSize(20);repMinus.setGravity(Gravity.CENTER);
        repMinus.setContentDescription("Odejmij jedno powtórzenie");
        repMinus.setBackground(rounded(CARD,7,BORDER));
        repsBox.addView(repMinus,new LinearLayout.LayoutParams(dp(27),dp(42)));
        EditText reps=field(current!=null?Integer.toString(current.reps):
            previous!=null?Integer.toString(previous.reps):"","powt.",false);
        reps.setTextSize(16);reps.setPadding(dp(1),dp(2),dp(1),dp(2));
        reps.setSingleLine(true);
        reps.setContentDescription("Powtórzenia, seria "+(index+1));
        repsBox.addView(reps,new LinearLayout.LayoutParams(0,dp(42),1));
        TextView repPlus=actionLabel("+",MUTED);
        repPlus.setTextSize(20);repPlus.setGravity(Gravity.CENTER);
        repPlus.setContentDescription("Dodaj jedno powtórzenie");
        repPlus.setBackground(rounded(CARD,7,BORDER));
        repsBox.addView(repPlus,new LinearLayout.LayoutParams(dp(27),dp(42)));
        LinearLayout.LayoutParams repsLp=new LinearLayout.LayoutParams(0,dp(42),0.95f);
        repsLp.rightMargin=dp(5);
        controls.addView(repsBox,repsLp);
        repMinus.setOnClickListener(v->changeReps(reps,-1));
        repPlus.setOnClickListener(v->changeReps(reps,1));

        Button save=button("✓",true);
        save.setTextSize(20);
        save.setPadding(0,0,0,0);
        save.setContentDescription(current==null?"Zapisz serię":"Zapisz zmianę serii");
        LinearLayout.LayoutParams saveLp=new LinearLayout.LayoutParams(dp(43),dp(42));
        saveLp.rightMargin=dp(4);
        controls.addView(save,saveLp);

        final int[] rirDraft={current==null?-1:current.rir};
        TextView rir=actionLabel(rirDraft[0]<0?"RIR":"R"+rirDraft[0],MUTED);
        rir.setTextSize(12);rir.setGravity(Gravity.CENTER);
        rir.setBackground(rounded(CARD2,7,BORDER));
        rir.setContentDescription("Ustaw RIR serii. Przytrzymaj, aby edytować notatkę.");
        controls.addView(rir,new LinearLayout.LayoutParams(dp(44),dp(42)));
        rir.setOnClickListener(v->{
            final String[] choices={"Bez RIR","RIR 0","RIR 1","RIR 2","RIR 3","RIR 4"};
            new AlertDialog.Builder(this).setTitle("Zapas powtórzeń")
                .setSingleChoiceItems(choices,rirDraft[0]+1,(d,selected)->{
                    rirDraft[0]=selected-1;
                    rir.setText(rirDraft[0]<0?"RIR":"R"+rirDraft[0]);
                    d.dismiss();
                }).setNegativeButton("Anuluj",null).show();
        });
        rir.setOnLongClickListener(v->{
            if(db.get(openSession,ex.id,index)==null)
                Toast.makeText(this,"Najpierw zapisz serię, aby dodać notatkę",Toast.LENGTH_SHORT).show();
            else editSetMetadata(openSession,ex,index,()->render());
            return true;
        });
        add(block,controls);

        save.setOnClickListener(v->{
            try {
                double w=Double.parseDouble(kg.getText().toString().trim().replace(',','.'));
                int repsValue=Integer.parseInt(reps.getText().toString().trim());
                if(!Double.isFinite(w)||w<0||w>2000||repsValue<1||repsValue>1000)
                    throw new NumberFormatException();
                WorkoutDb.Entry previousEntry=db.get(openSession,ex.id,index);
                undoPrevious=previousEntry;undoSession=openSession;
                undoExercise=ex.id;undoIndex=index;canUndoSet=true;
                WorkoutDb.Entry existing=db.get(openSession,ex.id,index);
                db.save(openSession,ex.id,index,w,repsValue,rirDraft[0],
                    existing==null?"":existing.note,existing!=null&&existing.warmup);
                save.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
                WorkoutDb.Entry saved=db.get(openSession,ex.id,index);
                boolean pr=db.isPersonalRecord(openSession,ex.id,saved);
                editingSetExercise="";editingSetIndex=-1;
                if(previousEntry==null && ex.restSeconds>0)startRest(ex.restSeconds);
                if(pr)Toast.makeText(this,"★ NOWY REKORD!  "+weight(w)+" kg",Toast.LENGTH_SHORT).show();
                if(previousEntry==null&&getSharedPreferences("forma_settings",0)
                    .getBoolean("auto_next",true) &&
                    db.countExerciseSets(openSession,ex.id)>=db.expectedSets(openWorkout,ex)) {
                    advanceAfterLastSet(ex);
                } else {
                    render();
                    scrollToExpandedExercise();
                }
            }catch(Exception error){
                Toast.makeText(this,"Sprawdź ciężar i powtórzenia",Toast.LENGTH_LONG).show();
            }
        });
    }

    private void scrollToExpandedExercise(){
        if(contentScroll!=null&&expandedCard!=null){
            ScrollView scroll=contentScroll;LinearLayout card=expandedCard;
            scroll.post(()->scroll.smoothScrollTo(0,Math.max(0,card.getTop()-dp(6))));
        }
    }

    private void changeReps(EditText field,int delta) {
        int value=0;
        try { value=Integer.parseInt(field.getText().toString().trim()); }
        catch(NumberFormatException ignored) { }
        field.setText(Integer.toString(Math.max(1,Math.min(1000,value+delta))));
    }

    private void changeWeight(EditText field,double change) {
        try {
            double current=Double.parseDouble(field.getText().toString().trim().replace(',','.'));
            double updated=Math.max(0,Math.min(2000,Math.round((current+change)*4)/4.0));
            field.setText(weight(updated));
        } catch(Exception error) {
            field.setText(weight(Math.max(0,change)));
        }
    }

    private void editSetMetadata(long session,Plan.Exercise ex,int index,Runnable completed) {
        WorkoutDb.Entry entry=db.get(session,ex.id,index);
        if(entry==null)return;
        LinearLayout form=col();
        form.setPadding(dp(19),dp(9),dp(19),dp(5));
        add(form,text("ZAPAS POWTÓRZEŃ (RIR)",12,MUTED,true));
        space(form,6);
        Spinner rir=new Spinner(this);
        String[] options={"Nie ustawiaj","0 — bez zapasu","1 powtórzenie","2 powtórzenia","3 powtórzenia","4 powtórzenia"};
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options);
        rir.setAdapter(adapter);
        rir.setSelection(Math.max(0,Math.min(5,entry.rir+1)));
        add(form,rir);
        space(form,9);
        CheckBox warmup=new CheckBox(this);
        warmup.setText("Seria rozgrzewkowa — poza progresem");
        warmup.setTextColor(TEXT);
        warmup.setChecked(entry.warmup);
        add(form,warmup);
        space(form,11);
        add(form,text("NOTATKA DO SERII",12,MUTED,true));
        space(form,5);
        EditText note=new EditText(this);
        note.setSingleLine(false);note.setMinLines(2);note.setMaxLines(4);
        note.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        note.setText(entry.note);note.setTextColor(TEXT);note.setTextSize(15);
        note.setHint("Opcjonalnie: technika, samopoczucie…");
        note.setHintTextColor(MUTED);
        note.setBackground(rounded(CARD2,10,BORDER));
        add(form,note);
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle(ex.name+" • seria "+(index+1))
            .setView(form)
            .setNegativeButton("Anuluj",null)
            .setPositiveButton("Zapisz",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try {
                String value=note.getText().toString();
                if(value.length()>500) {
                    Toast.makeText(this,"Notatka może mieć maksymalnie 500 znaków",Toast.LENGTH_LONG).show();
                    return;
                }
                db.save(session,ex.id,index,entry.weight,entry.reps,rir.getSelectedItemPosition()-1,value,warmup.isChecked());
                dialog.dismiss();
                if(completed!=null)completed.run();
            }catch(Exception error) {
                Toast.makeText(this,"Nie udało się zapisać szczegółów serii",Toast.LENGTH_LONG).show();
            }
        }));
        dialog.show();
    }

    private void showSessionActions() {
        // Consistent with the compact dark app UI, rather than Android's
        // platform-default plain list. Destructive action still requires confirmation.
        LinearLayout sheet=col();
        sheet.setPadding(dp(18),dp(14),dp(18),dp(12));
        add(sheet,text("Opcje treningu",19,TEXT,true));
        space(sheet,4);
        add(sheet,text(db.workoutLabel(openWorkout)+"  •  wybierz działanie",12,MUTED,false));
        space(sheet,13);
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setView(sheet).create();
        addSessionMenuItem(sheet,"Ⅱ","Wstrzymaj trening",
            "Wróć do planu i kontynuuj później",MUTED,()->{
                dialog.dismiss();pauseWorkout();
            });
        space(sheet,7);
        addSessionMenuItem(sheet,"✓","Zakończ i zapisz",
            "Zapisz wykonane serie w historii",GREEN,()->{
                dialog.dismiss();finishWorkout();
            });
        space(sheet,12);
        addSessionMenuItem(sheet,"×","Anuluj i usuń trening",
            "Wymaga dodatkowego potwierdzenia",RED,()->{
                dialog.dismiss();cancelWorkout();
            });
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        Window window=dialog.getWindow();
        if(window!=null) {
            window.setBackgroundDrawable(rounded(CARD,16,BORDER));
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(
                getResources().getDisplayMetrics().widthPixels-dp(16),
                WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private void addSessionMenuItem(LinearLayout target,String glyph,String title,
                                    String subtitle,int accent,Runnable action) {
        LinearLayout item=row();
        item.setPadding(dp(12),dp(9),dp(12),dp(9));
        item.setBackground(rounded(CARD2,10,BORDER));
        TextView sign=text(glyph,23,accent,true);
        sign.setGravity(Gravity.CENTER);
        item.addView(sign,new LinearLayout.LayoutParams(dp(39),dp(47)));
        LinearLayout description=col();
        LinearLayout.LayoutParams details=new LinearLayout.LayoutParams(0,-2,1);
        details.leftMargin=dp(8);
        item.addView(description,details);
        add(description,text(title,15,accent==RED?RED:TEXT,true));
        space(description,3);
        add(description,text(subtitle,12,MUTED,false));
        item.setMinimumHeight(dp(65));
        item.setClickable(true);
        item.setContentDescription(title+". "+subtitle);
        item.setOnClickListener(v->action.run());
        add(target,item);
    }
    private void pauseWorkout() {
        if(openSession<0)return;
        db.pause(openSession);
        WorkoutStatusService.stop(this);
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
                WorkoutStatusService.stop(this);
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
                    WorkoutStatusService.stop(this);
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
                WorkoutStatusService.stop(this);
                stopRest();
                openSession=-1;
                canUndoSet=false;
                detailSession=id;
                expandedExerciseId="";
                page="summary";
                render();
                if(cloud.signedIn())runCloudSync();
            }).show();
    }

    private int sessionFeeling(String notes) {
        if(notes==null)return 0;
        java.util.regex.Matcher matcher=java.util.regex.Pattern
            .compile("(?m)^Samopoczucie: ([1-5])/5$").matcher(notes);
        return matcher.find()?Integer.parseInt(matcher.group(1)):0;
    }
    private void saveSessionFeeling(WorkoutDb.Session session,int value) {
        String notes=db.sessionNotes(session.id);
        if(notes==null)notes="";
        notes=notes.replaceAll("(?m)^Samopoczucie: [1-5]/5\\s*\\n?","").trim();
        if(value>0)notes=(notes.isEmpty()?"":notes+"\n")+"Samopoczucie: "+value+"/5";
        db.updateCompleted(session.id,session.startedAt,notes);
        render();
    }
    private void showSessionReflection(WorkoutDb.Session session) {
        LinearLayout reflection=card(content);
        smallLabel(reflection,"JAK POSZEDŁ TRENING?");
        add(reflection,text("Ocena samopoczucia (opcjonalnie)",13,MUTED,false));
        space(reflection,9);
        int selected=sessionFeeling(db.sessionNotes(session.id));
        LinearLayout rating=row();
        String[] feelings={"1","2","3","4","5"};
        for(int i=0;i<5;i++) {
            final int value=i+1;
            TextView chip=text(feelings[i],16,selected==value?BG:TEXT,true);
            chip.setGravity(Gravity.CENTER);
            chip.setContentDescription("Samopoczucie "+value+" na 5");
            chip.setBackground(rounded(selected==value?TEAL:CARD2,10,selected==value?0:BORDER));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(41),1);
            if(i>0)lp.leftMargin=dp(7);
            rating.addView(chip,lp);
            chip.setOnClickListener(v->saveSessionFeeling(session,value));
        }
        add(reflection,rating);
        space(reflection,9);
        String[] meanings={"","bardzo słabo","słabo","przeciętnie","dobrze","świetnie"};
        TextView caption=text(selected>0?
            "Twoja ocena: "+selected+"/5 — "+meanings[selected]:
            "1 = bardzo słabo   ·   5 = świetnie",12,selected>0?TEAL:MUTED,false);
        add(reflection,caption);
        space(reflection,10);
        TextView note=actionLabel("✎  Dodaj / edytuj notatkę treningową  ›",TEAL);
        note.setMinHeight(dp(41));
        add(reflection,note);
        note.setOnClickListener(v->changeWorkoutNote(session));
    }

    private void showSessionSummary() {
        WorkoutDb.Session session=db.byId(detailSession);
        if(session==null||session.finishedAt<=0){page="history";render();return;}
        space(content,6);
        LinearLayout title=card(content);
        title.setBackground(rounded(CARD,16,GREEN));
        smallLabel(title,"✓  TRENING ZAKOŃCZONY");
        space(title,8);
        add(title,text(db.sessionWorkoutLabel(session),25,TEXT,true));
        add(title,text(new SimpleDateFormat("dd.MM.yyyy  HH:mm",
            Locale.forLanguageTag("pl-PL")).format(new Date(session.startedAt)),13,MUTED,false));

        LinearLayout key=card(content);
        smallLabel(key,"PODSUMOWANIE");
        summaryLine(key,"Serie (łącznie)",Integer.toString(session.sets));
        int working=0,warmups=0;
        for(WorkoutDb.Entry e:db.sets(session.id)){
            if(e.warmup)warmups++;else working++;
        }
        summaryLine(key,"Serie robocze",Integer.toString(working));
        if(warmups>0)summaryLine(key,"Rozgrzewkowe",Integer.toString(warmups));
        summaryLine(key,"Czas",formatTime(db.durationMillis(session)/1000));
        summaryLine(key,"Objętość",weight(session.volume)+" kg");
        summaryLine(key,"Nowe rekordy PR",Integer.toString(db.prCount(session.id)));
        add(key,text("Objętość uwzględnia sposób podawania ciężaru dla każdego ćwiczenia. "+
            "Rozgrzewki nie są wliczane.",12,MUTED,false));

        WorkoutDb.Session previous=WorkoutDb.isTestSession(session)?null:db.previousCompletedSameWorkout(session);
        LinearLayout compare=card(content);
        smallLabel(compare,"PORÓWNANIE Z POPRZEDNIM TRENINGIEM");
        space(compare,9);
        if(previous==null){
            add(compare,text("Brak wcześniejszej sesji z porównywalnym układem planu. Wyniki poszczególnych ćwiczeń porównujemy niezależnie od nazwy treningu.",13,MUTED,false));
        }else{
            double diff=session.volume-previous.volume;
            int setDiff=session.sets-previous.sets;
            add(compare,text((diff>=0?"+":"")+weight(diff)+" kg objętości",22,
                diff>=0?GREEN:AMBER,true));
            add(compare,text((setDiff>=0?"+":"")+setDiff+
                " serii  •  poprzednio "+weight(previous.volume)+" kg",13,MUTED,false));
            space(compare,7);
            TextView details=actionLabel("Zobacz poprzednią sesję  ›",SKY);
            details.setMinHeight(dp(34));
            add(compare,details);
            details.setOnClickListener(v->{
                detailSession=previous.id;page="detail";render();
            });
        }

        // Porównanie ćwiczeń opiera się na trwałych identyfikatorach, a nie nazwie planu.
        LinearLayout exerciseCompare=card(content);
        smallLabel(exerciseCompare,"ZMIANY W ĆWICZENIACH");
        int improvements=0,stable=0,decreases=0,comparable=0;
        java.util.HashSet<String> observed=new java.util.HashSet<>();
        for(WorkoutDb.Entry entry:db.sets(session.id)){
            if(entry.warmup || !observed.add(entry.exerciseId))continue;
            WorkoutDb.Entry bestCurrent=null;
            for(WorkoutDb.Entry candidate:db.sets(session.id)){
                if(candidate.warmup || !candidate.exerciseId.equals(entry.exerciseId))continue;
                if(bestCurrent==null || candidate.weight>bestCurrent.weight ||
                    (candidate.weight==bestCurrent.weight && candidate.reps>bestCurrent.reps))
                    bestCurrent=candidate;
            }
            WorkoutDb.Entry bestBefore=null;
            for(WorkoutDb.Session old:db.completed()){
                if(WorkoutDb.isTestSession(old)||old.id==session.id || old.startedAt>=session.startedAt)continue;
                for(WorkoutDb.Entry candidate:db.sets(old.id)){
                    if(candidate.warmup || !candidate.exerciseId.equals(entry.exerciseId))continue;
                    if(bestBefore==null || candidate.weight>bestBefore.weight ||
                        (candidate.weight==bestBefore.weight && candidate.reps>bestBefore.reps))
                        bestBefore=candidate;
                }
                if(bestBefore!=null)break;
            }
            if(bestCurrent==null || bestBefore==null)continue;
            comparable++;
            int status=Double.compare(bestCurrent.weight,bestBefore.weight);
            if(status==0)status=Integer.compare(bestCurrent.reps,bestBefore.reps);
            if(status>0)improvements++;else if(status<0)decreases++;else stable++;
        }
        if(WorkoutDb.isTestSession(session))
            add(exerciseCompare,text("Sesja testowa: nie wpływa na statystyki ani podpowiedzi.",13,AMBER,false));
        else if(comparable==0)add(exerciseCompare,text("Pierwsze wyniki tych ćwiczeń — porównanie pojawi się po kolejnej sesji.",13,MUTED,false));
        else{
            summaryLine(exerciseCompare,"Lepszy wynik",improvements+" ćw.");
            summaryLine(exerciseCompare,"Bez zmian",stable+" ćw.");
            summaryLine(exerciseCompare,"Niższy wynik",decreases+" ćw.");
            add(exerciseCompare,text("Porównanie najlepszej serii (ciężar, następnie powtórzenia) z poprzednim wykonaniem ćwiczenia.",11,MUTED,false));
        }
        showSessionReflection(session);
        space(content,5);
        Button done=button("Gotowe — wróć do planu",true);
        add(content,done);
        done.setOnClickListener(v->{page="plan";render();});
        TextView history=actionLabel("Zobacz historię treningów  ›",TEAL);
        history.setGravity(Gravity.CENTER);
        history.setMinHeight(dp(45));
        add(content,history);
        history.setOnClickListener(v->{page="history";render();});
    }

    private void startRest(int seconds) {
        if(Build.VERSION.SDK_INT>=33 && !RestTimer.canNotify(this) &&
            !getSharedPreferences("forma_timer",0).getBoolean("notifications_asked",false)) {
            getSharedPreferences("forma_timer",0).edit().putBoolean("notifications_asked",true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},6071);
        }
        RestTimer.start(this,seconds);
        getSharedPreferences("forma_settings",0).edit().putInt("rest_total",seconds).apply();
        restUntil=RestTimer.endTime(this);
        if(timerLabel!=null) timerLabel.setText(formatTime(seconds));
        if(restProgress!=null){restProgress.setMax(Math.max(1,seconds));restProgress.setProgress(0);}
    }
    private void stopRest() {
        RestTimer.stop(this);
        restUntil=0;
        if(timerLabel!=null) timerLabel.setText("00:00");
        if(restProgress!=null)restProgress.setProgress(0);
    }
    private String date(long millis) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT,Locale.forLanguageTag("pl-PL"))
                .format(new Date(millis));
    }

    private void showHistory() {
        space(content,4);
        headline(content,"Historia");
        List<WorkoutDb.Session> list=db.completed();
        if(list.isEmpty()) {
            LinearLayout c=card(content);
            add(c,text("Brak zakończonych treningów",17,TEXT,true));
            space(c,6);
            add(c,text("Wykonane sesje pojawią się tutaj.",13,MUTED,false));
            return;
        }
        for(WorkoutDb.Session session:list) {
            LinearLayout c=card(content);
            LinearLayout top=row();
            TextView kind=actionLabel(db.sessionWorkoutLabel(session),dayColor(session.workout));
            top.addView(kind,new LinearLayout.LayoutParams(0,dp(35),1));
            TextView more=actionLabel("⋮",SKY);
            more.setGravity(Gravity.CENTER);
            more.setTextSize(25);
            more.setContentDescription("Opcje treningu: edytuj lub usuń");
            top.addView(more,new LinearLayout.LayoutParams(dp(44),dp(41)));
            more.setOnClickListener(v->showCompletedActions(session));
            add(c,top);
            boolean imported=session.notes!=null&&session.notes.startsWith("Import z notatek 18.11.2026");
            String when=imported?
                new SimpleDateFormat("dd.MM.yyyy",Locale.forLanguageTag("pl-PL")).format(new Date(session.startedAt))+"  •  import":
                new SimpleDateFormat("dd.MM.yyyy  ·  HH:mm",Locale.forLanguageTag("pl-PL")).format(new Date(session.startedAt));
            add(c,text(when,17,TEXT,true));
            space(c,5);
            String duration=imported?"bez czasu":formatTime(db.durationMillis(session)/1000);
            String info=session.sets+" serii  •  "+duration+"  •  "+weight(session.volume)+" kg obj.";
            add(c,text(info,13,MUTED,false));
            if(WorkoutDb.isTestSession(session))
                add(c,text("TESTOWY  ·  poza statystykami i sugestiami",11,AMBER,true));
            String auditState=db.auditResolution(session.id);
            if(imported && session.sets!=18 && "pending".equals(auditState)) {
                space(c,3);
                add(c,text("⚠  Potwierdź dodatkowe serie lub edytuj zapis",12,AMBER,false));
            } else if(imported && "confirmed".equals(auditState)) {
                space(c,3);
                add(c,text("✓  Dodatkowe serie potwierdzone",12,GREEN,false));
            }
            clickCard(c,()->{detailSession=session.id;page="detail";render();});
        }
    }

    private void showDetail() {
        TextView back=actionLabel("‹  Historia",TEAL);
        back.setMinHeight(dp(38));
        add(content,back);
        back.setOnClickListener(v->{page="history";render();});
        WorkoutDb.Session selected=db.byId(detailSession);
        if(selected==null){page="history";render();return;}
        space(content,6);
        LinearLayout title=row();
        TextView name=text(db.sessionWorkoutLabel(selected),23,TEXT,true);
        title.addView(name,new LinearLayout.LayoutParams(0,dp(46),1));
        TextView more=text("⋮",26,TEAL,true);
        more.setGravity(Gravity.CENTER);
        more.setContentDescription("Opcje treningu: edytuj, usuń lub zobacz serie");
        title.addView(more,new LinearLayout.LayoutParams(dp(49),dp(46)));
        more.setOnClickListener(v->showCompletedActions(selected));
        add(content,title);
        add(content,text(new SimpleDateFormat("dd.MM.yyyy  ·  HH:mm",
            Locale.forLanguageTag("pl-PL")).format(new Date(selected.startedAt)),13,MUTED,false));
        if(WorkoutDb.isTestSession(selected))
            add(content,text("DANE TESTOWE  ·  bez wpływu na rekordy i analizę",12,AMBER,true));
        space(content,5);
        LinearLayout allControls=row();
        TextView collapse=actionLabel("Zwiń wszystkie",TEAL);
        collapse.setMinHeight(dp(42));
        allControls.addView(collapse,new LinearLayout.LayoutParams(0,dp(42),1));
        TextView expand=actionLabel("Rozwiń wszystkie",TEAL);
        expand.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        allControls.addView(expand,new LinearLayout.LayoutParams(0,dp(42),1));
        collapse.setOnClickListener(v->{
            for(WorkoutDb.Entry e:db.sets(detailSession))collapsedDetailExercises.add(e.exerciseId);
            render();
        });
        expand.setOnClickListener(v->{collapsedDetailExercises.clear();render();});
        add(content,allControls);
        LinearLayout stats=card(content);
        smallLabel(stats,"PODSUMOWANIE SESJI");
        LinearLayout summary=row();
        String[] values={Integer.toString(selected.sets),
            formatTime(db.durationMillis(selected)/1000),weight(selected.volume)+" kg"};
        String[] names={"SERIE","CZAS","OBJĘTOŚĆ"};
        for(int i=0;i<3;i++){
            LinearLayout cell=col();
            add(cell,text(values[i],18,TEXT,true));
            add(cell,text(names[i],10,MUTED,true));
            summary.addView(cell,new LinearLayout.LayoutParams(0,-2,1));
        }
        space(stats,8);add(stats,summary);
        space(content,2);
        String notes=db.sessionNotes(detailSession);
        if(notes!=null && notes.startsWith("Import z notatek 18.11.2026")) {
            String auditState=db.auditResolution(selected.id);
            boolean confirmed="confirmed".equals(auditState);
            boolean dismissed="dismissed".equals(auditState);
            LinearLayout audit=card(content);
            TextView auditLink=actionLabel(confirmed?"✓ Import zatwierdzony  ·  Szczegóły ›":
                dismissed?"Import — ostrzeżenie odrzucone  ›":"⚠ Sprawdź poprawność importu  ›",
                confirmed?GREEN:dismissed?MUTED:AMBER);
            auditLink.setMinHeight(dp(34));
            add(audit,auditLink);
            auditLink.setOnClickListener(v->showAuditActions(selected));
        }
        if(notes!=null&&!notes.isEmpty()) {
            LinearLayout c=card(content);
            TextView noteToggle=actionLabel("Notatka treningowa  ›",TEAL);
            noteToggle.setMinHeight(dp(35));
            add(c,noteToggle);
            TextView body=text(notes,13,MUTED,false);
            body.setVisibility(View.GONE);
            add(c,body);
            noteToggle.setOnClickListener(v->{
                boolean show=body.getVisibility()!=View.VISIBLE;
                body.setVisibility(show?View.VISIBLE:View.GONE);
                noteToggle.setText(show?"Notatka treningowa  ⌃":"Notatka treningowa  ›");
            });
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
                Plan.Exercise ex=db.exerciseById(entry.exerciseId);
                current=card(content);
                final String exerciseId=entry.exerciseId;
                LinearLayout header=row();
                TextView label=text(ex==null?entry.exerciseId:ex.name,16,TEXT,true);
                header.addView(label,new LinearLayout.LayoutParams(0,dp(38),1));
                TextView arrow=text(collapsedDetailExercises.contains(exerciseId)?"⌄":"⌃",19,MUTED,true);
                arrow.setGravity(Gravity.CENTER);
                header.addView(arrow,new LinearLayout.LayoutParams(dp(36),dp(38)));
                add(current,header);
                String exerciseComment=db.exerciseComment(selected.id,exerciseId);
                LinearLayout commentRow=row();
                TextView commentText=actionLabel(exerciseComment.isEmpty()?
                    "✎  Dodaj komentarz do ćwiczenia":
                    "✎  "+exerciseComment,exerciseComment.isEmpty()?MUTED:TEAL);
                commentText.setMaxLines(3);
                commentText.setMinHeight(dp(36));
                commentRow.addView(commentText,new LinearLayout.LayoutParams(0,-2,1));
                add(current,commentRow);
                final String exerciseName=ex==null?entry.exerciseId:ex.name;
                commentText.setOnClickListener(v->changeExerciseComment(selected.id,exerciseId,exerciseName));
                header.setContentDescription("Rozwiń lub zwiń serie ćwiczenia");
                header.setOnClickListener(v->{
                    if(!collapsedDetailExercises.add(exerciseId))
                        collapsedDetailExercises.remove(exerciseId);
                    render();
                });
                previous=entry.exerciseId;
            }
            if(current!=null&&!collapsedDetailExercises.contains(entry.exerciseId)) {
                LinearLayout row=row();
                TextView left=text(String.format(Locale.ROOT,"%02d",entry.setIndex+1),13,MUTED,true);
                row.addView(left,new LinearLayout.LayoutParams(0,dp(30),1));
                TextView right=text(weight(entry.weight)+" kg  ×  "+entry.reps,15,entry.warmup?AMBER:TEAL,true);
                row.addView(right);
                add(current,row);
                if(entry.warmup||entry.rir>=0||!entry.note.isEmpty()) {
                    String extra=(entry.warmup?"Rozgrzewka  •  ":"")+
                        (entry.rir>=0?"RIR "+entry.rir+"  ":"")+
                        (entry.note.isEmpty()?"":entry.note);
                    add(current,text(extra,12,MUTED,false));
                    space(current,5);
                }
            }
        }
    }


    private void showAuditActions(WorkoutDb.Session session) {
        new AlertDialog.Builder(this)
            .setTitle("Weryfikacja importu")
            .setMessage(db.initialImportAudit(session.id)+"\n\n"+
                "Zatwierdź = wszystkie serie są prawidłowe. "+
                "Odrzuć ostrzeżenie = ukryj komunikat bez usuwania serii. "+
                "Edytuj = popraw wpisy ręcznie.")
            .setPositiveButton("Zatwierdź",(d,w)->{
                db.setAuditResolution(session.id,"confirmed");
                render();
            })
            .setNeutralButton("Odrzuć ostrzeżenie",(d,w)->{
                db.setAuditResolution(session.id,"dismissed");
                render();
            })
            .setNegativeButton("Edytuj",(d,w)->{
                detailSession=session.id;page="edit";render();
            }).show();
    }

    private void showCompletedActions(WorkoutDb.Session session) {
        LinearLayout sheet=col();
        sheet.setPadding(dp(15),dp(14),dp(15),dp(14));
        add(sheet,text(db.sessionWorkoutLabel(session),19,TEXT,true));
        space(sheet,4);
        add(sheet,text(date(session.startedAt),12,MUTED,false));
        space(sheet,12);
        AlertDialog dialog=new AlertDialog.Builder(this).setView(sheet).create();
        addSessionMenuItem(sheet,"≡","Zobacz serie",
            "Szczegóły zapisanego treningu",TEAL,()->{
                dialog.dismiss();detailSession=session.id;page="detail";render();
            });
        space(sheet,7);
        addSessionMenuItem(sheet,"✎","Edytuj trening",
            "Ciężar, powtórzenia, RIR i notatki",SKY,()->{
                dialog.dismiss();detailSession=session.id;page="edit";render();
            });
        space(sheet,7);
        boolean test=WorkoutDb.isTestSession(session);
        addSessionMenuItem(sheet,"◈",test?"Oznacz jako prawdziwy":"Oznacz jako testowy",
            test?"Przywróć do statystyk i sugestii":"Zachowaj wpis, ale pomiń w progresie",AMBER,()->{
                dialog.dismiss();
                db.setTestSession(session.id,!test);
                render();
            });
        space(sheet,11);
        addSessionMenuItem(sheet,"×","Usuń trening",
            "Wymaga potwierdzenia",RED,()->{
                dialog.dismiss();confirmDeleteCompleted(session);
            });
        dialog.show();
        Window window=dialog.getWindow();
        if(window!=null) {
            window.setBackgroundDrawable(rounded(CARD,15,BORDER));
            window.setLayout(getResources().getDisplayMetrics().widthPixels-dp(44),
                WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private void confirmDeleteCompleted(WorkoutDb.Session session) {
        new AlertDialog.Builder(this)
            .setTitle("Usunąć trening?")
            .setMessage(db.sessionWorkoutLabel(session)+" • "+date(session.startedAt)+
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
        String original=session.notes==null?"":session.notes;
        int feeling=sessionFeeling(original);
        note.setText(original.replaceAll("(?m)^Samopoczucie: [1-5]/5\\s*\\n?","")
            .replace("[FORMA:TEST]","").trim());
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
                String userNote=note.getText().toString().trim();
                if(feeling>0)userNote+=(userNote.isEmpty()?"":"\n")+"Samopoczucie: "+feeling+"/5";
                if(WorkoutDb.isTestSession(session))
                    userNote+=(userNote.isEmpty()?"":"\n")+"[FORMA:TEST]";
                db.updateCompleted(session.id,session.startedAt,userNote);
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
                if(previous==null)db.save(session.id,ex.id,index,value,repeats);
                else db.save(session.id,ex.id,index,value,repeats,previous.rir,
                    previous.note,previous.warmup);
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
        space(content,5);
        LinearLayout editHeader=row();
        TextView editTitle=text("Edycja · "+db.sessionWorkoutLabel(session),21,TEXT,true);
        editHeader.addView(editTitle,new LinearLayout.LayoutParams(0,dp(43),1));
        TextView menu=text("⋮",25,TEAL,true);menu.setGravity(Gravity.CENTER);
        editHeader.addView(menu,new LinearLayout.LayoutParams(dp(45),dp(43)));
        menu.setOnClickListener(v->showCompletedActions(session));
        add(content,editHeader);
        add(content,text("Dotknij serii, aby poprawić ciężar i powtórzenia. Zmiany zapisują się lokalnie.",12,MUTED,false));
        space(content,9);
        LinearLayout dateCard=card(content);
        smallLabel(dateCard,"SZCZEGÓŁY SESJI");
        space(dateCard,7);
        add(dateCard,text(new SimpleDateFormat("dd.MM.yyyy",Locale.forLanguageTag("pl-PL"))
            .format(new Date(session.startedAt)),18,TEXT,true));
        space(dateCard,7);
        LinearLayout sessionActions=row();
        TextView changeDate=actionLabel("◷  Zmień datę",TEAL);
        changeDate.setGravity(Gravity.CENTER);
        sessionActions.addView(changeDate,new LinearLayout.LayoutParams(0,dp(40),1));
        changeDate.setOnClickListener(v->changeWorkoutDate(session));
        TextView note=actionLabel("✎  Notatka",SKY);
        note.setGravity(Gravity.CENTER);
        sessionActions.addView(note,new LinearLayout.LayoutParams(0,dp(40),1));
        note.setOnClickListener(v->changeWorkoutNote(session));
        add(dateCard,sessionActions);

        java.util.ArrayList<Plan.Exercise> historyExercises=new java.util.ArrayList<>(db.workoutPlan(session.workout));
        java.util.HashSet<String> visible=new java.util.HashSet<>();
        for(Plan.Exercise e:historyExercises)visible.add(e.id);
        for(WorkoutDb.Entry entry:db.sets(session.id)) {
            if(!visible.contains(entry.exerciseId)) {
                Plan.Exercise original=db.exerciseById(entry.exerciseId);
                if(original!=null) {
                    historyExercises.add(original);
                    visible.add(original.id);
                }
            }
        }
        for(Plan.Exercise ex:historyExercises) {
            LinearLayout c=card(content);
            add(c,text(ex.name,16,TEXT,true));
            String comment=db.exerciseComment(session.id,ex.id);
            TextView commentEdit=actionLabel(comment.isEmpty()?
                "✎  Komentarz (maszyna / uchwyt)":"✎  "+comment,
                comment.isEmpty()?MUTED:TEAL);
            commentEdit.setMaxLines(3);
            commentEdit.setMinHeight(dp(36));
            add(c,commentEdit);
            commentEdit.setOnClickListener(v->changeExerciseComment(session.id,ex.id,ex.name));
            space(c,4);
            int count=0;
            for(WorkoutDb.Entry entry:db.sets(session.id)) {
                if(!entry.exerciseId.equals(ex.id))continue;
                count++;
                LinearLayout line=row();
                TextView value=text((entry.setIndex+1)+".  "+weight(entry.weight)+" kg × "+entry.reps+
                    (entry.warmup?"  ○":""),15,entry.warmup?AMBER:TEXT,true);
                line.addView(value,new LinearLayout.LayoutParams(0,dp(39),1));
                Button edit=button("Edytuj",false);
                edit.setTextSize(12);
                line.addView(edit,new LinearLayout.LayoutParams(dp(68),dp(39)));
                edit.setOnClickListener(v->editSavedSet(session,ex,entry.setIndex,entry));
                TextView detail=actionLabel("RIR",SKY);
                detail.setGravity(Gravity.CENTER);
                detail.setContentDescription("Edytuj RIR, notatkę i rozgrzewkę");
                line.addView(detail,new LinearLayout.LayoutParams(dp(43),dp(39)));
                detail.setOnClickListener(v->editSetMetadata(session.id,ex,entry.setIndex,()->render()));
                TextView remove=text("×",23,MUTED,true);
                remove.setGravity(Gravity.CENTER);
                line.addView(remove,new LinearLayout.LayoutParams(dp(38),dp(39)));
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
        Button done=button("Gotowe",true);
        add(content,done);
        done.setOnClickListener(v->completeEditing());
        space(content,3);
        add(content,text("Zmiany zapisywane są od razu. Usuwanie całego treningu znajdziesz w menu ⋮.",12,MUTED,false));
    }

    private void changeCalendarMonth(int delta) {
        Calendar date=Calendar.getInstance();
        date.clear();
        date.set(calendarYear,calendarMonth,1);
        date.add(Calendar.MONTH,delta);
        calendarYear=date.get(Calendar.YEAR);
        calendarMonth=date.get(Calendar.MONTH);
        render();
    }

    private void showCalendar() {
        space(content,3);
        headline(content,"Kalendarz i statystyki");
        add(content,text("Treningi, regularność i objętość",13,MUTED,false));
        space(content,12);
        Calendar start=Calendar.getInstance();
        start.clear();
        start.set(calendarYear,calendarMonth,1);
        Calendar end=(Calendar)start.clone();
        end.add(Calendar.MONTH,1);
        Calendar previous=(Calendar)start.clone();
        previous.add(Calendar.MONTH,-1);
        long startMs=start.getTimeInMillis(),endMs=end.getTimeInMillis();
        long previousMs=previous.getTimeInMillis();
        java.util.HashMap<Integer,java.util.ArrayList<WorkoutDb.Session>> days=new java.util.HashMap<>();
        int count=0,previousCount=0;
        double volume=0,previousVolume=0;
        int thisWeek=0;
        long lastWeek=System.currentTimeMillis()-7L*24*60*60*1000;
        Calendar itemDate=Calendar.getInstance();
        for(WorkoutDb.Session session:db.completed()) {
            long time=session.startedAt;
            if(time>=lastWeek && time<=System.currentTimeMillis())thisWeek++;
            if(time>=startMs && time<endMs) {
                count++;volume+=session.volume;
                itemDate.setTimeInMillis(time);
                int day=itemDate.get(Calendar.DAY_OF_MONTH);
                if(!days.containsKey(day))days.put(day,new java.util.ArrayList<>());
                days.get(day).add(session);
            } else if(time>=previousMs&&time<startMs) {
                previousCount++;previousVolume+=session.volume;
            }
        }
        LinearLayout top=row();
        TextView back=actionLabel("‹",SKY);
        back.setTextSize(26);
        back.setGravity(Gravity.CENTER);
        top.addView(back,new LinearLayout.LayoutParams(dp(48),dp(44)));
        back.setOnClickListener(v->changeCalendarMonth(-1));
        String title=new SimpleDateFormat("LLLL yyyy",Locale.forLanguageTag("pl-PL")).format(start.getTime());
        TextView period=text(title,18,TEXT,true);
        period.setGravity(Gravity.CENTER);
        top.addView(period,new LinearLayout.LayoutParams(0,dp(44),1));
        TextView next=actionLabel("›",SKY);
        next.setTextSize(26);next.setGravity(Gravity.CENTER);
        top.addView(next,new LinearLayout.LayoutParams(dp(48),dp(44)));
        next.setOnClickListener(v->changeCalendarMonth(1));
        add(content,top);
        space(content,7);
        LinearLayout calendarCard=card(content);
        GridLayout grid=new GridLayout(this);
        grid.setColumnCount(7);
        String[] daysOfWeek={"Pn","Wt","Śr","Cz","Pt","Sb","Nd"};
        for(int col=0;col<7;col++) {
            TextView heading=text(daysOfWeek[col],12,MUTED,true);
            heading.setGravity(Gravity.CENTER);
            GridLayout.LayoutParams lp=new GridLayout.LayoutParams(
                GridLayout.spec(0),GridLayout.spec(col,1f));
            lp.width=0;lp.height=dp(29);
            grid.addView(heading,lp);
        }
        int offset=(start.get(Calendar.DAY_OF_WEEK)+5)%7;
        int length=start.getActualMaximum(Calendar.DAY_OF_MONTH);
        int slots=((offset+length+6)/7)*7;
        java.util.HashMap<Integer,Integer> scheduleWeekdays=new java.util.HashMap<>();
        for(WorkoutDb.ScheduleDay scheduled:db.schedule())
            scheduleWeekdays.put(scheduled.weekday,scheduled.workout);
        for(int n=0;n<slots;n++) {
            final int day=n-offset+1;
            int rowIndex=1+n/7;
            int column=n%7;
            boolean inRange=day>=1&&day<=length;
            java.util.ArrayList<WorkoutDb.Session> workouts=inRange?days.get(day):null;
            int weekday=column+1;
            Integer scheduledWorkout=scheduleWeekdays.get(weekday);
            LinearLayout cell=col();
            cell.setGravity(Gravity.CENTER);
            if(workouts!=null)cell.setBackground(rounded(CARD2,9,TEAL));
            if(inRange) {
                TextView date=text(Integer.toString(day),14,workouts==null?TEXT:TEAL,workouts!=null);
                date.setGravity(Gravity.CENTER);
                add(cell,date);
                if(workouts!=null) {
                    TextView dot=text(workouts.size()>1?"●●":"●",9,TEAL,true);
                    dot.setGravity(Gravity.CENTER);
                    add(cell,dot);
                    cell.setContentDescription(day+"."+(calendarMonth+1)+
                        " — wykonano "+workouts.size()+" treningów");
                    cell.setOnClickListener(v->openCalendarDay(day,workouts));
                } else if(scheduledWorkout!=null) {
                    TextView dot=text("●",8,MUTED,true);
                    dot.setGravity(Gravity.CENTER);
                    add(cell,dot);
                    cell.setContentDescription(day+"."+(calendarMonth+1)+
                        " — zaplanowano "+db.workoutLabel(scheduledWorkout));
                    cell.setOnClickListener(v->showPlannedCalendarDay(day,scheduledWorkout));
                } else {
                    cell.setContentDescription(day+"."+(calendarMonth+1)+", brak treningu");
                }
            }
            GridLayout.LayoutParams lp=new GridLayout.LayoutParams(
                GridLayout.spec(rowIndex),GridLayout.spec(column,1f));
            lp.width=0;lp.height=dp(42);
            lp.setMargins(dp(1),dp(1),dp(1),dp(1));
            grid.addView(cell,lp);
        }
        add(calendarCard,grid);
        space(calendarCard,8);
        LinearLayout legend=row();
        TextView performed=text("●  Wykonany",12,TEAL,false);
        legend.addView(performed);
        TextView planned=text("      ●  Zaplanowany",12,MUTED,false);
        legend.addView(planned);
        add(calendarCard,legend);
        space(content,5);
        add(content,text("Podsumowanie miesiąca",18,TEXT,true));
        space(content,7);
        LinearLayout stats=card(content);
        summaryLine(stats,"Treningi",count+"  ("+(count-previousCount>=0?"+":"")+(count-previousCount)+" m/m)");
        summaryLine(stats,"Dni z treningiem",days.size()+" / "+length);
        summaryLine(stats,"Objętość robocza",weight(volume)+" kg");
        summaryLine(stats,"Poprzedni miesiąc",weight(previousVolume)+" kg");
        double delta=previousVolume>0?(volume-previousVolume)*100/previousVolume:0;
        if(previousVolume>0) {
            add(stats,text("Zmiana objętości: "+(delta>=0?"+":"")+
                String.format(Locale.forLanguageTag("pl-PL"),"%.1f",delta)+"%",14,delta>=0?GREEN:AMBER,true));
        }
        space(content,7);
        LinearLayout cadence=card(content);
        smallLabel(cadence,"REGULARNOŚĆ");
        space(cadence,8);
        add(cadence,text(thisWeek+" treningi w ostatnich 7 dniach",19,TEXT,true));
        space(cadence,5);
        add(cadence,text("Cel orientacyjny: 3 sesje na tydzień. Dzień na kalendarzu otwiera zapisane treningi.",13,MUTED,false));
    }

    private void showPlannedCalendarDay(int day,int workout) {
        new AlertDialog.Builder(this)
            .setTitle(day+"."+(calendarMonth+1)+"."+calendarYear)
            .setMessage("Zaplanowano: "+db.workoutLabel(workout)+
                ". Rozpoczęcie uruchomi trening z aktualną datą i godziną.")
            .setNegativeButton("Zamknij",null)
            .setPositiveButton("Rozpocznij teraz",(dialog,which)->startWorkout(workout))
            .show();
    }

    private void openCalendarDay(int day,java.util.ArrayList<WorkoutDb.Session> sessions) {
        String[] options=new String[sessions.size()];
        for(int i=0;i<sessions.size();i++)
            options[i]=Plan.title(sessions.get(i).workout)+" • "+
                new SimpleDateFormat("HH:mm",Locale.forLanguageTag("pl-PL")).format(new Date(sessions.get(i).startedAt))+
                " • "+sessions.get(i).sets+" serii";
        new AlertDialog.Builder(this)
            .setTitle(day+"."+(calendarMonth+1)+"."+calendarYear)
            .setItems(options,(d,idx)->{
                detailSession=sessions.get(idx).id;
                page="detail";render();
            }).setNegativeButton("Zamknij",null).show();
    }

    private WorkoutDb.Entry bestInExercise(List<WorkoutDb.Entry> entries,String exerciseId) {
        WorkoutDb.Entry best=null;
        for(WorkoutDb.Entry entry:entries) {
            if(!entry.exerciseId.equals(exerciseId))continue;
            if(best==null || entry.weight>best.weight ||
                    (entry.weight==best.weight && entry.reps>best.reps)) best=entry;
        }
        return best;
    }

    private boolean exerciseIsNewRecord(String exercise,WorkoutDb.Entry current,long startedAt) {
        if(current==null)return false;
        boolean hadPrevious=false;
        for(WorkoutDb.Progress prior:db.progress(exercise)) {
            if(prior.date>=startedAt)continue;
            hadPrevious=true;
            if(prior.maxWeight>current.weight ||
                (prior.maxWeight==current.weight && prior.repsAtMax>=current.reps))return false;
        }
        return hadPrevious;
    }

    private String groupForExercise(String id) {
        if(java.util.Arrays.asList("db_flat","smith_incline","chest_press","butterfly").contains(id))
            return "Klatka";
        if(java.util.Arrays.asList("lat_wide","lat_close","lat_one","row_cable","straight_arm").contains(id))
            return "Plecy";
        if(java.util.Arrays.asList("leg_press","leg_ext","leg_curl","rev_lunge").contains(id))
            return "Nogi";
        if(java.util.Arrays.asList("cable_lateral","db_lateral","facepull").contains(id))
            return "Barki";
        if(java.util.Arrays.asList("tri_push","tri_overhead").contains(id))
            return "Triceps";
        if(java.util.Arrays.asList("bi_cable").contains(id))
            return "Biceps";
        if(java.util.Arrays.asList("calf","calf_standing","calf_seated").contains(id))
            return "Łydki";
        if(java.util.Arrays.asList("ab_crunch","pallof").contains(id))
            return "Core";
        return "Inne";
    }
    private long weekStart(int offset) {
        Calendar cal=Calendar.getInstance(Locale.forLanguageTag("pl-PL"));
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        cal.set(Calendar.HOUR_OF_DAY,0);cal.set(Calendar.MINUTE,0);
        cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);
        int days=(cal.get(Calendar.DAY_OF_WEEK)+5)%7;
        cal.add(Calendar.DAY_OF_MONTH,-days+7*offset);
        return cal.getTimeInMillis();
    }
    private static final class WeekReport {
        int sessions,workingSets;
        double volume;
        final java.util.LinkedHashMap<String,Integer> sets=new java.util.LinkedHashMap<>();
        final java.util.LinkedHashMap<String,Double> volumes=new java.util.LinkedHashMap<>();
    }
    private WeekReport weekReport(long start,long end) {
        WeekReport result=new WeekReport();
        for(WorkoutDb.Session session:db.completed()) {
            if(WorkoutDb.isTestSession(session)||session.startedAt<start||session.startedAt>=end)continue;
            result.sessions++;
            for(WorkoutDb.Entry entry:db.sets(session.id)) {
                if(entry.warmup)continue;
                result.workingSets++;
                String group=groupForExercise(entry.exerciseId);
                double amount=entry.weight*entry.reps*db.loadMultiplier(entry.exerciseId);
                result.volume+=amount;
                result.sets.put(group,result.sets.containsKey(group)?result.sets.get(group)+1:1);
                result.volumes.put(group,result.volumes.containsKey(group)?result.volumes.get(group)+amount:amount);
            }
        }
        return result;
    }
    private void showWeeklyAnalysis() {
        TextView back=actionLabel("‹  Progres",TEAL);
        back.setMinHeight(dp(43));add(content,back);
        back.setOnClickListener(v->{page="progress";render();});
        headline(content,"Analiza tygodnia");
        long start=weekStart(weeklyOffset), end=weekStart(weeklyOffset+1);
        WeekReport week=weekReport(start,end);
        WeekReport previous=weekReport(weekStart(weeklyOffset-1),start);
        LinearLayout nav=row();
        TextView left=actionLabel("‹  Poprzedni",TEAL);
        nav.addView(left,new LinearLayout.LayoutParams(0,dp(44),1));
        left.setOnClickListener(v->{weeklyOffset--;render();});
        TextView range=text(new SimpleDateFormat("dd.MM",Locale.forLanguageTag("pl-PL")).format(new Date(start))+
            " – "+new SimpleDateFormat("dd.MM.yyyy",Locale.forLanguageTag("pl-PL")).format(new Date(end-1)),13,TEXT,true);
        range.setGravity(Gravity.CENTER);
        nav.addView(range,new LinearLayout.LayoutParams(0,dp(44),1.6f));
        TextView right=actionLabel("Następny  ›",weeklyOffset<0?TEAL:MUTED);
        right.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        nav.addView(right,new LinearLayout.LayoutParams(0,dp(44),1));
        right.setOnClickListener(v->{if(weeklyOffset<0){weeklyOffset++;render();}});
        add(content,nav);
        LinearLayout metrics=card(content);
        smallLabel(metrics,"REALIZACJA TRENINGÓW");
        summaryLine(metrics,"Sesje",week.sessions+" / 3");
        summaryLine(metrics,"Serie robocze",Integer.toString(week.workingSets));
        summaryLine(metrics,"Objętość",weight(week.volume)+" kg");
        summaryLine(metrics,"Poprzedni tydzień",weight(previous.volume)+" kg");
        space(metrics,7);
        int difference=week.sessions-previous.sessions;
        add(metrics,text((difference>=0?"+":"")+difference+
            " sesji względem poprzedniego tygodnia",12,MUTED,false));
        LinearLayout part=card(content);
        smallLabel(part,"PARTIE — SERIE I OBJĘTOŚĆ");
        add(part,text("Serie przypisano do głównej partii ćwiczenia; nie liczymy serii wielostawowych kilka razy.",12,MUTED,false));
        space(part,10);
        String[] order={"Klatka","Plecy","Nogi","Barki","Biceps","Triceps","Łydki","Core","Inne"};
        int max=1;
        for(int value:week.sets.values())max=Math.max(max,value);
        boolean any=false;
        for(String group:order) {
            Integer count=week.sets.get(group);
            if(count==null||count==0)continue;
            any=true;
            LinearLayout line=row();
            TextView name=text(group,14,TEXT,true);
            line.addView(name,new LinearLayout.LayoutParams(0,dp(32),1));
            TextView value=text(count+" serii  ·  "+weight(week.volumes.get(group))+" kg",12,TEAL,false);
            line.addView(value);
            add(part,line);
            ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
            bar.setMax(max);bar.setProgress(count);
            bar.setProgressTintList(ColorStateList.valueOf(TEAL));
            bar.setProgressBackgroundTintList(ColorStateList.valueOf(BORDER));
            add(part,bar);
            space(part,4);
        }
        if(!any)add(part,text("Brak zapisanych serii roboczych w wybranym tygodniu.",14,MUTED,false));
        space(content,6);
        add(content,text("Objętość = kg × powtórzenia × mnożnik sprzętu. Własne ćwiczenia bez przypisanej partii widnieją jako „Inne”.",12,MUTED,false));
    }

    private void showProgress() {
        space(content,4);
        headline(content,"Progres");
        add(content,text("Wyniki według dnia treningowego",13,MUTED,false));
        space(content,7);
        WeekReport currentWeek=weekReport(weekStart(0),weekStart(1));
        LinearLayout weekLink=card(content);
        smallLabel(weekLink,"TEN TYDZIEŃ");
        space(weekLink,8);
        LinearLayout weekRow=row();
        String[] weeklyValues={currentWeek.sessions+" / 3",String.valueOf(currentWeek.workingSets),weight(currentWeek.volume)+" kg"};
        String[] weeklyLabels={"TRENINGI","SERIE","OBJĘTOŚĆ"};
        for(int i=0;i<3;i++){
            LinearLayout cell=col();
            add(cell,text(weeklyValues[i],i==2?17:21,TEXT,true));
            add(cell,text(weeklyLabels[i],10,MUTED,true));
            weekRow.addView(cell,new LinearLayout.LayoutParams(0,-2,1));
        }
        add(weekLink,weekRow);
        space(weekLink,10);
        add(weekLink,text("Analiza tygodnia i partii mięśniowych  ›",13,TEAL,true));
        clickCard(weekLink,()->{weeklyOffset=0;page="weekly";render();});
        space(content,6);
        LinearLayout selector=row();
        for(int day=0;day<3;day++) {
            final int selected=day;
            boolean active=selectedProgressWorkout==day;
            int accent=dayColor(day);
            TextView btn=text(Plan.title(day),13,active?BG:accent,true);
            btn.setGravity(Gravity.CENTER);
            btn.setBackground(rounded(active?accent:CARD2,12,active?0:BORDER));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(43),1);
            if(day>0)lp.leftMargin=dp(7);
            selector.addView(btn,lp);
            btn.setOnClickListener(v->{selectedProgressWorkout=selected;render();});
        }
        add(content,selector);
        space(content,12);

        WorkoutDb.Session latest=null;
        for(WorkoutDb.Session item:db.completed()) {
            if(!WorkoutDb.isTestSession(item)&&item.workout==selectedProgressWorkout){latest=item;break;}
        }
        if(latest!=null) {
            LinearLayout summary=card(content);
            LinearLayout summaryTop=row();
            TextView date=text(new SimpleDateFormat("dd.MM.yyyy",Locale.forLanguageTag("pl-PL")).format(new Date(latest.startedAt)),18,TEXT,true);
            summaryTop.addView(date,new LinearLayout.LayoutParams(0,dp(30),1));
            TextView label=actionLabel("OSTATNI TRENING",dayColor(selectedProgressWorkout));
            summaryTop.addView(label);
            add(summary,summaryTop);
            space(summary,3);
            add(summary,text(latest.sets+" serii  •  "+weight(latest.volume)+" kg objętości",13,MUTED,false));
        } else {
            LinearLayout empty=card(content);
            add(empty,text("Brak zakończonego treningu "+Plan.title(selectedProgressWorkout),15,TEXT,true));
            space(empty,5);
            add(empty,text("Poniżej są ćwiczenia z tego dnia. Po zapisaniu treningu zobaczysz tu wyniki.",12,MUTED,false));
        }
        LinearLayout header=row();
        TextView name=text("Ćwiczenia",18,TEXT,true);
        header.addView(name,new LinearLayout.LayoutParams(0,dp(36),1));
        TextView info=actionLabel("DOTKNIJ, BY ZOBACZYĆ TREND",MUTED);
        info.setTextSize(10);
        header.addView(info);
        add(content,header);
        space(content,3);
        List<WorkoutDb.Entry> results=latest==null?new java.util.ArrayList<>():db.sets(latest.id);
        List<Plan.Exercise> exercises=db.workoutPlan(selectedProgressWorkout);
        for(int i=0;i<exercises.size();i+=2) {
            LinearLayout tiles=row();
            tiles.setGravity(Gravity.TOP);
            for(int col=0;col<2;col++) {
                if(i+col>=exercises.size()) {
                    tiles.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
                    continue;
                }
                Plan.Exercise ex=exercises.get(i+col);
                WorkoutDb.Entry best=bestInExercise(results,ex.id);
                boolean pr=latest!=null && exerciseIsNewRecord(ex.id,best,latest.startedAt);
                LinearLayout tile=col();
                tile.setPadding(dp(11),dp(11),dp(11),dp(11));
                tile.setMinimumHeight(dp(126));
                tile.setBackground(rounded(CARD,13,pr?GREEN:BORDER));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
                if(col>0)lp.leftMargin=dp(8);
                lp.bottomMargin=dp(8);
                tiles.addView(tile,lp);
                TextView title=text(ex.name,13,TEXT,true);
                title.setMaxLines(3);
                title.setMinHeight(dp(49));
                add(tile,title);
                space(tile,5);
                add(tile,text(best==null?"—":weight(best.weight)+" kg",21,best==null?MUTED:dayColor(selectedProgressWorkout),true));
                List<WorkoutDb.Progress> exerciseHistory=db.progress(ex.id);
                if(exerciseHistory.size()>1){
                    WorkoutDb.Progress recent=exerciseHistory.get(0);
                    WorkoutDb.Progress prior=exerciseHistory.get(1);
                    double delta=recent.maxWeight-prior.maxWeight;
                    String direction=delta>0.001?"↑ +":delta< -0.001?"↓ ":"→ ";
                    String changeLabel=Math.abs(delta)<0.001?"bez zmian":direction+weight(delta)+" kg";
                    add(tile,text(changeLabel,11,delta>0.001?GREEN:delta< -0.001?AMBER:MUTED,true));
                }
                LinearLayout bottom=row();
                TextView reps=actionLabel(best==null?"Brak serii":("× "+best.reps+" powt."),MUTED);
                bottom.addView(reps,new LinearLayout.LayoutParams(0,dp(28),1));
                TextView mark=actionLabel(pr?"PR":"›",pr?GREEN:MUTED);
                bottom.addView(mark);
                add(tile,bottom);
                clickCard(tile,()->{
                    selectedExercise=ex.id;
                    page="exerciseProgress";
                    render();
                });
            }
            add(content,tiles);
        }
    }

    private double exerciseVolumeAt(String exercise,long startedAt) {
        double total=0;
        for(WorkoutDb.Session session:db.completed()) {
            if(WorkoutDb.isTestSession(session)||session.startedAt!=startedAt)continue;
            for(WorkoutDb.Entry e:db.sets(session.id)) {
                if(e.warmup||!e.exerciseId.equals(exercise))continue;
                total+=e.weight*e.reps*db.loadMultiplier(exercise);
            }
        }
        return total;
    }
    private double trendValue(WorkoutDb.Progress progress,String exercise,int metric) {
        if(metric==1)return progress.totalReps;
        if(metric==2)return exerciseVolumeAt(exercise,progress.date);
        return progress.maxWeight;
    }
    private String trendValueLabel(double value,int metric) {
        return metric==1?Integer.toString((int)value)+" powt.":
            weight(value)+" kg";
    }

    private void showExerciseProgress() {
        space(content,3);
        TextView back=actionLabel("‹  Progres",TEAL);
        back.setMinHeight(dp(39));
        add(content,back);
        back.setOnClickListener(v->{page="progress";render();});
        Plan.Exercise ex=db.exerciseById(selectedExercise);
        if(ex==null){page="progress";render();return;}
        space(content,4);
        headline(content,ex.name);
        List<WorkoutDb.Progress> list=db.progress(ex.id);
        if(list.isEmpty()) {
            LinearLayout c=card(content);
            add(c,text("Brak zapisanych wyników",16,TEXT,true));
            add(c,text("Wykonaj ćwiczenie, aby zacząć śledzić postęp.",13,MUTED,false));
            return;
        }
        WorkoutDb.Progress newest=list.get(0);
        WorkoutDb.Progress allTime=newest;
        for(WorkoutDb.Progress p:list) {
            if(p.maxWeight>allTime.maxWeight ||
                (p.maxWeight==allTime.maxWeight && p.repsAtMax>allTime.repsAtMax))allTime=p;
        }
        LinearLayout hero=card(content);
        smallLabel(hero,"OSTATNI WYNIK");
        space(hero,7);
        add(hero,text(weight(newest.maxWeight)+" kg × "+newest.repsAtMax,23,TEXT,true));
        space(hero,6);
        add(hero,text("Rekord: "+weight(allTime.maxWeight)+" kg × "+allTime.repsAtMax,14,GREEN,true));
        space(hero,5);
        double estimatedMax=0;
        double recordVolume=0;
        for(WorkoutDb.Progress p:list){
            if(p.repsAtMax>0 && p.repsAtMax<=12)
                estimatedMax=Math.max(estimatedMax,p.maxWeight*(1.0+p.repsAtMax/30.0));
            recordVolume=Math.max(recordVolume,exerciseVolumeAt(ex.id,p.date));
        }
        if(estimatedMax>0)
            add(hero,text("Szacowane 1RM: "+weight(estimatedMax)+" kg  ·  wzór Epleya (orientacyjnie)",12,MUTED,false));
        add(hero,text("Najwyższa objętość sesji: "+weight(recordVolume)+" kg",12,MUTED,false));
        space(hero,4);
        add(hero,text(new SimpleDateFormat("dd.MM.yyyy",Locale.forLanguageTag("pl-PL")).format(new Date(newest.date)),12,MUTED,false));

        String suggestion=db.progressionTip(ex);
        if(suggestion!=null) {
            LinearLayout advice=card(content);
            smallLabel(advice,"PROPOZYCJA PROGRESJI");
            space(advice,5);
            add(advice,text(suggestion,13,TEXT,false));
        }
        int count=Math.min(8,list.size());
        if(count>=2) {
            LinearLayout chart=card(content);
            smallLabel(chart,"TREND WYNIKÓW — "+count+" OSTATNICH SESJI");
            space(chart,10);
            String[] kinds={"Ciężar","Powtórzenia","Objętość"};
            int metric=getSharedPreferences("forma_settings",0).getInt("trend_metric",0);
            if(metric<0||metric>2)metric=0;
            LinearLayout selector=row();
            for(int i=0;i<kinds.length;i++) {
                final int chosen=i;
                TextView tab=text(kinds[i],12,metric==i?BG:MUTED,true);
                tab.setGravity(Gravity.CENTER);
                tab.setBackground(rounded(metric==i?TEAL:CARD2,9,metric==i?0:BORDER));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(37),1);
                if(i>0)lp.leftMargin=dp(5);
                selector.addView(tab,lp);
                tab.setOnClickListener(v->{
                    getSharedPreferences("forma_settings",0).edit().putInt("trend_metric",chosen).apply();
                    render();
                });
            }
            add(chart,selector);
            space(chart,11);
            double max=0;
            for(int i=0;i<count;i++)max=Math.max(max,trendValue(list.get(i),ex.id,metric));
            for(int i=count-1;i>=0;i--) {
                WorkoutDb.Progress p=list.get(i);
                LinearLayout line=row();
                TextView date=text(new SimpleDateFormat("dd.MM",Locale.forLanguageTag("pl-PL"))
                    .format(new Date(p.date)),12,MUTED,false);
                line.addView(date,new LinearLayout.LayoutParams(dp(46),dp(30)));
                LinearLayout bar=row();
                LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(11),1);
                line.addView(bar,bp);
                double value=trendValue(p,ex.id,metric);
                double fraction=max>0?Math.max(0.04,value/max):0.04;
                View segment=new View(this);
                segment.setBackground(rounded(TEAL,4,0));
                bar.addView(segment,new LinearLayout.LayoutParams(0,dp(10),(float)fraction));
                bar.addView(new View(this),new LinearLayout.LayoutParams(0,dp(10),(float)(1-fraction)));
                TextView amount=text(trendValueLabel(value,metric),12,TEXT,true);
                amount.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
                line.addView(amount,new LinearLayout.LayoutParams(dp(91),dp(30)));
                add(chart,line);
            }
            space(chart,6);
            add(chart,text(metric==2?"Objętość to suma ciężar × powtórzenia × mnożnik dla serii roboczych.":
                metric==1?"Łączna liczba powtórzeń w seriach roboczych.":
                "Najwyższy ciężar w serii roboczej danej sesji.",12,MUTED,false));
        }

        add(content,text("Ostatnie sesje",18,TEXT,true));
        space(content,8);
        for(int i=0;i<Math.min(8,list.size());i++) {
            WorkoutDb.Progress p=list.get(i);
            LinearLayout c=card(content);
            LinearLayout line=row();
            TextView date=text(new SimpleDateFormat("dd.MM.yyyy",Locale.forLanguageTag("pl-PL")).format(new Date(p.date)),13,MUTED,false);
            line.addView(date,new LinearLayout.LayoutParams(0,dp(27),1));
            line.addView(text(weight(p.maxWeight)+" kg × "+p.repsAtMax,15,TEXT,true));
            add(c,line);
            add(c,text(p.numberOfSets+" serii  •  "+p.totalReps+" powt.",12,dayColor(selectedProgressWorkout),false));
        }
    }

    private void exportBackup() {
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE,"forma-kopia-"+
            new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date())+".json");
        startActivityForResult(intent,BACKUP_REQUEST);
    }

    private void importBackup() {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent,RESTORE_REQUEST);
    }

    private void handleJsonBackup(Uri uri) {
        new Thread(()->{
            try {
                org.json.JSONObject content=WorkoutBackup.exportData(db,getSharedPreferences("forma_settings",0));
                try(OutputStream output=getContentResolver().openOutputStream(uri)) {
                    if(output==null)throw new Exception("Nie można utworzyć pliku");
                    output.write(content.toString(2).getBytes(StandardCharsets.UTF_8));
                }
                runOnUiThread(()->Toast.makeText(this,"Pełna kopia JSON zapisana ✓",Toast.LENGTH_LONG).show());
            } catch(Exception e) {
                runOnUiThread(()->new AlertDialog.Builder(this)
                    .setTitle("Błąd eksportu").setMessage(e.getMessage())
                    .setPositiveButton("OK",null).show());
            }
        }).start();
    }

    private void handleJsonRestore(Uri uri) {
        new Thread(()->{
            try {
                byte[] bytes;
                try(InputStream input=getContentResolver().openInputStream(uri);
                    ByteArrayOutputStream output=new ByteArrayOutputStream()) {
                    if(input==null)throw new Exception("Nie można odczytać pliku");
                    byte[] buffer=new byte[8192];int size;
                    while((size=input.read(buffer))!=-1) {
                        if(output.size()+size>16_000_000)
                            throw new Exception("Plik kopii jest za duży (maks. 16 MB)");
                        output.write(buffer,0,size);
                    }
                    bytes=output.toByteArray();
                }
                final org.json.JSONObject backup=new org.json.JSONObject(
                    new String(bytes,StandardCharsets.UTF_8));
                final String preview=WorkoutBackup.preview(backup);
                runOnUiThread(()->new AlertDialog.Builder(this)
                    .setTitle("Przywrócić kopię treningów?")
                    .setMessage(preview)
                    .setNegativeButton("Anuluj",null)
                    .setPositiveButton("Połącz dane",(d,w)->new Thread(()->{
                        try {
                            String summary=WorkoutBackup.restore(db,
                                getSharedPreferences("forma_settings",0),backup);
                            runOnUiThread(()->{
                                page="history";render();
                                new AlertDialog.Builder(this).setTitle("Import ukończony")
                                    .setMessage(summary).setPositiveButton("OK",null).show();
                            });
                        } catch(Exception error) {
                            runOnUiThread(()->new AlertDialog.Builder(this)
                                .setTitle("Błąd przywracania")
                                .setMessage("Nie zmieniono bazy (operacja jest transakcyjna). "+
                                    error.getMessage()).setPositiveButton("OK",null).show());
                        }
                    }).start()).show());
            } catch(Exception e) {
                runOnUiThread(()->new AlertDialog.Builder(this)
                    .setTitle("Nieprawidłowy plik kopii")
                    .setMessage(e.getMessage()).setPositiveButton("OK",null).show());
            }
        }).start();
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
        if(result==RESULT_OK && data!=null && data.getData()!=null) {
            if(request==BACKUP_REQUEST) {handleJsonBackup(data.getData());return;}
            if(request==RESTORE_REQUEST) {handleJsonRestore(data.getData());return;}
        }
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
        if(page.equals("edit")) {completeEditing();return;}
        if(page.equals("summary")) {page="history";render();return;}
        if(page.equals("planEditor")) {page="plan";render();return;}
        if(page.equals("exerciseProgress")) {page="progress";render();return;}
        if(page.equals("session")) {
            page="plan";
            render();
            return;
        }
        if(page.equals("detail")||page.equals("progress")||page.equals("history")||page.equals("settings")||page.equals("calendar")) {
            page=page.equals("detail")?"history":"plan";
            render();
        } else super.onBackPressed();
    }
}
