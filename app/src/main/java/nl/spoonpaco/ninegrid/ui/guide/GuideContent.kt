package nl.spoonpaco.ninegrid.ui.guide

/** Bouwstenen van een hoofdstuk. */
sealed interface Block
data class H(val text: String) : Block
data class P(val text: String) : Block
data class Steps(val items: List<String>) : Block
data class Tip(val text: String) : Block
data class Note(val text: String) : Block

data class Chapter(val id: String, val title: String, val summary: String, val blocks: List<Block>)

/** De handleiding in het Nederlands en het Engels. Hoofdstuk-id's zijn in beide talen gelijk. */
object Guide {
    fun chapters(dutch: Boolean): List<Chapter> = if (dutch) NL else EN
    fun chapter(id: String, dutch: Boolean) = chapters(dutch).firstOrNull { it.id == id }

    private val NL = listOf(
        Chapter("start", "Snel beginnen", "In vijf stappen van een lege app naar je eerste kaarten.", listOf(
            P("NineGrid begint leeg. Alles wat je invoert staat op je eigen telefoon en werkt ook zonder internet; alleen voor het opzoeken van kaarten en prijzen is internet nodig."),
            Steps(listOf(
                "Tik onderaan op Scannen en fotografeer een verzamelblad met negen kaarten. Of tik in Collectie op + en kies Lijst plakken of Kaart zoeken.",
                "Controleer in het controlescherm of alle kaarten goed gevonden zijn.",
                "Kies waar ze naartoe gaan: je collectie, een nieuw deck of een bestaand deck, en tik op Opslaan.",
                "Bekijk je kaarten in Collectie en je decks in Decks. Tik op een kaart voor plaatje, prijs en links.",
                "Maak af en toe een back-up via Meer → Instellingen en back-up, of zet de wekelijkse automatische back-up aan.",
            )),
            Tip("Rechtsboven op de meeste schermen staat een ?. Die opent meteen het hoofdstuk dat bij dat scherm hoort."),
        )),
        Chapter("add", "Kaarten toevoegen", "Scannen, een bestaande foto, een lijst plakken of één kaart zoeken.", listOf(
            H("Scannen"),
            Steps(listOf(
                "Tik onderaan op Scannen. De eerste keer vraagt de app toestemming voor de camera.",
                "Vul bij Set van dit blad de setcode in als alle kaarten uit één set komen, bijvoorbeeld MH1. Dat maakt het herkennen veel betrouwbaarder. Gemengd blad? Laat het leeg.",
                "Leg het blad plat en houd de telefoon er recht boven, zodat het blad het kader vult. Het raster helpt je de negen vakken recht te krijgen.",
                "Tik op de ronde cameraknop. De app leest per kaart de naam bovenaan en de set en het nummer onderaan.",
                "Je komt in het controlescherm (zie het volgende hoofdstuk).",
            )),
            Tip("Goed licht zonder schittering werkt het best. Kantel foils een beetje tot de glans weg is."),
            Note("Foto's worden alleen op je telefoon gelezen en nergens bewaard of verstuurd."),
            H("Een bestaande foto gebruiken"),
            P("Tik op het scanscherm op Uit foto's en kies een foto uit je galerij. De rest gaat hetzelfde als bij scannen."),
            H("Een lijst plakken"),
            P("Via Collectie → + → Lijst plakken, Meer → Lijst plakken, of in een deck. Eén kaart per regel:"),
            P("1 Edgar Markov (INR) 328\n4 Murder (M20) 109 *F*\n1 Sol Ring (C21) 263 *E*"),
            Steps(listOf(
                "*F* achter een regel betekent foil, *E* betekent etched.",
                "Een regel Commander, Deck of Sideboard begint een nieuw deel. SB: voor een regel zet die kaart in het sideboard.",
                "Zonder kopjes geldt: alles na een lege regel is sideboard.",
                "Set en nummer mogen ontbreken; dan zoekt de app op naam.",
            )),
            Tip("Lijsten uit Moxfield, Archidekt en MTG Arena kun je meestal zo plakken."),
            H("Eén kaart zoeken"),
            P("Tik in Collectie op + → Kaart zoeken, typ een deel van de naam, kies de kaart en daarna de juiste print."),
        )),
        Chapter("review", "Het controlescherm", "Kaarten nakijken voordat ze worden opgeslagen.", listOf(
            P("Na scannen of plakken zie je elke kaart met plaatje, set, nummer en aantal. Kaarten die niet gevonden zijn, staan in rood."),
            Steps(listOf(
                "Niet gevonden? Tik op het vergrootglas en zoek de kaart zelf. Je ziet wat de scanner las, dat helpt bij het zoeken.",
                "Verkeerde print? Tik op de kaart → Meer opties → Andere print kiezen.",
                "Pas het aantal aan met − en +. Zet Foil aan of uit met de knop eronder.",
                "Onder Meer opties: afwerking (non-foil, foil, etched), taal, conditie (standaard NM) en proxy.",
                "Wil je een kaart niet opslaan? Haal het vinkje links weg.",
                "Kies bovenaan waar de kaarten heen gaan: Collectie, Nieuw deck of Bestaand deck.",
                "Tik onderaan op Opslaan.",
            )),
            H("Map, blad en vak"),
            P("Kies je Collectie, dan kun je een map en bladnummer invullen. Bij scannen krijgt elke kaart dan automatisch zijn vak (1 linksboven, 9 rechtsonder). Na het opslaan schuift het bladnummer één op, zodat je meteen het volgende blad kunt scannen."),
            Note("Scan je een blad dat er al in staat, dan waarschuwt de app: Al gescand? Je kiest dan zelf of je het toch toevoegt."),
            H("Commander"),
            P("Maak je een Commander-deck, tik dan bij de aanvoerder op de knop Commander. Zonder commander kun je wel opslaan, maar de deckcontrole meldt het dan."),
        )),
        Chapter("collection", "Je collectie", "Zoeken, filteren, het kaartscherm en plekken.", listOf(
            Steps(listOf(
                "Zoek bovenaan op naam. Met de knoppen eronder filter je op rarity, foil en kleur.",
                "Decks meetellen: aan = ook kaarten die in een deck zitten tonen; uit = alleen wat los in je collectie ligt.",
                "Sorteer op prijs of naam. De kaarten staan gegroepeerd per set.",
            )),
            H("Het kaartscherm"),
            P("Tik op een kaart voor het plaatje, de prijs (normaal en foil), legaliteit per format en links naar Scryfall en Cardmarket (of TCGplayer bij dollars)."),
            P("Onder Jouw exemplaren staat elk exemplaar met plek en details:"),
            Steps(listOf(
                "− en + passen het aantal aan.",
                "Verplaatsen zet (een deel van) de stapel in een deck, of terug naar je collectie met map, blad en vak.",
                "Bewerken past afwerking, taal, conditie en proxy aan.",
                "Verwijderen haalt de exemplaren uit de app.",
            )),
            Note("Proxies worden grijs getoond en tellen niet mee in aantallen of waarde."),
        )),
        Chapter("decks", "Decks", "Decks maken, vullen vanuit je collectie en controleren.", listOf(
            H("Een nieuw deck"),
            Steps(listOf(
                "Ga naar Decks en tik op Nieuw deck.",
                "Plak de decklijst en tik op Volgende.",
                "Geef het deck een naam en kies het format. De app stelt er een voor (100 kaarten met een commander wordt Commander).",
                "Tik op Opslaan.",
            )),
            H("Kaarten uit je collectie in een deck zetten"),
            P("Een kaart zit óf in je collectie óf in een deck, nooit in allebei. Zo telt niets dubbel."),
            Steps(listOf(
                "Open het deck en tik op Uit collectie kiezen.",
                "Zoek op naam of type. Je ziet alleen kaarten die je hebt en die nog niet in een deck zitten, met hun plek.",
                "Tik een kaart aan, kies het aantal en het deel (Commander, Deck of Sideboard) en tik op In deck zetten.",
            )),
            P("Je kunt ook vanaf een kaart werken: kaartscherm → Verplaatsen → kies het deck."),
            H("Een lijst plakken in een deck"),
            P("Plak je een lijst in een nieuw of bestaand deck, dan kijkt de app per kaart of je hem al hebt. Zo ja, dan staat Uit collectie halen aan: de kaart verhuist uit je collectie. Alleen wat je niet (genoeg) hebt, komt er nieuw bij. Heb je een kaart extra gekocht, vink het dan uit."),
            Tip("De app pakt eerst dezelfde print en afwerking, dan dezelfde print, en pas daarna een andere print van dezelfde kaart. Proxies worden nooit uit je collectie gehaald."),
            H("Kaarten terug naar je collectie"),
            P("Tik in het deck op de kaart → Verplaatsen → Collectie. Verwijder je een heel deck, dan kies je Kaarten houden (terug naar de collectie) of Kaarten ook verwijderen."),
            H("Controles en bracket"),
            P("Bovenaan het deck staat of het legaal is in zijn format: aantal kaarten, sideboard, maximaal 4 of 1 exemplaar, verboden kaarten en commander-kleuren. Bij Commander staat een bracket-inschatting op basis van de Game Changers-lijst."),
            Note("De bracket is een inschatting. Combo's en snelle mana tellen ook mee: overleg met je tafel."),
            H("Delen en exporteren"),
            P("Met het deel-icoon stuur je de lijst naar WhatsApp, mail of Moxfield. Via ⋮ → Exporteren als tekst sla je hem op als bestand."),
        )),
        Chapter("overview", "Overzicht en prijzen", "Wat de cijfers betekenen en waar de prijzen vandaan komen.", listOf(
            P("Overzicht toont per set en per deck het aantal kaarten per rarity (M, R, U, C, O = overig), het totaal en de waarde. Daaronder staan Totaal sets, Totaal decks en de Totale waarde collectie."),
            Steps(listOf(
                "Prijzen komen via Scryfall: Cardmarket in euro's of TCGplayer in dollars (kies in Instellingen).",
                "Meestal is het de trendprijs, nooit de laagste prijs.",
                "Foils krijgen de foilprijs. Er is één prijs per print, ongeacht taal of conditie.",
                "Tik rechtsboven op het ververs-icoon om alle prijzen bij te werken.",
            )),
            Note("Proxies tellen als nul. Kaarten tellen maar één keer: in je collectie of in een deck."),
        )),
        Chapter("undo", "Een stap terug (wijzigingslog)", "Wat er bewaard wordt en hoe je iets ongedaan maakt.", listOf(
            P("Elke wijziging komt in het wijzigingslog: kaarten toevoegen, verplaatsen, bewerken, verwijderen, en decks maken, wijzigen of verwijderen."),
            Steps(listOf(
                "Ga naar Meer → Wijzigingslog.",
                "Tik op Laatste wijziging ongedaan maken.",
                "Nog een stap terug? Tik nog een keer. Ongedaan gemaakte regels worden doorgestreept.",
            )),
            Tip("Een heel deck dat je in één keer hebt opgeslagen, is ook één wijziging. Eén tik zet alles terug, ook de kaarten die uit je collectie kwamen."),
            Note("Niet ongedaan te maken: instellingen, prijsupdates en het terugzetten van een back-up. Maak daarom eerst een back-up voordat je er een terugzet."),
        )),
        Chapter("backup", "Back-up en nieuwe telefoon", "Je gegevens veilig bewaren en meenemen.", listOf(
            P("Alles staat op deze telefoon. Gaat hij kapot of koop je een nieuwe, dan heb je een back-up nodig."),
            Steps(listOf(
                "Ga naar Meer → Instellingen en back-up.",
                "Nu back-uppen: kies een plek, bijvoorbeeld Google Drive of Downloads.",
                "Wekelijkse automatische back-up: zet hem aan en kies een map. De app zet daar elke week een nieuw bestand neer.",
                "Op een nieuwe telefoon: installeer NineGrid, tik op Terugzetten en kies het back-upbestand.",
            )),
            Note("Terugzetten vervangt alles wat op de telefoon staat door de inhoud van de back-up."),
            H("Exporteren"),
            P("Collectie exporteren (CSV) maakt een bestand dat je in Google Sheets of Excel kunt openen."),
        )),
        Chapter("settings", "Instellingen", "Thema, taal, valuta en plaatjes.", listOf(
            Steps(listOf(
                "Thema: dag, nacht of volg telefoon.",
                "Taal: Nederlands, Engels of volg telefoon.",
                "Valuta: EUR (Cardmarket) of USD (TCGplayer).",
                "Afbeeldingen opslaan: uit, klein of volledig. Alleen plaatjes van kaarten die je hebt worden bewaard; gaat een kaart weg, dan verdwijnt het plaatje ook.",
                "Afbeeldingen alleen via wifi: bespaart mobiele data.",
                "Controleer op updates: de app kijkt ook zelf één keer per dag.",
            )),
        )),
        Chapter("help", "Problemen oplossen", "Als iets niet doet wat je verwacht.", listOf(
            H("De scanner herkent kaarten niet"),
            Steps(listOf(
                "Vul de set van het blad in; dan hoeft alleen nummer of naam gelezen te worden.",
                "Meer licht, geen schittering, telefoon recht boven het blad.",
                "Zorg dat het blad het kader vult en niet scheef ligt.",
                "Wat niet gevonden wordt, kies je in het controlescherm zelf via het vergrootglas.",
            )),
            H("Geen plaatje bij een kaart"),
            P("Controleer of je internet hebt (of wifi, als Alleen via wifi aan staat). In lijsten zie je geen plaatjes als Afbeeldingen opslaan op Uit staat. Lukt het niet, dan staat de reden in het vak van het plaatje."),
            H("Cardmarket blokkeert"),
            P("De knop Cardmarket opent de zoekpagina op kaartnaam. Blijft Cardmarket blokkeren, open de link dan in een andere browser of probeer het later opnieuw."),
            H("Een update installeren"),
            P("Krijg je de melding Update beschikbaar, tik dan op Downloaden en open het bestand. De nieuwe versie installeert over de oude heen; je kaarten en decks blijven staan."),
        )),
    )

