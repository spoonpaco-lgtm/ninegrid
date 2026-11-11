# NineGrid – Roadmap en de lijst

Dit is "de lijst": wat Mick wil dat NineGrid nog krijgt. Een nieuw gesprek begint hier.

## Werkwijze

- Communicatie met Mick in het Nederlands; de app is Engels/Nederlands.
- **Niets bouwen zonder startsein.** Voorstellen gaan eerst op de lijst; bouwen pas als Mick "start" of "begin maar" zegt.
- Denk mee en spreek tegen als iets beter kan; geen ja-knikker.
- Elke versie krijgt een passende planeswalker-naam. 0.x zijn testversies; 1.0 = fase 2. **Saheeli** is gereserveerd.
- Bouwen gebeurt alleen in GitHub Actions. Een release ontstaat als `versionNameValue` in `app/build.gradle.kts` verandert op `main`.
- Testen zonder release: werk op een branch en start de workflow Build met de hand op die branch (`workflow_dispatch`); pas als die slaagt naar `main`.
- Keystore, wachtwoorden en de AI-sleutel blijven buiten de repo (GitHub secrets / alleen op de telefoon).
- Bij elke versie: handleiding (NL/EN), Wat is nieuw, FEATURES.md en FEATURES.nl.md, RELEASE_NOTES.md bijwerken.
- Tellen op afbeeldingen: vak voor vak / kwadrant voor kwadrant.

## Hoe te werken (technisch, voor een nieuw gesprek)

**Repo en bouwen**
- Repo: `spoonpaco-lgtm/ninegrid`. Haal hem binnen met `add_repo` (toegang: push) en kloon hem; registreer de map met `register_repo_root`.
- Lokaal kan niets compileren (geen toegang tot Maven/Google). Alles wordt gebouwd in GitHub Actions (`.github/workflows/build.yml`: `testReleaseUnitTest assembleRelease`).
- Werkwijze per versie: nieuwe branch, wijzigen, committen, pushen, workflow met de hand starten op die branch (`gh workflow run build.yml --ref <branch>`), wachten met `gh run watch <id> --exit-status`, fouten lezen met `gh run view <id> --log-failed`. Pas als dat groen is naar `main` mergen. Een release ontstaat vanzelf als `versionNameValue` in `app/build.gradle.kts` op `main` nieuw is; daarna met `gh api repos/spoonpaco-lgtm/ninegrid/releases/tags/vX.Y.Z` controleren of de APK erbij staat.
- `gh` GraphQL is geblokkeerd: gebruik `gh api repos/...`, `gh run list/view/watch`; `gh release view` werkt dus niet.
- Opmaakwijzigingen (zoals 0.9.8.2) eerst als testbuild naar Mick, pas na zijn "merge" naar main.
- Commit-afsluiting: `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>` en de `Claude-Session`-regel van het gesprek.

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

### 0.9.8 – nog na te kijken op Micks telefoon

- Vegen op het kaartscherm (niet in vrienden- en ruillijsten, niet bij zoeken/scan).
- Label NIEUW: alleen losse kaarten; ook als een stapel groter wordt.
- Commander wijzigen: de oude commander die terug naar de collectie gaat wordt los (oude plek niet bewaard); creature-type als deckregel verandert niet vanzelf (alleen een melding).
- Fabrieksinstellingen: app start daarna opnieuw bij de introductie; terugzetten via Meer → Back-up (niet in de introductie zelf).
- Combokaartjes: plaatjes van kaarten die je niet hebt komen via Scryfall op naam.
- Lampmelding en scanrapport (Ontwikkelaar-menu).
- Nog open: foil-ster ★ bij echte foils; bladscan met 0.9.7.1; bladscan leest minder dan 0.9.6 (voorbeeldfoto/scanrapport nodig).

### 0.9.7 – nog na te kijken op Micks telefoon

Gebouwd en getest met unittests; dit kan alleen op een echte telefoon gecontroleerd worden:

