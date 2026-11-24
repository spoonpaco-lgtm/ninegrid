NineGrid 0.9.20 – Kiora

Kiora ziet alles wat in het water ligt: het hele camerabeeld.

- Scanscherm: het hele camerabeeld staat nu op het scherm (vroeger viel links en rechts ~40% weg). Het kader ligt op het echte beeld; je houdt de telefoon dichterbij en een kaart krijgt ~1,5 keer zoveel pixels in de breedte, zodat de kleine voetregel (set en nummer) beter leesbaar is. De balken staan boven en onder het beeld, niet erover.
- Combodatabase nog kleiner: elke kaartnaam en elk "wat het doet" één keer, de koppeling alleen met nummers. Bijwerken gebeurt in een apart bestand: de oude blijft bruikbaar tot de nieuwe klaar is. De database van 0.9.19 wordt vanzelf vervangen (op wifi).
- Scanner: kaarten uit The List (PLST) worden niet meer gekozen als het origineel bestaat (die dragen de setcode van het origineel; Adanto Vanguard en Smiting Helix werden PLST).
- Scanner: het plaatje kiest geen print meer (testset: 8 keer hetzelfde als de tekst, 1 keer fout: Huatli). De cijfers blijven in het scanrapport.
- Scanner zonder set: staat de naam niet op de naamregel maar wel als bestaande kaartnaam in een andere regel, dan zoekt de printkiezer daarmee (oranje). Testset: Exclude, Etchings of the Chosen, Throes of Chaos, Magic Missile.
- Planeswalkers: losse getallen onderaan (loyalty) tellen niet meer als kaartnummer; alleen een nummer met zeldzaamheidsletter of /totaal (Aminatou en Tyvar Kell kregen nr 37).
- Scanrapport: als er geen naam gevonden wordt, staat erbij welke twee namen op de letters het best pasten (om Tura Kennerüd uit te zoeken).

NineGrid 0.9.19 – Jaya

Jaya Ballard: alles wat overbodig is, eruit gebrand.

- Combodatabase veel kleiner (was 196 MB): stappen en voorwaarden staan één keer, ingepakt, en worden pas geladen als je een combo opent; formats als bits; de voorkant van een kaartnaam alleen bij kaarten met //; na het inlezen wordt het werkbestand opgeruimd. Inlezen gaat in grotere stukken, dus sneller.
- De database van 0.9.18 wordt vanzelf vervangen: de oude gaat weg en de nieuwe wordt op wifi opgehaald.
- Scanner: zijn er meerdere prints met dezelfde naam in de set (gewoon, showcase, borderless) en kon de tekst niet kiezen, dan kiest het plaatje, maar alleen als die prints echt een ander plaatje hebben (niet alleen een ander kader). Blijft oranje.
- Scanner: een half gelezen naam wordt herkend aan de letters die er wel staan, in de goede volgorde ("avoc etva" → Havoc Festival, "ierüd, Skyknight" → Tura Kennerüd). Alleen binnen de set en als één naam duidelijk het best past. Blijft oranje.

NineGrid 0.9.18 – Zur

Zur, de meester van de enchantments en het zoeken: combo's vinden in je hele collectie.

- Combo's in je collectie (Collectie → de ∞-knop bovenin met het aantal complete combo's, en op het kaartscherm). Download één keer de combodatabase van Commander Spellbook (~30 MB, zoals Spellbook zelf vraagt in plaats van hun API leeg te halen). Daarna rekent je telefoon alles zelf uit, zonder internet; je collectie gaat nergens heen.
  - Drie lijsten: Compleet, Bijna (je mist één kaart) en Beste aankoop (welke kaart de meeste combo's afmaakt, met prijs en Op verlanglijst).
  - Ingedeeld op wat de combo doet: Winnen, Oneindig mana, Oneindige tokens, Lock en extra beurten, Overig. Populairste eerst, met het bracket-label van Spellbook.
  - Filters: kleuren, format (legaliteit van Spellbook), alle kaarten vrij (niet in een deck), alleen 2 kaarten, prijs van de ontbrekende kaart, en één kaart (vanaf het kaartscherm).
  - Tik op een combo: waar elke kaart ligt (map, blad, vak of deck), wat ontbreekt met prijs en welke vriend hem heeft, wat hij doet, voorwaarden en stappen (ook offline), Nieuw deck met deze combo, Toevoegen aan deck (alleen decks waar de kleuren in passen) en een link naar Spellbook.
  - Altijd actueel: na een scan of import rekent hij vanzelf opnieuw. Eens per week kijkt de app (op wifi, tijdens opladen) of Spellbook iets nieuws heeft.
- Combo's per deck: met de combodatabase op de telefoon direct en zonder internet. De bracketschatting van Spellbook (combo's van twee kaarten) blijft via de knop Zoek opnieuw.

NineGrid 0.9.17 – Dack

Dack Fayden pakt altijd het juiste exemplaar: de printkiezer.

