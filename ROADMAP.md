# NineGrid – Roadmap en de lijst

Dit is "de lijst": wat Mick wil dat NineGrid nog krijgt. Een nieuw gesprek begint hier.

## Werkwijze

- Communicatie met Mick in het Nederlands; de app is Engels/Nederlands.
- **Niets bouwen zonder startsein.** Voorstellen gaan eerst op de lijst; bouwen pas als Mick "start" of "begin maar" zegt.
- Denk mee en spreek tegen als iets beter kan; geen ja-knikker.
- Elke versie krijgt een passende planeswalker-naam. 0.x zijn testversies; 1.0 = fase 2. **Saheeli** is gereserveerd.
- Bouwen gebeurt alleen in GitHub Actions. Een release ontstaat als `versionNameValue` in `app/build.gradle.kts` verandert op `main`.
- **Nooit releasen zonder Micks expliciete "ja, release".** Mergen naar `main` met een nieuwe versie = release.
- Testen zonder release: werk op een branch en start de workflow Build met de hand op die branch (`workflow_dispatch`); pas als die slaagt naar `main`.
- Keystore, wachtwoorden en de AI-sleutel blijven buiten de repo (GitHub secrets / alleen op de telefoon).
- Bij elke versie: handleiding (NL/EN), Wat is nieuw, FEATURES.md en FEATURES.nl.md, RELEASE_NOTES.md bijwerken.
- Tellen op afbeeldingen: vak voor vak / kwadrant voor kwadrant.
- Vóór het voorstellen van nieuwe features eerst FEATURES.md in de repo nakijken of het er al in zit (16 nov: proefhand was voorgesteld maar bestond al).

## Hoe te werken (technisch, voor een nieuw gesprek)

**Repo en bouwen**
- Repo: `spoonpaco-lgtm/ninegrid`. Haal hem binnen met `add_repo` (toegang: push) en kloon hem; registreer de map met `register_repo_root`.
- Lokaal kan niets compileren (geen toegang tot Maven/Google). Alles wordt gebouwd in GitHub Actions (`.github/workflows/build.yml`: `testReleaseUnitTest assembleRelease`).
- Werkwijze per versie: nieuwe branch, wijzigen, committen, pushen, workflow met de hand starten op die branch (`gh workflow run build.yml --ref <branch>`), wachten met `gh run watch <id> --exit-status`, fouten lezen met `gh run view <id> --log-failed`. Pas als dat groen is naar `main` mergen. Een release ontstaat vanzelf als `versionNameValue` in `app/build.gradle.kts` op `main` nieuw is; daarna met `gh api repos/spoonpaco-lgtm/ninegrid/releases/tags/vX.Y.Z` controleren of de APK erbij staat.
- `gh` GraphQL is geblokkeerd: gebruik `gh api repos/...`, `gh run list/view/watch`; `gh release view` werkt dus niet.
- Opmaakwijzigingen (zoals 0.9.8.2) eerst als testbuild naar Mick, pas na zijn "merge" naar main.
- Commit-afsluiting: de `Co-Authored-By`- en `Claude-Session`-regels die het gesprek opgeeft.

**Elke versie**
- `versionNameValue` (en `versionCodenameValue` bij een nieuwe naam), RELEASE_NOTES.md, handleiding (`ui/guide/GuideContent.kt`, NL en EN), Wat is nieuw (`ui/guide/WhatsNew.kt`, NL en EN), FEATURES.md en FEATURES.nl.md, ROADMAP.md (naar Uitgebracht).
- Teksten staan in `res/values/strings.xml` (EN) en `res/values-nl/strings.xml`; elke nieuwe string in beide.
- Regex-literals: geen losse `{` of `}` (de test `regexesAreAndroidSafe` slaat anders alarm).
- Nieuwe logica in `domain/` met een unittest in `app/src/test/.../ParserTest.kt`; dat is de enige controle die vóór de build kan.

**Dit document bijwerken**
- Eerst `project_read` op `claude/NineGrid-roadmap.md`, anders weigert `project_write`. Daarna bewerken in `ROADMAP.md` in de repo en terugschrijven met `project_write(path="claude/NineGrid-roadmap.md", local_path=".../ROADMAP.md")`.

**Scanner: hoe het werkt (stand 0.9.14)**
- `domain/ScanParser.kt` leest per vak de bovenkant (naam) en een onderste strook (3× vergroot: nummer, set, taal, ★). Zonder setcode én nummer volgt een tweede leespoging met meer contrast (`reread`, met bekende setcodes erbij). Jaartal (ook met W) is nooit een nummer (`sanitize`); kleine tekstgroepjes naast het blad (buurpagina) tellen niet (`mainBlock`).
- Raster: camera met kader → vaste vakken; galerijfoto met ingevulde set → raster op de kaartnamen (`domain/NameGrid.kt`, ankers ≥ 85%), anders het raster uit de tekst. Lege rij/kolom schuift niet op; leeg vak via `EmptySlots.decide`.
- Setlijst: `CardCache.setList`/`scanList`, eerst uit de offline database (`OfflineCards`), dan Scryfall.
- Naam: `domain/NameMatch.kt`, beste naam binnen de set (marge 0,12, drempel 0,7, groen vraagt 80%); `bestInLines` zoekt ook in andere regels (altijd oranje; basislanden alleen met typeregel). Splitkaarten: vak zonder naam gedraaid lezen (`rotatedNames` in ScanScreen.kt).
- Groen alleen als naam, set en nummer samen kloppen en het nummer bij de best passende naam hoort. Altijd oranje (`domain/SetCheck.kt`): andere set dan opgegeven, geen nummer bij meerdere prints met die naam, geen set én geen nummer (geen gok-print). Alleen een nummer is nooit groen.
- Voorstel "Alles uit set X?" (`domain/SheetSet.kt`): telt pas na een tik van de gebruiker. Een set telt alleen als de gebruiker hem opgeeft.
- Meten: Ontwikkelaar → Testset draaien op de telefoon, **met de offline database aan** (anders te lage cijfers). Scanrapport (Ontwikkelaar, op het controlescherm "Scan delen"): foto en per vak wat gelezen is.

