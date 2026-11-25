# Changelog

Per versie een kopje `## <versie>` met een opsomming. De workflow zet die regels in de GitHub Release en de app toont ze in de updatemelding.

## 1.1
- Gewijzigd: de hoes van het nummer komt niet meer van een verzamelalbum (Compilation, DJ-mix of Mixtape) of van een release van "Various Artists". Is er geen andere hoes, dan blijft het bij de koperbalken (of de foto van de artiest, als die aan staat). Eerder opgeslagen hoezen worden bij de eerste keer opnieuw opgehaald.

## 1.0
- Nieuw: de eerste openbare versie van SceneRadio, afgeleid van een privé hobbyproject. Alleen om te luisteren: geen account, geen token en geen schrijven naar GitHub.
- Nieuw: de standaardlijst "Nederlandse radio" en de logo's komen uit de openbare repository onneau/sceneradio-m3u, zonder token. De app ziet een nieuwe lijst aan de SHA-256 van het bestand.
- Nieuw: updates komen uit de Releases van onneau/sceneradio, zonder token. De app controleert de download (SHA-256, dezelfde pakketnaam, een hoger versienummer en dezelfde ondertekening) en volgt alleen doorverwijzingen naar GitHub zelf. Je kunt de update ook alleen in Downloads zetten.
- Nieuw: de Amiga-visualizer met slogans (ook met de actuele tijd via {tijd}), equalizer, koperbalken, sterren, hoes van het nummer en foto van de artiest, met de positie van de hoes in visualizer en speler.
- Nieuw: Android Auto met tabbladen, zoeken, spraak, hervatten, stuurbediening, zenderlogo's en het hartje; het nummer staat als titel, de artiest eronder en de zender als album.
- Nieuw: een eigen icoon (een paarse S met koperbalken) en een eigen banner voor Android TV.
- Nieuw: onder Instellingen > Over SceneRadio staat welke verbindingen de app maakt en waar hoezen en foto's vandaan komen.
- Gewijzigd: foto's van artiesten komen alleen van Wikimedia Commons.
- Gewijzigd: bij zenders toevoegen kun je een link, een bestand of geplakte tekst gebruiken, of zoeken in Radio Browser. Zoeken in GitHub en de snelle lijsten van derden zijn er niet meer.