- Printkiezer (zonder ingevulde set): alle prints met de gelezen naam uit de offline kaartdatabase krijgen punten voor wat er in de voetregel staat (nummer, setcode, zeldzaamheidsletter, jaartal). Half gelezen telt mee ("S0130" ligt dicht bij 139, "FON" bij FDN), maar alleen tussen prints die echt bestaan. Groen alleen als set en nummer duidelijk kloppen; anders oranje met de beste print. Werkt offline en is sneller dan zoeken bij Scryfall.
- Printkiezer ook bij een ingevulde set: zijn er meerdere prints met die naam in de set (gewoon, showcase, borderless) en is het nummer niet precies gelezen, dan kiest hij op wat er wel staat. Dat blijft oranje.
- Nummer onderaan beter gelezen: ook aan elkaar ("CO098", "U0146") en met S voor 5 ("C O16s" = 165). Het © van de copyrightregel, gelezen als "C 2024", hield het echte nummer erboven tegen; dat gebeurt niet meer.
- Art Series-kaarten, tokens en emblemen worden nooit meer gekozen (Snakeskin Veil werd "Snakeskin Veil // Snakeskin Veil" uit ATDM). De offline database neemt ze bij de volgende download niet meer op.
- Scanrapport: per camerabeeld wat er gelezen is, en waarom de printkiezer een print koos.
- Testset: 28 losse kaarten (FDN) uit de verzamelde scans van 23 nov.
- Ruisnummer: is de naam goed gelezen, heeft die kaart maar één print in de set en hoort het gelezen nummer bij een heel andere kaart (of bij geen kaart), dan telt dat nummer niet mee. Zo worden goed gelezen kaarten vaker groen.
- Zonder set (niet ingevuld en niet gelezen) wordt een kaart alleen groen als set én nummer in de voetregel gevonden zijn.
- Scherm blijft aan tijdens het scannen, tot 5 minuten zonder gebruik (aanraken, knop of scan). Tijdens Testset draaien blijft de telefoon aan.

NineGrid 0.9.16 – Urza

Urza, die alles uitprobeerde en vastlegde: de testset en de scanrapporten voor losse kaarten.

- Testset voor losse kaarten: een map `losse-kaarten/` naast de bladen, met NNN.jpg en één uitslag.txt (`001: Thriving Bluff (J25 775) | set=ONE | kader=…`). Testset draaien doet eerst de bladen en dan de losse kaarten, via de route van kaart voor kaart (eigen setveld per foto, het richtkader als dat bekend is). De uitslag heeft twee blokken: Bladen en Losse kaarten. Het stemmen over camerabeelden kan de testset niet nabootsen (een foto is één beeld).
- Scanrapporten verzamelen (Instellingen → Ontwikkelaar): na elke scan bewaart de app de foto, het rapport en wat hij vond. Alles delen maakt één zip in de indeling van de testset: `losse-kaarten/` met de foto's, de rapporten en een uitslag.txt met voorstellen, en per blad een map met foto.jpg, scanrapport.txt en uitslag.txt. Daarna Verzameling wissen.
- Fix scanrapport: bij een losse kaart stond bij de uitkomst "-" (een losse kaart heeft geen vaknummer). Nu staat er wat de app vond, met groen of oranje. Het rapport noemt ook het kader op de foto.

NineGrid 0.9.15 – Niko

Niko Aris, die met spiegelscherven werkt: de scanner kijkt nu ook naar het plaatje, niet alleen naar de tekst.

- Herkennen op het plaatje (Instellingen → Scannen, standaard aan): met een ingevulde set zoekt de scanner per vak de kaart uit die set die er het meest op lijkt. Beeldmodel: MobileNet V3 small (image embedder van MediaPipe, Apache 2.0), draait op de telefoon met TensorFlow Lite. De APK wordt daardoor een paar MB groter.
- Alleen de illustratie wordt vergeleken (niet de hele kaart: binnen een set lijken kader en tekstvak op elkaar). Ook vakken zonder enige tekst (splitkaarten, onleesbare naam) krijgen het plaatje.
- Het plaatje maakt nooit groen en overschrijft nooit een gelezen naam. Vond de tekst niets, of las hij geen naam, en is het plaatje duidelijk (score ≥ 0,55 en marge ≥ 0,09 tot de beste kaart met een andere naam; afgesteld op de testset van 22 nov), dan komt die kaart erin, oranje met "herkend op het plaatje". De kaart wordt opgehaald via set en nummer (offline), anders via id.
- Het plaatje kiest geen print tussen prints met dezelfde naam (getest: niet betrouwbaar); twijfel over de print blijft oranje. Las de tekst alleen een nummer en geen naam, dan mag het plaatje de kaart aanvullen.
- Fix (fout sinds 0.9.14): de controle "meerdere prints met deze naam in de set" gebruikte alleen de setlijst van het setscherm. Bij een set die je daar nooit opende, zag de app geen andere prints en kon een kaart groen worden met de verkeerde print (Huatli LCI 189 in plaats van 339). Nu de setlijst van de scanner (offline database eerst); ook het kiezen tussen prints op de foto werkt daardoor weer voor zulke sets.
- Las de tekst in een vak helemaal niets (vaak een splitkaart), dan toont de app de beste gok van het plaatje vanaf score 0,5, ook bij een kleine marge: oranje met "beste gok van het plaatje, controleer". Was de tekst zeker maar ziet het plaatje duidelijk een andere kaart, dan wordt het oranje met "het plaatje lijkt op …". Prints met hetzelfde plaatje (gewoon/showcase) onderscheidt het plaatje niet.
- Offline: per set één keer vingerafdrukken uit de kleine Scryfall-plaatjes (int8, een paar honderd kB per set, in de app-opslag); de plaatjes zelf zijn daarna niet nodig. Een set die je bij het scannen invult, komt er vanzelf bij (met internet, niet op mobiel bij "alleen via wifi"); die scan leest nog alleen tekst. Sets vooraf downloaden maakt ze voor alle sets in je collectie en decks. De nachtelijke update vult sets aan met nieuwe kaarten. Fabrieksinstellingen zonder "offline database bewaren" wist ze.
- Kaart voor kaart: de camera kijkt nu altijd mee bij 1 kaart (ook zonder Automatisch). Mist de foto set of nummer, dan vult de app dat aan met wat minstens twee beelden van dezelfde kaart lazen (laatste 2,5 s). Wat de foto las, gaat voor.
- Controlescherm (39c): is er iets te controleren, dan staat de groep Zeker ingeklapt; tik erop om de kaarten te tonen.
- Scanrapport: per vak wat het plaatje zei (naam, nummer, score/marge, tweede kaart), of het plaatje aan stond en vingerafdrukken had; bij 1 kaart hoeveel camerabeelden meestemden.
- Testset draaien: bij een storing bij Scryfall wacht hij even en probeert hij het blad tot 3 keer opnieuw, in plaats van het hele blad als niet gevonden te tellen. Maakt eerst de vingerafdrukken van de set van elk blad, geeft de vakuitsnedes mee zoals een echte scan, en meldt hoe vaak het plaatje de juiste naam had, met scores en marges van goed en fout (om de grenzen bij te stellen).

