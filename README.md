# La Torrentola 🎬🍿

🌐 **Languages / Idiomas:** **English** | [Español](README.es.md)

Welcome to **La Torrentola**! Your ultimate, unrestricted media hub for Android and Android TV. Crafted from the ground up to deliver a modern, smooth, and cinematic entertainment experience, La Torrentola lets you explore high-definition movie and TV series catalogs, stream torrents instantly without endless waits, connect your home media servers and drives, and seamlessly synchronize your personal library to the cloud with your Google account.

Whether on your phone's touchscreen, your tablet, a foldable device, or comfortably from your living room sofa using your Smart TV remote control, La Torrentola turns any screen into your personal cinema.

---

## ✨ Why You Will Love La Torrentola

* 🚀 **Instant Streaming**: Forget waiting hours for a complete download. The app prioritizes and verifies the initial file segments so your movie or TV episode starts playing in seconds.
* 📺 **Built for Your Television (100% Android TV & Google TV Ready)**: Say goodbye to clumsy virtual cursors. The user interface navigates naturally with your TV remote (D-pad), featuring interactive card scaling, high-contrast focus rings, and large-screen optimized layouts.
* 💎 **Modern "Frosted Glass" Aesthetic**: Experience a premium visual presentation powered by real-time translucent frosted glass blur, smooth scrolling animations, and adaptive Material 3 styling.
* 📡 **Your Home Cloud (NAS, PC, SMB & NFS)**: Connect directly to shared folders on your Windows PC, Linux home servers, or NAS appliances (Synology, QNAP, TrueNAS, Unraid). Stream your home video collection over Wi-Fi without stuttering!
* ☁️ **Full Google Cloud Synchronization**: Sign in with a single tap. Your favorite movies, followed TV shows, and exact playback progress synchronize across all your phones, tablets, and TVs.
* 🏷️ **Quick Progress Marking**: Finished an episode or movie? Long-press its poster to instantly mark it as "Completed" or "Uncompleted", complete with the streaming industry-standard red progress bar.
* 📱 **Foldable & Small Screen Optimization**: Fully adaptive interface designed with foldable cover screens (Galaxy Z Fold, Pixel Fold) in mind. Enjoy 100% of the information, badges, actions, and torrent options without text clipping or vertical character splitting.
* ▶️ **YouTube-Style Vertical & Fullscreen Player**: When watching on a phone held upright, the player controls hug the video itself: a Back button, the file/title name, Subtitles, Cast and a "More options" (⋮) menu sit right on top of the picture; play/pause and ±10s skip are centered on it; and the elapsed time, a one-tap **Fullscreen** button and the red progress scrubber sit right at its bottom edge. Audio tracks, OpenSubtitles search, subtitle appearance and Stats for nerds are one tap away in the "More options" sheet. Tap Fullscreen to rotate to landscape (and back); in landscape, system status and navigation bars hide automatically in immersive mode while respecting safe cutout and system bar margins so buttons like Cast are never overlapped.
* 🌐 **On-Device Smart Translation**: Using local on-device machine learning, movie summaries are automatically translated into Spanish without consuming mobile data or uploading your reading history to the cloud.
* 🍿 **Streaming Platform Explorer (OTT)**: Discover what is trending and streaming on Netflix, Disney+, Max (HBO), Amazon Prime Video, Apple TV+, and more.

---

## 🧭 Everything You Can Do: Comprehensive Feature Guide

### 1. 🎬 Explore Movies in Maximum Definition
* **Quality Filtering**: Browse titles in **4K Ultra HD (2160p)**, **1080p x265 (High Efficiency)**, **1080p Full HD**, and **720p**.
* **Genre Discovery**: Explore Action, Sci-Fi, Comedy, Drama, Horror, Romance, New Releases, or your Previously Watched history.
* **Immersive Movie Details**:
  * Full-screen panoramic backdrop and official movie logo artwork.
  * Detailed plot synopsis translated on the fly.
  * **Aggregated Ratings**: Side-by-side scores from IMDb, Rotten Tomatoes, and Metacritic for informed viewing choices.
  * **Full IMDb/TMDB Cast**: High-fidelity cast members and profile avatars directly from TMDB/IMDb with smooth shimmer loading placeholders, avoiding truncated metadata or visual jumps.
  * **Official Trailers**: Watch official YouTube HD trailers directly inside the app without ads or pop-up interruptions.
