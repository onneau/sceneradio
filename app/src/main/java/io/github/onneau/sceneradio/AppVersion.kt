package io.github.onneau.sceneradio

/**
 * Versie van de app, getoond onder Instellingen > "Over SceneRadio".
 *
 * NUMBER is het versienummer van de app: 1.0, 1.1, 1.2, ... Het gaat bij elke nieuwe versie omhoog. Hetzelfde nummer is ook:
 *  - de tag van de GitHub Release (v<NUMBER>) waar de app zijn updates vandaan haalt;
 *  - de basis van de versionCode van Android (1.14 wordt 1014000), zodat Android een update als nieuwer ziet.
 * Zet in CHANGELOG.md bij elk nieuw nummer een kopje "## <NUMBER>" met de opsomming van wat er nieuw is.
 * BUILD wordt door de GitHub Action ingevuld met de eerste 7 tekens van de commit; bij een lokale build
 * in Android Studio blijft het "lokaal". Pas de regels met NUMBER en BUILD alleen aan zoals ze er nu staan:
 * de workflow zoekt ze op.
 */
object AppVersion {
    const val NUMBER = "1.0"
    const val BUILD = "lokaal"
}