Ook in 0.9.15 (Mick 23 nov):
- Boosters: tik op een booster → boosterscherm met betaald, waarde toen/nu, winst of verlies en de kaarten (duurste eerst, de hit gemarkeerd), als lijst of plaatjes; "niet meer in je collectie" als je een kaart niet meer hebt; tik = kaartscherm; potlood = prijs aanpassen.
- Boosters wissen: per booster (prullenbak), meerdere tegelijk (lang indrukken, Alles), per box (tik op de boxregel) of Alle boosters wissen. Keuze: alleen de booster, of ook de kaarten uit de collectie halen (alleen losse exemplaren, nieuwste eerst; kaarten in een deck of al weg blijven staan en worden vooraf geteld). Eén wijziging in het wijzigingslog: Laatste actie ongedaan maken zet boosters, boxen en kaarten terug.
- Filteren en sorteren in één paneel (de oude losse sorteerknop is weg): sorteren bovenaan, tik nog eens = andersom (↓/↑); een andere sortering dan de standaard staat als chip onder de zoekbalk. Nieuw filter Prijs per stuk (snelle keuzes en Van/Tot; foil = foilprijs; zonder prijs valt weg zodra er een grens is). In Collectie, Alle kaarten (dan online), sets, decklijst en deckkaarten, vrienden, Uit collectie kiezen en de verlanglijst (die heeft nu ook zoeken en sorteren).
- Bewaarde combinaties: filters + sortering onder een naam, per scherm; tik = terugzetten, kruisje = weghalen.
- Grafiek Waarde: onder de grafiek de groei in de gekozen periode, uitgesplitst in prijzen en kaarten erbij/eraf (alleen dagen met bekende koers). De dagstand werd al een paar seconden na een wijziging bijgewerkt; nu ook na boosters wissen. Handleiding: wanneer een nieuwe kaart waar meetelt.

NineGrid 0.9.14 – Ob Nixilis

Ob Nixilis, die van winst en verlies leeft: Top movers op het Overzicht, met Winner én Loser of the Week.

- Top movers (samen met Winner of the Week): de kaarten die het meest stegen of daalden, alleen je eigen collectie (collectie en decks, geen proxies, foil apart). Schakelaars Dag | Week en Stijgers | Dalers. Bovenaan de grootste als grote kaart (Winner/Loser of the Week of of the Day), daaronder 2 t/m 5, met Toon top 20 de rest. Tik = kaartscherm. Inklapbaar (standaard ingeklapt: alleen de grote kaart); tik op de titel. Week/Dag, Stijgers/Dalers en open/dicht worden onthouden.
- Volgorde op geld: prijsverandering per stuk maal je aantal; bij gelijk bedrag de grootste procentuele beweging eerst. Het percentage staat erbij.
- Week = maandag tegen de maandag ervoor (zelfde als Winner of the Week en de widget, blijft de hele week gelijk). Dag = laatste nachtelijke prijsupdate tegen die daarvoor.
- Privacymodus: bedragen verborgen, percentages zichtbaar.
- Effecten op de grote kaart (Instellingen → Weergave): Gloed en bloed (mythic-gloed om de winnaar; bloed dat van de onderkant van de verliezer afdruipt, in de stijl van de Completed-olie, met schaduw) of Stempels (WINNER goud, DESTROYED rood). Speelt één keer bij openen of wisselen. Zonder Magic-effecten een groen of rood randje.
- Magic-effecten per effect (Instellingen → Weergave): de hoofdschakelaar zet alles in één keer aan of uit; staat hij aan, dan zet je elk effect los uit: foil-glans, verbannen-animatie, stempels, loyalty-schildjes, Winner en Loser of the Week, bewegende privacystijlen.
- Toxic (privacystijl) vernieuwd in de stijl van het bloed bij Loser: schaduw eronder, glanzende vulling van licht naar diep donker, druppels met bol en glimlicht, gaten met een schaduwrand, bellen die eerst opzwellen en dan knappen.
- Fix: bij Dalers had de grote kaart geen schildje; nu een rood ▼-schildje, zoals het groene ▲ bij Stijgers.
- Scanner: de setlijst (namen en nummers) komt eerst uit de offline kaartdatabase, dan pas van Scryfall. Kiezen binnen de set, zoeken in andere regels en het raster op de namen werken zo zonder internet, met minder verzoeken aan Scryfall. Het setscherm haalt zijn prijzen nog bij Scryfall.

NineGrid 0.9.13 – Nissa

Nissa, die de lijnen in het land ziet: het raster van een blad ligt nu op de kaartnamen zelf.

Testset op Micks telefoon (144 vakken, offline database aan): 88% goed (was 76% in 0.9.12, 68% in 0.9.11), 0% groen maar fout, 10% niet gevonden. Op bladen met een ingevulde set 91%. Lege vakken: 96% goed herkend (100% op bladen met een set).

