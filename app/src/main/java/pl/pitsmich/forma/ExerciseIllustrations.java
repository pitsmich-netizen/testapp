package pl.pitsmich.forma;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Offline preview art. Each atlas contains four sheets (2x2), each sheet three exercises. */
public final class ExerciseIllustrations {
    private ExerciseIllustrations(){}
    private static final Bitmap[] atlases=new Bitmap[2];

    /** Atlas group [0..7] and row [0..2]. Incorrectly oriented generated examples are omitted. */
    private static int[] location(String id) {
        if(id==null)return null;
        switch(id){
            case "db_flat":return new int[]{0,0};
            case "smith_incline":return new int[]{0,1};
            case "chest_press":return new int[]{0,2};
            case "lat_wide":return new int[]{1,0};
            case "lat_close":return new int[]{1,1};
            case "row_cable":return new int[]{2,0};
            case "straight_arm":return new int[]{2,1};
            case "facepull":return new int[]{2,2};
            // lat_one: generated picture has a low pulley instead of high; no art until corrected.
            // butterfly: generated start/end are reversed; no art until corrected.
            // leg_press: generated sled is angled, unlike the plan's horizontal machine.
            case "leg_curl":return new int[]{3,2};
            case "leg_ext":return new int[]{4,0};
            case "rev_lunge":return new int[]{4,1};
            case "calf_standing":return new int[]{4,2};
            case "calf_seated":return new int[]{5,0};
            case "calf":return new int[]{5,1};
            case "ab_crunch":return new int[]{5,2};
            case "cable_lateral":return new int[]{6,0};
            case "db_lateral":return new int[]{6,1};
            case "tri_push":return new int[]{6,2};
            case "tri_overhead":return new int[]{7,0};
            case "bi_cable":return new int[]{7,1};
            case "pallof":return new int[]{7,2};
            default:return null;
        }
    }
    private static synchronized Bitmap atlas(Context context,int number) {
        Bitmap cached=atlases[number];
        if(cached!=null && !cached.isRecycled())return cached;
        try(InputStream source=context.getAssets().open("forma_atlas_"+number+".base64");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[2048];
            int count;
            while((count=source.read(buffer))!=-1)bytes.write(buffer,0,count);
            byte[] decoded=Base64.decode(bytes.toString(StandardCharsets.US_ASCII.name()),Base64.DEFAULT);
            Bitmap bitmap=BitmapFactory.decodeByteArray(decoded,0,decoded.length);
            if(bitmap==null || bitmap.getWidth()!=400 || bitmap.getHeight()!=500)return null;
            atlases[number]=bitmap;
            return bitmap;
        }catch(Exception ignored){return null;}
    }
    /** Cropped START / KONIEC pair or null for exercises lacking an accurate illustration. */
    public static Bitmap forId(Context context,String id){
        int[] loc=location(id);
        if(loc==null)return null;
        Bitmap source=atlas(context,loc[0]/4);
        if(source==null)return null;
        int sheet=loc[0]%4;
        int x=(sheet%2)*200;
        int y=(sheet/2)*250+(loc[1]*250)/3;
        int bottom=(sheet/2)*250+((loc[1]+1)*250)/3;
        try {return Bitmap.createBitmap(source,x,y,200,bottom-y);}
        catch(Exception ignored){return null;}
    }
}
