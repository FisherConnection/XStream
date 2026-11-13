# XStream Website Media Downloader

Scan a page or up to 200 same-site linked pages for public images and videos. A WebView can expose JavaScript-loaded media. Shows thumbnails, MIME information, available sizes, per-file and batch downloading.

HLS/DASH playlists are copied as links rather than converted into video files. Protected, DRM, logged-in and non-discoverable assets may be missed. Respect site terms and copyright.

Build with JDK 17, Gradle 8.9, Android Gradle plugin 8.7.3, SDK 35. Run: gradle --no-daemon :app:assembleDebug.