- Raster op de namen (domain/NameGrid.kt): bij een foto zonder kader (galerij) en een ingevulde set zoekt de app eerst de regels die duidelijk een naam uit die set zijn (minstens 3, geen basislanden). Een naam staat altijd linksboven op de kaart, dus hun posities geven rijen, kolommen en kaartgrootte. Ontbreekt een rij of kolom, dan kiest de app de plek waar de meeste tekst binnen het raster valt, dan een raster binnen de foto, dan het raster het dichtst bij het midden. Lukt het niet (te weinig namen, maten die niet bij 3 x 3 passen), dan het raster uit de tekst zoals voorheen.
- Waarom: in de testset (18 nov) schoof het raster op GRN, DOM en het gemengde blad een halve rij. De onderste rij splitkaarten leverde geen tekst, de app rekte twee rijen gecentreerd uit tot drie, en vakken lazen de naam van het buurvak (vak 1 kreeg Boros Challenger uit vak 4).
- Een naam telt als anker alleen als hij bijna letterlijk gelezen is (85%); staat dezelfde naam ook in regeltekst, dan telt de bovenste. Een paar losse regels naast het blad beslissen niet over de plek. Eindcontrole: staan minder dan 3 op de 4 namen bovenaan hun vak, dan het oude raster.
- Camerascans (met kader) blijven zoals ze waren.
- Testset draaien: bovenaan "LET OP" als Scryfall in die run niet bereikbaar was of weigerde (een run op 18 nov gaf zo 86 in plaats van 110).

NineGrid 0.9.12 – Kasmina

Kasmina, die ziet wat anderen missen: een versie om de scanner betrouwbaarder te maken, gemeten met de testset op Micks telefoon (11 bladen, 144 vakken). Voor: 98 goed, 8 groen maar fout, 33 niet gevonden. Na: 109 goed, 0 groen maar fout, 25 niet gevonden.

Groen is weer te vertrouwen
- Nieuwe naamvergelijking (domain/NameMatch.kt). Vroeger telde een naam al als gelijk als het gelezen stuk erin zat ("Keyrune" → Azorius Keyrune) of de helft van de letters klopte, en won de eerste kaart die paste. Nu kiest de app binnen de set de naam die het best past, alleen als die duidelijk beter past dan de op één na beste, en het gelezen nummer telt alleen als de kaart bij dat nummer ook de best passende naam is.
- Groen vraagt een gelezen naam die voor 80% lijkt (zoals de testset telt). Alleen een nummer is nooit genoeg voor groen: een los cijfer is te makkelijk verkeerd gelezen ("9" werd Defiant Strike, "43" Maximize Altitude).
- Staat de naam niet op de naamregel, dan zoekt de app bij een bekende set in alle gelezen regels naar een naam uit die set ("oros Challenger gets" → Boros Challenger, "mpact Tremors" → Impact Tremors). Zo'n kaart komt altijd oranje binnen; staan er twee namen even goed in (de naam van het buurvak), dan geen keuze. Basislanden alleen als ook de typeregel "Basic Land" gelezen is, anders zou "forest" in regeltekst een Forest geven.
- De 8 gevallen uit de testset (Gloomwidow/Gloom Surgeon, Golgari/Azorius Keyrune, Selesnya Keyrune/Sentry, Raid/Goblin Bombardment, Nightveil Predator/Sprite, Knight of Autumn, Feed the Infection) staan als unittest.

Geen print meer op de gok
- Geen set ingevuld en geen nummer gelezen: de kaart staat oranje ("geen set of nummer gelezen: kijk welke print"), in plaats van een willekeurige print in het groen (Shock werd MSC 809, Lightning Mauler INR 399).
- Bestaan alle gevonden kaarten van het blad samen in één set (minstens 3), dan vraagt het controlescherm: "Alle kaarten op dit blad bestaan in Avacyn Restored (AVR). Die set gebruiken?" Pas na een tik op Ja, gebruiken telt die set en wordt alles opnieuw opgezocht, alsof je hem in de scanner had ingevuld. Bij twee gezamenlijke sets de oudste.