    private val EN = listOf(
        Chapter("start", "Quick start", "From an empty app to your first cards in five steps.", listOf(
            P("NineGrid starts empty. Everything you add lives on your own phone and works offline; you only need internet to look up cards and prices."),
            Steps(listOf(
                "Tap Scan at the bottom and photograph a binder page of nine cards. Or tap + in Collection and choose Paste list or Search card.",
                "Check in the review screen that every card was found correctly.",
                "Choose where they go: your collection, a new deck or an existing deck, and tap Save.",
                "Browse your cards in Collection and your decks in Decks. Tap a card for its image, price and links.",
                "Back up now and then via More → Settings and backup, or turn on the weekly automatic backup.",
            )),
            Tip("Most screens have a ? at the top right. It opens the chapter for that screen."),
        )),
        Chapter("add", "Adding cards", "Scanning, an existing photo, pasting a list or searching one card.", listOf(
            H("Scanning"),
            Steps(listOf(
                "Tap Scan at the bottom. The first time, the app asks for camera permission.",
                "If all cards are from one set, enter its code under Set of this page, for example MH1. That makes recognition much more reliable. Mixed page? Leave it empty.",
                "Lay the page flat and hold the phone straight above it so the page fills the frame. The grid helps you line up the nine slots.",
                "Tap the round camera button. The app reads each card's name at the top and its set and number at the bottom.",
                "You land in the review screen (see the next chapter).",
            )),
            Tip("Good light without glare works best. Tilt foils slightly until the shine is gone."),
            Note("Photos are only read on your phone and never stored or sent."),
            H("Using an existing photo"),
            P("On the scan screen tap From photos and choose a photo from your gallery. The rest works the same as scanning."),
            H("Pasting a list"),
            P("Via Collection → + → Paste list, More → Paste list, or inside a deck. One card per line:"),
            P("1 Edgar Markov (INR) 328\n4 Murder (M20) 109 *F*\n1 Sol Ring (C21) 263 *E*"),
            Steps(listOf(
                "*F* after a line means foil, *E* means etched.",
                "A line Commander, Deck or Sideboard starts a new section. SB: before a line puts that card in the sideboard.",
                "Without headers, everything after a blank line is sideboard.",
                "Set and number may be missing; the app then searches by name.",
            )),
            Tip("Lists from Moxfield, Archidekt and MTG Arena can usually be pasted as they are."),
            H("Searching one card"),
            P("Tap + in Collection → Search card, type part of the name, pick the card and then the right print."),
        )),
        Chapter("review", "The review screen", "Checking cards before they are saved.", listOf(
            P("After scanning or pasting you see every card with image, set, number and quantity. Cards that were not found are shown in red."),
            Steps(listOf(
                "Not found? Tap the magnifier and search the card yourself. You can see what the scanner read, which helps.",
                "Wrong print? Tap the card → More options → Choose other print.",
                "Change the quantity with − and +. Toggle Foil with the button below.",
                "Under More options: finish (non-foil, foil, etched), language, condition (NM by default) and proxy.",
                "Don't want to save a card? Untick it on the left.",
                "At the top choose where the cards go: Collection, New deck or Existing deck.",
                "Tap Save at the bottom.",
            )),
            H("Binder, page and slot"),
            P("When you choose Collection you can enter a binder and page number. When scanning, each card then gets its slot automatically (1 top left, 9 bottom right). After saving, the page number moves up by one so you can scan the next page right away."),
            Note("Scan a page that is already in the app and you get a warning: Already scanned? You decide whether to add it anyway."),
            H("Commander"),
            P("Making a Commander deck? Tap the Commander button on the card that leads the deck. You can save without one, but the deck check will mention it."),
        )),
        Chapter("collection", "Your collection", "Searching, filtering, the card screen and locations.", listOf(
            Steps(listOf(
                "Search by name at the top. The buttons below filter by rarity, foil and colour.",
                "Include decks: on = also show cards that are in a deck; off = only what is loose in your collection.",
                "Sort by price or name. Cards are grouped by set.",
            )),
            H("The card screen"),
            P("Tap a card for its image, price (normal and foil), legality per format and links to Scryfall and Cardmarket (or TCGplayer when using dollars)."),
            P("Your copies lists every copy with its location and details:"),
            Steps(listOf(
                "− and + change the quantity.",
                "Move puts (part of) the stack into a deck, or back into your collection with binder, page and slot.",
                "Edit changes finish, language, condition and proxy.",
                "Delete removes the copies from the app.",
            )),
            Note("Proxies are shown in grey and do not count in totals or value."),
        )),
        Chapter("decks", "Decks", "Making decks, filling them from your collection and checking them.", listOf(
            H("A new deck"),
            Steps(listOf(
                "Go to Decks and tap New deck.",
                "Paste the decklist and tap Next.",
                "Name the deck and choose the format. The app suggests one (100 cards with a commander becomes Commander).",
                "Tap Save.",
            )),
            H("Putting cards from your collection into a deck"),
            P("A card is either in your collection or in a deck, never both. That way nothing is counted twice."),
            Steps(listOf(
                "Open the deck and tap From collection.",
                "Search by name or type. You only see cards you own that are not in a deck yet, with their location.",
                "Tap a card, choose the quantity and section (Commander, Deck or Sideboard) and tap Put in deck.",
            )),
            P("You can also work from a card: card screen → Move → choose the deck."),
            H("Pasting a list into a deck"),
            P("When you paste a list into a new or existing deck, the app checks each card against your collection. If you own it, Take from collection is on: the card moves out of your collection. Only what you don't own (enough of) is added as new. Bought an extra copy? Untick it."),
            Tip("The app takes the same print and finish first, then the same print, and only then another print of the same card. Proxies are never taken from your collection."),
            H("Cards back to your collection"),
            P("In the deck tap the card → Move → Collection. When deleting a whole deck you choose Keep cards (back to the collection) or Delete cards too."),
            H("Checks and bracket"),
            P("The top of the deck shows whether it is legal in its format: card count, sideboard, 4 or 1 copy limit, banned cards and commander colours. Commander decks get a bracket estimate based on the Game Changers list."),
            Note("The bracket is an estimate. Combos and fast mana count too: talk to your table."),
            H("Sharing and exporting"),
            P("The share icon sends the list to WhatsApp, mail or Moxfield. Via ⋮ → Export as text you save it as a file."),
        )),
        Chapter("overview", "Overview and prices", "What the numbers mean and where prices come from.", listOf(
            P("Overview shows per set and per deck the number of cards per rarity (M, R, U, C, O = other), the total and the value. Below are Total sets, Total decks and the Total value collection."),
            Steps(listOf(
                "Prices come from Scryfall: Cardmarket in euros or TCGplayer in dollars (choose in Settings).",
                "Usually the trend price, never the lowest price.",
                "Foils use the foil price. There is one price per print, regardless of language or condition.",
                "Tap the refresh icon at the top right to update all prices.",
            )),
            Note("Proxies count as zero. Cards are counted once: in your collection or in a deck."),
        )),
        Chapter("undo", "Going back (change log)", "What is kept and how to undo something.", listOf(
            P("Every change goes into the change log: adding, moving, editing and deleting cards, and creating, changing or deleting decks."),
            Steps(listOf(
                "Go to More → Change log.",
                "Tap Undo latest change.",
                "Another step back? Tap again. Undone lines are struck through.",
            )),
            Tip("A whole deck saved in one go is one change too. One tap reverts everything, including the cards that came from your collection."),
            Note("Cannot be undone: settings, price updates and restoring a backup. So make a backup before restoring one."),
        )),
        Chapter("backup", "Backup and new phone", "Keeping your data safe and taking it with you.", listOf(
            P("Everything lives on this phone. If it breaks or you get a new one, you need a backup."),
            Steps(listOf(
                "Go to More → Settings and backup.",
                "Back up now: choose a place, for example Google Drive or Downloads.",
                "Weekly automatic backup: turn it on and choose a folder. The app puts a new file there every week.",
                "On a new phone: install NineGrid, tap Restore and choose the backup file.",
            )),
            Note("Restoring replaces everything on the phone with the contents of the backup."),
            H("Exporting"),
            P("Export collection (CSV) creates a file you can open in Google Sheets or Excel."),
        )),
        Chapter("settings", "Settings", "Theme, language, currency and images.", listOf(
            Steps(listOf(
                "Theme: day, night or follow phone.",
                "Language: Dutch, English or follow phone.",
                "Currency: EUR (Cardmarket) or USD (TCGplayer).",
                "Store images: off, small or full size. Only images of cards you own are kept; when a card leaves, its image goes too.",
                "Images only on Wi-Fi: saves mobile data.",
                "Check for updates: the app also checks by itself once a day.",
            )),
        )),
        Chapter("help", "Troubleshooting", "When something doesn't do what you expect.", listOf(
            H("The scanner doesn't recognise cards"),
            Steps(listOf(
                "Enter the set of the page; then only the number or name has to be read.",
                "More light, no glare, phone straight above the page.",
                "Make sure the page fills the frame and isn't crooked.",
                "Anything not found can be picked yourself in the review screen with the magnifier.",
            )),
            H("No image for a card"),
            P("Check that you have internet (or Wi-Fi, if Only on Wi-Fi is on). Lists show no images when Store images is Off. If it still fails, the reason is shown where the image should be."),
            H("Cardmarket blocks"),
            P("The Cardmarket button opens the search page for the card name. If Cardmarket keeps blocking, open the link in another browser or try again later."),
            H("Installing an update"),
            P("When you see Update available, tap Download and open the file. The new version installs over the old one; your cards and decks stay."),
        )),
    )
}
