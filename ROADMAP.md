# NineGrid – Roadmap en de lijst

Dit is "de lijst": wat Mick wil dat NineGrid nog krijgt. Een nieuw gesprek begint hier.

## Werkwijze

- Communicatie met Mick in het Nederlands; de app is Engels/Nederlands.
- **Niets bouwen zonder startsein.** Voorstellen gaan eerst op de lijst; bouwen pas als Mick "start" of "begin maar" zegt.
- Denk mee en spreek tegen als iets beter kan; geen ja-knikker.
- Elke versie krijgt een passende planeswalker-naam. 0.x zijn testversies; 1.0 = fase 2. **Saheeli** is gereserveerd.
- Bouwen gebeurt alleen in GitHub Actions. Een release ontstaat als `versionNameValue` in `app/build.gradle.kts` verandert.
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

## Volgende versie (naam nog kiezen)

Nieuwe functies 2, 3, 4 en 8, plus verbeteringen 12, 13 en 14. De nummers zijn die van de lijst zoals Mick ze kent.

2. Fabrieksinstellingen (Instellingen → Wissen):
   a. Eigen rode knop "Alles wissen (fabrieksinstellingen)", onder het bestaande Wissen
   b. Wist alles: collectie, decks, verlanglijst en prijsalarmen, boosters, gedeelde collecties van vrienden, historie, wijzigingslog, alle instellingen, AI-sleutel, deckregels, wachtende scans, offline kaartdatabase en afbeeldingencache
   c. Daarna start de app opnieuw met de introductie, zoals na een eerste installatie
   d. Eerst een back-up maken, net als bij Wissen (standaard aan); ongedaan maken kan hierna niet
   e. Bevestigen door WISSEN te typen; met privacymodus aan ook vingerafdruk
   f. Vinkje "Kaartdatabase bewaren" (standaard uit), zodat je schoon begint zonder opnieuw te downloaden
3. Exporteren naar andere sites en apps:
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
12. Manakosten rechts, vlak vóór de prijs (collectie én decks):
   a. Rechts uitgelijnd, zodat alle symbolen een nette kolom vormen
   b. Altijd dezelfde grootte (niet meer verkleinen bij lange namen); de naam krijgt de ruimte links
   c. Lands houden hun kleurbolletjes op dezelfde plek
13. Setscherm in de stijl van de andere schermen (schets docs/schetsen/setscherm.png):
   a. Kopkaart: "130 van 254 · 51%" groot met voortgangsbalk, de vier raritybalken, "Ontbrekend samen € …", Completionist met ⓘ; klapt bij scrollen in tot één regel ("▾ details")
   b. Afgerond zoekveld met filter ernaast, zoals Collectie
   c. Eén schakelaar "Ontbreekt (n) | Heb ik (n)" plus vinkje "Varianten meetellen", i.p.v. de drie losse blauwe knoppen
   d. Rijen in collectiestijl: raritystreep, plaatje (gedimd bij kaarten die je niet hebt), naam, nummer · rarity, manakosten en prijs rechts (zoals punt 12)
   e. Ontbreekt: rechts ♡ (verlanglijst) en + (toevoegen) klein onder elkaar, zodat lange namen passen
   f. Heb ik: per kaart aantal, foil en plek ("×2 · commons, blad 2" of "in deck Krenko")
14. Back-up compleet maken:
   a. Erbij: alle instellingen (thema, gildekleur, privacy, valuta, scaninstellingen, datumnotatie …)
   b. Erbij: deckregels (tribe, hoogste bracket, soort deck, eigen doelen per rol, zelf aangevinkte rolkaarten) en open/dicht + sortering van de groepen per deck
   c. Erbij: vrienden met hun gedeelde collecties (ruilmatches blijven)
   d. Erbij: je eigen deelcode, zodat vrienden je na terugzetten niet als nieuw persoon zien
   e. Erbij: het wijzigingslog, recente zoekopdrachten, laatste boosterprijs
   f. Bewust niet: de AI-sleutel (alleen op de telefoon), de offline kaartdatabase en de plaatjes (opnieuw te downloaden)
   g. Samenvatting na het maken en vóór het terugzetten: "1.069 kaarten · 6 decks · 1.243 prijspunten · 3 vrienden · instellingen"
   h. Terugzetten met keuze: alles (standaard) of losse delen (bijv. alleen decks, of zonder instellingen)
   i. Oudere back-ups blijven werken; wat er niet in staat, blijft zoals het op de telefoon is
   j. Decks na terugzetten (Mick: decks kwamen terug niet mee): uitzoeken met zijn back-upbestand; unittest die een back-up met decks maakt en terugzet

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
