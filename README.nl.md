# SceneRadio

[English](README.md) · **Nederlands**

SceneRadio is een Android-app om internetradio te luisteren, met een Amiga-visualizer in de stijl van een cracktro uit 1990. De app is gemaakt om te **luisteren**: er is geen account, geen token en geen schrijven naar GitHub. De standaardlijst met Nederlandse radiozenders en hun logo's haalt de app uit de openbare repository [onneau/sceneradio-m3u](https://github.com/onneau/sceneradio-m3u).

Gebouwd met **Kotlin** en **Jetpack Compose (Material 3)**, **Room**, **DataStore**, **Coil**, **OkHttp** en **AndroidX Media3**. Geen Firebase, geen reclame, geen trackers.

**Huidige versie: 1.3** (zie [Versies en bouwnummer](#-versies-en-bouwnummer)).

---

## 📲 Installeren

**Telefoon of tablet**
1. Download de APK uit de laatste [Release](https://github.com/onneau/sceneradio/releases/latest).
2. Open het bestand en sta *Installeren vanuit onbekende bronnen* toe voor de app waarmee je het opent (browser of bestandsbeheer).
3. Tik op **Installeren**. Daarna werkt de app zichzelf bij, zie [Updates](#-updates).

**Android Auto**
1. Installeer de APK op je telefoon.
2. Open **Instellingen > Android Auto**, tik 10 keer op het versienummer onderaan, open het menu rechtsboven (drie puntjes) > **Ontwikkelaarsinstellingen** en vink **Onbekende bronnen** aan.
3. Sluit de telefoon aan op de auto. SceneRadio staat tussen de media-apps.

**Android TV / Google TV**: installeer de APK, bijvoorbeeld met de app *Downloader* (geef die toestemming om apps te installeren). SceneRadio verschijnt met een eigen banner op het startscherm.

---

## 📱 Wat kan de app

- **Spelen**: zenders in een lijst of (liggend) als raster van tegels, zoeken, genrechips, sorteren, favorieten, zenders verbergen. Bufferstatus, audioformaat en wat er speelt (artiest en titel uit de stream).
- **Liggend scherm**: na ongeveer 7 seconden zonder aanraking verdwijnen de balken en verschijnt de [Amiga-visualizer](#-amiga-visualizer).
- **Android Auto**: tabbladen Favorieten, Recent, Genres en Alle zenders, zoeken en spraak, hervatten, stuurbediening en een hartje voor favorieten. Het nummer staat als titel, de artiest eronder en de zender als album. Android Auto tekent zelf de interface; de visualizer kan daar niet.
- **Android TV**: bediening met de afstandsbediening (D-pad).
- **Zenders toevoegen**: via een M3U-link, een M3U-bestand of geplakte tekst, of zoeken in de [Radio Browser](https://www.radio-browser.info)-database.
- **Zenders beheren op dit toestel**: zender toevoegen of bewerken, logo's koppelen, alle streams controleren, een lijst als `.m3u` bewaren of delen. Dit geldt alleen op jouw toestel.
- **Hoes van het nummer** en **foto van de artiest** (allebei standaard uit): zie [Privacy](#-privacy-en-netwerkverbindingen).
- **Back-up**: onder **Instellingen > Back-up** bewaar je je instellingen en eigen wijzigingen (favorieten, verborgen zenders, eigen zenders en logo's, geïmporteerde afspeellijsten) in één `.json`-bestand, in Downloads of via het deelmenu, en zet je ze later terug. Terugzetten verwijdert nooit een zender. Het bestand is niet versleuteld en bevat nooit tokens.
- **Taal**: Nederlands of English, onder **Instellingen > Taal / Language**. De app past zich meteen aan. Standaard volgt hij het toestel: Nederlands op een Nederlands toestel, anders English. Alleen het logboek blijft Nederlands.

---

## 📋 Standaard zenderlijst

De lijst "Nederlandse radio" staat als `nederlandse-radio.m3u` in de openbare repository `onneau/sceneradio-m3u` (branch `main`), met de logo's in `logos/`. De app haalt het bestand en de logo's rechtstreeks op via `raw.githubusercontent.com`. Er is geen account of token nodig.

- **Versie van de lijst**: de app berekent de SHA-256 van het bestand. Verandert die, dan is er een nieuwe lijst. De GitHub-API (met zijn lage limiet) is hiervoor niet nodig.
- **Bijwerken**: na het opstarten kijkt de app of er een nieuwere lijst is (hooguit eens per 6 uur). Is die er, dan staat bovenaan de zenderlijst een melding met **Bijwerken**. Handmatig kan het onder **Instellingen > Updates > Standaard zenderlijst > Controleren en bijwerken**.
- **Wat een update doet**: nieuwe zenders komen erbij, bestaande zenders worden bijgewerkt (naam, stream en genres; favorieten en verborgen zenders blijven zoals ze waren) en zenders die niet meer in de lijst staan verdwijnen, behalve favorieten. Zenders die je zelf hebt toegevoegd en logo's die je zelf hebt gekozen blijven staan.
- **Geen eigen audio**: de lijst bevat alleen de adressen van de streams. De streams komen rechtstreeks van de zenders.

---

## 🔄 Updates

De app meldt zelf dat er een nieuwe versie is, met een overzicht van wat er nieuw is.

1. De app kijkt eens per dag bij het openen (of via **Instellingen > Updates > De app > Nu controleren**) of de [Releases](https://github.com/onneau/sceneradio/releases) een nieuwere versie hebben. Dat staat uit te zetten.
2. Bij **Nu updaten** downloadt de app de APK, controleert de SHA-256 en of de APK dezelfde pakketnaam, een hoger versienummer en **dezelfde ondertekening** heeft als de geïnstalleerde app, en opent daarna het installatiescherm van Android. Android vraagt zelf om bevestiging. Je instellingen, favorieten en lijsten blijven bewaard.
3. De app volgt alleen doorverwijzingen naar GitHub zelf (`github.com` en `githubusercontent.com`, https) en stuurt nooit een token of wachtwoord mee.
4. Opent het installatiescherm niet (bijvoorbeeld op sommige auto-radio's)? Kies dan **Alleen downloaden (in Downloads)** vóór het downloaden, of **Opslaan in Downloads** erna. De gecontroleerde APK komt als `sceneradio-<versie>.apk` in Downloads; open hem daarna met de bestandsbeheerder.
5. De eerste keer vraagt Android om eenmalig *Onbekende apps installeren* toe te staan voor SceneRadio.

**Voor de beheerder: een release maken**
1. Zet in `AppVersion.kt` het nieuwe `NUMBER` en schrijf onder `## <versie>` in `CHANGELOG.md` wat er nieuw is, in een blok `### Nederlands` en een blok `### English`.
2. Push naar `main`. De workflow (`.github/workflows/android.yml`) bouwt en ondertekent de APK en maakt de Release `v<versie>` met de tekst uit `CHANGELOG.md`, maar alleen als de vaste sleutel in de Secrets staat en de release nog niet bestaat.

**De vaste ondertekeningssleutel (één keer)**
1. Maak op je computer een sleutel (bij het sleutelwachtwoord: gewoon Enter):
   ```
   keytool -genkeypair -v -keystore sceneradio.jks -alias sceneradio -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Zet hem om naar tekst: `base64 -w0 sceneradio.jks`
3. Maak in GitHub onder **Settings > Secrets and variables > Actions** drie secrets: `KEYSTORE_BASE64` (de uitvoer van stap 2), `KEYSTORE_PASSWORD` en `KEY_ALIAS` (`sceneradio`).
4. Bewaar `sceneradio.jks` en het wachtwoord veilig **buiten** de repository. Raak je ze kwijt, dan kun je geen updates meer over bestaande installaties heen zetten. De sleutel staat nooit in de repository (`.gitignore` sluit `*.jks` uit).

Zonder secrets bouwt de workflow wel, met een tijdelijke sleutel. Zo'n APK kan niet over een eerdere versie heen en er komt geen Release van.

De workflow draait alleen bij een `push` en met de hand, nooit bij een `pull_request`, zodat een voorstel van een ander nooit bij de sleutel kan komen. De acties zijn vastgezet op een commit.

---

## 🎛️ Amiga-visualizer

Draai de telefoon naar liggend en laat een zender spelen. Na ongeveer 7 seconden zonder aanraking komt de demo-scène. Raak het scherm aan en alles komt terug. Zolang de visualizer in beeld is, blijft het scherm aan.

- **Zendernaam** in het grote Scopex-lettertype, hellend en zwierend.
- **Sinus-scroller** met artiest en titel, met een gedithered spiegelbeeld.
- **Slogans** in Forest met een kleurcyclus of een vaste kleur. Er zijn 15 standaardslogans; de lijst staat onder **Instellingen > Weergave > Visualizer > Slogans** en is aan te passen. Met `{tijd}` in een slogan komt daar de actuele lokale tijd van je toestel (24-uurs of 12-uurs, volgens je instelling).
- **Equalizer** met 32 balken, **koperbalken**, een sterrenveld en een koperschijnsel.
- **Hoes van het nummer** in Amiga-stijl (standaard uit, drie groottes) en de **positie van de hoes** (Standaard, Links, Midden, Rechts) in visualizer en speler.
- **Dimmen** na een instelbare tijd.
- **Geluidsmeting zonder microfoon**: een `AudioProcessor` in ExoPlayer luistert mee met het geluid dat de speler zelf afspeelt (FFT, 24 banden). De app vraagt geen microfoonrechten.
- Alle snelheden, groottes en kleuren staan onder **Instellingen > Weergave > Visualizer**; **Alles terug naar standaard** zet alles terug.

De Amiga-kleurenlettertypes staan als PNG in `app/src/main/assets/fonts/`; zie [NOTICE.nl.md](NOTICE.nl.md) voor de herkomst.

---

## 🔒 Privacy en netwerkverbindingen

Er zijn geen accounts, geen advertenties en geen analyse. Zenders, favorieten en instellingen blijven op je toestel. De app maakt alleen verbinding met:

| Waarheen | Waarvoor | Wanneer |
| --- | --- | --- |
| De streams van de zenders | Muziek afspelen | Bij het luisteren |
| `raw.githubusercontent.com` (onneau/sceneradio-m3u) | De zenderlijst en de logo's | Na het opstarten (hooguit eens per 6 uur) en bij het tonen van logo's |
| `api.github.com` en `github.com` (onneau/sceneradio) | Controleren op en downloaden van een app-update | Hooguit eens per dag (uit te zetten) en bij **Nu updaten** |
| Radio Browser (`*.api.radio-browser.info`) | Zenders zoeken en een logo vinden | Alleen als je daar zoekt |
| MusicBrainz, Cover Art Archive | De hoes van het nummer dat speelt (artiest en titel gaan mee); verzamelalbums worden overgeslagen | Alleen als je **Hoes van het nummer** aanzet |
| MusicBrainz, Wikidata, Wikimedia Commons | Een foto van de artiest (alleen de naam gaat mee) | Alleen als je **Foto van de artiest** aanzet |

GitHub en de andere diensten zien daarbij je IP-adres. Hoezen en foto's zijn van hun makers en blijven alleen in de cache van het toestel. De automatische controle op een app-update is uit te zetten (**Instellingen > Updates**). De controle op een nieuwe standaardlijst gebeurt na het opstarten, hooguit eens per 6 uur.

**Permissies**: `INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`, `WAKE_LOCK` en `REQUEST_INSTALL_PACKAGES` (alleen voor de update). Bestanden openen gaat via de bestandskiezer van Android, zonder opslagpermissie. Veel radiostreams gebruiken `http`, daarom staat `usesCleartextTraffic` aan.

**Logboek**: onder **Instellingen > Over SceneRadio > Logboek** houdt de app bij wat hij deed, ook na een crash (de laatste 7 dagen, hooguit 500 KB). Het staat alleen op je toestel en gaat nooit vanzelf ergens naartoe. Wachtwoorden, sleutels en alles achter een `?` in een link worden eruit gehaald. Je kunt het delen om een fout te melden.

---

## 🛠️ Technische details

- **SDK**: `minSdk 24`, `compileSdk 36`, `targetSdk 36`; JDK 17, Gradle (Kotlin DSL), Android Gradle Plugin 9.4, KSP 2.3.
- **Media**: AndroidX Media3 1.11.1 (ExoPlayer, HLS, `MediaLibraryService` voor Android Auto, mediamelding en vergrendelscherm).
- **Database**: Room 2.8.5 (KSP). **Voorkeuren**: DataStore.
- **Pakketnaam**: `io.github.onneau.sceneradio` (ook de `namespace`).
- **Hulpscripts** in `tools/`: `make_icon.py` (app-icoon en TV-banner, vereist Pillow) en `colorfont_svg_to_png.py` (SVG-lettertypes naar PNG).
- **Bouwen**: `./gradlew :app:assembleRelease`. Zonder `KEYSTORE_PASSWORD` in de omgeving wordt de release-APK niet ondertekend.

### 🔢 Versies en bouwnummer

- **Versienummer**: `1.0`, `1.1`, … staat in `app/src/main/java/io/github/onneau/sceneradio/AppVersion.kt` (`NUMBER`).
- **Changelog**: `CHANGELOG.md` heeft per versie een kopje `## <versie>` met per taal een blok (`### Nederlands`, `### English`) en daaronder een opsomming. Die tekst komt in de Release; de updatemelding in de app toont het blok van de gekozen taal.
- **versionCode**: de workflow maakt die uit het versienummer (1.14 wordt 1014000).
- **Bouwnummer**: de workflow vult de eerste 7 tekens van de commit in. Bij een lokale build staat er "lokaal". Je ziet het onder **Instellingen > Over SceneRadio**.

### 🎨 App-icoon

Een **S** uit het Scopex-lettertype in felle chroom met een donkere rand, op een paars-naar-magenta achtergrond met vier koperbalken. De S staat als PNG in `drawable-nodpi/ic_launcher_r.png` (de bestandsnaam is historisch), de achtergrond is een vector, en de TV-banner (`drawable-xhdpi/tv_banner.png`) heeft dezelfde opzet. Alles wordt gemaakt door `python3 tools/make_icon.py`.

---

## 📄 Licentie en disclaimer

© 2026 Onno · [PolyForm Noncommercial License 1.0.0](LICENSE)

- Gebruiken, aanpassen, forken en delen mag, voor niet-commerciële doeleinden (privé, hobby, onderzoek, onderwijs, goede doelen). Commercieel gebruik is niet toegestaan.
- Geef je het door, ook een aangepaste versie, houd dan `LICENSE` en `NOTICE` erbij.
- Bijdragen (pull requests) vallen onder dezelfde licentie.

SceneRadio is een hobbyproject en heeft geen enkel verband met de radiozenders, omroepen of andere rechthebbenden van wie de namen, logo's of streams in de zenderlijst staan. Die namen en logo's zijn van hun eigenaren en worden alleen gebruikt om een zender herkenbaar te maken. Wil een rechthebbende iets laten verwijderen, meld dat dan via GitHub ([onneau](https://github.com/onneau)); het wordt dan zo snel mogelijk verwijderd. Zie [NOTICE.nl.md](NOTICE.nl.md) ([English](NOTICE)) voor wat niet onder deze licentie valt (lettertypes, logo's, bibliotheken).