* **Cast, Actor & Director Filmographies**: Search any actor, actress, or movie/TV director to explore matching artists via interactive chips with their biography positioned dynamically to the left of the selected artist, and view their complete filmography (both directed and acted works) sorted chronologically.

### 2. 📺 TV Series Season by Season
* **Extensive TV Catalog**: Browse currently airing shows, popular series, and all-time top-rated television.
* **Cinematic Immersive Experience**: Dynamic full-bleed backdrop headers with transparent title logos and directional scrims for both Android TV and handheld devices.
* **Intuitive Season & Episode Navigation**: Pick seasons and episodes with localized titles, air dates, synopsis overviews, and full D-pad remote accessibility on episode hero banners.
* **True Overall Series Progress**: TV series posters across Home, Favorites, and Search display an accurate overall percentage bar computed across all seasons and episodes, eliminating deceptive 100% completion bars from a single played episode. Season 0 specials are tracked individually on their own chips without skewing series-level percentages.
* **Intelligent Upcoming Season & Availability Handling**: If a TV show already lists a future upcoming season without torrents or aired episodes, completed series remain accurately classified as "Watched" until the new episodes air and become available.
* **Smart Next-Episode Detection**: Finished an episode? The app immediately evaluates whether the next episode is ready to stream, still awaiting broadcast, or if the season has concluded.
* **Multi-Release Scraping**: Automatically aggregates the best available torrent releases sorted by peer seed counts for maximum download bandwidth.

### 3. 🍿 Streaming Platform Catalog (OTT Providers)
* Want to know what is hot on your favorite streaming platform?
* Navigate to the **Streaming Providers** section and filter the catalog by network:
  * **Netflix**, **Disney+**, **Max**, **Amazon Prime Video**, **Apple TV+**, and many more.
* Discover popular titles and open their complete details with a single tap.

### 4. ⚡ Instant Playback & Torrent Management
Choose the torrent handling mode in **Settings** that fits your personal workflow:
1. **Direct In-App Playback (Streaming)**: Tap any video quality badge and the built-in media player begins streaming within seconds using our verified piece pipeline. Reaching over 95% of playback automatically marks the episode or movie as completed, updating season counters and recording it in your downloaded history. Starting playback never prematurely marks an episode or season as completed.
2. **Cast to Smart TV / Chromecast**: Stream the torrent directly to your Google Cast or Android TV receiver on your local Wi-Fi. Over 95% progress automatically marks the item as completed.
3. **External Client Handoff**: Prefer your external torrent app (Flud, LibreTorrent, uTorrent, or Synology DS get)? The app will hand off the magnet link automatically and immediately mark the item as downloaded and 100% completed in your library, keeping your watched history fully synchronized.
* **Integrated Download Manager**: Monitor active transfers in the background, pause, resume, check speeds and peer counts, or clean up completed files.
* **Cost-Efficient Cloud Sync**: Playback progress is recorded every 10 seconds in the fast, local Room database without incurring cloud write bills, syncing snapshots to Firebase Firestore only when playback is paused, stopped, finished, or the player is closed.
* **Seamless Next-Episode Transitions**: When selecting the next episode or using Auto-Play, the choice dialog stays open showing live liquid buffering progress until 100% ready, with a pinned Close button for easy dismissal. Previous video surfaces and hardware decoders are cleanly released, completely eliminating video stalls or black screens on Android.
* **Self-Healing Local Streaming & Stability**: The built-in local streaming server (`VerifiedTorrentHttpServer`) actively monitors socket health and seamlessly resurrects instances if Android TV memory pressure (LMK) or sleep cycles interrupt the connection, recovering playback silently without reporting false-positive errors.
* **Smart Next-Episode Progression**: Filtered multi-stream scraping prevents season pack confusion or re-downloading previously watched episodes when progressing through a series.

