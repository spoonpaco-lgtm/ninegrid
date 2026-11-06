# NineGrid – functies

*English: [FEATURES.md](FEATURES.md)*

Huidige versie: **0.9.0 – Jace**. Elke versie heeft de naam van een planeswalker.

## Kaarten toevoegen
- **Scan een verzamelblad met 9 kaarten** in één foto, of **één kaart tegelijk** met automatisch fotograferen. De tekstherkenning draait op je telefoon.
- Leest naam, set en nummer, ook van **kaarten in andere talen** (de gedrukte naam wordt gecontroleerd, de Engelse print levert de prijs).
- **Setveld**: vul de set van een blad in voor veel betrouwbaarder herkennen; wordt onthouden voor de volgende scan.
- **Foil per vak**: tik op het kleine raster van de laatste foto en dan op een vak (of Alles foil).
- Uitslag in kleur (● gevonden, ● controleren), met een knop **Controleer** met het aantal.
- **Controlescherm**: je foto naast de gevonden kaart, zelf een kaart zoeken, aantal, foil, taal, conditie, proxy.
- **Lijst plakken** in Moxfield/Arena-stijl: `1 Edgar Markov (INR) 328`, `*F*` foil, `*E*` etched, Commander / Deck / Sideboard.
- **Importeren** van je MTG Collection Sheet (Excel) of exports uit Moxfield, Archidekt, ManaBox, Deckbox, Dragon Shield, TCGplayer, Delver Lens en Helvault. Loopt op de achtergrond en gaat verder na een herstart.
- Optioneel een **offline kaartdatabase** (alle ~110.000 kaarten): scannen en opzoeken zonder internet, veel sneller importeren.

## Collectie
- Zoeken, **filteren** (rarity, kleur, type, foil, ook in decks, regeltekst) en **sorteren** (set, naam, prijs); filters worden onthouden.
- **Kaartscherm**: plaatje, normale en foilprijs, legaliteit per format, links naar Scryfall en Cardmarket / TCGplayer.
- **Plekken**: map, blad en vak per kaart.
- **Sets**: compleetheid per set, ontbrekende kaarten, varianten; **Completionist** telt alleen kaarten in binders, niet in decks.

## Decks
- Decks per format (Commander, Modern, Pioneer, Standard, Legacy, Pauper, Casual) met **legaliteitscontrole**.
- **Commander-bracket**: game changers, landvernietiging, extra beurten, tutors en combo's van twee kaarten.
- **Statistieken**: manacurve, kleuren in de kosten tegenover de lands die ze maken, kans op 2–4 lands in je openingshand.
- **Combo's** via Commander Spellbook: combo's in het deck, bijna-combo's waar één kaart aan ontbreekt (en waar die kaart in je collectie ligt). Laat zien als het deck sinds de laatste zoekactie is veranderd.
- **Suggesties** uit je eigen collectie: wat erbij, betere lands, wat eruit, met eigen deckregels (tribe, hoogste bracket, splash). Elke ruil bekijk je eerst.
- Een deck vullen vanuit je collectie; kaarten verhuizen, niets wordt dubbel geteld.

## Overzicht en prijzen
- Prijzen van **Cardmarket (EUR)** of **TCGplayer (USD)** via Scryfall, elke dag op de achtergrond bijgewerkt.
- **Waarde over tijd** vanaf dag één, per lijn: totaal, sets, decks, één deck of set; ook de **koers** zonder toevoegen en weghalen.
- **Winner of the Week**: de kaart die je het meeste opleverde.
- **Breakout**-melding: een kaart van jou stijgt op één dag minstens €3 en 25%.

## Delen en ruilen
- **Je collectie delen als klein bestand** (WhatsApp, e-mail …) onder je eigen naam; kies alles, de losse collectie, binders, sets of decks. Prijzen en proxies gaan niet mee.
- **Collecties van anderen** per persoon, alleen bekijken. Opnieuw sturen vervangt de oude versie. Ook exports uit andere apps, voor vrienden zonder NineGrid.
- **Ruilmatch**: welke kaarten van de ander jouw decks beter maken en andersom, met prijzen; vink aan en zie of een ruil ongeveer gelijk is.

## AI-hulp (optioneel, standaard uit)
- Je eigen sleutel voor Google Gemini (gratis variant), Claude, OpenAI, DeepSeek, Mistral, OpenRouter, Groq of een eigen adres.
- Leest bij het scannen alleen twijfelvakken; elke uitkomst wordt bij Scryfall gecontroleerd. AI-label per vak: bezig, gelukt of niet gelukt (met de reden).
- Decksuggesties uit je collectie of daarbuiten ("Overweeg te kopen"), gecontroleerd op kleuren, format en bracket.
- Begrijpelijke foutmeldingen en stil opnieuw proberen bij drukte; maximum per dag. De sleutel staat versleuteld op je telefoon en niet in de back-up.

## Veilig en makkelijk
- **Wijzigingslog met ongedaan maken**: elke wijziging, ook een hele import of een heel deck, kun je terugdraaien.
- **Back-up** met de hand of elke week naar een map naar keuze; terugzetten op een nieuwe telefoon; export als CSV.
- Instellingen in acht groepen; datum- en tijdformaat naar keuze; dag- en nachtthema; Nederlands en Engels.
- **Handleiding** in de app met zoeken, een ? op elk scherm, en **Wat is nieuw** na een update.

## Gepland
Plannen, geen beloftes. Wat echt in een versie zit, staat in de release notes.

**0.9.1 – Kaya**
- Bijwerken vanuit de app zelf (downloaden en installeren met één tik)
- Zoeken in alle Magic-kaarten, met "Alleen wat ik niet heb"
- Verlanglijst (willekeurige of precieze print, aantal, afgestreept bij scannen, te delen met vrienden)
- Ruilen makkelijker te vinden (Overzicht, decksuggesties "Bij vrienden", kaartscherm)
- Overzichtelijker deelscherm
- Eerlijke introductie over offline, met de keuze om de kaartdatabase te downloaden
- Nieuw logo en app-icoon
- Binders: filter, sorteren per binder en een bladweergave (bladen van 3×3, lege vakken zichtbaar)
- Completionist als vinkje met uitleg
- Privacymodus: prijzen verbergen (stijlen: compleated, zegel, schaduw, stippen); uitzetten met je vingerafdruk

**0.9.2 – Saheeli**
- Voorbeeldhand trekken; foil-glans; raritykleuren bij sets; kleine Magic-details
- Boosters openen: wat je betaalde tegenover wat eruit kwam (ook zonder bulk), boxen
- Gildekleuren als thema
- Prijsalarm voor kaarten op je verlanglijst

**1.0**
- Koppeling met Google Sheets
- Een deck delen via een QR-code

## Privacy
Geen account, geen tracking, geen advertenties. Kaartnamen en nummers gaan naar Scryfall; kaartnamen van een deck gaan naar Commander Spellbook als je combo's zoekt. Foto's blijven op je telefoon, tenzij je AI-hulp aanzet: dan gaat alleen de uitsnede van een twijfelvak naar de AI-dienst die je koos.
