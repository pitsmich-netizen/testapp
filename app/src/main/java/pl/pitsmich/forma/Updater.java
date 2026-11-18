package pl.pitsmich.forma;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.EditText;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;

/**
 * HTTPS manifest OTA for APKs distributed outside Google Play.
 * Updates MUST be signed with the same signing certificate as the installed app.
 * Never embed GitHub tokens or private repository credentials in an APK.
 */
public final class Updater {
    private static final String KEY_FEED="update_feed";
    private static final String DEFAULT_FEED="https://github.com/pitsmich-netizen/testapp/releases/latest/download/latest.json";
    private static final String KEY_LAST_CHECK="update_last_check";
    private static final long CHECK_INTERVAL_MS=24L*60L*60L*1000L;
    private static final long MAX_APK_BYTES=150L*1024L*1024L;
    private final Activity activity;
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean awaitingPermission=false;

    private static final class Release {
        int versionCode;
        String versionName, apkUrl, sha256, notes;
    }

    public Updater(Activity activity) { this.activity=activity; }

    public void afterPermissionReturn() {
        if(awaitingPermission && (Build.VERSION.SDK_INT<26 || activity.getPackageManager().canRequestPackageInstalls())) {
            awaitingPermission=false;
            File apk=new File(new File(activity.getCacheDir(),"updates"),"forma-update.apk");
            if(apk.isFile()) install(apk);
        }
    }