**Print kiezen, bans en reprints (stand 0.9.10)**
- Printlijst, ✎ met print en splitsen, + Exemplaar toevoegen: `ui/screens/PrintDialogs.kt`; regels (afwerkingen, prijs, sorteren) in `domain/PrintChoice.kt`; opslaan als één wijziging in `Repository.changeEntry`.
- Bans en reprints: `data/Watcher.kt` (stand in `filesDir/watch.json`, gewist bij fabrieksinstellingen), regels in `domain/Watch.kt`, melding in `PriceWorker.notifyWatch`. Legaliteit komt uit de prints, die de prijsupdate ververst.
- 4e: `domain/DeckFit.kt` en `AppViewModel.deckIssues()`; het controlescherm zet zulke kaarten in de groep Controleer.


## Uitgebracht

| Versie | Naam | Belangrijkste |
|---|---|---|
| 0.8.6 | Tamiyo | Offline kaartdatabase |
| 0.8.7 | Teferi | Decks, brackets en combo's |
| 0.8.8 | Karn | Suggesties uit je collectie |
| 0.8.9 | Ugin | Statistieken en prijshistorie |
| 0.9.0 | Jace | Delen met vrienden en ruilmatch, handleiding, Wat is nieuw |
| 0.9.1 | Kaya | Bijwerken vanuit de app, alle kaarten, verlanglijst, bladweergave, privacymodus, boosters, gildekleuren, Magic-effecten |
| 0.9.2 | Ajani | Fix: Game Changers en tutors (offline database) |
| 0.9.3 | Nahiri | Kaartscherm, deckscherm en scanscherm vernieuwd; paginateller |
| 0.9.4 | Ajani | Fix: privacystijlen compleated en zegel, glinstering |
| 0.9.5 | Narset | Fix: filters Alle kaarten, "vanaf"-prijzen verlanglijst |
| 0.9.6 | Chandra | Lamp, lege vakken, oranje alleen voor controleren, manakosten, verlanglijst-indeling |
| 0.9.7 | Teyo | Bugfixes, verbeteringen en QOL: punten 12, 13, 14, 15, 17, 18, 20, 21, 22, 23, 24, 25 (zie RELEASE_NOTES.md) |
| 0.9.7.1 | Teyo | Scannerreparatie: kale setcode, lege vakken, melding "X van 9 gelezen" bij een heel blad |
| 0.9.8 | Kaito | 27 Ontwikkelaar-menu met scanrapport, 26 vegen, 16 label NIEUW, 19 commander wijzigen, 2 fabrieksinstellingen, 30 lampmelding, 31 combokaartjes |
| 0.9.8.1 | Kaito | 32 opgegeven set is een grens (oranje met andere set) |
| 0.9.8.2 | Kaito | 29 vormgeving gelijk getrokken (alleen opmaak) |
| 0.9.9 | Vivien | 37 scanner repareren, 38 foil-twijfel blauw ★, 39 kaart voor kaart stroomlijnen, 4 scannen naar een deck, 28 één keuze uit de collectie halen, 33–36 filters/sorteren, thumbnails bij suggesties, inklapbare combo-blokken, segmentbalk Thumbnails \| Namen |
| 0.9.9.1 | Vivien | Fix leeg venster bij ontbrekende combokaart; lijnenkiezer (Aantal kaarten als vakje, groepen, alleen sets met kaarten); taartgrafiek Lijn \| Taart; inklapbaar Prijsverloop op het kaartscherm |
| 0.9.9.2 | Vivien | Scannen van één kaart: fotootje met gekleurde rand onderin; kader past tussen de gemeten balken; Controleer-knop niet meer afgekapt |
| 0.9.10 | Dovin | 8 print kiezen (printlijst, print van een exemplaar wijzigen en splitsen, + Exemplaar toevoegen, verlanglijst), 40 melding bij ban of reprint, 4e past niet in het deck oranje |
| 0.9.10.1 | Dovin | Menu opgeruimd: Meer in blokken, Back-up en export eigen scherm, Over op één plek, Lijst plakken in Importeren, Wijzigingslog heet Laatste actie ongedaan maken, Instellingen in 8 andere groepen |
| 0.9.11 | Tezzeret | Exporteren (Moxfield, Archidekt, Cardmarket), deck via QR, crashrapport, privacymodus met meer controle, 5 nieuwe privacystijlen + Zegel klein, 9 app-iconen, widgets, testset op de telefoon |
| 0.9.12 | Kasmina | Scanner betrouwbaarder: strengere naamvergelijking, alleen nummer nooit groen, naam ook in andere regels (oranje), geen print op de gok, voorstel set van het blad, splitkaarten gedraaid, buurpagina weg, jaartal met W; testset 98→109 goed, groen maar fout 8→0 |
| 0.9.13 | Nissa | Raster op de kaartnamen (galerijfoto met set); testset meldt Scryfall-storingen; testset 88% goed (set ingevuld 91%), 0% groen maar fout, lege vakken 96% |
| 0.9.14 | Ob Nixilis | Top movers (Winner én Loser of the Week/Day, inklapbaar, effecten Gloed en bloed of Stempels), Magic-effecten per effect, Toxic vernieuwd, setlijst voor de scanner uit de offline database |
| 0.9.15 | Niko | Herkennen op het plaatje (offline vingerafdrukken per set), beelden laten stemmen bij 1 kaart, 39c, printcontrole-fix (sinds 0.9.14), testset 18 bladen; boosterscherm en boosters wissen, filter Prijs, sorteren in twee richtingen, één paneel Filteren en sorteren, bewaarde combinaties, groei uitgesplitst onder de grafiek |

