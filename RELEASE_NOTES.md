NineGrid 0.9.9.1 – Vivien

Overzicht → grafiek: Lijnen kiezen
- Aantal kaarten is een vakje bovenin Lijnen kiezen, zoals de andere lijnen (de losse knop bij de grafiek is weg). Het telt niet mee in de 6 lijnen en werkt alleen bij Waarde.
- De lijst heeft groepen: eerst wat je al gekozen had, dan Totaal, Decks en Sets. Sets staan ingeklapt; zoeken klapt alles open.
- Alleen sets waar je nu kaarten van hebt, de duurste eerst, met de setnaam erbij ("MH1 · Modern Horizons"). Een set die je helemaal weghaalt verdwijnt uit de lijst en uit de grafiek; zijn geschiedenis blijft bewaard.

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