    public String feed() {
        return activity.getSharedPreferences("forma_preferences",0).getString(KEY_FEED,DEFAULT_FEED);
    }
    public void configure() {
        EditText input=new EditText(activity);
        input.setSingleLine(false);
        input.setText(feed());
        input.setHint("https://.../latest.json");
        int p=(int)(20*activity.getResources().getDisplayMetrics().density);
        android.widget.FrameLayout wrap=new android.widget.FrameLayout(activity);
        wrap.setPadding(p,p,p,p);
        wrap.addView(input);
        new AlertDialog.Builder(activity)
            .setTitle("Kanał aktualizacji OTA")
            .setMessage("Domyślnie używamy publicznych GitHub Releases FORMA. Zmień adres tylko, jeśli przeniesiesz aktualizacje na inny zaufany serwer.")
            .setView(wrap)
            .setNegativeButton("Anuluj",null)
            .setNeutralButton("Wyłącz",(d,w)->{
                activity.getSharedPreferences("forma_preferences",0).edit()
                    .putString(KEY_FEED,"").remove(KEY_LAST_CHECK).apply();
                Toast.makeText(activity,"Sprawdzanie aktualizacji wyłączone",Toast.LENGTH_SHORT).show();
            })
            .setPositiveButton("Zapisz",(d,w)->{
                String url=input.getText().toString().trim();
                if(!isHttps(url)) {
                    Toast.makeText(activity,"Wymagany pełny adres HTTPS",Toast.LENGTH_LONG).show();
                    return;
                }
                activity.getSharedPreferences("forma_preferences",0).edit()
                    .putString(KEY_FEED,url).remove(KEY_LAST_CHECK).apply();
                Toast.makeText(activity,"Kanał aktualizacji zapisany",Toast.LENGTH_SHORT).show();
                check(true);
            }).show();
    }
    public void check(boolean manual) {
        String url=feed();
        if(url.isEmpty()) {
            if(manual) new AlertDialog.Builder(activity).setTitle("Aktualizacje wyłączone")
                .setMessage("Włącz kanał OTA w ustawieniach aplikacji.")
                .setPositiveButton("OK",null).show();
            return;
        }
        long now=System.currentTimeMillis();
        long last=activity.getSharedPreferences("forma_preferences",0).getLong(KEY_LAST_CHECK,0);
        if(!manual && now-last<CHECK_INTERVAL_MS) return;
        activity.getSharedPreferences("forma_preferences",0).edit().putLong(KEY_LAST_CHECK,now).apply();
        if(manual) Toast.makeText(activity,"Sprawdzanie aktualizacji…",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            try {
                JSONObject data=new JSONObject(readManifest(url));
                Release release=new Release();
                release.versionCode=data.getInt("versionCode");
                release.versionName=data.optString("versionName",""+release.versionCode);
                release.apkUrl=data.getString("apkUrl");
                release.sha256=data.getString("sha256").trim().toLowerCase(Locale.ROOT);
                release.notes=data.optString("notes","");
                if(!isHttps(release.apkUrl) || !release.sha256.matches("[a-f0-9]{64}"))
                    throw new SecurityException("Nieprawidłowe dane aktualizacji");
                long installed=installedVersion();
                main.post(()->{
                    if(release.versionCode<=installed) {
                        if(manual) Toast.makeText(activity,"Masz aktualną wersję aplikacji",Toast.LENGTH_LONG).show();
                    } else {
                        prompt(release);
                    }
                });
            } catch(Exception e) {
                activity.getSharedPreferences("forma_preferences",0).edit().remove(KEY_LAST_CHECK).apply();
                main.post(()->{
                    if(manual) new AlertDialog.Builder(activity).setTitle("Nie udało się sprawdzić aktualizacji")
                        .setMessage(e.getMessage()).setPositiveButton("OK",null).show();
                });
            }
        },"forma-ota-check").start();
    }
    private void prompt(Release release) {
        String message="Nowa wersja "+release.versionName+"\n"+
            (release.notes.isEmpty()?"":("\n"+release.notes+"\n"))+
            "\nInstalację zatwierdzasz w systemowym instalatorze Androida.";
        new AlertDialog.Builder(activity)
            .setTitle("Aktualizacja FORMA")
            .setMessage(message)
            .setNegativeButton("Później",null)
            .setPositiveButton("Pobierz i zainstaluj",(d,w)->download(release))
            .show();
    }
    private void download(Release release) {
        Toast.makeText(activity,"Pobieranie aktualizacji…",Toast.LENGTH_LONG).show();
        new Thread(()->{
            File target=new File(activity.getCacheDir(),"forma-update.apk");
            try {
                File parent=target.getParentFile();
                if(parent==null || (!parent.isDirectory() && !parent.mkdirs()))
                    throw new IllegalStateException("Nie można utworzyć folderu aktualizacji");
                if(target.exists() && !target.delete()) throw new IllegalStateException("Nie można wyczyścić cache");
                MessageDigest sha=MessageDigest.getInstance("SHA-256");
                HttpURLConnection conn=openHttps(release.apkUrl);
                try {
                    long expected=conn.getContentLengthLong();
                    if(expected>MAX_APK_BYTES)throw new SecurityException("Plik APK jest za duży");
                    long readTotal=0;
                    try(InputStream in=conn.getInputStream(); FileOutputStream out=new FileOutputStream(target)) {
                        byte[] buf=new byte[32768]; int n;
                        while((n=in.read(buf))!=-1) {
                            readTotal+=n;
                            if(readTotal>MAX_APK_BYTES)throw new SecurityException("Plik APK jest za duży");
                            sha.update(buf,0,n);out.write(buf,0,n);
                        }
                    }
                } finally {conn.disconnect();}
                StringBuilder hash=new StringBuilder();
                for(byte b:sha.digest())hash.append(String.format(Locale.US,"%02x",b&0xff));
                if(!hash.toString().equals(release.sha256))
                    throw new SecurityException("Niepoprawna suma SHA-256; aktualizacja odrzucona");
                verifyApk(target,release.versionCode);
                main.post(()->install(target));
            }catch(Exception e) {
                if(target.exists()) target.delete();
                main.post(()->new AlertDialog.Builder(activity)
                    .setTitle("Aktualizacja nieudana").setMessage(e.getMessage())
                    .setPositiveButton("OK",null).show());
            }
        },"forma-ota-download").start();
    }
    private long installedVersion() throws Exception {
        PackageInfo info=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0);
        return Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;
    }
    @SuppressWarnings("deprecation")
    private void verifyApk(File apk,int expectedVersion) throws Exception {
        PackageManager pm=activity.getPackageManager();
        PackageInfo archive=pm.getPackageArchiveInfo(apk.getAbsolutePath(),PackageManager.GET_SIGNATURES);
        PackageInfo installed=pm.getPackageInfo(activity.getPackageName(),PackageManager.GET_SIGNATURES);
        if(archive==null || archive.packageName==null || !archive.packageName.equals(activity.getPackageName()))
            throw new SecurityException("Nieprawidłowy identyfikator pakietu APK");
        long version=Build.VERSION.SDK_INT>=28?archive.getLongVersionCode():archive.versionCode;
        if(version!=expectedVersion || version<=installedVersion())
            throw new SecurityException("Wersja APK nie zgadza się z informacją o aktualizacji");
        Signature[] a=archive.signatures, b=installed.signatures;
        if(a==null || b==null || a.length==0 || !Arrays.equals(a,b))
            throw new SecurityException("APK ma inny podpis niż zainstalowana aplikacja");
    }
    private void install(File file) {
        try {
            if(Build.VERSION.SDK_INT>=26 && !activity.getPackageManager().canRequestPackageInstalls()) {
                new AlertDialog.Builder(activity)
                    .setTitle("Zezwól na instalację aktualizacji")
                    .setMessage("Android wymaga jednorazowego zezwolenia na instalowanie aktualizacji przez aplikację FORMA. Włącz zezwolenie w ustawieniach i wróć tutaj.")
                    .setNegativeButton("Anuluj",null)
                    .setPositiveButton("Otwórz ustawienia",(d,w)->{
                        awaitingPermission=true;
                        Intent i=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:"+activity.getPackageName()));
                        activity.startActivity(i);
                    }).show();
                return;
            }
            if(!file.isFile() || file.length()==0)
                throw new IllegalStateException("Brakuje pobranego APK. Sprawdź aktualizacje ponownie.");
            Uri uri=FileProvider.getUriForFile(activity,activity.getPackageName()+".updates",file);
            Intent intent=new Intent(Intent.ACTION_INSTALL_PACKAGE);
            intent.setData(uri);
            intent.setClipData(ClipData.newUri(activity.getContentResolver(),"FORMA APK",uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                activity.startActivity(intent);
            } catch(ActivityNotFoundException notFound) {
                Intent fallback=new Intent(Intent.ACTION_VIEW);
                fallback.setDataAndType(uri,"application/vnd.android.package-archive");
                fallback.setClipData(ClipData.newUri(activity.getContentResolver(),"FORMA APK",uri));
                fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                activity.startActivity(fallback);
            }
        } catch(Exception e) {
            new AlertDialog.Builder(activity).setTitle("Nie można otworzyć instalatora")
                .setMessage(e.getMessage()).setPositiveButton("OK",null).show();
        }
    }
    private String readManifest(String url) throws Exception {
        HttpURLConnection c=openHttps(url);
        try {
            String type=c.getContentType();
            long size=c.getContentLengthLong();
            if(size>65536)throw new SecurityException("Plik manifestu jest za duży");
            try(InputStream in=c.getInputStream()) {
                java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
                byte[] buf=new byte[4096];int n;
                while((n=in.read(buf))!=-1) {
                    if(out.size()+n>65536) throw new SecurityException("Plik manifestu jest za duży");
                    out.write(buf,0,n);
                }
                return out.toString("UTF-8");
            }
        } finally {c.disconnect();}
    }
    private HttpURLConnection openHttps(String url) throws Exception {
        if(!isHttps(url))throw new SecurityException("Wymagany HTTPS");
        URL target=new URL(url);
        // Follow only HTTPS redirects (e.g. CDN object URLs).
        for(int i=0;i<6;i++) {
            HttpURLConnection c=(HttpURLConnection)target.openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(25000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent","FORMA-Android-OTA");
            int response=c.getResponseCode();
            if(response==301||response==302||response==303||response==307||response==308) {
                String location=c.getHeaderField("Location");
                c.disconnect();
                if(location==null) throw new SecurityException("Błąd przekierowania");
                target=new URL(target,location);
                if(!"https".equalsIgnoreCase(target.getProtocol()))
                    throw new SecurityException("Niebezpieczne przekierowanie");
                continue;
            }
            if(response!=200) {
                c.disconnect();
                throw new IllegalStateException("Serwer aktualizacji: HTTP "+response);
            }
            return c;
        }
        throw new SecurityException("Za dużo przekierowań");
    }
    private boolean isHttps(String value) {
        try {
            URL url=new URL(value);
            return "https".equalsIgnoreCase(url.getProtocol()) && url.getHost()!=null &&
                !url.getHost().isEmpty() && url.getUserInfo()==null;
        }catch(Exception e) {return false;}
    }
}
