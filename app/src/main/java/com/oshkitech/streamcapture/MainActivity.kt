package com.oshkitech.streamcapture

import android.app.*
import android.os.*
import android.content.*
import android.graphics.Color
import android.graphics.BitmapFactory
import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.*
import android.webkit.*
import android.widget.*
import java.net.*
import java.util.concurrent.Executors

class MainActivity : Activity() {
 private lateinit var urlBox: EditText
 private lateinit var list: LinearLayout
 private lateinit var status: TextView
 private lateinit var browser: WebView
 private val seen = HashSet<String>()
 private val selected = HashSet<String>()
 private val pool = Executors.newFixedThreadPool(3)
 private fun text(s:String, size:Float=15f): TextView = TextView(this).apply { text=s; textSize=size; setTextColor(Color.WHITE); setPadding(12,12,12,12) }
 private fun button(s:String, f:()->Unit): Button = Button(this).apply { text=s; setOnClickListener { f() } }
 override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState)
  window.statusBarColor=Color.rgb(12,22,36)
  val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(16,16,16,8); setBackgroundColor(Color.rgb(16,27,42)) }
  root.addView(text("XSTREAM",23f))
  urlBox=EditText(this).apply { hint="Paste video URL or webpage URL"; setSingleLine(true); setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY); inputType=android.text.InputType.TYPE_TEXT_VARIATION_URI }
  root.addView(urlBox)
  val actions=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
  actions.addView(button("SCAN URL") { scanDirect() },LinearLayout.LayoutParams(0,-2,1f))
  actions.addView(button("OPEN PAGE") { openPage() },LinearLayout.LayoutParams(0,-2,1f))
  root.addView(actions)
  val extras=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
  extras.addView(button("SCAN WEBSITE") { scanWebsite() },LinearLayout.LayoutParams(0,-2,1f))
  extras.addView(button("CAPTURE PAGE") { capturePageMedia() },LinearLayout.LayoutParams(0,-2,1f))
  extras.addView(button("DOWNLOAD SELECTED") { downloadSelected() },LinearLayout.LayoutParams(0,-2,1f))
  root.addView(extras)
  status=text("Paste a link to begin.",13f); root.addView(status)
  browser=WebView(this).apply { settings.javaScriptEnabled=true; settings.domStorageEnabled=true; settings.mediaPlaybackRequiresUserGesture=true
   webViewClient=object:WebViewClient() {
    override fun shouldInterceptRequest(view:WebView?,request:WebResourceRequest?):WebResourceResponse? {
     request?.url?.toString()?.let { u -> if(isVideo(u)||isImage(u)) runOnUiThread { addCandidate(u,"Detected page request") } }
     return null
    }
    override fun onPageFinished(view:WebView?,url:String?) { status.text="Page loaded. Scroll, then CAPTURE PAGE."; capturePageMedia() }
   }
  }
  root.addView(browser,LinearLayout.LayoutParams(-1,0,1f))
  root.addView(text("DETECTED VIDEOS",17f))
  val scroll=ScrollView(this); list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }; scroll.addView(list)
  root.addView(scroll,LinearLayout.LayoutParams(-1,0,2f))
  setContentView(root)
 }
 private fun validUrl():String? { val u=urlBox.text.toString().trim(); val parsed=runCatching { URI(u) }.getOrNull(); if(parsed?.scheme !in listOf("http","https") || parsed?.host.isNullOrBlank()) { status.text="Enter a valid http or https URL"; return null }; return u }
 private fun isVideo(u:String):Boolean { val p=runCatching { URI(u).path.lowercase() }.getOrDefault(""); return listOf(".mp4",".m4v",".mov",".webm",".mkv",".m3u8",".mpd",".ts").any { p.endsWith(it) } }
 private fun isImage(u:String):Boolean { val p=runCatching { URI(u).path.lowercase() }.getOrDefault(""); return listOf(".jpg",".jpeg",".png",".gif",".webp",".avif",".svg",".bmp",".heic").any { p.endsWith(it) } }
 private fun openPage() { val u=validUrl()?:return; browser.visibility=View.VISIBLE; browser.loadUrl(u); status.text="Opening webpage..." }
 private fun scanDirect() { val u=validUrl()?:return; status.text="Inspecting URL..."; pool.execute {
  try { val c=(URL(u).openConnection() as HttpURLConnection).apply { requestMethod="HEAD"; connectTimeout=12000; readTimeout=12000; instanceFollowRedirects=true; setRequestProperty("User-Agent","Mozilla/5.0") }; val type=c.contentType.orEmpty(); val finalUrl=c.url.toString(); val size=c.contentLengthLong; c.disconnect()
   runOnUiThread { if(isVideo(finalUrl)||isImage(finalUrl)||type.startsWith("video/")||type.startsWith("image/")||type.contains("mpegurl")||type.contains("dash+xml")) addCandidate(finalUrl,"${type.ifBlank { "Video" }} • ${sizeLabel(size)}") else status.text="No direct video identified. Try OPEN PAGE and start playback." }
   if(finalUrl.substringBefore('?').endsWith(".m3u8",true)) inspectPlaylist(finalUrl)
  } catch(e:Exception) { runOnUiThread { status.text="Could not inspect URL: ${e.message}" } }
 } }
 private fun inspectPlaylist(u:String) { try { val conn=(URL(u).openConnection() as HttpURLConnection).apply { connectTimeout=12000; readTimeout=12000 }; val lines=conn.inputStream.bufferedReader().use { it.readLines().take(3000) }; conn.disconnect()
  lines.forEachIndexed { i,line -> if(line.startsWith("#EXT-X-STREAM-INF") && i+1<lines.size) { val path=lines[i+1].trim(); if(path.isNotBlank()&&!path.startsWith("#")) { val target=URI(u).resolve(path).toString(); val quality=Regex("RESOLUTION=(\\d+x\\d+)").find(line)?.groupValues?.get(1)?:"Adaptive stream"; runOnUiThread { addCandidate(target,"HLS • $quality • size unknown") } } } }
 } catch(_:Exception) {} }
 private fun sizeLabel(n:Long)=if(n<0) "size unknown" else "%.1f MB".format(n/1048576.0)

 private fun addCandidate(u:String,info:String,imageHint:Boolean=false){
  if(!seen.add(u)||seen.size>5000)return
  selected.add(u)
  val name=runCatching { URI(u).path.substringAfterLast('/').ifBlank{"media"} }.getOrDefault("media")
  val ext=name.substringAfterLast('.', "unknown").uppercase()
  val isPicture=imageHint||isImage(u)
  val stream=ext in listOf("M3U8","MPD")
  val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(8,8,8,8);setBackgroundColor(Color.rgb(35,51,73))}
  val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  val check=CheckBox(this).apply{isChecked=true;setOnCheckedChangeListener{_,on->if(on)selected.add(u)else selected.remove(u)}}
  top.addView(check)
  val kind=if(isPicture)"IMAGE" else if(stream)"STREAM" else "VIDEO"
  top.addView(text(name+" ["+kind+"]",15f))
  panel.addView(top)
  val preview=ImageView(this).apply{
   layoutParams=LinearLayout.LayoutParams(-1,180);scaleType=ImageView.ScaleType.CENTER_INSIDE
   setBackgroundColor(Color.rgb(19,34,51));setImageResource(if(isPicture)android.R.drawable.ic_menu_gallery else android.R.drawable.ic_media_play)
  }
  panel.addView(preview)
  val details=text(info+" • size unknown",12f)
  panel.addView(details)
  if(seen.size<=180)pool.execute{
   var conn:HttpURLConnection?=null
   try{
    conn=(URL(u).openConnection() as HttpURLConnection).apply{requestMethod="HEAD";connectTimeout=8000;readTimeout=8000;setRequestProperty("User-Agent","Mozilla/5.0")}
    conn.connect()
    val length=conn.contentLengthLong
    val type=conn.contentType.orEmpty().substringBefore(';')
    runOnUiThread{if(!isFinishing&&!isDestroyed)details.text=type+" • "+sizeLabel(length)}
   }catch(_:Exception){}finally{conn?.disconnect()}
  }
  if(isPicture)pool.execute{
   var conn:HttpURLConnection?=null
   try{
    conn=(URL(u).openConnection() as HttpURLConnection).apply{connectTimeout=8000;readTimeout=12000;setRequestProperty("User-Agent","Mozilla/5.0")}
    val opt=BitmapFactory.Options().apply{inSampleSize=6}
    val bmp=conn.inputStream.use{BitmapFactory.decodeStream(it,null,opt)}
    if(bmp!=null)runOnUiThread{if(!isFinishing&&!isDestroyed)preview.setImageBitmap(bmp)}
   }catch(_:Exception){}finally{conn?.disconnect()}
  }else if(ext in listOf("MP4","M4V","MOV","WEBM","MKV"))pool.execute{
   val r=MediaMetadataRetriever()
   try{r.setDataSource(u,HashMap<String,String>());val frame=r.getFrameAtTime(1000000L,MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    if(frame!=null)runOnUiThread{if(!isFinishing&&!isDestroyed)preview.setImageBitmap(frame)}
   }catch(_:Exception){}finally{runCatching{r.release()}}
  }
  panel.addView(button(if(stream)"COPY STREAM URL" else "DOWNLOAD"){
   if(stream)copyLink(u)else if(permissionOk())download(u,name)
  })
  list.addView(panel);status.text=seen.size.toString()+" media URL(s) found"
 }
 private fun permissionOk():Boolean{
  if(Build.VERSION.SDK_INT<=28&&checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){
   requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),102)
   status.text="Allow storage access, then download again."
   return false
  }
  return true
 }
 private fun downloadSelected(){
  if(!permissionOk())return
  var queued=0;var streams=0
  for(u in selected.toList()){
   val name=runCatching{URI(u).path.substringAfterLast('/').ifBlank{"media"}}.getOrDefault("media")
   if(name.substringAfterLast('.',"").lowercase() in listOf("m3u8","mpd")){streams++;continue}
   if(download(u,name))queued++
  }
  status.text=queued.toString()+" downloads queued; "+streams+" streaming playlist links not downloadable"
 }
 private fun download(u:String,name:String):Boolean{
  return try{
   val safe=name.replace(Regex("[^a-zA-Z0-9._-]"),"_").takeLast(80)
   val unique="XStream_"+System.currentTimeMillis()+"_"+(u.hashCode().toLong() and 0xffffffffL).toString(16)+"_"+safe
   val req=DownloadManager.Request(Uri.parse(u)).setTitle(name)
    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,unique)
   (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
   true
  }catch(e:Exception){status.text="Download failed: "+e.message;false}
 }
 private fun copyLink(u:String){
  (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText("Stream URL",u))
  Toast.makeText(this,"Stream URL copied",Toast.LENGTH_SHORT).show()
 }
 private fun scanWebsite(){
  val u=validUrl()?:return
  status.text="Scanning up to 200 pages on this website..."
  seen.clear();selected.clear();list.removeAllViews()
  pool.execute{
   SiteCrawler.scan(u,200,
    onMedia={ url,image->runOnUiThread{if(!isFinishing&&!isDestroyed)addCandidate(url,"Website media",image)}},
    onProgress={ n->runOnUiThread{if(!isFinishing&&!isDestroyed)status.text="Scanned "+n+" pages; "+seen.size+" media found"}})
   runOnUiThread{if(!isFinishing&&!isDestroyed)status.text="Site scan finished: "+seen.size+" media URLs"}
  }
 }
 private fun capturePageMedia(){
  if(browser.visibility!=View.VISIBLE){status.text="Tap OPEN PAGE first.";return}
  val js="""(function(){
   var found=[],seen={};
   function add(v,img){
    if(!v||typeof v!=='string'||/^(data:|blob:|javascript:)/i.test(v))return;
    try{var url=new URL(v,location.href).href;if(!/^https?:/i.test(url)||seen[url])return;seen[url]=true;found.push({url:url,image:img});}catch(e){}
   }
   document.querySelectorAll('img,source,video').forEach(function(el){
    var img=el.tagName==='IMG'||(el.getAttribute('type')||'').indexOf('image/')===0;
    ['src','currentSrc','data-src','data-original','data-lazy-src','poster'].forEach(function(a){add(el[a]||el.getAttribute(a),a==='poster'?true:img)});
    ['srcset','data-srcset'].forEach(function(a){(el.getAttribute(a)||'').split(',').forEach(function(v){add(v.trim().split(/\s+/)[0],img)})});
   });
   document.querySelectorAll('a[href]').forEach(function(el){
    if(/\.(jpe?g|png|gif|webp|avif|mp4|webm|mov|m4v|mkv|m3u8|mpd)(\?|#|$)/i.test(el.href))
     add(el.href,/\.(jpe?g|png|gif|webp|avif)(\?|#|$)/i.test(el.href));
   });
   if(performance.getEntriesByType)performance.getEntriesByType('resource').forEach(function(r){
    if(/\.(jpe?g|png|gif|webp|avif|mp4|webm|mov|m4v|mkv|m3u8|mpd)(\?|#|$)/i.test(r.name))
     add(r.name,/\.(jpe?g|png|gif|webp|avif)(\?|#|$)/i.test(r.name));
   });
   return JSON.stringify(found.slice(0,5000));
  })()"""
  browser.evaluateJavascript(js){response->
   try{
    val data=org.json.JSONArray(org.json.JSONTokener(response).nextValue().toString())
    for(i in 0 until data.length()){
     val item=data.getJSONObject(i)
     addCandidate(item.getString("url"),"Webpage media",item.optBoolean("image"))
    }
    status.text=seen.size.toString()+" links detected. Scroll and capture again for lazy media."
   }catch(_:Exception){status.text="Could not inspect this page. Try SCAN WEBSITE."}
  }
 }
 override fun onDestroy() { browser.destroy(); pool.shutdownNow(); super.onDestroy() }
}