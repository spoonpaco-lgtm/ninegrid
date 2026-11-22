# NineGrid scanner-testset (18 bladen, 11, 17 en 22 november 2026)

Staat in de repo in `testset/`. Een nieuw gesprek vindt de bladen hier.

Elke map is één blad: `foto.jpg` en `scanrapport.txt` horen bij elkaar. Staat er ook `foto-2.jpg` met `scanrapport-2.txt`, dan is dat dezelfde pagina opnieuw gescand (nieuwere versie, meestal grotere foto).
De foto's zijn de verkleinde versie uit het gesprek (1200x1600 of 1500x2000); het rapport is gemaakt van de galerijfoto van 1530x2040.

| Map | Blad | Rapport gemaakt | App-versie | Opmerking |
|---|---|---|---|---|
| 01-ONE-mondrak | ONE, 9 kaarten, eerste kaart Mondrak, Glory Dominus | 11 nov 14:09 | 0.9.8 | vak 1 gelezen als set EATUR/ES (verzonnen taal) |
| 02-ONE-kinzu | ONE, rij 1 leeg + Kinzu of the Bleak Coven | 11 nov 16:58 | 0.9.8.2 | lege rij 1; Kinzu en Bilious Skulldweller niet gevonden |
| 03-WOT-fraying-sanity | Wilds of Eldraine: Enchanting Tales (WOT), rijen 1 en 3 grotendeels leeg | 11 nov 16:58 | 0.9.8.2 | raster uitgerekt, kaarten in verkeerde vakken |
| 04-WOT-leyline | WOT, 9 kaarten, eerste kaart Leyline of the Void | 11 nov 16:59 | 0.9.8.2 | Stab Wound als RVR, Raid Bombardment niet gevonden |
| 05-AVR-lightning-mauler | Avacyn Restored, 9 kaarten | 17 nov 12:21 | 0.9.11 | vak 8 en 9 oranje |
| 06-RTR-wayfaring-temple | Return to Ravnica (met een foil en Guild Kit-kaart), 9 kaarten | 17 nov 12:27 | 0.9.11 | PLST-prints en GK1: print kiezen is lastig |
| 07-DOM-wizards-lightning | Dominaria, 5 kaarten, vakken 3, 5, 6 en 9 leeg | 17 nov 12:31 | 0.9.11 | |
| 08-gemengd-shock | Gemengde sets op één blad (STA, DTK, SOI, AER, C21), vakken 2, 3, 7 en 9 leeg | 17 nov 12:34 | 0.9.11 | promo's en foils, app kiest eerst verkeerde print |
| 09-WAR-fblthp | War of the Spark, 9 kaarten | 17 nov 12:38 | 0.9.11 | |
| 10-GRN-underrealm-lich | Guilds of Ravnica, 9 kaarten waaronder 3 splitkaarten | 17 nov 13:05 en 13:07 | 0.9.11 | foto-2 is dezelfde pagina; vak 9 in foto 1 leeg gelezen |
| 11-ONE-mirrex-landen | ONE en WOE landen, vak 7 leeg | 17 nov 13:17 | 0.9.11 | |
| 12-LCI-huatli | Lost Caverns of Ixalan, 7 kaarten, vak 3 en 6 leeg | 22 nov 13:50 | 0.9.15 | Huatli showcase, twee foils zonder leesbaar nummer |
| 13-LCI-belligerent | LCI, 7 kaarten, vak 2 en 3 leeg | 22 nov 13:51 (zonder set) en 13:52 (set LCI) | 0.9.15 | twee keer Caparocti Sunborn: foil gewone print en showcase |
| 14-DGM-gaze-of-granite | Dragon's Maze, 9 kaarten, twee splitkaarten (vak 2 en 9) | 22 nov 13:55 | 0.9.15 | splitkaarten op hun kant |
| 15-GTC-hold-the-gates | Gatecrash, 6 kaarten, vak 2, 5 en 6 leeg | 22 nov 13:59 | 0.9.15 | Realmwright foil |
| 16-XLN-sanctum-seeker | Ixalan, 8 kaarten, vak 6 leeg | 22 nov 14:01 | 0.9.15 | lamp aan |
| 17-DOM-history-of-benalia | Dominaria, 9 kaarten | 22 nov 14:02 (set DOM) en 14:04 (zonder set) | 0.9.15 | Josu Vess foil |
| 18-DMR-denizen | Dominaria Remastered met 2 DMU-kaarten, vak 3, 6 en 9 leeg | 22 nov 14:06 | 0.9.15 | Denizen in oud kader, Tura Kennerüd showcase; gescand met set DMR |

Bladen 12 t/m 18 (22 nov) zijn camerafoto's van 1080x1440 uit het scanrapport.

De bladen 01 t/m 04 hebben ook `foto-2.jpg` en `scanrapport-2.txt`: dezelfde pagina's, op 17 nov gescand met 0.9.11 (rapporten van 13:11 tot 13:15).

Bron: de map "Scanner ninegrid" in Micks Google Drive.

Per map staat ook `uitslag.txt`: de juiste kaart per vak, door Claude van de foto gelezen. Mick controleert ze nog. Bij de nieuwe bladen (05 t/m 11) alleen namen, zonder set en nummer.

## Kader

Optioneel: `kader.txt` (bij `foto-2.jpg`: `kader-2.txt`) met "links boven rechts onder" in pixels van de foto. Dan leest de testset die foto ook met dat kader. De met de hand gezette kaders van 18 nov maakten het slechter (bijv. AVR 2 van 9 tegen 9 van 9 zonder kader) en zijn weggehaald; als galerijfoto kwam het overeen met wat de camera op dezelfde bladen las.

## Meting 0.9.12 (18 nov, als galerijfoto)

107 van 144 goed (was 98) · groen maar fout 2 (was 8). Grootste verlies: GRN (0 en 1 van 9), DOM 4 van 9, gemengd 3 van 9.

## Nulmeting 0.9.11 (18 nov, Samsung SM-A165F, als galerijfoto)

98 van 144 vakken goed · groen maar fout 8 · oranje maar goed 24 · niet gevonden 33. Groen maar fout: Raid/Goblin Bombardment, Gloomwidow/Gloom Surgeon, Golgari/Azorius Keyrune, Selesnya Keyrune/Sentry, Knight of Autumn/Maximize Altitude, Nightveil Predator/Sprite, Feed the Infection/Reject Imperfection, Shock STA/MSC. 29 van de 33 niet gevonden op DOM, WAR, GRN en het gemengde blad.
