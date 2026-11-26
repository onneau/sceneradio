# SceneRadio

**English** · [Nederlands](README.nl.md)

SceneRadio is an Android app for listening to internet radio, with an Amiga visualizer in the style of an 80s/90s cracktro. The app is made for **listening**: there is no account, no token and no writing to GitHub. The default list of Dutch radio stations and their logos comes from the public repository [onneau/sceneradio-m3u](https://github.com/onneau/sceneradio-m3u).

Built with **Kotlin** and **Jetpack Compose (Material 3)**, **Room**, **DataStore**, **Coil**, **OkHttp** and **AndroidX Media3**. No Firebase, no ads, no trackers.

**Current version: 1.3** (see [Versions and build number](#-versions-and-build-number)).

---

## 📲 Install

**Phone or tablet**
1. Download the APK from the latest [Release](https://github.com/onneau/sceneradio/releases/latest).
2. Open the file and allow *Install from unknown sources* for the app you open it with (browser or file manager).
3. Tap **Install**. After that the app updates itself, see [Updates](#-updates).

**Android Auto**
1. Install the APK on your phone.
2. Open **Settings > Android Auto**, tap the version number at the bottom 10 times, open the menu at the top right (three dots) > **Developer settings** and tick **Unknown sources**.
3. Connect the phone to the car. SceneRadio appears among the media apps.

**Android TV / Google TV**: install the APK, for example with the app *Downloader* (give it permission to install apps). SceneRadio appears on the home screen with its own banner.

---

## 📱 What the app does

- **Playing**: stations in a list or (in landscape) as a grid of tiles, search, genre chips, sorting, favorites, hiding stations. Buffer status, audio format and what is playing (artist and title from the stream).
- **Landscape screen**: after about 7 seconds without touching, the bars disappear and the [Amiga visualizer](#-amiga-visualizer) appears.
- **Android Auto**: tabs Favorites, Recent, Genres and All stations, search and voice, resume, steering wheel controls and a heart for favorites. The track is the title, the artist is below it and the station is the album. Android Auto draws the interface itself; the visualizer cannot be shown there.
- **Android TV**: control with the remote (D-pad).
- **Adding stations**: via an M3U link, an M3U file or pasted text, or by searching the [Radio Browser](https://www.radio-browser.info) database.
- **Managing stations on this device**: add or edit a station, link logos, check all streams, save or share a list as `.m3u`. This only applies to your own device.
- **Track cover** and **artist photo** (both off by default): see [Privacy](#-privacy-and-network-connections).
- **Backup**: under **Settings > Backup** you save your settings and own changes (favorites, hidden stations, own stations and logos, imported playlists) in one `.json` file, in Downloads or via the share menu, and restore it later. Restoring never removes a station. The file is not encrypted and never contains tokens.
- **Language**: Dutch or English, under **Settings > Taal / Language**. The app switches right away. By default it follows the device: Dutch on a Dutch device, otherwise English. Only the log stays in Dutch.

---

## 📋 Default station list

The list "Nederlandse radio" is stored as `nederlandse-radio.m3u` in the public repository `onneau/sceneradio-m3u` (branch `main`), with the logos in `logos/`. The app fetches the file and the logos directly via `raw.githubusercontent.com`. No account or token is needed.

- **Version of the list**: the app calculates the SHA-256 of the file. If it changes, there is a new list. The GitHub API (with its low limit) is not needed for this.
- **Updating**: after starting, the app checks whether there is a newer list (at most once per 6 hours). If there is, a message with **Update** appears at the top of the station list. You can also do it by hand under **Settings > Updates > Default station list > Check and update**.
- **What an update does**: new stations are added, existing stations are updated (name, stream and genres; favorites and hidden stations stay as they were) and stations that are no longer in the list disappear, except favorites. Stations you added yourself and logos you chose yourself are kept.
- **No audio of its own**: the list only contains the addresses of the streams. The streams come directly from the stations.

---

## 🔄 Updates

The app tells you by itself when there is a new version, with an overview of what is new.

1. Once a day when opening (or via **Settings > Updates > The app > Check now**) the app checks whether the [Releases](https://github.com/onneau/sceneradio/releases) have a newer version. You can turn this off.
2. With **Update now** the app downloads the APK, checks the SHA-256 and whether the APK has the same package name, a higher version number and **the same signature** as the installed app, and then opens the Android install screen. Android itself asks for confirmation. Your settings, favorites and lists are kept.
3. The app only follows redirects to GitHub itself (`github.com` and `githubusercontent.com`, https) and never sends a token or password.
4. Does the install screen not open (for example on some car radios)? Then choose **Download only (to Downloads)** before downloading, or **Save to Downloads** afterwards. The checked APK ends up as `sceneradio-<version>.apk` in Downloads; open it afterwards with the file manager.
5. The first time, Android asks you to allow *Install unknown apps* once for SceneRadio.

**For the maintainer: making a release**
1. Set the new `NUMBER` in `AppVersion.kt` and write what is new under `## <version>` in `CHANGELOG.md`, in a `### Nederlands` block and a `### English` block.
2. Push to `main`. The workflow (`.github/workflows/android.yml`) builds and signs the APK and creates the Release `v<version>` with the text from `CHANGELOG.md`, but only if the fixed key is in the Secrets and the release does not exist yet.

**The fixed signing key (once)**
1. Create a key on your computer (for the key password: just press Enter):
   ```
   keytool -genkeypair -v -keystore sceneradio.jks -alias sceneradio -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Convert it to text: `base64 -w0 sceneradio.jks`
3. In GitHub, under **Settings > Secrets and variables > Actions**, create three secrets: `KEYSTORE_BASE64` (the output of step 2), `KEYSTORE_PASSWORD` and `KEY_ALIAS` (`sceneradio`).
4. Keep `sceneradio.jks` and the password safe **outside** the repository. If you lose them, you can no longer update existing installations. The key is never in the repository (`.gitignore` excludes `*.jks`).

Without secrets the workflow still builds, with a temporary key. Such an APK cannot be installed over an earlier version and no Release is made.

The workflow only runs on a `push` and by hand, never on a `pull_request`, so a proposal from someone else can never reach the key. The actions are pinned to a commit.

---

## 🎛️ Amiga visualizer

Turn the phone to landscape and play a station. After about 7 seconds without touching, the demo scene appears. Touch the screen and everything comes back. While the visualizer is on screen, the screen stays on.

- **Station name** in the large Scopex font, tilting and swaying.
- **Sine scroller** with artist and title, with a dithered mirror image.
- **Slogans** in Forest with a color cycle or a fixed color. There are 15 default slogans; the list is under **Settings > Display > Visualizer > Slogans** and can be edited. With `{tijd}` in a slogan, the current local time of your device appears there (24-hour or 12-hour, following your setting).
- **Equalizer** with 32 bars, **copper bars**, a star field and a copper glow.
- **Track cover** in Amiga style (off by default, three sizes) and the **cover position** (Default, Left, Center, Right) in visualizer and player.
- **Dimming** after an adjustable time.
- **Sound measurement without a microphone**: an `AudioProcessor` in ExoPlayer listens in on the sound the player itself plays (FFT, 24 bands). The app asks for no microphone permission.
- All speeds, sizes and colors are under **Settings > Display > Visualizer**; **Reset everything to default** puts everything back.

The Amiga color fonts are stored as PNG in `app/src/main/assets/fonts/`; see [NOTICE](NOTICE) for their origin.

---

## 🔒 Privacy and network connections

There are no accounts, no advertisements and no analytics. Stations, favorites and settings stay on your device. The app only connects to:

| To | For | When |
| --- | --- | --- |
| The streams of the stations | Playing music | While listening |
| `raw.githubusercontent.com` (onneau/sceneradio-m3u) | The station list and the logos | After starting (at most once per 6 hours) and when showing logos |
| `api.github.com` and `github.com` (onneau/sceneradio) | Checking for and downloading an app update | At most once per day (can be turned off) and on **Update now** |
| Radio Browser (`*.api.radio-browser.info`) | Searching stations and finding a logo | Only when you search there |
| MusicBrainz, Cover Art Archive | The cover of the track that is playing (artist and title are sent); compilation albums are skipped | Only when you turn on **Show track cover** |
| MusicBrainz, Wikidata, Wikimedia Commons | A photo of the artist (only the name is sent) | Only when you turn on **Artist photo** |

GitHub and the other services see your IP address. Covers and photos belong to their creators and only stay in the device's cache. The automatic check for an app update can be turned off (**Settings > Updates**). The check for a new default list happens after starting, at most once per 6 hours.

**Permissions**: `INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`, `WAKE_LOCK` and `REQUEST_INSTALL_PACKAGES` (only for the update). Opening files goes through Android's file picker, without storage permission. Many radio streams use `http`, which is why `usesCleartextTraffic` is on.

**Log**: under **Settings > About SceneRadio > Log** the app keeps track of what it did, even after a crash (the last 7 days, at most 500 KB). It is only on your device and never goes anywhere by itself. Passwords, keys and everything after a `?` in a link are removed. You can share it to report a problem. The log is always written in Dutch, whatever language you choose.

---

## 🛠️ Technical details

- **SDK**: `minSdk 24`, `compileSdk 36`, `targetSdk 36`; JDK 17, Gradle (Kotlin DSL), Android Gradle Plugin 9.4, KSP 2.3.
- **Media**: AndroidX Media3 1.11.1 (ExoPlayer, HLS, `MediaLibraryService` for Android Auto, media notification and lock screen).
- **Database**: Room 2.8.5 (KSP). **Preferences**: DataStore.
- **Package name**: `io.github.onneau.sceneradio` (also the `namespace`).
- **Language**: texts are written in the code as `tr("Nederlands", "English")` (`i18n/Lang.kt`); the choice is kept in Compose state, so every screen follows it right away.
- **Helper scripts** in `tools/`: `make_icon.py` (app icon and TV banner, requires Pillow) and `colorfont_svg_to_png.py` (SVG fonts to PNG).
- **Building**: `./gradlew :app:assembleRelease`. Without `KEYSTORE_PASSWORD` in the environment the release APK is not signed.

### 🔢 Versions and build number

- **Version number**: `1.0`, `1.1`, … is in `app/src/main/java/io/github/onneau/sceneradio/AppVersion.kt` (`NUMBER`).
- **Changelog**: `CHANGELOG.md` has a heading `## <version>` per version, with a block per language (`### Nederlands`, `### English`) and a list below it. That text goes into the Release; the update message in the app shows the block of the chosen language.
- **versionCode**: the workflow derives it from the version number (1.14 becomes 1014000).
- **Build number**: the workflow fills in the first 7 characters of the commit. A local build shows "lokaal". You can see it under **Settings > About SceneRadio**.

### 🎨 App icon

An **S** in the Scopex font in bright chrome with a dark edge, on a purple-to-magenta background with four copper bars. The S is a PNG in `drawable-nodpi/ic_launcher_r.png` (the file name is historical), the background is a vector, and the TV banner (`drawable-xhdpi/tv_banner.png`) has the same design. Everything is made by `python3 tools/make_icon.py`.

---

## 📄 License and disclaimer

© 2026 Onno · [PolyForm Noncommercial License 1.0.0](LICENSE)

- Using, modifying, forking and sharing is allowed for non-commercial purposes (private, hobby, research, education, charities). Commercial use is not allowed.
- If you pass it on, including a modified version, keep `LICENSE` and `NOTICE` with it.
- Contributions (pull requests) fall under the same license.

SceneRadio is a hobby project and has no connection whatsoever with the radio stations, broadcasters or other rights holders whose names, logos or streams are in the station list. Those names and logos belong to their owners and are only used to make a station recognizable. If a rights holder wants something removed, report it via GitHub ([onneau](https://github.com/onneau)); it will be removed as soon as possible. See [NOTICE](NOTICE) ([Nederlands](NOTICE.nl.md)) for what is not covered by this license (fonts, logos, libraries).
