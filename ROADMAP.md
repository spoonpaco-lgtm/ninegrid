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
| 0.9.8 | nog kiezen (voorstel: Kaito) | 27 Ontwikkelaar-menu met scanrapport, 26 vegen, 16 NIEUW, 19 commander wijzigen, 2 fabrieksinstellingen, 30 melding bij lamp die niet aangaat |
| 0.9.8.1 | nog kiezen (patchnaam) | 29 Vormgeving gelijk trekken (alleen opmaak, geen nieuwe functies) |
| 0.9.9 | nog kiezen | 4 scannen naar een deck (pas als de bladscan weer goed leest), 28 deckimport: één keuze voor uit de collectie halen |
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
