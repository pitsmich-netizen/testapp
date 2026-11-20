package pl.pitsmich.forma;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * Sharp offline vector schematics, rendered to the actual screen resolution.
 * No enlarged low-resolution raster atlases; unknown exercise variations show text instead.
 */
public final class ExerciseDiagramView extends View {
    private static final int MINT=Color.rgb(120,196,177);
    private static final int WHITE=Color.rgb(240,244,244);
    private static final int MUTED=Color.rgb(148,156,162);
    private static final int CARD=Color.rgb(30,32,34);
    private final String exerciseId;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);

    public ExerciseDiagramView(Context context,String exerciseId){
        super(context);
        this.exerciseId=exerciseId;
        setContentDescription("Ostry schemat wektorowy ćwiczenia: pozycja początkowa i końcowa");
    }

    public static boolean supports(String id){
        if(id==null)return false;
        switch(id){
            case "lat_wide": case "lat_close": case "db_flat": case "row_cable":
            case "db_lateral": case "cable_lateral": case "leg_ext":
            case "tri_push": case "calf_standing": return true;
            default:return false;
        }
    }

    @Override protected void onMeasure(int widthMeasureSpec,int heightMeasureSpec){
        int width=MeasureSpec.getSize(widthMeasureSpec);
        int desired=Math.max((int)(140*getResources().getDisplayMetrics().density),
            Math.round(width*0.49f));
        setMeasuredDimension(width,resolveSize(desired,heightMeasureSpec));
    }

    private void stroke(int color,float width){
        p.setColor(color);p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
    }
    private void fill(int color){p.setColor(color);p.setStyle(Paint.Style.FILL);}
    private void line(Canvas c,float x1,float y1,float x2,float y2,int color,float w){
        stroke(color,w);c.drawLine(x1,y1,x2,y2,p);
    }
    private void circle(Canvas c,float x,float y,float r,int color){
        fill(color);c.drawCircle(x,y,r,p);
    }
    private void rect(Canvas c,float l,float t,float r,float b,int color){
        fill(color);c.drawRoundRect(l,t,r,b,5,5,p);
    }
    private void text(Canvas c,String v,float x,float y,float size,int color){
        fill(color);p.setTextSize(size);p.setTypeface(android.graphics.Typeface.create(
            "sans-serif-medium",android.graphics.Typeface.NORMAL));c.drawText(v,x,y,p);
    }
    private void joint(Canvas c,float x,float y){
        circle(c,x,y,3,MINT);
    }
    private void limbs(Canvas c,float... coordinates){
        for(int i=0;i+3<coordinates.length;i+=2){
            if(i+3>=coordinates.length)break;
            line(c,coordinates[i],coordinates[i+1],coordinates[i+2],coordinates[i+3],WHITE,5.5f);
        }
    }
    private void head(Canvas c,float x,float y){
        circle(c,x,y,11,WHITE);
        line(c,x-5,y+11,x-5,y+18,MUTED,2);
    }
    private void machine(Canvas c,float x,float top,float bottom){
        line(c,x,top,x,bottom,MUTED,3);
        rect(c,x-8,bottom-29,x+7,bottom-5,MUTED);
        for(int i=0;i<3;i++)line(c,x-7,bottom-25+i*7,x+6,bottom-25+i*7,CARD,1);
    }
    private void bar(Canvas c,float x,float y,float width){
        line(c,x-width/2,y,x+width/2,y,WHITE,5);
        rect(c,x-width/2-3,y-7,x-width/2+4,y+7,MINT);
        rect(c,x+width/2-4,y-7,x+width/2+3,y+7,MINT);
    }
    private void seated(Canvas c,float x,float y){
        head(c,x,y);
        line(c,x,y+15,x,y+73,WHITE,10);
        line(c,x,y+70,x-30,y+84,WHITE,6);
        line(c,x-30,y+84,x-30,y+122,WHITE,6);
        line(c,x,y+70,x+30,y+84,WHITE,6);
        line(c,x+30,y+84,x+30,y+122,WHITE,6);
        line(c,x-42,y+76,x+42,y+76,MUTED,4);
        line(c,x,y+77,x,y+112,MUTED,4);
    }
    private void standing(Canvas c,float x,float y){
        head(c,x,y);
        line(c,x,y+12,x,y+85,WHITE,10);
        line(c,x,y+84,x-24,y+133,WHITE,6);
        line(c,x-24,y+133,x-30,y+142,WHITE,5);
        line(c,x,y+84,x+24,y+133,WHITE,6);
        line(c,x+24,y+133,x+31,y+142,WHITE,5);
    }
    private void pose(Canvas c,String id,boolean finish){
        if(id.equals("lat_wide") || id.equals("lat_close")){
            machine(c,141,16,160);
            line(c,102,18,141,18,MUTED,3);
            line(c,102,18,102,finish?49:29,MUTED,2);
            seated(c,94,65);
            float reach=id.equals("lat_wide")?44:32;
            float handY=finish?51:28;
            bar(c,94,handY,reach*2+13);
            if(finish){
                limbs(c,94-13,87,94-31,105,94-reach,handY);
                limbs(c,94+13,87,94+31,105,94+reach,handY);
            }else{
                limbs(c,94-13,87,94-reach,handY);
                limbs(c,94+13,87,94+reach,handY);
            }
            return;
        }
        if(id.equals("db_flat")){
            // Side-view bench: shoulders at left, knees and feet to the right.
            rect(c,29,122,153,134,MUTED);
            line(c,104,135,104,161,MUTED,4);
            circle(c,45,104,11,WHITE);
            line(c,56,108,125,109,WHITE,11);
            line(c,125,109,147,140,WHITE,7);
            line(c,147,140,147,164,WHITE,7);
            line(c,78,110,78,finish?45:87,WHITE,6);
            line(c,95,110,95,finish?45:87,WHITE,6);
            bar(c,78,finish?42:84,23);
            bar(c,95,finish?42:84,23);
            return;
        }
        if(id.equals("row_cable")){
            machine(c,154,32,164);
            line(c,25,136,138,136,MUTED,3);
            head(c,47,61);
            line(c,47,75,49,120,WHITE,10);
            line(c,49,120,94,124,WHITE,6);
            line(c,94,124,114,145,WHITE,6);
            float hand=finish?86:131;
            line(c,53,84,finish?77:hand,finish?97:102,WHITE,6);
            if(finish)line(c,77,97,86,98,WHITE,6);
            line(c,hand,98,154,98,MINT,2);
            circle(c,hand,98,5,MINT);
            return;
        }
        if(id.equals("db_lateral")||id.equals("cable_lateral")){
            standing(c,92,29);
            if(finish){
                line(c,78,57,46,67,WHITE,6);
                line(c,46,67,26,57,WHITE,6);
                line(c,106,57,136,67,WHITE,6);
                line(c,136,67,157,57,WHITE,6);
                bar(c,25,57,14);bar(c,158,57,14);
            }else{
                line(c,78,57,70,99,WHITE,6);
                line(c,106,57,114,99,WHITE,6);
                bar(c,70,105,14);bar(c,114,105,14);
            }
            if(id.equals("cable_lateral")){
                circle(c,20,146,9,MUTED);
                line(c,20,146,finish?25:70,finish?57:105,MINT,2);
            }
            return;
        }
        if(id.equals("leg_ext")){
            rect(c,40,95,104,105,MUTED);
            line(c,44,46,44,95,MUTED,5);
            head(c,60,36);
            line(c,60,49,74,89,WHITE,11);
            line(c,74,89,112,106,WHITE,8);
            line(c,112,106,finish?151:116,finish?106:149,WHITE,7);
            circle(c,finish?151:116,finish?106:149,7,MINT);
            line(c,79,105,79,163,MUTED,4);
            return;
        }
        if(id.equals("tri_push")){
            machine(c,150,20,169);
            standing(c,88,27);
            line(c,88,54,150,20,MUTED,2);
            line(c,78,56,73,86,WHITE,6);
            line(c,102,56,108,86,WHITE,6);
            line(c,73,86,finish?78:78,finish?120:65,WHITE,6);
            line(c,108,86,finish?103:103,finish?120:65,WHITE,6);
            bar(c,90,finish?123:68,31);
            return;
        }
        if(id.equals("calf_standing")){
            standing(c,90,24);
            line(c,76,55,66,107,WHITE,6);
            line(c,104,55,114,107,WHITE,6);
            rect(c,42,163,138,169,MUTED);
            if(finish){
                line(c,65,143,61,161,WHITE,5);
                line(c,115,143,119,161,WHITE,5);
            }else{
                line(c,65,143,65,162,WHITE,5);
                line(c,115,143,115,162,WHITE,5);
            }
        }
    }

    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float w=getWidth(),h=getHeight();
        if(w<=0||h<=0)return;
        canvas.drawColor(CARD);
        float scale=Math.min(w/420f,h/235f);
        canvas.save();
        canvas.translate((w-420*scale)/2,(h-235*scale)/2);
        canvas.scale(scale,scale);
        for(int i=0;i<2;i++){
            int x=i==0?5:215;
            rect(canvas,x,5,x+200,225,Color.rgb(24,28,29));
            stroke(MINT,1.8f);
            canvas.drawRoundRect(x,5,x+200,225,13,13,p);
            text(canvas,i==0?"START":"KONIEC",x+12,27,14,MINT);
            canvas.save();
            canvas.translate(x+8,29);
            canvas.scale(.99f,.99f);
            pose(canvas,exerciseId,i==1);
            canvas.restore();
        }
        canvas.restore();
    }
}
