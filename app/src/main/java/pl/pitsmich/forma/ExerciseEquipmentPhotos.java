package pl.pitsmich.forma;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Generic exercise equipment photographs: lazy download, on-device cache, readable full-size preview. */
public final class ExerciseEquipmentPhotos {
    private static final String HOST="https://raw.githubusercontent.com/numbhill/gym-exercise-images/master/";
    private static final ExecutorService DOWNLOADS=Executors.newFixedThreadPool(2);
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final LruCache<String,Bitmap> MEMORY=new LruCache<String,Bitmap>(24*1024) {
        @Override protected int sizeOf(String key,Bitmap value){return Math.max(1,value.getByteCount()/1024);}
    };
    private ExerciseEquipmentPhotos(){}

    public static String relativePath(String id) {
        if(id==null)return null;
        switch(id){
            case "db_flat": return "chest/flat-dumbbell-press.webp";
            case "smith_incline": return "chest/incline-bench-press.webp";
            case "chest_press": return "chest/machine-chest-press.webp";
            case "lat_wide": case "lat_close": return "back/lat-pulldown.webp";
            case "lat_one": return "back/one-arm-cable-lat-pulldown.webp";
            case "row_cable": return "back/seated-cable-row.webp";
            case "straight_arm": return "back/lat-pulldown.webp";
            case "facepull": return "shoulders/face-pull.webp";
            case "leg_press": return "legs/leg-press.webp";
            case "leg_curl": return "legs/leg-curl.webp";
            case "leg_ext": return "legs/leg-extension.webp";
            case "calf_standing": return "legs/standing-calf-raise.webp";
            case "calf_seated": return "legs/calf-raise.webp";
            case "calf": return "legs/calf-raise.webp";
            case "db_lateral": return "arms/dumbbell-curl.webp";
            case "cable_lateral": return "shoulders/cable-lateral-raise.webp";
            case "tri_push": return "arms/rope-pushdown.webp";
            case "tri_overhead": return "arms/overhead-cable-triceps-extension.webp";
            case "bi_cable": return "arms/cable-curl.webp";
            default:return null;
        }
    }

    /** Returns false when the exercise has no reliable equipment reference. */
    public static boolean hasReference(String id){return relativePath(id)!=null;}

    private static File disk(Context context,String name) {
        String file=name.replace('/','_');
        File folder=new File(context.getFilesDir(),"equipment-photos");
        if(!folder.exists())folder.mkdirs();
        return new File(folder,file);
    }

    private static Bitmap decode(byte[] data) {
        if(data.length==0||data.length>4*1024*1024)return null;
        BitmapFactory.Options opts=new BitmapFactory.Options();
        opts.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(data,0,data.length,opts);
        if(opts.outWidth<100||opts.outHeight<100||opts.outWidth>5000||opts.outHeight>5000)return null;
        opts.inJustDecodeBounds=false;
        opts.inSampleSize=Math.max(1,Math.max(opts.outWidth,opts.outHeight)/1280);
        return BitmapFactory.decodeByteArray(data,0,data.length,opts);
    }

    private static byte[] readLimited(InputStream stream) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] buf=new byte[8192];
        int n;
        while((n=stream.read(buf))!=-1){
            if(out.size()+n>4*1024*1024)throw new IllegalStateException("Photo too large");
            out.write(buf,0,n);
        }
        return out.toByteArray();
    }

    public static void into(Context activityContext,String exerciseId,ImageView view,Runnable missing) {
        String path=relativePath(exerciseId);
        if(path==null){if(missing!=null)missing.run();return;}
        Bitmap cached=MEMORY.get(path);
        if(cached!=null&&!cached.isRecycled()){view.setImageBitmap(cached);return;}
        Context context=activityContext.getApplicationContext();
        DOWNLOADS.execute(()->{
            Bitmap result=null;
            File local=disk(context,path);
            try {
                if(local.isFile()){
                    try(InputStream input=new FileInputStream(local)){result=decode(readLimited(input));}
                }
                if(result==null) {
                    URL url=new URL(HOST+path);
                    HttpURLConnection c=(HttpURLConnection)url.openConnection();
                    try {
                        c.setConnectTimeout(6000);
                        c.setReadTimeout(9000);
                        c.setInstanceFollowRedirects(true);
                        c.setRequestProperty("Accept","image/webp,image/*");
                        if(c.getResponseCode()==200){
                            String type=c.getContentType();
                            if(type!=null&&type.startsWith("image/")){
                                byte[] bytes;
                                try(InputStream input=c.getInputStream()){bytes=readLimited(input);}
                                result=decode(bytes);
                                if(result!=null){
                                    try(FileOutputStream output=new FileOutputStream(local)){output.write(bytes);}
                                }
                            }
                        }
                    }finally {c.disconnect();}
                }
                if(result!=null)MEMORY.put(path,result);
            }catch(Exception ignored){}
            Bitmap bitmap=result;
            MAIN.post(()->{
                if(!path.equals(view.getTag()))return;
                if(bitmap!=null)view.setImageBitmap(bitmap);
                else if(missing!=null)missing.run();
            });
        });
    }

    public static String sourceUrl(){return "https://github.com/numbhill/gym-exercise-images";}
}
