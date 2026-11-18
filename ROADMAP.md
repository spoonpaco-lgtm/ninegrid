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

**Scanner: hoe het werkt (stand 0.9.8.2)**
- `domain/ScanParser.kt` leest per vak de bovenkant (naam) en een onderste strook (3× vergroot sinds 0.9.7: nummer, set, taal, ★). Een vak is compleet met setcode én nummer; anders volgt een tweede leespoging met meer contrast (`reread`, met bekende setcodes erbij).
- `Repository.preResolve` verbetert namen en controleert tegen de setlijst op de telefoon (nummer + naam kloppen samen = zeker; alleen de naam = print met die naam, liefst met het gelezen nummer). `Repository.resolve` vraagt dan Scryfall (set+nummer, daarna alternatieve cijfers, daarna op naam in de set).
- Een kaart is alleen groen als naam, set en nummer samen kloppen. Sinds 0.9.8.1 is een kaart uit een andere set dan gevraagd, of zonder nummer met meerdere prints van die naam, altijd oranje.
- Leeg vak: `EmptySlots.decide`, alleen leeg als er geen tekst in dat vak is en weinig structuur.
- Scanrapport (Meer → Instellingen → Ontwikkelaar, op het controlescherm "Scan delen"): foto en per vak wat gelezen is; vraag Mick om dat bij een slechte scan.

**Print kiezen, bans en reprints (stand 0.9.10)**
- Printlijst, ✎ met print en splitsen, + Exemplaar toevoegen: `ui/screens/PrintDialogs.kt`; regels (afwerkingen, prijs, sorteren) in `domain/PrintChoice.kt`; opslaan als één wijziging in `Repository.changeEntry`.
- Bans en reprints: `data/Watcher.kt` (stand in `filesDir/watch.json`, gewist bij fabrieksinstellingen), regels in `domain/Watch.kt`, melding in `PriceWorker.notifyWatch`. Legaliteit komt uit de prints, die de prijsupdate ververst.
- 4e: `domain/DeckFit.kt` en `AppViewModel.deckIssues()`; het controlescherm zet zulke kaarten in de groep Controleer.

**Bekende open vragen**
- Een heel blad leest minder dan in 0.9.6. Verdachten uit 0.9.7: onderste strook 3×, contrastpoging, gedeelde tekstherkenner. Nodig: scanrapport of voorbeeldfoto. Zo nodig die punten terugdraaien.
- Geeft ML Kit de ster ★ op echte foils terug? Alleen op de telefoon te zien.

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
- Scanner verbeteren (volgende stap): met de telefoonmeting varianten vergelijken (elk vak los lezen, meer pogingen op de voetstrook, hele foto vergroot). Nog niet gemeten; `ScanVariantsTest` staat klaar voor een kleinere run. De emulator is te traag voor meer dan één leesronde.
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
2. Splitkaarten (GRN rij 3): gedraaid lezen levert nog niets.
3. Gemengd blad zonder set.
4. Testset: "kaart als leeg gezien" apart vermelden; testset met losse kaartfoto's (één kaart per foto) om scannen per kaart te meten.

### 1e-movers. Volgende branch: Top movers + setlijst uit de offline database (Mick 18 nov, wacht op startsein)

**Ook in deze branch (Mick 18 nov):** scannerstap 1 uit 1d-scan: de setlijst uit de offline database halen (nieuwe query per set in `OfflineCards`, `CardCache.setList` eerst offline, dan Scryfall). Dan werken kiezen binnen de set, zoeken in andere regels en het raster op de namen ook zonder internet, met minder verzoeken aan Scryfall.

**Top movers:**