## Nog te doen

Volgorde: eerst wat Mick bij de telefoontest nog open heeft, dan de volgende versie, dan kleine verbeteringen, dan fase 2 en ideeën. Alles wat gebouwd is staat in de tabel hierboven en in RELEASE_NOTES.md.

### 1. 0.9.11 Tezzeret: uitgebracht 18 nov (Micks "ja, release"), nog te testen op de telefoon

Uitgebracht 18 nov na Micks "ja, release", zonder eerdere telefoontest; de punten hieronder zijn dus nog niet bevestigd. Volledige lijst in RELEASE_NOTES.md.

**Gebouwd in 0.9.11**
- Exporteren (3 sites, formaten opgezocht): deck → Moxfield, Archidekt, Cardmarket (kopiëren + site openen); collectie als Moxfield-CSV; verlanglijst als wants-lijst voor Cardmarket (max. 150 regels). Andere sites (Arena, Deckbox, ManaBox, TCGplayer) bewust nog niet.
- Deck delen via QR-code (ingepakt, lange lijsten in delen; ontvangen via Decks → scan-icoon).
- Crashrapport: bewaard bij een crash, bij de volgende start delen of niet; ook onder Ontwikkelaar.
- Verlanglijst: −1, vegen om te verwijderen met terugzetten, print op elke regel.
- Kleine verbeteringen: Lijn/Taart en splitsing onthouden; Alle kaarten naar Deck of Sideboard bij scannen/plakken naar een deck (niet bij Commander).
- Grafiekvenster: regels op volgorde van waarde op de gekozen dag, schuift mee.
- Meldingstoestemming (Android 13+) bij het aanzetten van prijs-, ban- en reprintmeldingen.
- Scanrapport: "app dacht eerst" per vak en de instellingen van de scan.
- Testset: 11 bladen in `testset/` (plus `testset.zip`); Ontwikkelaar → Testset draaien op de telefoon met de volledige scanroute; workflow Scanner-testset op de emulator alleen op verzoek.
- Privacymodus met meer controle: wat verbergen (alles / alleen totalen / alleen kaartprijzen), grafiek in procenten, aantallen verbergen, meldingen zonder bedragen, kaartprijzen boven een grens. Bewust niet: automatisch weer aan, FLAG_SECURE.
- Privacystijlen (`ui/components/PrivacyStyles.kt` en `Privacy.kt`), 10 sleutels: compleated, zegel, zegelklein, schaduw, toxic, lightning, fog, pixelized, countered, stippen.
  - Namen: Completed (opgeslagen sleutel blijft "compleated"), Lightning, Countered. Plus Zegel klein voor lijsten.
  - Toxic: grillige bijna zwarte groene ooze met groene oliefilm, gaten die knappen, bellen, druppels met rand.
  - Lightning: lus van 24 s, 7 slagen op onregelmatige momenten, elke slag een eigen vorm, knipperen, zwak blauw spoor tussen de slagen.
  - Fog: zelfde techniek als Schaduw, 6 drijvende wolken.
  - Pixelized: flikkerend pixelraster dat naar de randen vervaagt. Countered: blauwe stempel die neervalt.
  - `PrivacyStyle.timeline` (animatie): Toxic, Lightning, Pixelized, Countered; zonder Magic-effecten vallen die terug op stippen. Fog blijft zichtbaar. `privacyMask()` tekent nooit de bedragtekst.
  - Besloten: The Ooze komt er niet in. Niet gekozen (op de plank): Infect, Morph, Mana-pips.
- Negen extra app-iconen (activity-alias): Metalen (Zilver, Koper, Zwart-goud), Edelstenen (Smaragd, Robijn, Amethist), Licht en donker (Perkament, Inkt, Neon); Instellingen → Weergave → App-icoon.
- Widgets (`data/widget/`, gewone `AppWidgetProvider` met getekend plaatje): Waarde, Winner of the Week, Kaart van de dag, Meldingen, Verlanglijst, scanknop breed en klein. Bijwerken na de prijsupdate en bij wisselen van de privacymodus; bij privacy tegeltjes in plaats van bedragen.

**Nog te testen door Mick (telefoon)**
- Privacystijlen, vooral Toxic in de kleine voorbeeldrij bij Instellingen → Privacy; Lightning en Fog zoals zijn schetsen.
- Widgets: schalen de plaatjes goed, werken ze bij na de nachtelijke update en bij privacy.
- App-iconen: risico dat het startschermicoon na de update verdwijnt door de alias-verplaatsing (staat in de release notes).
- Exporteren: eerste import op Moxfield, Archidekt en Cardmarket nakijken (foil/etched via *F* en *E*).
- QR tussen twee telefoons; crashrapport-vraag bij de volgende start.

**Open vragen bij 0.9.11**
- Testset opnieuw draaien op de telefoon en `uitslag.txt` per blad controleren (door Claude van de foto gelezen; Mick moet nog nakijken).
- Verlanglijst: Mick zag na een printwijziging niet de juiste print of prijs; in de code niet te vinden, eerst vragen wat hij precies ziet.
- Scanner verbeteren: zie 1h-scan (de varianten uit 0.9.11 — elk vak los, meer pogingen op de voetstrook, hele foto vergroot — zijn niet gemeten; `ScanVariantsTest` staat nog klaar).
- Supporter-/donatielocking (stijlen of iconen alleen voor supporters): nog niet besloten.
- Testset-regels: alleen foto's van Micks eigen bladen (repo is publiek); nieuwe bladen in een map `NN-set-kaart` met foto en rapport, liefst originelen.

