# Podders in the Chat 🎙️

A modern, fast, native Android podcast player built with Jetpack Compose, Kotlin, Media3 ExoPlayer, and Room SQLite.

## Features

- **Subscribe to RSS Feeds Manually**:
  - Add any custom podcast RSS feed URL via the Subscriptions sheet.
  - Explore & one-tap subscribe to curated sample feeds (Syntax, NPR News Now, The Changelog, BBC Global News).
  - Manage your subscriptions (view episode count, unsubscribe/delete).
- **Auto-Refresh on App Launch**:
  - Whenever the app launches, it automatically connects to all subscribed RSS feeds in the background, parses updates, and populates the episode feed.
  - Manual pull/button refresh with spinning animation and progress indicator.
- **Highlights Newest Episodes**:
  - Recent episodes (published within the last 48 hours) are prominently badged with a vibrant `★ NEW` pill tag and subtle glowing border to catch your eye immediately.
  - Dedicated "★ Newest" filter tab to quickly review only new releases.
- **Progress Marker on Listened Episodes**:
  - Remembers your exact playback position across app restarts in a local Room database.
  - Episode cards display an integrated linear progress bar showing percent listened and remaining time (e.g. `45% listened • 22m left`).
  - Completed episodes (>95% listened) are marked with a green checkmark and "Played" badge.
  - Tapping a previously listened episode resumes right where you left off.
- **Rich Audio Player & Background Playback Engine (Media3 / ExoPlayer)**:
  - Persistent bottom mini-player with quick play/pause and +30s skip.
  - Full-screen player sheet with large artwork, interactive seek slider, -15s rewind, +30s skip, and speed control (1.0x, 1.25x, 1.5x, 2.0x).
  - **Screen-Off & Background Execution Protection**:
    - Runs as an active foreground service (`foregroundServiceType="mediaPlayback"`).
    - ExoPlayer holds `C.WAKE_MODE_NETWORK` (partial wake lock + Wi-Fi lock) and audio focus management (`handleAudioFocus = true`) so Android will not suspend the app when the screen turns off or when minimized.
    - Headphones disconnect detection (`setHandleAudioBecomingNoisy(true)`) automatically pauses when unplugged or disconnected.
  - **Notification Bar Player Controls**:
    - System media player notification with episode artwork, title, and podcast source.
    - Compact quick-action buttons: **Rewind 15s** (`CommandButton.ICON_SKIP_BACK_15`), **Play / Pause**, and **Skip 30s** (`CommandButton.ICON_SKIP_FORWARD_30`).
    - Tapping the notification opens the app (`MainActivity`) directly to the currently playing episode.
    - Automatic `POST_NOTIFICATIONS` runtime permission request for Android 13+.

## Architecture & Tech Stack

- **UI**: 100% Jetpack Compose with Material 3 Dark/Light themes.
- **Architecture**: MVVM with Kotlin Coroutines & `StateFlow`.
- **Database**: AndroidX Room (SQLite) with entities for `PodcastFeed` and `Episode`.
- **Audio Engine**: AndroidX Media3 ExoPlayer (`1.4.1`) with periodic progress persistence.
- **Networking & RSS**: OkHttp + Java DOM XML Parser handling RSS 2.0, iTunes Podcast extensions (`itunes:image`, `itunes:duration`, `itunes:author`), and enclosure audio links.
- **Image Loading**: Coil Compose for cached, performant artwork.

## Project Structure

```
~/podders-in-the-chat/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/poddersinthechat/app/
│   │   │   ├── MainActivity.kt
│   │   │   ├── PoddersApp.kt
│   │   │   ├── data/
│   │   │   │   ├── db/
│   │   │   │   │   ├── AppDatabase.kt
│   │   │   │   │   └── PodcastDao.kt
│   │   │   │   ├── model/
│   │   │   │   │   ├── Episode.kt
│   │   │   │   │   └── PodcastFeed.kt
│   │   │   │   ├── parser/
│   │   │   │   │   └── RssParser.kt
│   │   │   │   └── repository/
│   │   │   │       └── FeedRepository.kt
│   │   │   ├── player/
│   │   │   │   └── PodcastPlayerManager.kt
│   │   │   ├── service/
│   │   │   │   └── PodcastPlaybackService.kt
│   │   │   └── ui/
│   │   │       ├── components/
│   │   │       │   ├── EpisodeItem.kt
│   │   │       │   ├── FullPlayerSheet.kt
│   │   │       │   ├── MiniPlayer.kt
│   │   │       │   └── SubscriptionsDialog.kt
│   │   │       ├── screens/
│   │   │       │   └── MainScreen.kt
│   │   │       ├── theme/
│   │   │       │   ├── Color.kt
│   │   │       │   └── Theme.kt
│   │   │       └── viewmodel/
│   │   │           └── PodcastViewModel.kt
│   │   └── res/
│   └── src/test/
│       └── java/com/poddersinthechat/app/
│           └── RssParserTest.kt
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/
```

## Building & Installing

### Compile Debug APK
```bash
cd ~/podders-in-the-chat
./gradlew assembleDebug
```
The compiled APK will be at:
`app/build/outputs/apk/debug/app-debug.apk`

### Run Unit Tests
```bash
./gradlew testDebugUnitTest
```

### Install to an Android Device or Emulator via ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
