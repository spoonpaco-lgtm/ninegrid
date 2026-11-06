# NineGrid – functies

*English: [FEATURES.md](FEATURES.md)*

Huidige versie: **0.9.5 – Narset**. Elke versie heeft de naam van een planeswalker.

## Wat NineGrid anders maakt

- **Een heel binderblad in één foto, met de plek erbij.** Negen kaarten per foto, en elke kaart staat meteen op zijn map, blad en vak.
- **Alles ongedaan maken.** Elke wijziging is terug te draaien, ook een hele import of een heel deck.
- **Koers los van wat je toevoegt.** De grafiek laat zien hoeveel je collectie steeg of daalde door de prijzen, niet doordat je kaarten bijkocht.
- **Ruilmatch op basis van je decks.** Niet alleen "jij zoekt X, hij heeft X": NineGrid rekent uit welke kaarten van een vriend jouw decks echt beter maken, en andersom, met prijzen.
- **Delen zonder account.** Een klein bestand via WhatsApp of e-mail is genoeg; ook exports uit andere apps werken, voor vrienden zonder NineGrid.
- **Decksuggesties uit je eigen collectie.** Wat erbij en wat eruit kan, op basis van wat het deck mist, met je eigen regels (tribe, hoogste bracket, splash). Elke ruil bekijk je eerst.
- **Alles over een deck op je telefoon.** Bracket, combo's en bijna-combo's (met waar de ontbrekende kaart ligt: in je collectie, bij een vriend of op je verlanglijst), landadvies op basis van je curve, en een voorbeeldhand.
- **Je binder zoals hij echt is.** Een bladweergave van 3×3 met elke kaart op zijn vak; tik op een leeg vak om precies die kaart te scannen.
- **Boosters bijhouden.** Wat je betaalde tegenover wat eruit kwam, ook zonder bulk, per box en per set.
- **Privacymodus met je vingerafdruk.** Bedragen verborgen voor wie je telefoon pakt, in een eigen stijl, en alleen met je vingerafdruk weer zichtbaar.
- **AI met je eigen sleutel.** Kies uit acht diensten; elke uitkomst van de AI wordt bij Scryfall gecontroleerd voordat hij in je collectie komt.
- **Gratis, zonder grenzen, zonder account.** Geen maximum aan decks of exports, geen abonnement, geen tracking. Je collectie staat op je eigen telefoon.

## Kaarten toevoegen
- **Scan een verzamelblad met 9 kaarten** in één foto, of **één kaart tegelijk** met automatisch fotograferen. De tekstherkenning draait op je telefoon.
- Leest naam, set en nummer, ook van **kaarten in andere talen** (de gedrukte naam wordt gecontroleerd, de Engelse print levert de prijs).
- **Setveld**: vul de set van een blad in voor veel betrouwbaarder herkennen; wordt onthouden voor de volgende scan.
- **Foil per vak**: tik op het kleine raster van de laatste foto (met ✦) en dan op een vak (of Alles foil).
- **Vast kader**: het kader verschuift niet na een foto; een mislukte scan gooi je weg met 🗑 (met Terugzetten).
- Uitslag in kleur (● gevonden, ● controleren), met een knop **Controleer** met het aantal.
- **Controlescherm**: je foto naast de gevonden kaart, zelf een kaart zoeken, aantal, foil, taal, conditie, proxy.
- **Lijst plakken** in Moxfield/Arena-stijl: `1 Edgar Markov (INR) 328`, `*F*` foil, `*E*` etched, Commander / Deck / Sideboard.
- **Importeren** van je MTG Collection Sheet (Excel) of exports uit Moxfield, Archidekt, ManaBox, Deckbox, Dragon Shield, TCGplayer, Delver Lens en Helvault. Loopt op de achtergrond en gaat verder na een herstart.
- Optioneel een **offline kaartdatabase** (alle ~110.000 kaarten): scannen en opzoeken zonder internet, veel sneller importeren.

## Collectie
- Schakelaar **Mijn collectie | Alle kaarten | Verlanglijst**.
- Zoeken, **filteren** (rarity, kleur, type, foil, binder, ook in decks, regeltekst) en **sorteren** (set, naam, prijs, per binder); filters worden onthouden.
- **Alle kaarten**: zoeken in alle Magic-kaarten (offline database of Scryfall), met een label wat je al hebt en het filter "Alleen wat ik niet heb".
- **Verlanglijst**: willekeurige of precieze print, alleen foil, aantal; vanzelf afgestreept bij scannen; Gekocht in één tik; een lijst plakken.
- **Prijsalarm** op de verlanglijst: doel staat klaar op −10%, één melding per dag, bij elke print de goedkoopste.
- **Bladweergave** per binder: 3×3 met foil, stapels en lege vakken; alle bladen in één scherm; lang drukken om te verplaatsen.
- **Kaartscherm**: de kaart groot (volledig scherm met een tik, foil-glans bij kantelen), normale en foilprijs, legaliteit per format, wie van je vrienden hem heeft, links naar Scryfall en Cardmarket / TCGplayer.
- **Plekken**: map, blad en vak per kaart.
- **Sets**: compleetheid per set en per rarity, stempel Compleet, ontbrekende kaarten, varianten; **Completionist** (vinkje met ⓘ) telt alleen kaarten in binders, niet in decks.