### 1b. 0.9.12 Kasmina: uitgebracht 18 nov (Micks "ja, release")

Mick 18 nov: "bouw alle voorstellen in een branch". Basis: eerste testset-meting op de telefoon (0.9.11, 98 van 144 goed, 8 groen maar fout, 33 niet gevonden; zie `testset/README.md`). Groene build: https://github.com/spoonpaco-lgtm/ninegrid/actions/runs/44250039457. Naam Kasmina gekozen door Claude; Mick mag hem veranderen.
- Strengere naamvergelijking (`domain/NameMatch.kt`): beste naam binnen de set met marge 0,12, drempel 0,7; groen vraagt 80%; een gelezen nummer telt alleen als die kaart ook de best passende naam is. De 8 paren uit de testset als unittest.
- Geen set en geen nummer: oranje (`SetCheck.guessedPrint`), geen gok-print meer.
- Voorstel "Alles uit set X?" (`domain/SheetSet.kt`, `AppViewModel.suggestSheetSet`): alle gevonden kaarten (min. 3) bestaan samen in één set → chip op het controlescherm; telt pas na Micks tik (zo past het bij "een set telt alleen als de gebruiker hem opgeeft"). Oudste set bij meerdere.
- Splitkaarten: vak zonder naam een kwartslag gedraaid lezen (`rotatedNames` in ScanScreen.kt), eis twee namen.
- Galerijfoto: kleine tekstgroepjes links/rechts van het blad (buurpagina) tellen niet mee (`ScanParser.mainBlock`, alleen horizontaal: verticaal zou de illustratie-gaten de namen laten wegvallen).
- Jaartal met W ("2025W", "202W") is geen nummer, ook na opnieuw lezen (`sanitize`).
- Testset draaien: met camerakader (`kader.txt` per blad, door Claude op de foto's gezet) en daarnaast als galerijfoto.
- Later toegevoegd: alleen een nummer nooit groen; naam ook zoeken in andere regels bij een bekende set (`NameMatch.bestInLines`, altijd oranje; basislanden alleen met typeregel "Basic Land"). Handgezette kaders weggehaald (maakten het slechter); testset leest als galerijfoto en toont wat er gelezen is.
- Laatste meting (18 nov): 109 van 144 goed, 0 groen maar fout, 28 oranje maar goed, 25 niet gevonden.

### 1c-scan. 0.9.13 Nissa: uitgebracht 18 nov (Micks "release")

- Raster op de namen (`domain/NameGrid.kt`), alleen bij galerijfoto met ingevulde set; camera met kader ongewijzigd. Ankers ≥ 85% gelijk, bovenste bij dubbele naam, keuze: binnen de foto → meeste tekst (binnen 10%) → dichtst bij het midden; eindcontrole namen bovenaan het vak.
- Testset meldt Scryfall-storingen ("LET OP"). Les van 18 nov: Micks offline database stond uit; daardoor ging elke opzoeking via Scryfall en gaven twee runs te lage cijfers. Meten altijd met de offline database aan.
- Meting (offline database aan): 126 van 144 (88%), groen maar fout 0, niet gevonden 15; set ingevuld 91%; lege vakken 22 van 23.
- Wat overblijft: gemengd blad zonder set (6 vakken), GRN-splitkaarten (6), losse leesfouten (6: Eternal Wanderer showcase-nummer 2×, Intruder Alarm, Jace's Triumph, Havoc Festival, Raff Capashen).

### 1d-scan. Volgende scannerstappen (voorstel, wacht op startsein)

1. Setlijst uit de offline database halen: gaat mee in de Top movers-branch (zie 1e-movers).
2. Splitkaarten (GRN rij 3): gedraaid lezen levert nog niets. → aanpak via het plaatje, zie 1h-scan stap 1.
3. Gemengd blad zonder set. → zie 1h-scan stap 3.
4. Testset: "kaart als leeg gezien" apart vermelden; testset met losse kaartfoto's (één kaart per foto) om scannen per kaart te meten.

### 1h-scan. Herkennen op het plaatje naast tekst lezen (voorstel 22 nov; stap 1, 1b en 2 gebouwd in 0.9.15, zie 1i-scan; stap 3 wacht)

Aanleiding: grote scanner-apps lezen geen tekst maar herkennen het plaatje: camerastroom → kaart in beeld vinden en rechttrekken → ONNX-model maakt een vingerafdruk (embedding) → dichtstbijzijnde kaart in een int8-index van alle kaarten (apart gedownload) → bij herdrukken met hetzelfde plaatje de print laten kiezen. **Eigen uitwerking met een open model (MobileNet, Apache 2.0); geen model of index van een andere app.**

Waarom: de resterende fouten uit 0.9.13 (splitkaarten 6, gemengd blad zonder set 6, losse leesfouten 6) komen allemaal van tekst lezen; op het plaatje speelt draaiing, showcase-nummer of taal geen rol. Tekst lezen blijft nodig voor set en nummer: **combineren, niet vervangen**.

**Offline (Mick 22 nov: moet offline blijven werken):** het model zit in de app en rekent op de telefoon. Alleen de vingerafdrukken worden één keer gedownload/gemaakt; daarna offline. Zonder vingerafdrukken voor een set leest de scanner alleen tekst, zoals nu (niets kwijt).

**Stap 1: plaatje binnen de opgegeven set.**
- MediaPipe ImageEmbedder (klein MobileNet-model, Kotlin, geen eigen native code). APK wordt een paar MB groter.
- Bij een nieuwe set eenmalig de art-crops van Scryfall ophalen (~10–15 MB), omrekenen naar vingerafdrukken, alleen die bewaren (enkele honderden KB per set); de plaatjes zelf niet.
- Per vak eerst tekst zoals nu; bij oranje of leeg beslist het plaatje mee. Groen alleen als naam en plaatje het eens zijn.
- Past bij de regel "een set telt alleen als de gebruiker hem opgeeft".
- Onzeker: of een algemeen model binnen een set betrouwbaar genoeg is. Meten met de testset op de telefoon (offline database aan); verwacht effect op splitkaarten en losse leesfouten.

**Stap 1b: Sets vooraf downloaden.**
- Knop naast de offline database: vingerafdrukken voor alle sets in de collectie plus zelf gekozen sets, thuis op wifi.
- De nachtelijke update maakt vingerafdrukken voor nieuwe sets bij (alleen met internet).

**Stap 2: meerdere beelden laten stemmen bij kaart voor kaart.**
- CameraX `ImageAnalysis` met `STRATEGY_KEEP_ONLY_LATEST` : ~5 beelden achter elkaar lezen, de uitkomst die het vaakst terugkomt telt. Een slecht moment (glans, beweging) kost geen scan meer. Geen model nodig, volledig offline.

**Stap 3 (later): één index voor alle kaarten, voor gemengde bladen zonder set.**
- Index vooraf bouwen (bijvoorbeeld in GitHub Actions uit de Scryfall-plaatjes), int8 ingepakt, tientallen MB, één keer downloaden; daarna offline.
- Pas beginnen als stap 1 laat zien hoe goed een algemeen model op Micks foto's werkt; Een algemeen model is daarvoor misschien niet goed genoeg; dan een eigen getraind model. Herdrukken met hetzelfde plaatje: set/nummer uit de tekst of de printlijst.

**Tussendoor (klein):** scantips op het scanscherm (achtergrond, licht, niet overlappen, opnieuw scannen).

### 1e-movers. 0.9.14 Ob Nixilis: Top movers + setlijst uit de offline database (uitgebracht 22 nov op Micks "Release 0.9.14", nog te testen op de telefoon)

**Ook in deze branch (Mick 18 nov):** scannerstap 1 uit 1d-scan: de setlijst uit de offline database halen (nieuwe query per set in `OfflineCards`, `CardCache.setList` eerst offline, dan Scryfall). Dan werken kiezen binnen de set, zoeken in andere regels en het raster op de namen ook zonder internet, met minder verzoeken aan Scryfall.

**Top movers:**

Op het Overzicht, op de plek van Winner of the Week, één samengevoegd blok "Top movers" (Mick 18 nov: samenvoegen). Bovenaan de grote kaart met plaatje, met duidelijk erboven wat het is: **Winner of the Week** bij Stijgers, **Loser of the Week** bij Dalers (bij Dag: Winner/Loser of the Day). Daaronder de schakelaars en de lijst met de nummers 2 t/m 5 (de grote kaart is nummer 1, niet dubbel). De widget Winner of the Week blijft zoals hij is.
- **Alleen de eigen collectie** (Mick): collectie en decks samen, proxies niet; elke print één keer, foil apart. Niet de hele markt (Scryfall heeft geen prijshistorie; dagelijks alle ~100.000 prijzen bewaren is te zwaar) en niet de verlanglijst.
- **Schakelaars:** Dag | Week (standaard Week: dagbewegingen zijn vaak ruis van een paar verkopen) en Stijgers | Dalers.
- **Rekenen:** Dag = tegen gisteren, Week = tegen 7 dagen geleden (prijshistorie in `price_history`, elke nacht gevuld). Kaarten zonder prijs van toen tellen niet. **Rangschikken op valuta (Mick):** verandering in € × aantal dat je hebt; het percentage staat er alleen bij.
- **Per regel:** plaatje, naam, set, ★ bij foil, oude → nieuwe prijs, verandering in € en %, aantal. Tik = kaartscherm. Grote kaart + 4 regels, "Meer" voor de top 20.
- **Privacymodus:** bedragen verborgen → alleen percentages en pijltjes (volgt "wat verbergen").
- **Magic-effecten op de grote kaart (Mick 18 nov): twee aparte stijlen, te kiezen** (zoals de privacystijlen; plek voorstel: Instellingen → Weergave, naast Magic-effecten):
  - **Gloed en bloed:** Winner = oranje mythic-gloed rond de rand; Loser = olie "rood als bloed" die van onder de kaart in kruipt (techniek van Completed/`drawOil`, maar donkerrood).
  - **Stempels:** Winner = gouden stempel "WINNER"; Loser = rode stempel "DESTROYED" (zoals de Legaal-stempel, `Stamp` in `ui/components/Effects.kt`).
  - Speelt één keer bij openen of wisselen (~1,5 s), daarna stil. Magic-effecten uit → alleen een rustig groen of rood randje. Eigen tekenwerk, geen Wizards-materiaal.
  - Schetsen getoond 18 nov; Mick: het bloed moet van de onderkant van de kaart afdruipen, in dezelfde stijl als de Completed-olie, met schaduw (niet plat). Zo gebouwd in `ui/components/MoverEffects.kt` (`MoverFrame`).
- Gebouwd: Week = maandag tegen de maandag ervoor (zoals Winner of the Week en de widget); Dag = laatste prijsdag tegen de dag ervoor (`PriceDao.lastDay`/`dayBefore`). `PriceMovers.top/loser` met unittest. Scanner: `CardCache.scanList` (offline database eerst, niet als bestand bewaard).
- Winner of the Week = nummer 1 van de weekstijgers. Nagekeken 22 nov: `computeWinner` en de widget (`WidgetData.winner`) rekenen allebei met `PriceMovers.movers` + `winner`, dezelfde maandagen. Loser of the Week is nieuw (nummer 1 van de dalers).
- Technisch: rekenwerk in `domain/` met unittest; gegevens uit `price_history` en de exemplaren; handleiding, Wat is nieuw, FEATURES.

### 1f-movers. Top movers inklapbaar: in deze branch (0.9.14), gebouwd 18 nov

- Het Top movers-blok wordt inklapbaar. Ingeklapt: alleen de grote kaart (Winner of Loser of the Week/Day, met het effect). Uitgeklapt: de schakelaars Dag/Week en Stijgers/Dalers, de nummers 2 t/m 5 en Toon top 20.
- Gebouwd: standaard ingeklapt; tik op de titel (pijltje rechts); Week/Dag, Stijgers/Dalers en open/dicht onthouden in de instelling `moversView` ("week,up,closed").

### 1g-movers. Ook in deze branch (Mick 18 nov), gebouwd 18 nov

- Toxic dezelfde makeover als het bloed: schaduw eronder (verschoven), glanzende vulling licht→diep donker met donkere onderrand, druppels met hals, bol en glimlicht, gaten met schaduw van binnen, rand licht boven en donker onder, bellen die eerst opzwellen.
- Elk Magic-effect een eigen schakelaar (`domain/FxSet.kt`, instelling `fxOff`, `LocalFx`): foil, exile, stamps, shields, movers, privacy. Hoofdschakelaar blijft; de losse schakelaars staan eronder als hij aan staat. Stempels uit raakt de WINNER/DESTROYED-stempel niet (die hoort bij Winner en Loser).
- Fix: Dalers had geen schildje op de grote kaart (alleen bij Stijgers getekend); nu ▼ rood.

### 1i-scan. 0.9.15 Niko: uitgebracht 23 nov (Mick: "voeg dit allemaal toe aan 0.9.15 en als dat gedaan is release je hem meteen")

Naam Niko (Aris, spiegelscherven = beelden) gekozen door Claude; Mick mag hem veranderen.
- **1h stap 1 + 1b:** herkennen op het plaatje. `data/Fingerprints.kt` (Embedder met TensorFlow Lite 2.16.1, model `assets/embedder.tflite` = MediaPipe MobileNet V3 small, door de workflow opgehaald; zonder model leest de scanner alleen tekst). Vingerafdrukken per set in `filesDir/fingerprints/SET.bin` (int8), gemaakt uit `SetCard.image` via Coil. Zoeken en grenzen in `domain/PictureIndex.kt` (MIN_SCORE 0,55, MIN_MARGIN 0,05, **geschat**). In `AppViewModel.resolveAll`: plaatje uit `crops` per vak, nooit groen; tekst onzeker → kaart van het plaatje (oranje); tekst zeker en plaatje duidelijk anders → oranje. Instelling `pictureMatch` (Scannen), Sets vooraf downloaden (`Repository.ownedSetCodes`), set uit het setveld automatisch op de achtergrond, nachtelijk aanvullen in `PriceWorker`.
- **1h stap 2:** `domain/FrameVotes.kt`: bij 1 kaart draait de analyse altijd mee (ook zonder Automatisch); foto mist set/nummer → aanvullen met ≥ 2 beelden van dezelfde kaart (laatste 2,5 s).
- **39c:** controlescherm: groep Zeker ingeklapt als er iets te controleren is (▸/▾).
- Scanrapport en testset: plaatje per vak (naam #nr score/marge, 2e kaart); testset maakt eerst de vingerafdrukken, geeft `cropsFor` mee en meldt plaatje-goed met scores/marges van goed en fout.
- **Na testset 22 nov (2 runs, 118 en 113; zonder storingen 126/125, gelijk aan 0.9.13):** plaatje top-1 maar 33%; overschreef 2× een goede gelezen naam (Impact Tremors, Feed the Infection); vakken zonder tekst (GRN-splitkaarten, Jace's Triumph) kregen het plaatje nooit; één Scryfall-storing schreef een heel blad af. Gerepareerd (Mick: "doe dat"): alleen de illustratie vergelijken (vingerafdrukken versie 2, oude worden opnieuw gemaakt), vakken met alleen een set gaan ook langs het plaatje, een gelezen naam wordt nooit overschreven, testset probeert bij storing tot 3× opnieuw. Nog niet: print kiezen met het plaatje bij dezelfde naam (Mondrak-2: plaatje gaf #335 goed, app koos #11), pas na een meting.
- **Na testset 22 nov run 3 (127 van 144, groen maar fout 0, oranje maar goed 31→36, plaatje 50/115 = 43%; marge scheidt goed van fout, score niet):** Mick "bouw alles": MIN_MARGIN 0,05 → 0,09; kaart van het plaatje via set+nummer (Mondrak-1 werd niet overgenomen); punt 4 print kiezen bij dezelfde naam (`printMargin` ≥ 0,05); vak zonder tekst → beste gok vanaf score 0,5 als oranje "controleer" (splitkaarten). Let op: grenzen afgesteld op dezelfde 144 vakken; nieuwe testbladen nodig om dat te toetsen.
- **Testset 22 nov met 18 bladen (225 vakken), laatste build:** plaatje aan 200 goed / uit 194 (oude 144: 129 tegen 126; nieuwe 81: 71 tegen 68), groen maar fout 1 in beide (Huatli). Mick bevestigde Huatli 339 en Kinzu 406. Mick "bouw maar": printCandidates via de scannerlijst (fout sinds 0.9.14: alleen het setscherm-bestand → bij een nieuwe set geen andere prints → groen met verkeerde print), plaatje vult aan als de tekst geen naam las (Mondrak-1), plaatje kiest geen print meer (Kinzu 411 bij marge 0,15). Voor 0.9.16: raster GTC als galerijfoto (6 van 6 kaarten niets gelezen, camerascan wel).
- **Testset 22 nov 18:05 (build 45028235674, plaatje aan):** 203 van 225, groen maar fout 0, oranje maar goed 56 (was 48: meer prints worden nu terecht als dubbelzinnig gezien), niet gevonden 16. Opgelost: Huatli (339), Kinzu (406), Caparocti showcase 2×. Nieuw fout: Bedrock Tortoise LCI 176 → 378 (oranje; de oude 24×33-vergelijker kiest nu ook bij LCI en koos verkeerd). Mondrak-1 nog steeds Bladed Ambassador ondanks plaatje 0,78/0,31 en geen gelezen naam: oorzaak onbekend (vermoeden: kaart van het plaatje niet op te halen); voor 0.9.16 de reden in het label/testset zetten.
- **Nog te doen na de build:** testset draaien op de telefoon (offline database aan, internet voor de eerste vingerafdrukken), daarna MIN_SCORE/MIN_MARGIN bijstellen met de gemelde scores. APK-groei nakijken (TFLite ~ een paar MB per ABI + model ~4 MB).
- Onzeker: normalisatie van de invoer (nu -1..1) en of een algemeen model de kaarten binnen een set goed genoeg uit elkaar houdt. De testset laat het zien.

### 1j. Ook in 0.9.15 (Mick 23 nov): boosters, filteren en sorteren, meetellen

- Boosterscherm (`BoosterDetailScreen`, route `booster/{id}`): kop met betaald/toen/nu/winst, kaarten duurste eerst met hit, Thumbnails | Namen, "niet meer in je collectie", tik = kaartscherm, potlood = prijs.
- Boosters wissen (`Repository.deleteBoosters`): alleen de booster of ook de kaarten (losse exemplaren, nieuwste eerst; deck of weg blijft staan, vooraf geteld via `boosterCardsInCollection`); meerdere (lang indrukken), per box, alles. `UndoData` heeft nu `restoreBoosters/Cards/Boxes`; ongedaan maken zet alles terug.
- `FilterSearchBar`: één paneel Filteren en sorteren; sorteerrichting via `SortKey` ("key~r"), elk scherm draait zijn lijst om; chip voor een niet-standaard sortering. Filter Prijs (`CardFilter.priceMin/priceMax`, `FilterGroup.PRICE`, per stuk, foilprijs). Bewaarde combinaties (`SavedView`, per scherm in `deckUiPrefs` "views_<scherm>", niet in de back-up). Verlanglijst kreeg zoeken, filter en sorteren (scherm "wish").
- Grafiek: `domain/Growth.split` onder de grafiek (prijzen vs kaarten erbij/eraf). De dagstand werd al 3 s na een wijziging bijgewerkt (`AppViewModel.snapshot`); mijn eerdere uitleg "pas bij de volgende keer openen" klopte niet.
- Nog na te kijken op de telefoon: alles hierboven; vooral boosters wissen met kaarten + ongedaan maken, en het paneel op alle schermen.
- Later: bewaarde combinaties in de back-up; raster GTC als galerijfoto (0.9.16); Mondrak-1 diagnose.

### 2. Kleine verbeteringen (nog geen versie)

- Scannen, kaart voor kaart: 39a (set van de vorige kaart als aanname) is door Mick afgewezen, 17 nov: scannen wordt er trager van; een set telt alleen als de gebruiker hem zelf opgeeft. 39c gebouwd in 0.9.15 (zie 1i-scan).
- Prijshistorie ophalen uit MTGJSON (tot 90 dagen terug): formaat en Cardmarket-waarde zijn nog niet nagekeken, en Mick heeft het nog niet bevestigd.

### 3. Nog na te kijken op Micks telefoon

Alleen wat Mick nog niet heeft bevestigd. Wat hij werkend heeft gezien, gaat eruit (22 nov: Mick laat weten wat hij al gebruikt).

**0.9.14 (Top movers)**
- Top movers op het Overzicht: grote kaart Winner/Loser, Dag | Week, Stijgers | Dalers, inklappen en onthouden, Toon top 20, privacymodus met alleen percentages.
- Effecten Gloed en bloed / Stempels; elk Magic-effect een eigen schakelaar; Toxic vernieuwd; rood ▼-schildje bij Dalers.
- Widget Winner of the Week en het blok tonen dezelfde kaart (in de code gelijk; op de telefoon bevestigen).
- Scannen zonder internet met een set die in de offline database staat.

**0.9.10.1 (menu)**
- Meer in blokken: Handleiding + Wat is nieuw bovenaan; Collectie (Importeren, Back-up en export, Delen met vrienden, Laatste actie ongedaan maken); App (Instellingen, Over NineGrid).
- Lijst plakken zit in Importeren (knop onder Bestand kiezen); Collectie → + en een deck hebben hem nog.
- Back-up en export is een eigen scherm (back-up, terugzetten, automatische back-up, CSV; onderaan rood Alles wissen).
- Over NineGrid heeft versie, updates, automatisch bijwerken en Wat is nieuw.
- Instellingen heeft acht groepen: Weergave, Taal en regio, Privacy, Scannen, Kaartgegevens, Collectie, AI-hulp, Ontwikkelaar. Controleer of de ?-knoppen bij elk scherm naar een passend hoofdstuk gaan.

**0.9.10 (print kiezen, bans en reprints)**
- Kaartscherm: Andere prints › opent de printlijst (plaatje, jaar, prijs per afwerking, goedkoopst, ✓); sorteren Nieuwste | Goedkoopste; tik = nieuw kaartscherm erbovenop, Terug = vorige print.
- ✎ bij een exemplaar: regel Print › → printlijst; bij ×3 het vak "Voor hoeveel exemplaren"; foil die niet bestaat → Normaal met melding; daarna springt het kaartscherm naar de nieuwe print; ongedaan maken.
- + Exemplaar toevoegen: afwerkingen met prijs, aantal, collectie met map/blad/vak of een deck met Deck/Sideboard/Commander.
- Bans en reprints (Instellingen → Collectie): de eerste nachtelijke controle legt alleen de stand vast; meldingen pas bij een echte wijziging daarna. Reprints volgt sets die nog niet uit zijn of de laatste 30 dagen uitkwamen.
- Plakken naar een deck of scannen vanuit een deck met een banned kaart of een kaart buiten de commanderkleuren → oranje bij Controleer met de reden.
- Etched heeft bij Cardmarket (via Scryfall) geen eigen prijs: in de printlijst dan de foilprijs, maar alleen als er van die print geen gewone foil bestaat.

**0.9.9.2 en 0.9.9 (scanner)**
- Scannen van één kaart: fotootje met rand groen/oranje/blauw ★; tik = +1, kruisje = weg; bij plaatjes uit blijft het chipje met de naam.
- Lege hoesjes met glans: grenzen 0.35 en 0.7 in `EmptySlots.decide` zijn geschat (testset 0.9.13: lege vakken 22 van 23 goed).
- Verzonnen setcode of taal valt weg; jaartal telt niet als kaartnummer; de set van het blad wint van één verlezen set.
- Foil-twijfel is blauw ★ (chips "★ Foil" en "Geen foil"). **Open:** geeft ML Kit de ★ terug op echte foils? De hint werkt alleen dan; zo niet, foil herkennen op beeld.
- Kaart voor kaart: +1 en kruisje, Opslaan als alles zeker is, Foil als sessiestand.
- Scannen vanuit een deck (menu Toevoegen → Scannen) en de keuze "uit de collectie halen" (alles / exact dezelfde / niets).
- Filters en sorteren bij deck, vriend en set; thumbnails bij suggesties; combo-blokken inklappen; segmentbalk Thumbnails | Namen.

**Ouder**
- 0.9.8: lampmelding (punt 30 in RELEASE_NOTES.md) nog niet nagekeken.

**Weggehaald 22 nov (ingehaald):** 0.9.7.1 "bladscan leest minder dan 0.9.6" (testset nu 88%, 0 groen maar fout), lamp (lage batterij, code niet stuk), dubbele ★-vraag, geschatte rastermaten (vervangen door raster op de namen).

### Bekende beperkingen

- Filter Game Changers: de offline database kent ze niet. In de collectie en op de verlanglijst werkt het filter met wat er per kaart bewaard is; bij Alle kaarten zoekt het bij Scryfall (internet nodig).
- Manakosten in het setscherm: bij kaarten die je niet hebt pas zichtbaar nadat de setlijst is ververst.
- Prijsverloop: alleen kaarten in de collectie hebben bewaarde prijzen.

## Fase 2 (wacht op startsein) → wordt 1.0
- **Must vóór de release van 1.0: volledige codereview** (Mick, 18 nov). Op een eigen branch, na de release van 0.9.11, niet door een testbuild heen. In drie stappen:
  1. Spelling en taal: alle teksten in `res/values/strings.xml` en `res/values-nl/strings.xml` (ongeveer 1.200 elk), handleiding (`GuideContent.kt`) en Wat is nieuw (`WhatsNew.kt`), NL en EN. Ongeveer een uur plus testbuild.
  2. Gerichte bugreview van de zwaarste plekken: `ui/AppViewModel.kt` (ongeveer 2.200 regels), de scanner (`ScanParser.kt`, `ScanScreen.kt`, `EmptySlots`) en `data/Repository.kt`: crashes, dataverlies, foute prijzen.
  3. De rest van de code (in totaal ongeveer 22.400 regels in 89 bestanden), bestand voor bestand, over twee à drie gesprekken.
  - Claude kan de app niet draaien: vondsten zijn "mogelijk fout" tot Mick ze op de telefoon bevestigt. Elke reparatie apart en klein, met unittest waar dat kan.
- Sheet-koppeling (deel 2 punt 2, optie A: nieuwe Sheet via drive.file; Google Cloud-project + privacypagina nodig)

## Later (idee, geen versie)
- Verkoopalarm voor kaarten die je hebt: "meld als Ragavan boven € 60 komt" (omgekeerde prijsalarm, lijkt op Breakout)
- Jaaroverzicht in december: "Jouw jaar in Magic" (kaarten toegevoegd, duurste vondst, meest gescande set, groei in waarde, beste ruil), te delen als afbeelding, privacymodus respecteren
- Supporter-variant (nog niet besloten): app blijft helemaal gratis (alle kaartgegevens, prijzen, zoeken, ruilmatch, offline database: nooit achter betaling — Scryfall verbiedt paywall, Wizards Fan Content Policy verbiedt betaalde toegang); vrijwillige steun via Google Play Billing (15% kosten) met extra's zonder kaartgegevens of Magic-materiaal: extra thema's/kleuren, andere app-iconen in NineGrid-stijl, supporter-badge bij Over
  - Phyrexisch lettertype nooit als betaald extra (materiaal van Wizards)
  - Betaalde Sheet-synchronisatie: eerst schriftelijk navragen bij Scryfall
  - Voor echt verkopen: vooraf toestemming Scryfall en eventueel licentie Wizards; inkomsten via eigen bedrijf (btw → boekhouder)