- **Scanner (25)**: een paar bladen scannen, ook een met foils. Leest ML Kit de ster ★ echt? (De hint werkt alleen als de tekstherkenning het teken ★ teruggeeft; zo niet, dan verandert er niets en moet er een andere aanpak komen, bijvoorbeeld op beeld.)
- ✓ **Bladweergave (21)**: nagekeken door Mick, klaar (past zonder scrollen, met gebaren én met drie knoppen).
- ✓ **Melding (23)**: nagekeken door Mick, klaar (Ongedaan maken staat overal boven de navigatiebalk).
- ✓ **Icoon (20)**: nagekeken door Mick, klaar (het logo past in het rondje).
- ✓ **Deckimport (22)**: nagekeken door Mick, werkt (verschillende Island-prints komen goed binnen).
- ✓ **Back-up (14)**: nagekeken door Mick, klaar (volledige back-up maken en terugzetten werkt).

### 0.9.7.1 – nog open na de reparatie

- **Bladscan leest minder dan in 0.9.6** bij een heel blad in beeld: oorzaak nog niet zeker. Verdachten uit 0.9.7: onderste strook 3× vergroot, contrastpoging, gedeelde tekstherkenner. Nodig: een voorbeeldfoto van zo'n blad met de vakken die ontbreken. Als de 0.9.7.1-reparatie niet genoeg blijkt, die punten terugdraaien naar 0.9.6.
- **Lamp (zaklamp-knop)**: oorzaak gevonden door Mick: batterij te laag (de telefoon laat de lamp dan niet branden). Het icoon veranderde wel. De code is dus niet stuk; er komt een melding, zie punt 30.

### Wat anders uitpakte dan op de lijst stond

- **15b/c Prijsbeweging** bestond al als de knop "Koers" (dagstanden bewaren het koersverschil sinds 0.8.9). De knop heet nu Prijsbeweging; er is niets nieuws voor gebouwd.
- **17b Filter Game Changers**: de offline database kent geen Game Changers. In de collectie en op de verlanglijst werkt het filter met wat er per kaart bewaard is; bij Alle kaarten zoekt het bij Scryfall (internet nodig).
- **13d Manakosten in het setscherm**: bij kaarten die je niet hebt pas zichtbaar nadat de setlijst is ververst.
- **14j Decks na terugzetten**: opgelost volgens Mick (decks komen terug met de nieuwe back-up uit 0.9.7). Wat er eerder misging is nooit gevonden; er is wel een test dat een back-upbestand met decks heel terugkomt.

## Versieplan

| Versie | Naam | Inhoud |
|---|---|---|
| 0.9.8 ✔ | Kaito (uitgebracht) | 27 Ontwikkelaar-menu met scanrapport, 26 vegen, 16 NIEUW, 19 commander wijzigen, 2 fabrieksinstellingen, 30 melding bij lamp die niet aangaat, 31 kaartjes bij combo's |
| 0.9.8.1 | Kaito | 32 opgegeven set is een grens (oranje met andere set) |
| 0.9.8.2 | Kaito | 29 Vormgeving gelijk trekken (alleen opmaak) |
| 0.9.9 | nog kiezen | 4 scannen naar een deck (pas als de bladscan weer goed leest), 28 deckimport: één keuze voor uit de collectie halen, 33 filter- en sorteerknoppen bij alle kaartenlijsten, 34 kaartjes bij suggesties en tikken op elke combokaart, 35 inklapbare blokken bij combo's, 36 weergavekeuze Thumbnails | Namen als segmentbalk |
| 0.9.10 | nog kiezen | 8 print kiezen, in twee stappen: eerst 8a–e (printlijst), daarna 8f–l (exemplaren wijzigen en splitsen) |
| 0.9.11 | nog kiezen | 3 exporteren (sites nog te kiezen; eerst de actuele importformaten nakijken) |
| 1.0 | — | Fase 2 |

De nummers hieronder zijn die van de lijst zoals Mick ze kent.

## Nieuwe features (zie Versieplan voor de volgorde)

2. Fabrieksinstellingen (Instellingen → Wissen):
   a. Geen tweede knop naast Wissen, maar in Wissen een keuze: alleen collectie, of alles (fabrieksinstellingen); rode knop
   b. Wist alles: collectie, decks, verlanglijst en prijsalarmen, boosters, gedeelde collecties van vrienden, historie, wijzigingslog, alle instellingen, AI-sleutel, deckregels, wachtende scans, offline kaartdatabase en afbeeldingencache
   c. Daarna start de app opnieuw met de introductie, zoals na een eerste installatie
   d. Eerst een back-up maken, net als bij Wissen (standaard aan); ongedaan maken kan hierna niet
   e. Bevestigen door WISSEN te typen; met privacymodus aan ook vingerafdruk
   f. Vinkje "Kaartdatabase bewaren" (standaard uit), zodat je schoon begint zonder opnieuw te downloaden
   g. In de introductie na het wissen ook "Terugzetten uit back-up"