## Decks
- Decks per format (Commander, Modern, Pioneer, Standard, Legacy, Pauper, Casual) met **legaliteitscontrole**.
- **Deckscherm**: banner met de art van je commander, kaarten per type (in- en uitklapbaar, sorteren op mana, naam of prijs), manakosten op elke regel, label GC bij Game Changers, zoeken in het deck.
- **Commander-bracket**: game changers, landvernietiging, extra beurten, tutors en combo's van twee kaarten.
- **Statistieken**: manacurve met oordeel per decktype en **landadvies** (formule van Frank Karsten), kleuren in de kosten tegenover de lands, kans op 2–4 lands met **Trek 7** (voorbeeldhand met mulligan), kaarttypen.
- **Combo's** via Commander Spellbook: combo's in het deck, invloed op de bracket, bijna-combo's met een actie per status (In deck, Ruilmatch, Op verlanglijst). Laat zien als het deck sinds de laatste zoekactie is veranderd.
- **Suggesties** uit je eigen collectie: rollen als balkjes met doelen per decktype (automatisch, of zelf kiezen), zelf kaarten bij een rol aanvinken, wat erbij, betere lands, wat eruit, en **Bij vrienden**. Elke ruil bekijk je eerst.
- Stempel **Legaal** of **Banned**.
- Een deck vullen vanuit je collectie; kaarten verhuizen, niets wordt dubbel geteld.

## Overzicht en prijzen
- Prijzen van **Cardmarket (EUR)** of **TCGplayer (USD)** via Scryfall, elke dag op de achtergrond bijgewerkt.
- **Waarde over tijd** vanaf dag één, per lijn: totaal, sets, decks, één deck of set; ook de **koers** zonder toevoegen en weghalen.
- **Winner of the Week**: de kaart die je het meeste opleverde.
- **Breakout**-melding: een kaart van jou stijgt op één dag minstens €3 en 25%.
- Prijsbewegingen als loyalty-schildjes (▲ +6 / ▼ −3).
- **Boosters**: per booster betaald, eruit, winst of verlies (ook zonder bulk) en de hit; boxen; per set het gemiddelde, waarde toen en nu.

## Delen en ruilen
- **Je collectie delen als klein bestand** (WhatsApp, e-mail …) onder je eigen naam; kies alles, de losse collectie, binders, sets of decks. Prijzen en proxies gaan niet mee.
- **Collecties van anderen** per persoon, alleen bekijken. Opnieuw sturen vervangt de oude versie. Ook exports uit andere apps, voor vrienden zonder NineGrid.
- **Ruilmatch**: welke kaarten van de ander jouw decks beter maken en andersom, met prijzen; vink aan en zie of een ruil ongeveer gelijk is. Verlanglijsten gaan mee: wat de ander van jouw verlanglijst heeft staat bovenaan.
- Ruilen is overal te zien: blok **Ruilen** op Overzicht, **Bij vrienden** in decksuggesties, **Ook bij: Jan ×2** op het kaartscherm.

## AI-hulp (optioneel, standaard uit)
- Je eigen sleutel voor Google Gemini (gratis variant), Claude, OpenAI, DeepSeek, Mistral, OpenRouter, Groq of een eigen adres.
- Leest bij het scannen alleen twijfelvakken; elke uitkomst wordt bij Scryfall gecontroleerd. AI-label per vak: bezig, gelukt of niet gelukt (met de reden).
- Decksuggesties uit je collectie of daarbuiten ("Overweeg te kopen"), gecontroleerd op kleuren, format en bracket.
- Begrijpelijke foutmeldingen en stil opnieuw proberen bij drukte; maximum per dag. De sleutel staat versleuteld op je telefoon en niet in de back-up.

## Veilig en makkelijk
- **Wijzigingslog met ongedaan maken**: elke wijziging, ook een hele import of een heel deck, kun je terugdraaien.
- **Back-up** met de hand of elke week naar een map naar keuze; terugzetten op een nieuwe telefoon; export als CSV.
- **Bijwerken vanuit de app**: dagelijks kijken, op de achtergrond downloaden (standaard via wifi), één tik om te installeren.
- **Privacymodus**: alle bedragen verborgen behalve in de ruilmatch; uitzetten, even kijken, back-up en export alleen met vingerafdruk; stijlen compleated, zegel, schaduw en stippen.
- Instellingen in acht groepen; datum- en tijdformaat naar keuze; dag en nacht (zon en maan); **gildekleuren**; Magic-effecten (foil-glans bij kantelen, verbannen, stempels) uit te zetten; Nederlands en Engels.
- Back-up neemt ook verlanglijst, boosters en prijsalarmen mee.
- **Handleiding** in de app met zoeken, een ? op elk scherm, en **Wat is nieuw** na een update.

## Gepland
Plannen, geen beloftes. Wat echt in een versie zit, staat in de release notes.

**Later**
- Verkoopalarm, jaaroverzicht, widget

**1.0**
- Koppeling met Google Sheets
- Een deck delen via een QR-code

## Privacy
Geen account, geen tracking, geen advertenties. Kaartnamen en nummers gaan naar Scryfall; kaartnamen van een deck gaan naar Commander Spellbook als je combo's zoekt. Foto's blijven op je telefoon, tenzij je AI-hulp aanzet: dan gaat alleen de uitsnede van een twijfelvak naar de AI-dienst die je koos.