### 5. 🏠 Connect Your Home Server (NAS, PC, SMB & NFS)
Have a personal movie collection stored on your computer or home server? La Torrentola connects directly:
* **One-Tap Network Scanner**: Automatically scans your local Wi-Fi network for active SMB (Windows/Samba) and NFS (Linux/NAS) shares.
* **Fast Folder Browsing**: Navigate your media folders with the same speed as local storage.
* **Stutter-Free 4K Streaming**: Powered by our asynchronous 16 MB ring-buffer prefetch engine, eliminating Wi-Fi jitter and playback stalls even on high-bitrate 1080p and 4K media.
* **Cast from NAS to TV**: Stream any video from your home server directly to your Chromecast with one tap.

### 6. 💾 USB Drives & External Storage
* Connect a USB flash drive or portable hard drive to your Android TV box or phone.
* The app automatically detects removable volumes, lets you choose where to save torrent downloads, and plays media files directly from the drive.

### 7. 💬 Tailored Subtitles & Cinema Audio
* **Integrated & Online Subtitles**: Use embedded subtitle tracks (MKV/MP4) or search and download matching subtitles in your language on the fly via OpenSubtitles.
* **Full Subtitle Styling**: Customize font size, text color, dark background contrast, and time offset synchronization.
* **Smart Surround Audio**:
  * Enjoy multi-channel audio tracks in multiple languages (5.1 and 7.1).
  * Connected to an older soundbar or AVR via optical (SPDIF) or HDMI ARC? The app automatically transcodes multichannel audio to **Dolby Digital (AC3)** so you never experience silent audio or flat stereo downmixes.

### 8. ☁️ Your Library Everywhere You Go (Offline-First & Shared Family TVs)
* **Favorites List**: Bookmark movies and TV shows you plan to watch later, with ultra-smooth 60fps scrolling and instant D-pad navigation powered by precalculated watch progress maps.
* **Smart Watch History**: The app remembers the exact minute and second where you paused, whether you were streaming a torrent, playing from your NAS, or watching a local file.
* **Fast Offline-First Room Architecture**: Favorites, downloads, and playback progress live locally in Room on your device, delivering instant zero-latency browsing with zero cloud read fees (keeping usage strictly within the Free Tier).
* **Global IMDb Standard & Vendor Independence**: All favorites, downloads, and progress are anchored purely and exclusively to universal IMDb identifiers (`tt0111161` for movies, `tt0944947_s1_e2` for regular episodes, `tt0944947_s0_e1` for specials), shielding your library from third-party catalog disruptions with zero legacy overhead.
* **Disney+ Style Avatar Profile Selector ("¿Quién anda ahí?")**: Enjoy an OTT experience every time you launch the app. When saved profiles exist on the device, the app opens directly onto the circular avatar selector without any welcome-screen flash, featuring personalized photos, an Add Profile (`+`) button, and an Edit Profiles management mode to remove unused profiles.
* **Passwordless Biometric Passkey Unlock**: On handheld devices with fingerprint or screen lock capabilities, tapping any saved profile presents a secure biometric prompt to unlock passwordlessly, entering the home catalog immediately when the session is active or falling back gracefully to Google Sign-In. On Android TV, users navigate smoothly with D-pad focus.
* **Shared TV & Multi-Account Profile Isolation**: Sharing an Android TV or living room Chromecast with family members? The app remembers each authenticated Google account locally. When users switch accounts or log out, their respective watch histories and favorites remain completely preserved and isolated in local storage without wiping the device database.
* **Effortless Cloud Sync**: Start watching on your phone during your commute and continue right where you left off on your living room Smart TV with automatic background sync.

