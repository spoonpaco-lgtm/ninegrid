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
- Vóór het voorstellen van nieuwe features eerst FEATURES.md in de repo nakijken of het er al in zit (16 nov: proefhand was voorgesteld maar bestond al).

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

## Nog te doen

Volgorde: eerst wat Mick bij de telefoontest nog open heeft, dan de volgende versie, dan kleine verbeteringen, dan fase 2 en ideeën. Alles wat gebouwd is staat in de tabel hierboven en in RELEASE_NOTES.md.

### 1. Volgende versie: 0.9.11 (naam nog kiezen)

Exporteren naar andere sites en apps (begin met de twee of drie sites die Mick echt gebruikt):
- Decks: ⋮ → Exporteren met keuze Moxfield, Archidekt, MTG Arena, Deckbox, ManaBox, TCGplayer, Cardmarket (wants-lijst) of Tekst (zoals nu); elk in het formaat dat die site het best leest (Commander/Sideboard goed gelabeld, foil/etched zoals de site verwacht, Arena zonder onbekende setnummers)
- Kopiëren + site openen: lijst naar het klembord en de importpagina van die site opent; delen en opslaan als bestand blijven
- Collectie: Meer → Back-up en export → CSV, ook in het eigen formaat van Moxfield, Archidekt, Deckbox, ManaBox of TCGplayer (juiste kolommen, conditie voluit zoals "Near Mint", taal voluit, foil/etched in eigen kolom), zodat uploaden zonder kolommen koppelen werkt
- Verlanglijst: als wants-lijst voor Cardmarket (en de andere sites waar dat kan)
- Wat exporteren: alles, alleen losse kaarten, een binder of een set (zelfde keuze als het deelscherm); proxies standaard niet mee
- Vóór het bouwen de actuele importformaten van elke site nakijken, niet uit het hoofd

Ook in 0.9.11:
- Meldingstoestemming (Android 13+) wordt nu alleen bij importeren gevraagd; ook vragen bij het aanzetten van bans en reprints.

Scanner: testset en beter rapport (voorstel, wacht op startsein; Mick 17 nov: in 0.9.11):

- Doel: de scanner meetbaar beter maken en oude fouten nooit laten terugkomen.
- **Scanrapport met de eerste gok:** de app onthoudt direct na de scan wat hij zelf van elk vak maakte. Bij Scan delen staat per vak "app dacht: X" en "jij maakte er: Y" (alleen als die verschillen). Nu staat de uitkomst pas bij het delen en toont dan de door Mick gecorrigeerde kaart; de eerste gok van de app gaat verloren.
- **Testset met foto's (ongeveer 20):** foto's in de repo, elk met een klein bestand met de juiste kaarten per vak ("jij maakte er" uit het rapport). Basis: de vier rapporten van 11 nov.
- **Draaien op GitHub:** een aparte workflow met een emulator laat de echte scanner (ML Kit zit in de app, `text-recognition:16.0.1`) alle foto's lezen en toont per foto en vak wat goed of fout is. Duurt ongeveer 10 tot 15 minuten en de herkenning op een emulator kan afwijken van Micks Samsung.
- **Draaien op de telefoon:** Ontwikkelaar → Testset draaien: de app leest de foto's uit een map op de telefoon en toont goed/fout per foto. Dit is de echte meting.
- **Waar:** de map `testset/` in de repo (Mick, 17 nov: in de repo, zodat ze altijd bij de hand zijn). Begin: 4 bladen van 11 nov, elk in een eigen map met `foto.jpg` en `scanrapport.txt` (zie `testset/README.md`). Per map moet nog een `uitslag.txt` komen met de juiste kaart per vak. De repo is publiek en kaartfoto's zijn Wizards-materiaal: alleen foto's van eigen bladen. Nieuwe bladen: foto en rapport in een nieuwe map `NN-set-kaart`. De foto's zijn verkleinde versies; originelen zijn beter.

Crashrapport verzenden (nieuw; Mick 17 nov: in 0.9.11):
- Stort de app in, dan bewaart hij een klein rapport op de telefoon: versie, toestel, Android-versie, de foutmelding met de regels code, en het scherm waar het gebeurde. Geen kaarten, namen of andere gegevens van de gebruiker.
- Bij de eerstvolgende start vraagt de app: "NineGrid is gestopt. Rapport delen?" met Delen (Android-deelmenu, net als Scan delen) en Niet nu. Er wordt niets automatisch verstuurd.
- Ook te vinden onder Ontwikkelaar → Laatste crashrapport delen.
- Zo kan Mick een crash zonder uitleg doorsturen en vindt de oorzaak snel.

