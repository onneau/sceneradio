# Changelog

Per versie een kopje `## <versie>` met een blok `### Nederlands` en een blok `### English`. De workflow zet die regels in de GitHub Release en de app toont het blok van de gekozen taal in de updatemelding.

For each version a heading `## <version>` with a `### Nederlands` block and a `### English` block. The workflow puts those lines in the GitHub Release and the app shows the block of the chosen language in the update message.

## 1.3

### Nederlands
- Nieuw: back-up. Onder Instellingen > Back-up bewaar je je instellingen en eigen wijzigingen in één bestand (in Downloads of via het deelmenu) en zet je ze later terug, ook op een andere telefoon of na opnieuw installeren. Je kiest per onderdeel: instellingen en taal, favorieten en verborgen zenders (met je sortering), eigen zenders, eigen logo's en geïmporteerde afspeellijsten.
- Nieuw: terugzetten toont eerst een samenvatting, controleert het bestand helemaal en verwijdert nooit een zender: instellingen worden overschreven, de rest wordt toegevoegd. Het bestand is niet versleuteld en bevat nooit tokens. Een back-up van EtherWave Radio kun je ook in SceneRadio terugzetten en andersom.

### English
- New: backup. Under Settings > Backup you save your settings and own changes in one file (in Downloads or via the share menu) and restore them later, also on another phone or after reinstalling. You choose per part: settings and language, favorites and hidden stations (with your sort order), own stations, own logos and imported playlists.
- New: restoring first shows a summary, checks the whole file and never removes a station: settings are overwritten, everything else is added. The file is not encrypted and never contains tokens. A backup from EtherWave Radio can also be restored in SceneRadio and the other way around.

## 1.2

### Nederlands
- Nieuw: taalkeuze. Onder Instellingen > Taal / Language kies je Nederlands, English of "Volg het systeem" (Nederlands op een Nederlands toestel, anders English). De app past zich meteen aan, zonder opnieuw te starten. Alleen het logboek blijft Nederlands.
- Nieuw: README, NOTICE en dit changelog staan in het Nederlands en in het Engels, en de updatemelding toont de tekst in de gekozen taal.
- Gewijzigd: datums en bestandsgroottes volgen de gekozen taal.

### English
- New: language choice. Under Settings > Taal / Language you choose Nederlands, English or "Follow the system" (Dutch on a Dutch device, otherwise English). The app switches right away, without restarting. Only the log stays in Dutch.
- New: README, NOTICE and this changelog are available in Dutch and in English, and the update message shows the text in the chosen language.
- Changed: dates and file sizes follow the chosen language.

## 1.1

### Nederlands
- Gewijzigd: de hoes van het nummer komt niet meer van een verzamelalbum (Compilation, DJ-mix of Mixtape) of van een release van "Various Artists". Is er geen andere hoes, dan blijft het bij de koperbalken (of de foto van de artiest, als die aan staat). Eerder opgeslagen hoezen worden bij de eerste keer opnieuw opgehaald.

### English
- Changed: the track cover no longer comes from a compilation album (Compilation, DJ-mix or Mixtape) or from a "Various Artists" release. If there is no other cover, you get the copper bars (or the artist photo, if that is on). Covers saved earlier are fetched again the first time.

## 1.0

### Nederlands
- Nieuw: de eerste openbare versie van SceneRadio, afgeleid van een privé hobbyproject. Alleen om te luisteren: geen account, geen token en geen schrijven naar GitHub.
- Nieuw: de standaardlijst "Nederlandse radio" en de logo's komen uit de openbare repository onneau/sceneradio-m3u, zonder token. De app ziet een nieuwe lijst aan de SHA-256 van het bestand.
- Nieuw: updates komen uit de Releases van onneau/sceneradio, zonder token. De app controleert de download (SHA-256, dezelfde pakketnaam, een hoger versienummer en dezelfde ondertekening) en volgt alleen doorverwijzingen naar GitHub zelf. Je kunt de update ook alleen in Downloads zetten.
- Nieuw: de Amiga-visualizer met slogans (ook met de actuele tijd via {tijd}), equalizer, koperbalken, sterren, hoes van het nummer en foto van de artiest, met de positie van de hoes in visualizer en speler.
- Nieuw: Android Auto met tabbladen, zoeken, spraak, hervatten, stuurbediening, zenderlogo's en het hartje; het nummer staat als titel, de artiest eronder en de zender als album.
- Nieuw: een eigen icoon (een paarse S met koperbalken) en een eigen banner voor Android TV.
- Nieuw: onder Instellingen > Over SceneRadio staat welke verbindingen de app maakt en waar hoezen en foto's vandaan komen.
- Gewijzigd: foto's van artiesten komen alleen van Wikimedia Commons.
- Gewijzigd: bij zenders toevoegen kun je een link, een bestand of geplakte tekst gebruiken, of zoeken in Radio Browser. Zoeken in GitHub en de snelle lijsten van derden zijn er niet meer.

### English
- New: the first public version of SceneRadio, derived from a private hobby project. Only for listening: no account, no token and no writing to GitHub.
- New: the default list "Nederlandse radio" and the logos come from the public repository onneau/sceneradio-m3u, without a token. The app recognizes a new list by the SHA-256 of the file.
- New: updates come from the Releases of onneau/sceneradio, without a token. The app checks the download (SHA-256, same package name, a higher version number and the same signature) and only follows redirects to GitHub itself. You can also just save the update to Downloads.
- New: the Amiga visualizer with slogans (also with the current time via {tijd}), equalizer, copper bars, stars, track cover and artist photo, with the cover position in visualizer and player.
- New: Android Auto with tabs, search, voice, resume, steering wheel controls, station logos and the heart; the track is the title, the artist is below it and the station is the album.
- New: its own icon (a purple S with copper bars) and its own banner for Android TV.
- New: under Settings > About SceneRadio it says which connections the app makes and where covers and photos come from.
- Changed: artist photos only come from Wikimedia Commons.
- Changed: when adding stations you can use a link, a file or pasted text, or search Radio Browser. Searching GitHub and the quick third-party lists are gone.
