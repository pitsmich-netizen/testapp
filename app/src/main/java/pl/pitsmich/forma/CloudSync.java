package pl.pitsmich.forma;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Optional Supabase sync. All workouts work without a login or internet connection.
 * The public publishable key is NOT a credential to access another user's rows;
 * every database request uses an Auth access token with server-side RLS.
 */
public final class CloudSync {
    private static final String BASE="https://yulcsnmlqxogrggkiayb.supabase.co";
    private static final String API_KEY="sb_publishable_6QxgKWASAW5ATDPN2Maf3g_gda51FKr";
    private static final String PREFS="forma_cloud";
    private static final String SESSION_KEY="auth_encrypted";
    private static final String BOUND_UID="bound_uid";
    private static final String KEY_ALIAS="forma-cloud-auth-v1";
    private static final int PAGE_SIZE=500;
    private final Activity activity;
    private final WorkoutDb db;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService executor=Executors.newSingleThreadExecutor();

    public interface Callback { void complete(boolean success,String message); }

    public CloudSync(Activity activity,WorkoutDb db) {
        this.activity=activity;
        this.db=db;
    }

    public boolean signedIn() { return loadToken()!=null; }

    public void signIn(String email,String password,boolean createAccount,Callback cb) {
        executor.execute(()->{
            try {
                if(email.trim().isEmpty() || password.isEmpty()) throw new Exception("Wpisz e-mail i hasło.");
                JSONObject input=new JSONObject();
                input.put("email",email.trim());
                input.put("password",password);
                String url=BASE+"/auth/v1/"+(createAccount?"signup":"token?grant_type=password");
                JSONObject reply=new JSONObject(request("POST",url,input.toString(),null,null));
                if(!reply.has("access_token")) {
                    send(cb,true,"Konto utworzone. Jeśli Supabase wymaga potwierdzenia adresu e-mail, sprawdź skrzynkę i zaloguj się później.");
                    return;
                }
                remember(reply);
                String outcome=syncNow();
                send(cb,true,"Zalogowano. "+outcome);
            }catch(Exception e) { send(cb,false,e.getMessage()); }
        });
    }

    public void sync(Callback cb) {
        executor.execute(()->{
            try { send(cb,true,syncNow()); }
            catch(Exception e) { send(cb,false,e.getMessage()); }
        });
    }

    public void signOut() {
        prefs().edit().remove(SESSION_KEY).apply();
    }
    public void close() { executor.shutdown(); }

    private SharedPreferences prefs() { return activity.getSharedPreferences(PREFS,0); }
    private void send(Callback cb,boolean ok,String message) {
        main.post(()->cb.complete(ok,message));
    }

    private static String required(JSONObject obj,String field) throws Exception {
        String value=obj.optString(field,"");
        if(value.isEmpty()) throw new Exception("Brakuje pola "+field+" w odpowiedzi logowania.");
        return value;
    }
    private void remember(JSONObject reply) throws Exception {
        JSONObject user=reply.optJSONObject("user");
        String uid=user!=null?user.optString("id",""):reply.optString("user_id","");
        if(uid.isEmpty()) {
            JSONObject old=loadToken();
            uid=old!=null?old.optString("uid",""):"";
        }
        if(uid.isEmpty()) throw new Exception("Nie rozpoznano konta Supabase.");
        String existing=prefs().getString(BOUND_UID,null);
        if(existing!=null && !existing.equals(uid)) {
            throw new Exception("Ten telefon ma już dane powiązane z innym kontem FORMA. Aby uniknąć wysłania prywatnych treningów na inne konto, synchronizacja została zablokowana.");
        }
        JSONObject session=new JSONObject();
        session.put("uid",uid);
        session.put("access_token",required(reply,"access_token"));
        session.put("refresh_token",required(reply,"refresh_token"));
        long expires=reply.optLong("expires_at",0);
        if(expires<=0) expires=System.currentTimeMillis()/1000L+reply.optLong("expires_in",3600);
        session.put("expires_at",expires);
        prefs().edit().putString(SESSION_KEY,encrypt(session.toString()))
            .putString(BOUND_UID,uid).apply();
    }