3. Exporteren naar andere sites en apps (eigen versie 0.9.11; begin met de twee of drie sites die Mick echt gebruikt):
   a. Decks: ⋮ → Exporteren met keuze Moxfield, Archidekt, MTG Arena, Deckbox, ManaBox, TCGplayer, Cardmarket (wants-lijst) of Tekst (zoals nu); elk in het formaat dat die site het best leest (Commander/Sideboard goed gelabeld, foil/etched zoals de site verwacht, Arena zonder onbekende setnummers)
   b. Kopiëren + site openen: lijst naar het klembord en de importpagina van die site opent; delen en opslaan als bestand blijven
   c. Collectie: Meer → Exporteren als CSV in het eigen formaat van Moxfield, Archidekt, Deckbox, ManaBox of TCGplayer (juiste kolommen, conditie voluit zoals "Near Mint", taal voluit, foil/etched in eigen kolom), zodat uploaden zonder kolommen koppelen werkt
   d. Verlanglijst: als wants-lijst voor Cardmarket (en de andere sites waar dat kan)
   e. Wat exporteren: alles, alleen losse kaarten, een binder of een set (zelfde keuze als het deelscherm); proxies standaard niet mee
   f. Vóór het bouwen de actuele importformaten van elke site nakijken, niet uit het hoofd
4. Scannen rechtstreeks naar een deck:
   a. In een deck: + Toevoegen ▾ → Scannen; de scanner toont bovenin "Naar deck: <naam>" (in plaats van binder/blad); start in 1 kaart, Blad kan ook
   b. Na Controleer: het controlescherm met het deck al als bestemming, bovenaan keuze Deck / Sideboard / Commander
   c. Per kaart kijkt de app in je collectie: los in je collectie → verplaatsen naar het deck (standaard), met "Uit collectie · Rood blad 3 vak 5"; zit al in een ander deck → keuze verplaatsen of nieuw; heb je nog niet → nieuw, direct in het deck
   d. Per kaart nog te wijzigen (verplaatsen of nieuw), en als proxy te markeren
   e. Kaarten buiten de kleuren van de commander of banned meteen oranje in het controlescherm
   f. Na opslaan terug naar het deck; de scanner vergeet het deck, zodat de volgende gewone scan weer naar de collectie gaat
   g. Ongedaan maken werkt zoals altijd; verplaatste kaarten gaan terug naar hun oude plek
   h. Gebruikt de regels van 0.9.7 (domain/DeckTake): basic lands alleen precies dezelfde print
8. Print kiezen:
   a. Verlanglijst → Bewerken: onder "precies deze print" een regel "SLD 1798 · Secret Lair Drop ›"; tik = lijst met alle prints van die kaart
   b. Per print: klein plaatje, set, nummer en jaar, normale en foilprijs, "goedkoopst" bij de goedkoopste, ✓ bij de huidige
   c. Tik = die print kiezen; prijs en prijsalarm op de verlanglijst rekenen daarna met die print
   d. Sorteren: nieuwste eerst of goedkoopste eerst
   e. Kaartscherm: "Andere prints ›" onder de setregel, met dezelfde lijst; overstappen naar een andere print en die aan collectie of verlanglijst toevoegen
   f. Print van een exemplaar wijzigen: ✎ bij Jouw exemplaren → bovenaan "Print: SLD 1798 · Secret Lair Drop ›" → dezelfde printlijst; het exemplaar verhuist naar die print en houdt plek (binder/blad/vak of deck), foil, taal, conditie en proxy
   g. Bij meer exemplaren (×3) kiezen hoeveel er mee verhuizen (alles of bijv. 1)
   h. Foil die niet bestaat bij de nieuwe print: melding en voorstel Normaal (of etched als dat bestaat); zelfde print op dezelfde plek → samenvoegen tot één stapel
   i. Daarna springt het kaartscherm naar de nieuwe print; ongedaan maken werkt zoals altijd
   j. "+ Exemplaar toevoegen" altijd zichtbaar op het kaartscherm, met keuze Normaal | Foil | Etched (alleen wat voor die print bestaat) en meteen de plek; een foil krijgt een eigen regel met de foilprijs
   k. Afwerking voor een deel van een stapel wijzigen: ✎ → Foil bij ×3 vraagt hoeveel (bijv. 1 foil, 2 blijven normaal, elk een eigen regel)
   l. In de printlijst per print welke versies er zijn (normaal, foil, etched) met hun prijzen