Op het Overzicht, op de plek van Winner of the Week, één samengevoegd blok "Top movers" (Mick 18 nov: samenvoegen). Bovenaan de grote kaart met plaatje, met duidelijk erboven wat het is: **Winner of the Week** bij Stijgers, **Loser of the Week** bij Dalers (bij Dag: Winner/Loser of the Day). Daaronder de schakelaars en de lijst met de nummers 2 t/m 5 (de grote kaart is nummer 1, niet dubbel). De widget Winner of the Week blijft zoals hij is.
- **Alleen de eigen collectie** (Mick): collectie en decks samen, proxies niet; elke print één keer, foil apart. Niet de hele markt (Scryfall heeft geen prijshistorie; dagelijks alle ~100.000 prijzen bewaren is te zwaar) en niet de verlanglijst.
- **Schakelaars:** Dag | Week (standaard Week: dagbewegingen zijn vaak ruis van een paar verkopen) en Stijgers | Dalers.
- **Rekenen:** Dag = tegen gisteren, Week = tegen 7 dagen geleden (prijshistorie in `price_history`, elke nacht gevuld). Kaarten zonder prijs van toen tellen niet. **Rangschikken op valuta (Mick):** verandering in € × aantal dat je hebt; het percentage staat er alleen bij.
- **Per regel:** plaatje, naam, set, ★ bij foil, oude → nieuwe prijs, verandering in € en %, aantal. Tik = kaartscherm. Grote kaart + 4 regels, "Meer" voor de top 20.
- **Privacymodus:** bedragen verborgen → alleen percentages en pijltjes (volgt "wat verbergen").
- **Magic-effecten op de grote kaart (Mick 18 nov: optie 3 + 4):**
  - Winner: oranje mythic-gloed rond de rand + gouden stempel "WINNER" die neerkomt (zoals de Legaal-stempel, `Stamp` in `ui/components/Effects.kt`).
  - Loser: olie "rood als bloed" die van onder de kaart in kruipt (techniek van Completed/`drawOil`, maar donkerrood) + stempel "DESTROYED". Voorstel Claude: de stempel niet ook rood (valt weg op het rood), maar donker/botwit; nog te bevestigen.
  - Speelt één keer bij openen of wisselen (~1,5 s), daarna stil. Magic-effecten uit → alleen een rustig groen of rood randje. Eigen tekenwerk, geen Wizards-materiaal. Eerst schetsen tonen.
- Winner of the Week = nummer 1 van de weekstijgers: nakijken of de huidige berekening (`computeWinner`) hetzelfde rekent (€ × aantal), zodat blok en widget niet verschillen. Loser of the Week is nieuw (nummer 1 van de dalers).
- Technisch: rekenwerk in `domain/` met unittest; gegevens uit `price_history` en de exemplaren; handleiding, Wat is nieuw, FEATURES.

### 2. Kleine verbeteringen (nog geen versie)

- Scannen, kaart voor kaart: 39a (set van de vorige kaart als aanname) is door Mick afgewezen, 17 nov: scannen wordt er trager van; een set telt alleen als de gebruiker hem zelf opgeeft. 39c groene kaarten overslaan op het controlescherm (pas bouwen als de herkenning betrouwbaar is; wacht op een test op de telefoon).
- Prijshistorie ophalen uit MTGJSON (tot 90 dagen terug): formaat en Cardmarket-waarde zijn nog niet nagekeken, en Mick heeft het nog niet bevestigd.

### 3. Nog na te kijken op Micks telefoon

Alleen wat Mick nog niet heeft bevestigd.

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
- Bladscan: een lege rij of kolom schuift de rest niet meer op (maten TEXT_COLS 2.35, TEXT_ROWS 2.86 en grenzen 1.4/2.1 zijn geschat); lege hoesjes met glans (grenzen 0.35 en 0.7 in `EmptySlots.decide` zijn geschat).
- Verzonnen setcode of taal valt weg; jaartal telt niet als kaartnummer; de set van het blad wint van één verlezen set.
- Foil-twijfel is blauw ★ (chips "★ Foil" en "Geen foil"); geeft ML Kit ★ op echte foils?
- Kaart voor kaart: +1 en kruisje, Opslaan als alles zeker is, Foil als sessiestand.
- Scannen vanuit een deck (menu Toevoegen → Scannen) en de keuze "uit de collectie halen" (alles / exact dezelfde / niets).
- Filters en sorteren bij deck, vriend en set; thumbnails bij suggesties; combo-blokken inklappen; segmentbalk Thumbnails | Namen.

**Ouder**
- 0.9.8: lampmelding (zie punt 30 in RELEASE_NOTES.md) nog niet nagekeken; een nieuw scanrapport met 0.9.9 of nieuwer is nodig om de geschatte maten te toetsen.
- 0.9.7: scanner met een blad met foils: leest ML Kit de ster ★ echt? (de hint werkt alleen als de tekstherkenning het teken ★ teruggeeft; zo niet, dan een andere aanpak, bijvoorbeeld op beeld).
- 0.9.7.1: bladscan leest minder dan in 0.9.6 bij een heel blad in beeld; oorzaak nog niet zeker. Verdachten uit 0.9.7: onderste strook 3× vergroot, contrastpoging, gedeelde tekstherkenner. Nodig: een voorbeeldfoto of scanrapport met de vakken die ontbreken. Zo nodig die punten terugdraaien naar 0.9.6.
- Lamp (zaklamp-knop): de oorzaak bij Mick was een lage batterij (de telefoon laat de lamp dan niet branden); de code is niet stuk.

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
