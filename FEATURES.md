# NineGrid – features

*Nederlands: [FEATURES.nl.md](FEATURES.nl.md)*

Current version: **0.9.0 – Jace**. Every version is named after a planeswalker.

## What makes NineGrid different

- **A whole binder page in one photo, with its place.** Nine cards per photo, and every card is filed at its binder, page and slot straight away.
- **Undo everything.** Every change can be reverted, even a whole import or a whole deck.
- **Price movement separate from what you add.** The chart shows how much your collection rose or fell because of prices, not because you bought more cards.
- **A trade match based on your decks.** Not just "you want X, they have X": NineGrid works out which of a friend's cards actually improve your decks, and the other way round, with prices.
- **Sharing without an account.** A small file by WhatsApp or e-mail is enough; exports from other apps work too, for friends without NineGrid.
- **Deck suggestions from your own collection.** What to add and what to cut, based on what the deck lacks, with your own rules (tribe, maximum bracket, splash). You review every swap first.
- **Everything about a deck on your phone.** Bracket, combos and almost-combos (with where the missing card is in your collection), mana base versus costs and opening-hand odds.
- **AI with your own key.** Choose from eight services; every AI result is checked at Scryfall before it reaches your collection.
- **Free, no limits, no account.** No caps on decks or exports, no subscription, no tracking. Your collection lives on your own phone.

## Adding cards
- **Scan a binder page of 9 cards** in one photo, or **one card at a time** with automatic capture. Text recognition runs on your phone.
- Reads name, set and collector number, including **foreign-language cards** (the printed name is checked, the English print supplies the price).
- **Set field**: fill in the set of a page for much more reliable recognition; remembered for the next scan.
- **Foil per slot**: tap the small grid of the last photo, then tap a slot (or All foil).
- Result in colour (● found, ● to check), with a **Check** button showing how many need a look.
- **Review screen**: your photo next to the found card, search a card yourself, quantity, foil, language, condition, proxy.
- **Paste a list** in Moxfield / Arena style: `1 Edgar Markov (INR) 328`, `*F*` foil, `*E*` etched, Commander / Deck / Sideboard.
- **Import** your MTG Collection Sheet (Excel) or exports from Moxfield, Archidekt, ManaBox, Deckbox, Dragon Shield, TCGplayer, Delver Lens and Helvault. Runs in the background and resumes after a restart.
- Optional **offline card database** (all ~110,000 cards): scan and look up without internet, much faster imports.

## Collection
- Search, **filter** (rarity, colour, type, foil, also in decks, rules text) and **sort** (set, name, price); filters are remembered.
- **Card screen**: image, normal and foil price, legality per format, links to Scryfall and Cardmarket / TCGplayer.
- **Locations**: binder, page and slot per card.
- **Sets screen**: completeness per set, missing cards, variants; **Completionist** counts only cards in binders, not in decks.

## Decks
- Decks per format (Commander, Modern, Pioneer, Standard, Legacy, Pauper, Casual) with **legality check**.
- **Commander bracket estimate**: game changers, mass land denial, extra turns, tutors and two-card combos.
- **Statistics**: mana curve, colours in costs versus the lands that make them, odds of 2–4 lands in the opening hand.
- **Combos** via Commander Spellbook: combos in the deck, almost-combos missing one card (and where that card is in your collection). Shows when the deck changed since the last search.
- **Suggestions** from your own collection: what to add, better lands, what to cut, with your own deck rules (tribe, maximum bracket, splash). Review every swap before it happens.
- Fill a deck from your collection; cards move, nothing is counted twice.

## Overview and prices
- Prices from **Cardmarket (EUR)** or **TCGplayer (USD)** via Scryfall, refreshed daily in the background.
- **Value over time** from day one, per line: total, sets, decks, a single deck or set; also the **price movement** without additions and removals.
- **Winner of the Week**: the card that gained you the most.
- **Breakout** alert: a card you own rises at least €3 and 25% in a day.

## Share and trade
- **Share your collection as a small file** (WhatsApp, e-mail …) under your own name; choose everything, the loose collection, binders, sets or decks. No prices and no proxies are sent.
- **Collections of others** per person, view only. Sending again replaces the old version. Also exports from other apps, for friends without NineGrid.
- **Trade match**: which of their cards improve your decks and the other way round, with prices; tick cards to see whether a trade is about even.

## AI help (optional, off by default)
- Your own key for Google Gemini (free tier), Claude, OpenAI, DeepSeek, Mistral, OpenRouter, Groq or a custom address.
- Reads only doubtful slots when scanning; every result is checked at Scryfall. AI label per slot: busy, done or failed (with the reason).
- Deck suggestions from your collection or beyond ("Consider buying"), checked for colours, format and bracket.
- Clear error messages and quiet retries when a service is busy; daily maximum. The key is stored encrypted on the phone and not in the backup.

## Safety and comfort
- **Change log with undo**: every change, also a whole import or a whole deck, can be undone.
- **Backup** by hand or weekly to a folder of your choice; restore on a new phone; CSV export.
- Settings in eight groups; date and time format of your choice; day and night theme; English and Dutch.
- **Guide** in the app with search, a ? on every screen, and **What's new** after an update.

## Planned
Plans, not promises. See the release notes for what actually shipped.

**0.9.1 – Kaya**
- Update from inside the app (download and install with one tap)
- Search all Magic cards, with "only what I don't have"
- Wishlist (any or a specific print, quantity, crossed off when scanned, shared with friends)
- Trading easier to find (Overview, deck suggestions "At friends", card screen)
- Clearer share screen
- Honest onboarding about offline use, with the option to download the card database
- New logo and app icon
- Binders: filter, sort per binder and a binder page view (3×3 pages, empty slots visible)
- Completionist as a checkbox with explanation
- Privacy mode: hide prices (styles: compleated, seal, shadow, dots); turning it off needs your fingerprint

**0.9.2 – Saheeli**
- Draw a sample hand; foil shine; rarity colours in set completeness; small Magic touches
- Booster opening: what you paid versus what came out (also without bulk), boxes
- Guild colours as theme
- Price alert for cards on your wishlist

**1.0**
- Google Sheet sync
- Share a deck by QR code

## Privacy
No account, no tracking, no ads. Card names and numbers go to Scryfall; deck card names go to Commander Spellbook when you search for combos. Photos stay on the phone, unless you turn on AI help: then only the crop of a doubtful slot goes to the AI service you chose.