16. Label NIEUW voor toegevoegde kaarten:
   a. Nieuw toegevoegde kaarten krijgen een klein label NIEUW in de collectie, in de bladweergave en op het kaartscherm
   b. Instellingen: hoelang het label blijft: 1 dag, 3 dagen, 7 dagen (standaard), 14 dagen, 30 dagen of uit
   c. Filter "Nieuw" en sortering "Nieuwste eerst"
   d. Wel nieuw: scannen, handmatig toevoegen, een lijst of Sheet importeren, en een extra exemplaar van een kaart die je al had (alleen dat exemplaar krijgt het label)
   e. Niet nieuw: een kaart van de collectie naar een deck verplaatsen (of terug), en een back-up terugzetten
   f. Knop "Alles gezien" haalt het label meteen weg
19. Commander wijzigen in een Commander-deck:
   a. In het deck: ⋮ → "Commander wijzigen", en op het kaartscherm van een kaart in het deck "Maak commander"
   b. Nieuwe commander kiezen uit de kaarten in het deck, uit je collectie of via zoeken/scannen; alleen kaarten die commander mogen zijn
   c. Wat met de oude commander: keuze "Naar het deck" (standaard) of "Terug naar collectie" (naar zijn oude plek)
   d. Twee commanders waar dat mag (Partner, Friends forever, Choose a Background, Doctor's companion): tweede commander toevoegen of vervangen
   e. Na het wisselen: kleuren van het deck opnieuw bepaald, kaarten buiten de nieuwe kleuren oranje, bracket en deckregels opnieuw berekend; is er een tribe ingesteld, dan vraagt de app of die mee moet veranderen
   f. Ongedaan maken werkt zoals altijd

26. Vegen op het kaartscherm (nieuw, 0.9.8):
   a. Op het kaartscherm links/rechts vegen naar de volgende/vorige kaart
   b. Volgorde = de lijst waaruit je kwam: collectie of Alle kaarten (met filter en sortering), setscherm (binnen Ontbreekt of Heb ik), deck, verlanglijst, vriend; bij zoeken en scannen geen lijst, dus geen vegen
   c. Boven in beeld de plek, bijvoorbeeld "12 van 280"; aan begin of einde gebeurt niets
   d. Past op bestaande horizontale gebaren op het kaartscherm (afbeelding, printlijst); testen op de telefoon

27. Ontwikkelaar-menu (nieuw, 0.9.8):
   a. Onderaan Instellingen een onderdeel "Ontwikkelaar"; de bestaande instelling "Duur van de herkenning tonen" verhuist erheen
   b. Scanrapport: na een scan (op het controlescherm) een knop "Scan delen" met de foto en per vak wat gelezen is (setcode, nummer, taal, naam, losse regels, leeg ja/nee, welke leesronde), zodat fouten zonder rondvragen te vinden zijn
   c. Delen via het deelmenu van Android; geen automatisch versturen

28. Deckimport: één keuze voor "uit de collectie halen" (nieuw, 0.9.9; gekozen door Mick: voorstel 1):
   a. Alleen bij een deck, bovenaan het controlescherm, een rij met drie knoppen: "Alles wat ik heb" (standaard, zoals nu), "Alleen exact dezelfde print" en "Niets"
   b. Eén tik zet alle vinkjes "Uit collectie halen" tegelijk; daaronder een samenvatting, bijvoorbeeld "62 uit je collectie · 9 andere print · 29 nieuw"
   c. Het vinkje per kaart blijft bestaan voor uitzonderingen
   d. Niet gekozen (kan later): filter op uitzonderingen (Andere print, Tekort, Niet gevonden); standaard voor "andere print" instelbaar en onthouden
   e. Past op hetzelfde scherm als 4 (scannen naar een deck), vandaar dezelfde versie

29. Vormgeving gelijk trekken over de hele app (nieuw, eigen opmaakversie na 0.9.8):
   a. Gevonden in de code (nov 2026): titelbalk vet op 7 schermen en gewoon op 6 (Controleren, Importeren, Plakken, Kaart kiezen, Boosters, Sets, Kaartscherm); 13 verschillende hoekafrondingen (2–24 dp); lettergroottes los ingesteld (9–30 sp, vooral 12 en 13); kaartvlakken deels met de eigen kaartkleur (cardSurface: SectionCard, Boosters, Delen) en deels de themastandaard; schermmarge 16 dp op 12 schermen, 12 dp op 5; onderruimte van lijsten 16, 24, 32, 40 of 96 dp
   b. Doel: overal titel vet (naam van set of deck als tweede regel), vier hoekmaten (klein 6, normaal 12, groot 16, rond voor knoppen en labels), vijf lettermaten (11 bijschrift, 13 tekst, 14 benadrukt, 16 kop, 24 groot), één kaartkleur, één schermmarge (16 dp)
   c. Eerst de maten als vaste bouwstenen in het thema vastleggen, dan de schermen omzetten
   d. Niet aanpassen: bladweergave en scanner (eigen maten door de rasters)
   e. Alleen opmaak; testen met een testbuild die Mick op zijn telefoon bekijkt, per scherm, vóór de release
   f. Het controle- en deckimportscherm (28) wordt in 0.9.9 al in de nieuwe stijl gebouwd

30. Melding als de lamp niet aangaat (nieuw, 0.9.8; oorzaak bij Mick was een lage batterij):
   a. Gaat de lamp na tikken niet aan (de camera meldt geen "aan", of enableTorch faalt), dan een korte melding in plaats van stil niets doen
   b. Is de batterij laag of staat batterijbesparing aan, dan zegt de melding dat: bijvoorbeeld "De lamp gaat niet aan: batterij 8%. De telefoon blokkeert de lamp bij een lage batterij." Anders een algemene melding ("De lamp kon niet aan; sluit andere apps die de camera gebruiken")
   c. Het icoon springt terug naar uit als de lamp niet aan wil
   d. Bij het openen van de scanner met lage batterij kan de knop alvast een waarschuwingskleur krijgen (nog te beslissen)
   e. De drempel per telefoon verschilt en is niet op te vragen; de melding rekent daarom op wat de camera meldt, niet op een vast percentage

31. Kaartjes bij combo's (nieuw, 0.9.8):
   a. Deck → tabblad Combo's: per combo een rij kleine kaartplaatjes met een + ertussen, in plaats van alleen namen in vakjes
   b. Tik = kaartscherm, vasthouden = kaart groot; wat de combo oplevert (blauwe labels) en de uitklapbare voorwaarden blijven eronder
   c. Bijna-combo's: de ontbrekende kaart gedimd met een amberkleurige gestippelde rand; de knoppen (In collectie, Bij vriend, Op verlanglijst) blijven
   d. Twee kaarten passen naast elkaar; bij drie of meer schuift de rij zijwaarts zodat de plaatjes niet te klein worden
   e. Bovenaan het tabblad keuze Kaartjes | Namen, onthouden; standaard Kaartjes
   f. Plaatjes: Spellbook geeft alleen namen. Kaarten in het deck gebruiken Micks print; de ontbrekende kaart wordt via Scryfall opgezocht. Pas laden als ze in beeld komen, kleine afmeting, bestaande plaatjescache; dubbelzijdige kaarten tonen de voorkant

32. Opgegeven set is een grens, geen voorkeur (gebouwd in 0.9.8.1; vereenvoudigd: de zoekroute blijft, maar elke kaart uit een andere set of zonder nummer met meerdere prints is oranje; keuze Mick: oranje met de andere set):
   a. Heeft Mick op het scanscherm een set ingevuld, dan zoekt de app alleen in die set: `cardByName` valt niet meer terug op een zoekopdracht zonder set, en `betterPrint` neemt geen print met hetzelfde nummer uit een andere set
   b. Staat de kaart niet in die set, maar wel in een andere, dan komt hij binnen als oranje met de andere set erbij ("niet in MH2, wel in MH1"); Mick kan de set accepteren of de kaart zelf zoeken
   c. Alleen gelezen of van het blad afgeleide set: buiten de set zoeken mag nog, maar zo'n kaart is altijd oranje met de gevonden set
   d. Meerdere prints met dezelfde naam in de set (gewoon, borderless, showcase) en geen nummer gelezen: niet de eerste gewone print nemen maar oranje met de kaartfoto-vergelijking
   e. Test: forced set + kaart die er niet in staat geeft nooit een stille print uit een andere set

33. Filter- en sorteerknoppen bij alle lijsten met kaarten (nieuw, 0.9.9; voorstel Mick, 11 nov):
   a. Nu al op de gedeelde zoekbalk (FilterSearchBar): Collectie (filter + sorteren), Alle kaarten (filter), Decks en Sets (sorteren), Kies uit collectie (filter), Setscherm (alleen filter op rarity)
   b. Erbij: kaartenscherm van een vriend (Delen), met dezelfde filters als de collectie en sorteren op set, naam, prijs
   c. Erbij: setscherm krijgt sorteren (nummer, naam, prijs, rarity)
   d. Erbij: deck-detail krijgt filteren (kleur, type, rarity) naast het bestaande sorteren
   e. Bewust niet: kaartzoeker in dialogen (Kaart zoeken, Commander kiezen; zoekopdracht bij Scryfall, max tien treffers), setkiezer bij vrienden en lijnenkiezer in de grafiek (korte keuzelijsten)
   f. Zelfde bouwsteen, andere opties; filterkeuze per scherm onthouden zoals nu bij Collectie

34. Kaartjes bij suggesties, en combo's gelijk trekken (nieuw, 0.9.9; Mick, 11 nov):
   a. Deck → tabblad Suggesties (toevoegen, landen, schrappen, vrienden): per kaart een klein kaartplaatje, zoals de combo-kaartjes
   b. Tik op het plaatje opent het kaartscherm; vasthouden toont de kaart groot. Dezelfde bouwsteen als bij combo's (ComboRow/comboImageUrl in DecksScreen), zodat het overal hetzelfde werkt
   c. Combo's: nu opent tikken alleen een kaart die je zelf hebt (deck of collectie); een kaart die je niet hebt (de ontbrekende) doet niets. Dat moet ook werken: kaartscherm via Scryfall op naam, zoals bij zoeken
   d. Keuze Kaartjes | Namen geldt ook voor suggesties (dezelfde onthouden instelling)
   e. Plaatjes lui laden, kleine afmeting, bestaande cache; dubbelzijdige kaarten tonen de voorkant

35. Inklapbare blokken op het Combo's-tabblad (nieuw, 0.9.9; Mick, 11 nov):
   a. "Combo's in dit deck" en "Bijna" (almost) krijgen een kopje dat in- en uitklapt, zoals de blokken bij Suggesties al doen (TabCard met `closed`/`onToggle`)
   b. De aantallen blijven in het kopje zichtbaar als het blok dicht is
   c. Onthouden per deck, zoals bij Suggesties; standaard open
   d. Hoort bij 34: dezelfde kaartjes en hetzelfde gedrag op beide tabbladen

36. Weergavekeuze bij Combo's en Suggesties: één segmentbalk (nieuw, 0.9.9; Mick, 11 nov; voorstel A):
   a. De twee losse chips Cards | Names worden één afgeronde segmentbalk over de volle breedte, zoals Collectie | Alle kaarten | Verlanglijst: **▦ Thumbnails | ☰ Namen**
   b. Het woord is "Thumbnails" (Mick, 11 nov), in NL en EN, en overal in de app waar nu "kaartjes" staat voor de kleine plaatjes (combo's, suggesties, handleiding, Wat is nieuw)
   c. Bovenaan het tabblad, op dezelfde plek en met dezelfde marge en afronding op Combo's én Suggesties; de keuze wordt onthouden (nu al: `combo_cards` in deck_ui)

37. Scanner repareren na vier scanrapporten van 11 nov (nieuw, 0.9.9.0 of eigen patch; voorrang boven 4 en 28):
   Gevonden in de rapporten (SM-A165F, galerijfoto's 1530x2040, ONE en WOT):
   a. **Raster uit de tekst (galerijfoto's)**: zonder kader maakt `ScanParser.frameOf` het raster van de omhullende van alle gelezen tekst. Is een rij of kolom leeg, dan wordt het raster uitgerekt en komen de naam en de voet van één kaart in verschillende vakken terecht (foto met één kaart in rij 1 en drie in rij 2: namen in vak 4-6, rest in vak 7-9). Oplossing: raster uit de kaartmaten (kolomafstand × 1,4 voor de rijafstand, verankerd op de voetregels "###/### R" die elke kaart heeft), niet uit de tekstrand
   b. **Verzonnen setcode en taal uit de voetregel**: "ONE•EN" wordt gelezen als EATUR ES, EXCE PT, INSE TW, CAK ES, MOTEN. Een taalcode die niet EN is stuurt de kaart het buitenlandse pad in (set + nummer + gedrukte naam), en dat mislukt altijd: Kinzu, Bilious Skulldweller, Mondrak en Raid Bombardment kwamen daardoor "niet gevonden" terwijl de Engelse naam goed gelezen was. Oplossing: setcode alleen accepteren als hij in de setlijst staat (`vm.setInfo`); taal alleen als de gelezen naam geen exacte Engelse kaartnaam is; zonder gedrukte naam altijd Engels
   c. **Jaartal als nummer**: "© 2023 Wizards" levert nummer 2023 (Polluted Bonds, Stab Wound → RVR 93). Oplossing: 2019 t/m 2026 vlak bij ©/Wizards negeren, en een nummer dat niet in de setlijst voorkomt behandelen als niet gelezen (dan beslist de naam)
   d. **Set van het blad wint niet van een verlezen set**: Infested Fleshcutter las TOK op een ONE-blad. Oplossing: komt de gelezen set niet overeen met de overheersende set van het blad en klopt naam + nummer in de overheersende set, dan die set
   e. **Lege vakken herkend als kaart** (rij 3 en rij 1 met gestreepte, glimmende hoesjes): de drempel van `EmptySlots` ligt te laag voor dit patroon. Oplossing: drempel per foto uit het verschil tussen vakken met leesbare kaart en de rest
   f. Hoe de kaarten uit de rapporten er nu voor staan: foto 1 (0.9.8) 6 van 9 goed, foto 3 6 van 8, foto 4 5 van 8 goed (+3 oranje), foto 2 (rij leeg) 3 van 4 goed maar in de verkeerde vakken
   g0. Mick vult bij het scannen meestal geen set in (11 nov): de app moet de overheersende set van het blad zelf gebruiken (d); een ingevulde set blijft altijd leidend. Tot de reparatie: set invullen helpt tegen verlezen sets, maar niet tegen de verzonnen taalcode (b); galerijfoto's van een blad met een lege rij of kolom geven vakken in de verkeerde rij (a), dus die liever met de camera scannen
   g. Na de reparatie dezelfde vier foto's als test: de regels als unittests met de gelezen regels uit de rapporten (zonder telefoon te draaien)

## Fase 2 (wacht op startsein) → wordt 1.0
- Sheet-koppeling (deel 2 punt 2, optie A: nieuwe Sheet via drive.file; Google Cloud-project + privacypagina nodig)
- Route D: deck delen via QR-code (scherm scannen → decklijst)

## Later (idee, geen versie)
- Verkoopalarm voor kaarten die je hebt: "meld als Ragavan boven € 60 komt" (omgekeerde prijsalarm, lijkt op Breakout)
- Jaaroverzicht in december: "Jouw jaar in Magic" (kaarten toegevoegd, duurste vondst, meest gescande set, groei in waarde, beste ruil), te delen als afbeelding, privacymodus respecteren
- Widget op het startscherm: waarde collectie en Winner of the Week in logostijl; bedekt als de privacymodus aan staat
- Supporter-variant: app blijft helemaal gratis (alle kaartgegevens, prijzen, zoeken, ruilmatch, offline database: nooit achter betaling — Scryfall verbiedt paywall, Wizards Fan Content Policy verbiedt betaalde toegang); vrijwillige steun via Google Play Billing (15% kosten) met extra's zonder kaartgegevens of Magic-materiaal: extra thema's/kleuren, andere app-iconen in NineGrid-stijl, supporter-badge bij Over
  - Phyrexisch lettertype nooit als betaald extra (materiaal van Wizards)
  - Betaalde Sheet-synchronisatie: eerst schriftelijk navragen bij Scryfall
  - Voor echt verkopen: vooraf toestemming Scryfall en eventueel licentie Wizards; inkomsten via eigen bedrijf (btw → boekhouder)