    private JSONObject loadToken() {
        try {
            String encrypted=prefs().getString(SESSION_KEY,null);
            if(encrypted==null || encrypted.isEmpty()) return null;
            return new JSONObject(decrypt(encrypted));
        }catch(Exception e) {
            prefs().edit().remove(SESSION_KEY).apply();
            return null;
        }
    }

    private JSONObject activeToken() throws Exception {
        JSONObject token=loadToken();
        if(token==null) throw new Exception("Najpierw zaloguj się do konta FORMA.");
        if(token.optLong("expires_at",0) <= System.currentTimeMillis()/1000+90) {
            JSONObject body=new JSONObject();
            body.put("refresh_token",required(token,"refresh_token"));
            JSONObject refreshed=new JSONObject(request("POST",BASE+"/auth/v1/token?grant_type=refresh_token",body.toString(),null,null));
            if(!refreshed.has("user_id") && refreshed.optJSONObject("user")==null)
                refreshed.put("user_id",required(token,"uid"));
            remember(refreshed);
            token=loadToken();
            if(token==null) throw new Exception("Sesja wygasła, zaloguj się ponownie.");
        }
        return token;
    }

    private String syncNow() throws Exception {
        JSONObject token=activeToken();
        String uid=required(token,"uid");
        String access=required(token,"access_token");

        // Download first, then upload locally stored workouts.
        Map<String,String> remoteIds=new HashMap<>();
        int pulledSessions=0;
        for(JSONObject remote:allPages("forma_sessions","id,client_id,workout,started_at,finished_at,notes",access)) {
            String cloudId=remote.getString("client_id");
            String serverId=remote.getString("id");
            remoteIds.put(cloudId,serverId);
            long started=parseTime(remote.getString("started_at"));
            long finished=remote.isNull("finished_at")?0:parseTime(remote.getString("finished_at"));
            if(finished==0) continue; // Do not overwrite or duplicate an active workout.
            db.insertCloudSession(cloudId,remote.getInt("workout"),started,finished,remote.optString("notes",""));
            pulledSessions++;
        }

        Map<String,Long> localIds=new HashMap<>();
        for(WorkoutDb.Session session:db.completed()) {
            if(session.cloudId!=null) localIds.put(session.cloudId,session.id);
        }
        for(JSONObject remote:allPages("forma_sets","session_id,exercise_id,set_index,weight_kg,reps",access)) {
            String remoteSessionId=remote.getString("session_id");
            String cloudId=null;
            for(Map.Entry<String,String> e:remoteIds.entrySet()) {
                if(e.getValue().equals(remoteSessionId)) { cloudId=e.getKey(); break; }
            }
            if(cloudId==null) continue;
            Long localId=localIds.get(cloudId);
            if(localId==null) continue;
            db.insertCloudSet(localId,remote.getString("exercise_id"),
                remote.getInt("set_index"),remote.getDouble("weight_kg"),remote.getInt("reps"));
        }

        int uploaded=0;
        for(WorkoutDb.Session session:db.completed()) {
            if(session.cloudId==null || session.cloudId.isEmpty()) continue;
            String remoteId=remoteIds.get(session.cloudId);
            if(remoteId==null) {
                JSONObject body=new JSONObject();
                body.put("user_id",uid);
                body.put("client_id",session.cloudId);
                body.put("workout",session.workout);
                body.put("started_at",iso(session.startedAt));
                body.put("finished_at",iso(session.finishedAt));
                body.put("notes",session.notes==null?"":session.notes);
                JSONArray res=new JSONArray(request("POST",
                    BASE+"/rest/v1/forma_sessions?on_conflict=user_id,client_id",
                    new JSONArray().put(body).toString(),access,"resolution=merge-duplicates,return=representation"));
                if(res.length()==0) throw new Exception("Serwer nie zwrócił identyfikatora sesji.");
                remoteId=res.getJSONObject(0).getString("id");
                remoteIds.put(session.cloudId,remoteId);
            }
            JSONArray sets=new JSONArray();
            for(WorkoutDb.Entry entry:db.sets(session.id)) {
                JSONObject row=new JSONObject();
                row.put("session_id",remoteId);
                row.put("user_id",uid);
                row.put("exercise_id",entry.exerciseId);
                row.put("set_index",entry.setIndex);
                row.put("weight_kg",entry.weight);
                row.put("reps",entry.reps);
                sets.put(row);
            }
            if(sets.length()>0)
                request("POST",BASE+"/rest/v1/forma_sets?on_conflict=session_id,exercise_id,set_index",
                    sets.toString(),access,"resolution=merge-duplicates,return=minimal");
            uploaded++;
        }
        return "Synchronizacja gotowa. Sesje lokalne: "+uploaded+
            ", sesje z chmury: "+pulledSessions+".";
    }

