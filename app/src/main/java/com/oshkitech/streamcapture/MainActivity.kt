package com.oshkitech.streamcapture

import android.app.*
import android.os.*
import android.content.*
import android.graphics.Color
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
  status=text("Paste a link to begin.",13f); root.addView(status)
  browser=WebView(this).apply { settings.javaScriptEnabled=true; settings.domStorageEnabled=true; settings.mediaPlaybackRequiresUserGesture=true
   webViewClient=object:WebViewClient() {
    override fun shouldInterceptRequest(view:WebView?,request:WebResourceRequest?):WebResourceResponse? {
     request?.url?.toString()?.let { u -> if(isVideo(u)) runOnUiThread { addCandidate(u,"Detected page request") } }
     return null
    }
    override fun onPageFinished(view:WebView?,url:String?) { status.text="Page loaded. Play the video to detect its media requests." }
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
 private fun openPage() { val u=validUrl()?:return; browser.visibility=View.VISIBLE; browser.loadUrl(u); status.text="Opening webpage..." }
 private fun scanDirect() { val u=validUrl()?:return; status.text="Inspecting URL..."; pool.execute {
  try { val c=(URL(u).openConnection() as HttpURLConnection).apply { requestMethod="HEAD"; connectTimeout=12000; readTimeout=12000; instanceFollowRedirects=true; setRequestProperty("User-Agent","Mozilla/5.0") }; val type=c.contentType.orEmpty(); val finalUrl=c.url.toString(); val size=c.contentLengthLong; c.disconnect()
   runOnUiThread { if(isVideo(finalUrl)||type.startsWith("video/")||type.contains("mpegurl")||type.contains("dash+xml")) addCandidate(finalUrl,"${type.ifBlank { "Video" }} • ${sizeLabel(size)}") else status.text="No direct video identified. Try OPEN PAGE and start playback." }
   if(finalUrl.substringBefore('?').endsWith(".m3u8",true)) inspectPlaylist(finalUrl)
  } catch(e:Exception) { runOnUiThread { status.text="Could not inspect URL: ${e.message}" } }
 } }
 private fun inspectPlaylist(u:String) { try { val conn=(URL(u).openConnection() as HttpURLConnection).apply { connectTimeout=12000; readTimeout=12000 }; val lines=conn.inputStream.bufferedReader().use { it.readLines().take(3000) }; conn.disconnect()
  lines.forEachIndexed { i,line -> if(line.startsWith("#EXT-X-STREAM-INF") && i+1<lines.size) { val path=lines[i+1].trim(); if(path.isNotBlank()&&!path.startsWith("#")) { val target=URI(u).resolve(path).toString(); val quality=Regex("RESOLUTION=(\\d+x\\d+)").find(line)?.groupValues?.get(1)?:"Adaptive stream"; runOnUiThread { addCandidate(target,"HLS • $quality • size unknown") } } } }
 } catch(_:Exception) {} }
 private fun sizeLabel(n:Long)=if(n<0) "size unknown" else "%.1f MB".format(n/1048576.0)
 private fun addCandidate(u:String, info:String) { if(!seen.add(u)) return; val name=runCatching { URI(u).path.substringAfterLast('/').ifBlank { "video" } }.getOrDefault("video")
  val ext=name.substringAfterLast('.',"unknown").uppercase(); val panel=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(8,8,8,8); setBackgroundColor(Color.rgb(35,51,73)) }
  val thumbnail=ImageView(this).apply { layoutParams=LinearLayout.LayoutParams(-1,180); scaleType=ImageView.ScaleType.CENTER_INSIDE; setBackgroundColor(Color.rgb(19,34,51)); setImageResource(android.R.drawable.ic_media_play) }
  panel.addView(thumbnail)
  panel.addView(text("$name  [$ext]",15f)); panel.addView(text(info,12f))
  if(ext in listOf("MP4","M4V","MOV","WEBM","MKV")) pool.execute {
   val retriever=MediaMetadataRetriever()
   try { retriever.setDataSource(u,HashMap<String,String>()); val frame=retriever.getFrameAtTime(1000000L,MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    if(frame!=null) runOnUiThread { if(!isFinishing && !isDestroyed) thumbnail.setImageBitmap(frame) }
   } catch(_:Exception) {} finally { runCatching { retriever.release() } }
  }
  panel.addView(button(if(ext in listOf("MP4","M4V","MOV","WEBM","MKV")) "DOWNLOAD" else "COPY STREAM URL") { if(ext in listOf("MP4","M4V","MOV","WEBM","MKV")) download(u,name) else { (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText("Stream URL",u)); Toast.makeText(this,"Stream URL copied",Toast.LENGTH_SHORT).show() } })
  list.addView(panel); status.text="${seen.size} media URL(s) found"
 }
 private fun download(u:String,name:String) { try { val request=DownloadManager.Request(Uri.parse(u)).setTitle(name).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,name.replace(Regex("[^a-zA-Z0-9._-]"),"_")); (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request); Toast.makeText(this,"Download queued",Toast.LENGTH_SHORT).show() } catch(e:Exception) { status.text="Download failed: ${e.message}" } }
 override fun onDestroy() { browser.destroy(); pool.shutdownNow(); super.onDestroy() }
}