Lezen
- Splitkaarten (Discovery // Dispersal): een vak zonder naam wordt een kwartslag gedraaid gelezen; twee namen = een splitkaart.
- Galerijfoto met een stuk buurpagina (kaartruggen links): tekst die met een gat van het blad gescheiden is en weinig regels heeft, telt niet mee voor het raster.
- Jaartallen met de W van Wizards ("2025W", "202W") tellen niet als kaartnummer.

Testset
- Testset draaien leest als galerijfoto (dat kwam overeen met de camera op dezelfde bladen) en zet bij elke fout wat de scanner las. Optioneel kader.txt per blad.
- Bekend: als het blad maar een deel van de foto vult (GRN, DOM, het gemengde blad), schuift het raster en lezen vakken de naam van het buurvak. Dat is de volgende stap (0.9.13: het raster op de gevonden namen leggen).

NineGrid 0.9.11 – Tezzeret

Tezzeret, de uitvinder die alles met alles verbindt: een versie om je lijsten de deur uit te krijgen en om crashes en scanfouten beter te kunnen zien. Gebouwd en getest met unittests; nog niet op een echte telefoon geprobeerd.

Exporteren (3 sites)
- Deck → ⋮ → Exporteren naar een site…: Moxfield, Archidekt of Cardmarket. De lijst wordt gekopieerd en de site opent; plakken in het importvak. Moxfield en Archidekt krijgen "aantal Naam (SET) nummer" met *F* of *E*. Cardmarket krijgt "aantal Naam (Setnaam)": hun lijst kent geen nummer, foil of taal.
- Meer → Back-up en export → Collectie voor Moxfield (CSV): kolommen zoals Moxfield zelf exporteert (Count, Tradelist Count, Name, Edition, Condition, Language, Foil, Tags, Last Modified, Collector Number, Alter, Proxy, Purchase Price). Kaarten in decks staan er als losse regels in. Alles staat als Near Mint.
- Verlanglijst → Kopieer voor Cardmarket: wat je nog mist, bij een gekozen print met setnaam. Cardmarket neemt maximaal 150 regels per lijst.
- De formaten zijn opgezocht bij Moxfield, Archidekt en Cardmarket. Foil en etched via *F* en *E* is de gangbare schrijfwijze, maar niet bij elke site officieel gedocumenteerd: even nakijken na je eerste import.

Deck delen via QR
- Deck → ⋮ → Delen via QR-code. Op de andere telefoon: Decks → scan-icoon. Het deck komt in het controlescherm, net als een geplakte lijst.
- De code bevat de decklijst, ingepakt. Een commanderdeck van 100 kaarten past in één code; een veel langere lijst komt in delen, in willekeurige volgorde te scannen. Er gaat niets over internet.

Crashrapport
- NineGrid bewaart bij een crash een klein rapport (versie, toestel, Android, foutmelding; geen kaarten). Bij de volgende start vraag je of je het wilt delen; het deelmenu van Android doet de rest. Ook te vinden onder Instellingen → Ontwikkelaar → Laatste crashrapport delen.

Verlanglijst
- Eén minder: de −knop op de regel (bij meer dan één). Een regel weghalen: naar links vegen. Beide met Terugzetten in de melding.
- Elke regel zegt welke print hij toont (setnaam, set en nummer, de prijs van die print) of dat het elke print is. Dezelfde kaart in verschillende prints kon al als aparte regels; kies bij Verlanglijst toevoegen een print.
- Een printwijziging in ✎ heb ik in de code nagelopen en zie geen fout; als het bij jou toch de oude print of prijs laat zien, graag precies wat je ziet.

Kleine verbeteringen
- Overzicht: de keuze Lijn of Taart en de splitsing (set, rarity, deck) blijven onthouden na het sluiten van de app.
- Scannen of plakken naar een deck: bovenin het controlescherm Alle kaarten naar: Deck of Sideboard, in één tik. Commander-kaarten blijven staan; bij een Commander-deck is de knop er niet.

Privacymodus: meer controle
- Wat verbergen: alles, alleen totalen of alleen kaartprijzen.
- Grafiek in procenten, aantallen kaarten ook verbergen, meldingen altijd zonder bedragen, en alleen kaartprijzen boven een zelf gekozen bedrag verbergen.

Privacystijlen erbij
- Toxic (organische groene ooze met gaten die knappen en druppels), Lightning (blauwe bliksem van links naar rechts), Fog (losse mist), Pixelized (flikkerend pixelraster dat naar de randen vervaagt) en Countered (blauwe stempel die neervalt). Plus Zegel klein voor prijzen in lijsten.
- Compleated heet nu Completed (alleen de naam; je gekozen stijl blijft staan).
- Lightning slaat onregelmatig in (een paar keer per halve minuut, soms twee vlak na elkaar), telkens met een andere vorm en een knipperende nagloed. Fog drijft langzaam, zoals Schaduw. Toxic heeft een grilliger rand, een groene oliefilm en druppels met rand.
- Staan de Magic-effecten uit, dan vallen Toxic, Lightning, Pixelized en Countered terug op rustige stippen.

Negen extra app-iconen
- Drie sets: Metalen (Zilver, Koper, Zwart-goud), Edelstenen (Smaragd, Robijn, Amethist) en Licht en donker (Perkament, Inkt, Neon). Instellingen → Weergave → App-icoon.
- Let op: het startpictogram zit nu in een alias. Na deze update kan het icoon van het startscherm verdwijnen; zet het dan opnieuw neer vanuit je appmenu.

Widgets voor het startscherm
- Waarde, Winner of the Week, Kaart van de dag, Meldingen, Verlanglijst en twee scanknoppen (breed met Blad en Kaart, klein zoals de scanner stond).
- Ze werken bij na de nachtelijke prijsupdate en bij het wisselen van de privacymodus. Bij privacymodus vervangen tegeltjes de bedragen.

Grafiek en testset
- Het venster bij tikken op de grafiek toont de lijnen op volgorde van waarde op die dag.
- Testset draaien gebruikt nu de hele scanroute, zodat de meting klopt met de app.

Meldingen
- Toestemming voor meldingen wordt gevraagd zodra je prijsstijging-, ban- of reprintmeldingen aanzet (Android 13 en hoger).

Scanner: testset en beter rapport
- Het scanrapport noemt per vak ook wat de app eerst dacht ("app dacht eerst: …"), naast wat het werd na jouw correcties. Ook de instellingen van de scan: ingevulde set, lamp, automatisch afdrukken, AI-hulp, offline database en taal.
- testset/ in de repo heeft vier bladen met foto, scanrapport en uitslag.txt (de juiste kaart per vak). Op GitHub draait Actions → Scanner-testset de scanner op een emulator over die foto's en zet de uitslag in de samenvatting. Alleen op verzoek; de gewone bouw en release blijven ongemoeid. De emulator kan andere uitslagen geven dan de Samsung.
- testset/ heeft nu elf bladen (zeven nieuwe uit Micks Drive, plus nieuwe scans van de eerste vier), met foto, scanrapport en uitslag.txt.
- Instellingen → Ontwikkelaar → Testset draaien: kies een map met die bladen op je telefoon (de map testset/ uit de repo, als zip van GitHub uitgepakt). De echte scanner, met opnieuw lezen, leest elke foto en je krijgt per blad te zien wat goed en fout is, met Delen. Dit is de echte meting; de emulator is een benadering en traag.

NineGrid 0.9.10.1 – Dovin

Menu opgeruimd
- Meer is verdeeld in blokken: bovenaan Handleiding en Wat is nieuw; Collectie (Importeren, Back-up en export, Delen met vrienden, Laatste actie ongedaan maken); App (Instellingen, Over NineGrid).
- Lijst plakken zit nu in Importeren, naast het kiezen van een bestand.
- Back-up en export is een eigen scherm op Meer: back-up maken, terugzetten, automatische back-up, CSV en onderaan in het rood Alles wissen.
- Over NineGrid staat nog maar op één plek (Meer), met versie, controleren op updates, automatisch bijwerken en Wat is nieuw. De groep Over verdween uit Instellingen en de Handleiding staat niet meer dubbel.
- Instellingen heeft acht groepen: Weergave (thema, gildekleur, effecten), Taal en regio (taal, valuta, datum en tijd), Privacy, Scannen, Kaartgegevens, Collectie, AI-hulp en Ontwikkelaar.

NineGrid 0.9.10 – Dovin

Dovin Baan, de Azorius-ambtenaar die elke regel en elk formulier kent: een versie over welke print je precies hebt en welke kaarten nog mogen. Gebouwd en getest met unittests; nog niet op een echte telefoon geprobeerd.

Print kiezen (8)
- Kaartscherm: onder de setregel staat Andere prints ›. Dat opent de lijst met alle prints van de kaart: klein plaatje, set, nummer en jaar, per afwerking (normaal, foil, etched) de prijs, "goedkoopst" bij de goedkoopste en ✓ bij de huidige. Sorteren op nieuwste of goedkoopste. Tik op een print om erheen te gaan; Terug brengt je bij de vorige print.
- Print van een exemplaar wijzigen: ✎ bij Jouw exemplaren → bovenaan de regel Print › → dezelfde printlijst. Het exemplaar houdt zijn plek (binder, blad en vak, of deck), taal, conditie en proxy. Daarna springt het kaartscherm naar de nieuwe print. Ongedaan maken werkt zoals altijd.
- Bij een stapel (×3) kies je voor hoeveel exemplaren het geldt; de rest blijft zoals hij was, in een eigen regel. Zo zet je ook 1 van 3 op foil.
- Bestaat de afwerking niet bij de nieuwe print, dan kiest de app Normaal (of wat er wel is) en zegt dat. Ligt er op dezelfde plek al een gelijke stapel, dan wordt het één stapel.
- + Exemplaar toevoegen staat altijd op het kaartscherm (was alleen te zien als je de kaart nog niet had): Normaal, Foil of Etched (alleen wat er van die print bestaat, met prijs), het aantal en meteen de plek: de collectie (met map, blad en vak) of een deck.
- Verlanglijst → ✎: bij "precies deze print" staat de printregel; tik voor de printlijst. Prijs en prijsalarm rekenen daarna met die print.

Melding bij ban of reprint (40)
- Na de dagelijkse prijsupdate kijkt de app of er iets veranderd is aan kaarten die je hebt (geen proxies).
- Bans: wordt een kaart gebanned, restricted, weer toegestaan, niet meer legaal of juist legaal in een format waarin je een deck hebt, dan krijg je een melding met hoeveel je er hebt, wat ze waard zijn en in welk deck ze zitten. Met Alle formats volg je alle zes formats.
- Reprints: sets die nog niet uit zijn of de afgelopen 30 dagen uitkwamen (geen tokens of digitale sets). Komt een kaart die je hebt daarin voor in een andere print, dan een melding met de set, de releasedatum en jouw print met zijn waarde. Elke reprint wordt één keer gemeld. Alleen sets waarvan het aantal kaarten veranderde worden opnieuw opgehaald, dus na de spoilers kost het bijna niets.
- Eén melding voor de bans, één voor de reprints; met één kaart opent tikken die kaart. Privacymodus: geen bedragen in de melding.
- Instellingen → Collectie: bans en reprints apart aan of uit, Alle formats, en vanaf welke waarde per stuk een reprint gemeld wordt (standaard €5).
- De eerste controle legt alleen de stand vast, zonder meldingen. Een reprint laat de prijs niet altijd dalen: de melding is informatie, geen verkoopadvies.

Past niet in het deck (4e)
- Gaan de kaarten naar een deck (scannen vanuit een deck, een lijst plakken naar een deck), dan staat een kaart die banned of niet legaal is in het format van het deck, of buiten de kleuren van de commander valt, meteen oranje bij Controleer, met de reden. De commander komt uit het deck zelf en uit de kaarten op het scherm met de knop Commander. Opslaan kan nog steeds.

NineGrid 0.9.9.2 – Vivien

Scanner, een kaart tegelijk
- Onderin staat van de laatste drie kaarten nu een fotootje van de gevonden kaart in plaats van de naam, met een gekleurde rand: groen is zeker, oranje is controleren, blauw met ★ is foil checken. Tik op het fotootje voor een extra exemplaar (+1), het kruisje haalt de kaart weg. Staan plaatjes uit in de instellingen, dan blijft het chipje met de naam.
- Het kader in de scanner past nu precies tussen de balken: de onderband wordt gemeten, zodat de rand van de balk niet meer dwars door het kader loopt (bij Blad en bij 1 kaart).
- De knop Controleer onder Opslaan werd afgekapt; hij past nu, en het uitslagvak is bij 1 kaart hoger voor de fotootjes.

NineGrid 0.9.9.1 – Vivien

Overzicht → grafiek: Lijnen kiezen
- Aantal kaarten is een vakje bovenin Lijnen kiezen, zoals de andere lijnen (de losse knop bij de grafiek is weg). Het telt niet mee in de 6 lijnen en werkt alleen bij Waarde.
- De lijst heeft groepen: eerst wat je al gekozen had, dan Totaal, Decks en Sets. Sets staan ingeklapt; zoeken klapt alles open.
- Alleen sets waar je nu kaarten van hebt, de duurste eerst, met de setnaam erbij ("MH1 · Modern Horizons"). Een set die je helemaal weghaalt verdwijnt uit de lijst en uit de grafiek; zijn geschiedenis blijft bewaard.
- Overzicht: bij de grafiek een schakelaar Lijn | Taart. De taart toont de verdeling van nu per set, per rarity of per deck, naar Waarde of Kaarten; de zes grootste plakken en de rest samen als Overig. Tik op een regel van de legenda om naar die set of dat deck te gaan.
- Kaartscherm: een inklapbaar blok Prijsverloop met een grafiekje van de prijs van deze print, uit de dagelijkse prijzen die de app al bewaart (de laatste maand per dag, daarna één per maand). Dichtgeklapt zie je de verandering van de afgelopen week als schildje. Bij een kaart met foil kies je Normaal of Foil.

Fix
- Combo's: tikken op een kaart die je niet hebt opende een leeg venster. De kaart wordt nu eerst opgehaald en onthouden, dan opent het kaartscherm.

NineGrid 0.9.9 – Vivien

Vivien, de jager die sporen volgt: een versie vol scannen, herkennen en kaartjes. Gebouwd op papier getest (unittests met de gelezen regels uit de vier scanrapporten); de scanner is nog niet op een echte telefoon geprobeerd.

Scanner (reparaties na vier scanrapporten)
- Galerijfoto's zonder kader: het raster komt uit de kaartmaten. Een lege rij of kolom schuift de andere kaarten niet meer een vak op.
- Een setcode die niet bestaat is er niet. "ONE•EN" las de OCR soms als "EATUR ES"; daarmee kwam er een verzonnen taal bij en werd de kaart niet gevonden. Een niet-Engelse taal telt nu alleen als de gelezen naam geen Engelse kaartnaam is.
- Een jaartal uit de copyrightregel (© 2023 Wizards) is geen kaartnummer meer.
- De set die op het blad overheerst wint van één verlezen set, als de naam (en het nummer) daar bestaan. Een zelf ingevulde set blijft leidend.
- Lege hoesjes met glans of strepen worden vaker als leeg herkend, als er in dat vak ook geen tekst is gelezen.

Nieuw
- Foil-twijfel heeft een eigen kleur: blauw met ★ en "Foil?". Oranje is nu alleen nog voor een echte kaartcheck. Op het controlescherm staan de kaarten in groepen (Controleer, Foil checken, Zeker); bij een blauwe kaart bevestig je de foil of kies je Geen foil.
- Kaart voor kaart scannen: onderin staan de laatste drie kaarten met wat er gevonden is (groen, oranje, blauw ★). Tik op een kaart voor een exemplaar erbij (+1), kruisje om hem weg te halen. Is alles zeker, dan staat er direct Opslaan; anders Controleer (n). Foil is een stand voor de hele sessie en blauw zolang hij aan staat.
- Scannen naar een deck: in een deck onder Toevoegen kies je Scannen. De scanner toont "Naar deck: ..."; op het controlescherm staat het deck als bestemming, met de regels van uit de collectie halen. Na opslaan ben je terug in het deck.
- Deckimport en scannen naar een deck: boven de kaarten één keuze voor uit de collectie halen: Alles wat ik heb, Exact dezelfde print of Niets, met een samenvatting ("62 uit je collectie · 9 andere print · 29 nieuw"). Het vinkje per kaart blijft voor uitzonderingen.
- Filter- en sorteerknoppen: kaarten van een vriend (filter + sorteren op prijs, set, naam), setscherm sorteren (nummer, naam, prijs, rarity), deck filteren (kleur, type, rarity).
- Thumbnails bij Suggesties, zoals bij Combo's: tik opent de kaart (ook een kaart die je niet hebt, via Scryfall), vasthouden toont hem groot. Combo's: tikken werkt nu ook op een kaart die je niet hebt.
- Combo's: de blokken Combo's in dit deck en Bijna klappen in en uit, per deck onthouden.
- Weergavekeuze bij Combo's en Suggesties is één segmentbalk: ▦ Thumbnails | ☰ Namen.

Nog niet gedaan
- De set van de vorige kaart als aanname bij kaart voor kaart scannen.
- Kaarten buiten de kleuren van de commander meteen oranje bij scannen naar een deck.
- Kiezen of alles naar Deck, Sideboard of Commander gaat in één keer (per kaart kan het al).

---

NineGrid 0.9.8.2 – Kaito (opmaak)

Alleen opmaak, geen nieuwe functies. Bladweergave en scanner zijn bewust niet aangepast.

Verbeterd
- Titels in de balk zijn overal vet.
- Hoekafrondingen teruggebracht tot drie maten (klein 6, normaal 12, groot 16) en lettergroottes tot vijf (11, 13, 14, 16, 24); vaste bouwstenen in het thema (NgShape, NgText, NgSpace).
- Schermmarge in lijsten en filters 16 dp in plaats van 12.

---

NineGrid 0.9.8.1 – Kaito (reparatie)

Scanner: een set is nu een grens.

Opgelost
- Heb je op het scanscherm een set ingevuld (of is de set gelezen), en staat de kaart niet in die set, dan kwam er stil een print uit een andere set. Nu staat zo'n kaart oranje met erbij "niet in MH2, wel in MH1".
- Is er geen nummer gelezen en bestaan er meerdere prints met die naam in de set (gewoon, borderless, showcase), dan is de gekozen print een gok. Zo'n kaart staat nu oranje ("meerdere prints, kijk welke het is") in plaats van groen.

---

NineGrid 0.9.8 – Kaito

Kaito, de schaduwwandelaar: komt en gaat ongezien. Deze versie maakt het kaartscherm en de decks handiger en geeft je gereedschap om een slechte scan uit te zoeken.

Nieuw
- Vegen op het kaartscherm: links of rechts voor de volgende of vorige kaart uit de lijst waar je vandaan kwam (collectie, deck, set, verlanglijst).
- Label NIEUW op kaarten die je pas toevoegde (ook als een stapel groter wordt). In de instellingen kies je hoe lang (1, 3, 7, 14, 30 dagen of nooit) en met Alles gezien haal je het weg. In de collectie is er een filter Nieuw en de sortering Nieuwste eerst.
- Commander wijzigen: uit het deck, je losse collectie of een nieuw gezochte kaart; vervangen (de oude gaat naar het deck of terug naar je collectie) of als tweede commander erbij (Partner, Friends forever, Background, Doctor's companion). Ook via Maak commander op het kaartscherm. Een creature-type als deckregel verandert niet vanzelf; daar krijg je een melding van.
- Combo's als kaartjes met + ertussen. Tik opent de kaart, lang indrukken toont hem groot, de ontbrekende kaart is vager met een oranje rand. Met Namen krijg je de oude weergave terug.
- Ontwikkelaar-menu in de instellingen (de tijdmeting zit er nu ook in) met een scanrapport: na een scan deel je de foto en per vak wat er gelezen is.
- Alles wissen heeft nu ook Alles (fabrieksinstellingen): eerst een back-up, daarna gaat alles weg (ook instellingen en AI-sleutel; de offline kaartdatabase kun je bewaren) en start de app als nieuw.

Verbeterd
- Gaat de lamp van de scanner niet aan, dan zegt de app waarom (lage batterij of batterijbesparing) en springt het icoon terug.

---

NineGrid 0.9.7.1 – Teyo (reparatie)

Reparatie van de scanner uit 0.9.7.

Opgelost
- Een los woord of jaartal onderaan een kaart werd soms voor de setcode aangezien, waardoor het vak niet opnieuw gelezen werd en de kaart verkeerd of niet gevonden werd. Een setcode zonder taal telt nu alleen nog als hij letters en een cijfer heeft (MH2), of na opnieuw lezen als de set bestaat.
- Lege vakken werden vaak "niet gevonden" als er losse tekens in gelezen waren (reflectie, rand van het hoesje). Alleen een naam of een nummer met set bewijst nu een kaart; een vak met alleen losse tekens en weinig structuur telt als leeg.
- Bij een heel blad komt niet meer de blokkerende melding "dichterbij" of "onscherp". De scan gaat door met wat gelezen is en bovenin staat "6 van 9 vakken gelezen. Niet gelezen: vak 3, 7". Bij één kaart blijft de melding bestaan.

---

NineGrid 0.9.7 – Teyo

Teyo, de schildmagiër: zijn kracht is beschermen. Deze versie zet fouten dicht, maakt je back-up compleet en poetst op wat er al was. Geen nieuwe functies; die komen in 0.9.8.

Opgelost
- Deck importeren met verschillende prints van basic lands: je kreeg één print goed en voor de rest landjes uit je collectie (bijvoorbeeld 5th Edition). Basic lands komen nu alleen nog uit je collectie als het precies dezelfde print is; anders komt de print uit je lijst er nieuw bij.
- Bij andere kaarten mag een andere print uit je collectie nog steeds het deck in, maar het controlescherm laat in oranje zien welke print dat is. Vinkje uit = de print uit de lijst, nieuw.
- De bladweergave past altijd op één scherm: de vakken schalen mee met de hoogte, je hoeft niet meer te scrollen.
- Het logo in het app-icoon is kleiner, zodat het ook in een rond icoon helemaal past.
- De melding met Ongedaan maken staat boven de navigatiebalk van Android (en boven het toetsenbord), in plaats van erop.

Verbeterd
- Scanner: cijfers die als O, I of l gelezen worden, worden hersteld; een letter achter het nummer (103a) blijft staan; namen met // (Fire // Ice) worden goed gelezen; een setcode zonder taalcode telt mee; en vakken die nog iets missen krijgen een tweede leespoging met meer contrast.
- Scanner: een losse letter is geen nummer meer, en de M van mythic hoort niet bij het nummer ("374M" is 374).
- Scanner: foils hebben een ster (★) tussen set en taal. Die regel wordt nu gelezen, en leest de scanner de ster, dan staat de kaart alvast op foil. Het vak is oranje ("★ gelezen: foil?"): één tik in het grote raster bevestigt het.
- Scanner: sneller (één tekstherkenner voor alles) en de regel met nummer en set wordt drie keer vergroot in plaats van twee.
- Grafiek: de as begint niet meer bij nul maar volgt de laagste en hoogste waarde in de periode, zodat ook kleine veranderingen zichtbaar zijn. In het kader staat het precieze bedrag.
- Grafiek: bij Waarde loopt het aantal kaarten mee als tweede lijn met een eigen as rechts; aan en uit met de knop Aantal kaarten. Koers heet nu Prijsbeweging.
- Back-up compleet: ook instellingen, deckregels en open/dichte groepen, vrienden met hun gedeelde collecties, je deelcode, het wijzigingslog en recente zoekopdrachten. Niet erin: de AI-sleutel, de offline kaartdatabase en de plaatjes.
- Back-up: na het maken zie je wat erin zit; bij terugzetten zie je dat ook, en kies je wat je terugzet (alles, of losse delen). Oudere back-ups blijven werken.
- Setscherm in de stijl van de rest: kopkaart met voortgang en raritybalken (klapt in bij scrollen), zoeken met filter, één schakelaar Ontbreekt | Heb ik met aantallen, en regels met plaatje, manakosten en prijs. Bij Ontbreekt ♡ en +, bij Heb ik aantal, foil en plek.

Handiger
- Manakosten staan rechts, vlak vóór de prijs, altijd even groot en in een nette kolom (collectie, decks, verlanglijst). Lands tonen daar hun kleuren.
- Nieuw filter Game Changers in Collectie, Alle kaarten en op de verlanglijst.
- Verlanglijst: tik op een kaart opent het kaartscherm; bewerken gaat met het potlood ✎.
- Suggesties bij een deck: de blokken Kan erin, Betere lands en Kan eruit klappen in en uit, tonen het aantal en leggen in een paar regels uit wat ze zijn. De redenen per kaart zijn duidelijker ("kost 6+ mana").

Goed om te weten
- Het filter Game Changers bij Alle kaarten zoekt bij Scryfall en heeft dus internet nodig.
- Manakosten in het setscherm verschijnen bij kaarten die je nog niet hebt pas nadat de setlijst is ververst (de ververs-knop rechtsboven, of vanzelf na een week).