Deck delen via QR-code (was Route D in Fase 2; Mick 17 nov: in 0.9.11):
- Deck → ⋮ → Delen via QR: op het scherm een QR-code met de decklijst. De ontvanger opent in NineGrid de scanner, richt de camera op het scherm en krijgt de lijst in hetzelfde controlescherm als bij Lijst plakken; hij kiest zelf wat hij toevoegt.
- Compact: per kaart aantal, set en kaartnummer (bijvoorbeeld `1 MH2 123`), Commander en Sideboard gemarkeerd; de namen haalt de ontvanger zelf op uit de offline database of bij Scryfall. Een QR-code houdt ongeveer 2.900 bytes; een Commander-deck van 100 kaarten is ongeveer 1 KB, ingepakt minder. Een heel groot deck kan over twee of drie codes na elkaar.
- Zonder internet, zonder account en zonder bestand dat je moet appen; handig als je iemand naast je hebt.
- Kost: een extra onderdeel om QR-codes te lezen (ML Kit barcode-scanning, ongeveer 2 à 3 MB). Alleen NineGrid begrijpt de code; gewone tekst in de QR zou elke QR-lezer kunnen tonen, maar maakt de code groter en lastiger te scannen (nog te kiezen).
- Geen koppeling met Moxfield of Archidekt; dat is Exporteren.

### 1b. Volgende versie na 0.9.11 (Mick 17 nov: "in de volgende versie")

Verlanglijst:
- Een print minder (−1) en snel verwijderen (swipe) met ongedaan maken. Nu kan alleen via ✎ → Verwijderen (hele regel) of het aantal verlagen.
- Verlanglijstregels per print: dezelfde kaart meerdere keren op de lijst, elk met eigen aantal en prijs (nu één print of "elke print" per regel, dus 2 van print A en 1 van print B kan niet).
- De regel toont altijd welke print hij vertoont: plaatje, set, nummer en de prijs van díe print (bij "elke print" duidelijk "vanaf"). Mick vindt dat dit na een printwijziging niet goed gaat; eerst uitzoeken wat hij precies ziet.

Scanrapport:
- Ook de instellingen en omstandigheden van de scan in het rapport: ingevulde set (of leeg), flits aan of uit, automatisch afdrukken of zelf, AI-hulp aan, offline database aan, taalinstelling, enkel blad of één kaart. Nu staan er alleen versie, toestel, soort (blad of één kaart), camera of galerij, scherpte, dekking, leestijd, opnieuw gelezen en fotoformaat in.

### 2. Kleine verbeteringen (nog geen versie)

- Taartgrafiek: de gekozen weergave (Lijn of Taart) en splitsing onthouden na het sluiten van de app.
- Scannen, kaart voor kaart: 39a de set van de vorige kaart als aanname; 39c groene kaarten overslaan op het controlescherm (pas bouwen als de herkenning betrouwbaar is; wacht op een test op de telefoon).
- Scannen naar een deck: één keuze Deck / Sideboard / Commander voor alle kaarten tegelijk.
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
- Sheet-koppeling (deel 2 punt 2, optie A: nieuwe Sheet via drive.file; Google Cloud-project + privacypagina nodig)

## Later (idee, geen versie)
- Verkoopalarm voor kaarten die je hebt: "meld als Ragavan boven € 60 komt" (omgekeerde prijsalarm, lijkt op Breakout)
- Jaaroverzicht in december: "Jouw jaar in Magic" (kaarten toegevoegd, duurste vondst, meest gescande set, groei in waarde, beste ruil), te delen als afbeelding, privacymodus respecteren
- Widget op het startscherm: waarde collectie en Winner of the Week in logostijl; bedekt als de privacymodus aan staat
- Supporter-variant: app blijft helemaal gratis (alle kaartgegevens, prijzen, zoeken, ruilmatch, offline database: nooit achter betaling — Scryfall verbiedt paywall, Wizards Fan Content Policy verbiedt betaalde toegang); vrijwillige steun via Google Play Billing (15% kosten) met extra's zonder kaartgegevens of Magic-materiaal: extra thema's/kleuren, andere app-iconen in NineGrid-stijl, supporter-badge bij Over
  - Phyrexisch lettertype nooit als betaald extra (materiaal van Wizards)
  - Betaalde Sheet-synchronisatie: eerst schriftelijk navragen bij Scryfall
  - Voor echt verkopen: vooraf toestemming Scryfall en eventueel licentie Wizards; inkomsten via eigen bedrijf (btw → boekhouder)