    private List<JSONObject> allPages(String table,String columns,String token) throws Exception {
        java.util.ArrayList<JSONObject> out=new java.util.ArrayList<>();
        for(int offset=0;offset<100000;offset+=PAGE_SIZE) {
            String endpoint=BASE+"/rest/v1/"+table+"?select="+columns+"&order=created_at.asc&limit="+PAGE_SIZE+"&offset="+offset;
            JSONArray items=new JSONArray(request("GET",endpoint,null,token,null));
            for(int i=0;i<items.length();i++) out.add(items.getJSONObject(i));
            if(items.length()<PAGE_SIZE) break;
        }
        return out;
    }

    private static String iso(long millis) {
        SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(millis));
    }
    private static long parseTime(String s) {
        return OffsetDateTime.parse(s).toInstant().toEpochMilli();
    }

    private String request(String method,String path,String body,String token,String preference) throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL(path).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("apikey",API_KEY);
        conn.setRequestProperty("Accept","application/json");
        if(token!=null) conn.setRequestProperty("Authorization","Bearer "+token);
        if(preference!=null) conn.setRequestProperty("Prefer",preference);
        if(body!=null) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type","application/json");
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
            try(OutputStream out=conn.getOutputStream()) { out.write(bytes); }
        }
        try {
            int code=conn.getResponseCode();
            InputStream stream=(code>=200 && code<300)?conn.getInputStream():conn.getErrorStream();
            String result=stream==null?"":readLimited(stream,2_000_000);
            if(code<200 || code>=300) {
                String error="Błąd serwera ("+code+").";
                try {
                    JSONObject response=new JSONObject(result);
                    error=response.optString("msg",response.optString("message",response.optString("error_description",error)));
                }catch(Exception ignored) { }
                throw new Exception(error);
            }
            return result;
        } finally {conn.disconnect();}
    }
    private static String readLimited(InputStream in,int max) throws Exception {
        try(InputStream stream=in; ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192];int n;
            while((n=stream.read(buffer))!=-1) {
                if(out.size()+n>max) throw new Exception("Odpowiedź serwera jest za duża.");
                out.write(buffer,0,n);
            }
            return out.toString("UTF-8");
        }
    }
    private SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if(ks.containsAlias(KEY_ALIAS))
            return (SecretKey)ks.getKey(KEY_ALIAS,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return generator.generateKey();
    }
    private String encrypt(String plain) throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] iv=cipher.getIV();
        byte[] ciphertext=cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(iv,Base64.NO_WRAP)+
            "."+Base64.encodeToString(ciphertext,Base64.NO_WRAP);
    }
    private String decrypt(String encrypted) throws Exception {
        String[] parts=encrypted.split("\\.",2);
        if(parts.length!=2) throw new Exception("Błąd zapisu sesji.");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.DEFAULT)));
        return new String(cipher.doFinal(Base64.decode(parts[1],Base64.DEFAULT)),StandardCharsets.UTF_8);
    }
}