### 9. 🎙️ Voice Search, Text-To-Speech & Release Notifications
* **Voice Search**: Tap the microphone icon or press the voice button on your TV remote to search for titles naturally without tedious on-screen keyboard typing.
* **Text-To-Speech Summaries**: Listen to movie titles and summaries spoken aloud via an asynchronous, offloaded speech engine designed for zero UI lag across Android TV and mobile devices.
* **New Release Alerts & Smart Navigation**: Receive notifications for hot new releases and jump straight into the movie details with a single tap. Even if you don't have an active session when tapping a notification, the app securely guides you through login and automatically opens the movie details right after!

---

## 📖 Step-by-Step User Guide: How to Accomplish It

### How to watch a movie instantly
1. Open **La Torrentola** and browse the home catalog (or search for a specific title).
2. Tap the movie poster to open its detail screen.
3. Select your preferred video quality badge (**1080p**, **4K**, **720p**).
4. The built-in player opens and playback begins within seconds.
5. Tap the **Subtitles** icon inside the player controls to enable or search for subtitles in your preferred language.

### How to watch a TV series episode by episode
1. On the home screen, select the **TV Series** tab.
2. Select a show, choose the **Season**, and tap the desired **Episode**.
3. View the list of matching releases sorted by speed and pick one to begin streaming.
4. When the episode concludes, the player presents a prompt to proceed straight into the next episode.

### How to connect your home NAS or PC (SMB / NFS)
1. Go to **Settings** ➔ **External media / Network storage** (or tap the storage icon on the top app bar).
2. Tap **Scan Network** to automatically detect local SMB and NFS servers, or tap **Add Server** to manually enter the IP address (e.g. `192.168.1.100`).
3. Choose the protocol (**SMB** for Windows/Samba or **NFS** for Linux/NAS) and enter credentials if your share is password-protected.
4. Browse your shared folders and tap any video file to start streaming.

### How to cast content to your Smart TV / Chromecast
1. Make sure your phone/tablet and your Chromecast/Android TV are connected to the **same Wi-Fi network**.
2. While playing any video in the app (or from the detail screen), tap the **Cast** icon.
3. Select your TV from the device list.
4. Video and subtitles transfer to the big screen, turning your phone into a remote control with play/pause, seek, and volume adjustments.

### How to mark movies or episodes as watched
* **On mobile/touchscreen**: Long-press any movie poster, season chip, or episode card for one second. From the dialog menu, select **"Mark as completed"**.
* **On Android TV**: Move D-pad focus to the target card and hold down the **OK / Center** button. The options dialog appears to mark as watched or unwatched.
* The 100% red progress bar and completed badge will appear immediately.

---

## 🔒 Privacy & Personal Customization

* **Dark Mode, Light Mode & AMOLED Pure Black**: Pick your preferred visual style in Settings, including an *AMOLED Pure Black* theme engineered for maximum contrast and battery conservation on OLED panels.
* **Language Exclusion Filter**: Automatically hide catalog titles whose original language you prefer not to see.
* **Minimum Rating Threshold**: Configure a minimum IMDb rating score to filter out low-rated releases.
* **Hardware-Backed Security**: Network passwords and subtitle credentials are encrypted and stored inside your device's hardware security module (**Android Keystore**).

---

## 🛠️ Developer Information & Architecture

Are you a software engineer, Android developer, or interested in the technical inner workings of the app?

All technical specifications regarding Clean Architecture, MVI/MVVM patterns, Jetpack Compose, the `libtorrent4j` streaming pipeline, real-time surround audio transcoding (`Ac3TranscodingAudioSink`), native JNI `libnfs` integration, reactive Room/Firestore sync, R8/ProGuard shrinking rules, AI Agent Skills (`.agents/skills` & `.agents/firebase-skills`), and complete Mermaid sequence diagrams are detailed in:

👉 **[Read the Technical Architecture Guide (ARCHITECTURE.md)](ARCHITECTURE.md)** 👈

---

Enjoy the ultimate movie and TV series experience with **La Torrentola**! 🎬✨
