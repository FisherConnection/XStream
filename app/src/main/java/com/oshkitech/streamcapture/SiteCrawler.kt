package com.oshkitech.streamcapture

import org.jsoup.Jsoup
import java.net.URI
import java.util.ArrayDeque

object SiteCrawler {
 private val imageExt=setOf("jpg","jpeg","png","gif","webp","avif","svg","bmp","heic")
 private val videoExt=setOf("mp4","m4v","mov","webm","mkv","avi","3gp","m3u8","mpd")
 private fun kind(url:String):Boolean?{
  val path=runCatching{URI(url).path.orEmpty().lowercase()}.getOrDefault("")
  val ext=path.substringAfterLast('.',"")
  return when(ext){in imageExt->true;in videoExt->false;else->null}
 }
 private fun clean(base:String,raw:String):String?{
  if(raw.isBlank()||raw.startsWith("data:")||raw.startsWith("blob:")||raw.startsWith("javascript:"))return null
  return runCatching{
   val uri=URI(base).resolve(raw.trim())
   if(uri.scheme?.lowercase() !in listOf("https","http")||uri.host.isNullOrBlank())null
   else uri.toASCIIString().substringBefore('#')
  }.getOrNull()
 }
 fun scan(start:String,maxPages:Int,onMedia:(String,Boolean)->Unit,onProgress:(Int)->Unit){
  val host=URI(start).host.lowercase().removePrefix("www.")
  val queue=ArrayDeque<String>();val visited=HashSet<String>();val files=HashSet<String>()
  queue.add(start)
  fun media(base:String,raw:String,hint:Boolean?=null){
   val link=clean(base,raw)?:return
   val image=kind(link)?:hint?:return
   if(files.size<5000&&files.add(link))onMedia(link,image)
  }
  while(queue.isNotEmpty()&&visited.size<maxPages){
   val url=queue.removeFirst()
   if(!visited.add(url))continue
   try{
    val document=Jsoup.connect(url).userAgent("Mozilla/5.0 (compatible; XStream/1.0)")
     .timeout(18000).maxBodySize(8*1024*1024).followRedirects(true).get()
    val base=document.baseUri()
    document.getAllElements().forEach{el->
     val tag=el.tagName().lowercase()
     val meta=el.attr("property")+" "+el.attr("name")
     val hint=when(tag){
      "img","picture"->true
      "video"->false
      "source"->el.attr("type").startsWith("image/")
      "meta"->if(meta.contains("image",true))true else if(meta.contains("video",true))false else null
      else->null
     }
     listOf("src","data-src","data-original","data-lazy-src","data-background","poster","content","href").forEach{attr->
      if(el.hasAttr(attr)&&(attr!="content"||tag=="meta")&&(attr!="href"||tag=="a"||tag=="link"))
       media(base,el.attr(attr),if(attr=="poster")true else hint)
     }
     listOf("srcset","data-srcset").forEach{attr->
      if(el.hasAttr(attr))el.attr(attr).split(',').forEach{v->media(base,v.trim().substringBefore(' '),hint?:true)}
     }
    }
    if(maxPages>1)document.select("a[href]").forEach{a->
     val link=clean(base,a.absUrl("href"))?:return@forEach
     val uri=runCatching{URI(link)}.getOrNull()?:return@forEach
     val last=uri.path.substringAfterLast('/',"")
     val ext=last.substringAfterLast('.',"").lowercase()
     val page=!last.contains('.')||ext in listOf("html","htm","php","asp","aspx","jsp","shtml")
     if(uri.host.lowercase().removePrefix("www.")==host&&page&&link !in visited&&link !in queue&&queue.size<3000)queue.add(link)
    }
   }catch(_:Exception){}
   onProgress(visited.size)
   if(maxPages>1)Thread.sleep(250)
  }
 }
}
