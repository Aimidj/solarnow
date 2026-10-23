package dk.waqt.widget;
import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MainActivity extends Activity {
 static final String[] N={"Fajr","Dhuhr","ʿAsr","Maghrib","ʿIshāʾ"};
 static final String[] K={"fajr","zohr_shadow","mithlain","sunset","esha"};
 static final int GOLD=0xffdec48e,PALE=0xfff7eee1,MUTED=0xff9fb0b3,BG=0xff0f1b23;
 static SharedPreferences prefs(Context c){return c.getSharedPreferences("waqt",MODE_PRIVATE);}
 static String day(long t) { SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.US); f.setTimeZone(TimeZone.getDefault());return f.format(new Date(t));}
 static String tomorrow(){Calendar c=Calendar.getInstance();c.add(Calendar.DATE,1);return day(c.getTimeInMillis());}
 static JSONObject find(String d){
   try {JSONArray a=new JSONObject(prefs(APP).getString("json","{}")).getJSONArray("list");
   for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&d.equals(x.optString("d")))return x;}}
   catch(Exception ignored){} return null;
 }
 static Context APP;
 static long event(JSONObject x,String key) {
   if(x==null)return -1;
   String h=x.optString(key+"_time",""),z=x.optString(key+"_tz",x.optString("dtz",""));
   Matcher m=Pattern.compile("([+-]\\d{4})$").matcher(z);
   if(!h.matches("\\d{2}:\\d{2}:\\d{2}")||!m.find())return -1;
   try {SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z",Locale.US);
    f.setLenient(false);long ms=f.parse(x.getString("d")+" "+h+" "+m.group(1)).getTime();return (ms+59999)/60000*60000;}
   catch(Exception ex){return -1;}
 }
 static String clock(long ms) {if(ms<0)return "—";SimpleDateFormat f=new SimpleDateFormat("HH:mm",Locale.US);return f.format(new Date(ms));}
 static int next(long[] out) {
   long now=System.currentTimeMillis(),best=Long.MAX_VALUE;int idx=-1;JSONObject[] d={find(day(now)),find(tomorrow())};
   for(JSONObject obj:d)for(int i=0;i<5;i++){long ms=event(obj,K[i]);if(ms>now&&ms<best){best=ms;idx=i;}}
   if(idx>=0)out[0]=best;return idx;
 }
 static void refreshWidgets(Context c) {
  int[] ids=AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c,WaqtWidget.class));
  if(ids.length>0)new WaqtWidget().onUpdate(c,AppWidgetManager.getInstance(c),ids);
 }
 static boolean fetch(Context c) {
  SharedPreferences p=prefs(c); if(!p.contains("lat"))return false;
  double lat=Double.longBitsToDouble(p.getLong("lat",0)),lon=Double.longBitsToDouble(p.getLong("lon",0));
  if(Math.abs(lat)>90||Math.abs(lon)>180)return false;
  try {
   JSONObject all=new JSONObject();for(String date:new String[]{day(System.currentTimeMillis()),tomorrow()}) {
    if(all.has(date))continue;
    String url=String.format(Locale.US,"https://www.muwaqqit.com/api.json?d=%s;lt=%.6f&ln=%.6f;fa=-19;era=-12&ea=-16&tztype=auto&tz=%s",date,lat,lon,java.net.URLEncoder.encode(TimeZone.getDefault().getID(),"UTF-8"));
    HttpURLConnection h=(HttpURLConnection)new URL(url).openConnection();h.setConnectTimeout(12000);h.setReadTimeout(12000);h.setRequestProperty("Accept","application/json");
    try {if(h.getResponseCode()!=200)return false;
     StringBuilder sb=new StringBuilder();try(InputStream stream=h.getInputStream();BufferedReader r=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){
       String s;while((s=r.readLine())!=null){sb.append(s);if(sb.length()>1500000)return false;}
     }
     JSONArray list=new JSONObject(sb.toString()).getJSONArray("list");
     for(int i=0;i<list.length();i++){JSONObject x=list.optJSONObject(i);if(x!=null&&x.optString("d").matches("\\d{4}-\\d{2}-\\d{2}"))all.put(x.getString("d"),x);}
    }finally{h.disconnect();}
   }
   if(!all.has(day(System.currentTimeMillis()))||!all.has(tomorrow()))return false;
   if(Double.longBitsToDouble(p.getLong("lat",0))!=lat||Double.longBitsToDouble(p.getLong("lon",0))!=lon)return false;
   JSONArray a=new JSONArray();java.util.Iterator<String> it=all.keys();while(it.hasNext())a.put(all.getJSONObject(it.next()));
   p.edit().putString("json",new JSONObject().put("list",a).toString()).putLong("fetched",System.currentTimeMillis()).apply();
   refreshWidgets(c);return true;
  }catch(Exception ex){return false;}
 }
 Handler handler=new Handler(Looper.getMainLooper());TextView city,dayLabel,nextName,timer,at,status;
 TextView[] times=new TextView[5];LocationManager gps;LocationListener listener;boolean destroyed=false;
 @Override public void onCreate(Bundle b){super.onCreate(b);APP=getApplicationContext();build();redraw();requestGps();handler.post(ticker);}
 @Override protected void onResume(){super.onResume();if(city!=null)redraw();}
 @Override protected void onDestroy(){destroyed=true;handler.removeCallbacks(ticker);try{if(listener!=null)gps.removeUpdates(listener);}catch(Exception ignored){}super.onDestroy();}
 TextView tx(String text,int sz,int color){TextView v=new TextView(this);v.setText(text);v.setTextColor(color);v.setTextSize(sz);v.setIncludeFontPadding(false);return v;}
 int px(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 LinearLayout col(){LinearLayout a=new LinearLayout(this);a.setOrientation(LinearLayout.VERTICAL);return a;}
 void build(){
  getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
  scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff293e43,BG,0xff0b151d}));
  LinearLayout body=col();body.setPadding(px(25),px(30),px(25),px(30));scroll.addView(body);setContentView(scroll);
  TextView logo=tx("☾    W A Q T",21,GOLD);logo.setTypeface(null,Typeface.BOLD);body.addView(logo);
  city=tx("⌖  Finder lokation…",14,GOLD);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=px(36);body.addView(city,p);
  dayLabel=tx("",12,MUTED);p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=px(9);body.addView(dayLabel,p);
  TextView eyebrow=tx("N Æ S T E   B Ø N",11,GOLD);eyebrow.setGravity(Gravity.CENTER);p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=px(65);body.addView(eyebrow,p);
  nextName=tx("—",43,PALE);nextName.setGravity(Gravity.CENTER);nextName.setTypeface(Typeface.SERIF);p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=px(13);body.addView(nextName,p);
  timer=tx("--:--:--",42,PALE);timer.setGravity(Gravity.CENTER);timer.setTypeface(Typeface.create("sans-serif-light",0));body.addView(timer);
  at=tx("Henter tider fra Muwaqqit",12,MUTED);at.setGravity(Gravity.CENTER);p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=px(10);p.bottomMargin=px(51);body.addView(at,p);
  TextView heading=tx("DAGENS BØNNETIDER                                  MUWAQQIT",10,GOLD);body.addView(heading);
  for(int i=0;i<5;i++){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
   TextView label=tx(N[i],18,PALE);row.addView(label,new LinearLayout.LayoutParams(0,px(58),1));label.setGravity(Gravity.CENTER_VERTICAL);
   times[i]=tx("—",19,PALE);times[i].setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);row.addView(times[i]);body.addView(row);
   View line=new View(this);line.setBackgroundColor(0xff405055);body.addView(line,new LinearLayout.LayoutParams(-1,px(1)));
  }
  status=tx("Lokation og tider opdateres automatisk",12,MUTED);p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=px(24);body.addView(status,p);
  TextView info=tx("Hanafi ʿAsr · Fajr −19° · ʿIshāʾ −16°",11,MUTED);p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=px(7);body.addView(info,p);
  Button update=new Button(this);update.setAllCaps(false);update.setText("⌖  Opdater GPS og tider");update.setOnClickListener(v->requestGps());
  p=new LinearLayout.LayoutParams(-1,px(52));p.topMargin=px(18);body.addView(update,p);
 }
 void redraw(){
  if(dayLabel==null)return;
  String c=prefs(this).getString("city","GPS ikke valgt");city.setText("⌖  "+c);
  SimpleDateFormat f=new SimpleDateFormat("EEEE d. MMMM yyyy",new Locale("da","DK"));dayLabel.setText(f.format(new Date()));
  JSONObject obj=find(day(System.currentTimeMillis()));
  for(int i=0;i<5;i++)times[i].setText(clock(event(obj,K[i])));
  long[] ms=new long[1];int idx=next(ms);
  if(idx<0){nextName.setText("—");timer.setText("--:--:--");at.setText("Åbn appen med internet for tider");return;}
  nextName.setText(N[idx]);long remaining=Math.max(0,(ms[0]-System.currentTimeMillis()+999)/1000);
  timer.setText(String.format(Locale.US,"%02d:%02d:%02d",remaining/3600,remaining/60%60,remaining%60));
  at.setText("Kl. "+clock(ms[0]));
 }
 Runnable ticker=new Runnable(){public void run(){if(destroyed)return;redraw();handler.postDelayed(this,1000);}};
 void requestGps(){
  if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
   requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION},12);return;
  }
  gps=(LocationManager)getSystemService(LOCATION_SERVICE);
  if(gps==null){status.setText("GPS ikke tilgængelig");return;}
  try {
    Location last=null;for(String provider:new String[]{LocationManager.NETWORK_PROVIDER,LocationManager.GPS_PROVIDER}) {
     if(!gps.isProviderEnabled(provider))continue;Location p=gps.getLastKnownLocation(provider);
     if(p!=null&&(last==null||p.getTime()>last.getTime()))last=p;
    }
    if(last!=null&&System.currentTimeMillis()-last.getTime()<4*3600000L)accept(last);
    String provider=gps.isProviderEnabled(LocationManager.NETWORK_PROVIDER)?LocationManager.NETWORK_PROVIDER:
      (gps.isProviderEnabled(LocationManager.GPS_PROVIDER)?LocationManager.GPS_PROVIDER:null);
    if(provider==null){status.setText("Slå lokation til i Android");return;}
    listener=new LocationListener(){@Override public void onLocationChanged(Location l){accept(l);try{gps.removeUpdates(this);}catch(Exception ignored){}}};
    gps.requestLocationUpdates(provider,0,0,listener,Looper.getMainLooper());
    LocationListener toCancel=listener;handler.postDelayed(()->{try{gps.removeUpdates(toCancel);}catch(Exception ignored){}},14000);
    if(last==null)status.setText("Finder din GPS-position…");
  }catch(SecurityException e){status.setText("Lokationstilladelse mangler");}
 }
 void accept(Location l){
   if(l==null)return;double lat=l.getLatitude(),lon=l.getLongitude();if(Math.abs(lat)>90||Math.abs(lon)>180)return;
   SharedPreferences p=prefs(this);
   boolean change=!p.contains("lat")||Math.abs(Double.longBitsToDouble(p.getLong("lat",0))-lat)>0.01||Math.abs(Double.longBitsToDouble(p.getLong("lon",0))-lon)>0.01;
   p.edit().putLong("lat",Double.doubleToLongBits(lat)).putLong("lon",Double.doubleToLongBits(lon))
     .putString("city",String.format(Locale.US,"GPS · %.3f°, %.3f°",lat,lon)).apply();
   redraw();refreshWidgets(this);schedule(this,true);
   if(change||find(day(System.currentTimeMillis()))==null||System.currentTimeMillis()-p.getLong("fetched",0)>8*3600000L)load();
 }
 void load(){status.setText("Henter Muwaqqit…");new Thread(()->{
   boolean ok=fetch(getApplicationContext());
   runOnUiThread(()->{if(destroyed)return;status.setText(ok?"✓ Muwaqqit · tider opdateret":"Kunne ikke hente tider · prøv igen");redraw();});
  },"waqt-fetch").start();}
 @Override public void onRequestPermissionsResult(int code,String[] perms,int[] grants){super.onRequestPermissionsResult(code,perms,grants);if(code==12)requestGps();}
 static void schedule(Context c,boolean urgent){
  JobScheduler js=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);if(js==null||!prefs(c).contains("lat"))return;
  if(js.getPendingJob(7012)!=null)return;
  JobInfo j=new JobInfo.Builder(7012,new ComponentName(c,SyncJob.class)).setPersisted(true)
    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(urgent?5000:6*3600000L)
    .setOverrideDeadline(urgent?30*60000L:12*3600000L).build();
  js.schedule(j);
 }
 public static final class SyncJob extends JobService {
  @Override public boolean onStartJob(JobParameters p){
   new Thread(()->{try{APP=getApplicationContext();fetch(this);}finally{
    jobFinished(p,false);new Handler(Looper.getMainLooper()).postDelayed(()->schedule(this,false),2000);
   }},"waqt-sync").start();return true;
  }
  @Override public boolean onStopJob(JobParameters p){return false;}
 }
 public static final class WaqtWidget extends AppWidgetProvider {
  static final String FLIP="dk.waqt.widget.FLIP";
  static final int[] IDs={R.id.w_fajr,R.id.w_dhuhr,R.id.w_asr,R.id.w_maghrib,R.id.w_isha};
  @Override public void onEnabled(Context c){APP=c.getApplicationContext();schedule(c,true);}
  @Override public void onUpdate(Context c,AppWidgetManager manager,int[] ids){
   APP=c.getApplicationContext();
   for(int id:ids)render(c,manager,id);
   schedule(c,false);
  }
  @Override public void onReceive(Context c,Intent i){
   super.onReceive(c,i);
   if(i!=null&&(FLIP.equals(i.getAction())||Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())||Intent.ACTION_TIMEZONE_CHANGED.equals(i.getAction())||Intent.ACTION_DATE_CHANGED.equals(i.getAction())))refreshWidgets(c);
  }
  void render(Context c,AppWidgetManager manager,int id){
   RemoteViews rv=new RemoteViews(c.getPackageName(),R.layout.waqt_widget);
   Intent open=new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
   rv.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(c,10,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
   rv.setTextViewText(R.id.w_location,prefs(c).getString("city","Tryk her · tillad GPS"));
   rv.setTextViewText(R.id.w_date,new SimpleDateFormat("d. MMM",new Locale("da","DK")).format(new Date()));
   JSONObject today=find(day(System.currentTimeMillis()));
   for(int j=0;j<5;j++)rv.setTextViewText(IDs[j],N[j]+"\n"+clock(event(today,K[j])));
   long[] ms=new long[1];int idx=next(ms);
   if(idx<0){rv.setTextViewText(R.id.w_next,"Åbn Waqt");rv.setTextViewText(R.id.w_at,"GPS og bønnetider");rv.setViewVisibility(R.id.w_countdown,View.GONE);}
   else {
    rv.setTextViewText(R.id.w_next,N[idx]);rv.setTextViewText(R.id.w_at,"Kl. "+clock(ms[0]));
    rv.setViewVisibility(R.id.w_countdown,View.VISIBLE);
    rv.setChronometer(R.id.w_countdown,SystemClock.elapsedRealtime()+Math.max(0,ms[0]-System.currentTimeMillis()),null,true);
    rv.setChronometerCountDown(R.id.w_countdown,true);
    AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
    if(a!=null){
     PendingIntent pi=PendingIntent.getBroadcast(c,11,new Intent(c,WaqtWidget.class).setAction(FLIP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
     a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,Math.max(System.currentTimeMillis()+6000,ms[0]+3000),pi);
    }
   }
   manager.updateAppWidget(id,rv);
  }
 }
}
