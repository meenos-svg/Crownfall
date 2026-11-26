# Crownfall test queue

Things built but not yet tested on a real phone. Tick them off as you go.
Most need a second Google account (a friend or a second phone).

## Alliances (SQL part 5)
- [x] Create an alliance (Herald posts "founded")
- [x] Alliance chat sends and shows up
- [x] Request aid appears in the aid list
- [ ] Second account joins an open alliance
- [ ] Invite a lord to an invite-only alliance; they accept / decline
- [ ] Donate to someone else's aid request (resources move, donor gets honor)
- [ ] Contribute to alliance tech (costs 150 gold, XP goes up, levels at the bar)
- [ ] Promote / demote an officer
- [ ] Transfer leadership, then leave
- [ ] Kick a member
- [ ] Leader alone leaves → alliance disbands

## War (SQL part 6)
- [ ] Gather from a resource site: troops leave, come back with the reward
- [ ] Raid a Shadow Bastion: report shows in the War Room with losses and plunder
- [ ] Recall a march before it lands: troops come home
- [ ] A 4th march is refused (3 slots)
- [ ] Raid a shielded player is refused
- [ ] Raid an unshielded second account: both lose troops, loot moves, both see a report (theirs says DEFENSE)
- [ ] Attacking drops your own Peace Shield
- [ ] Defender's alliance chat gets a Herald message about the raid
- [ ] Launch a rally: alliance chat shows the WAR RALLY call
- [ ] Second account joins the rally (sends all troops at home)
- [ ] Leader taps Launch Strike: rally shows as LAUNCHED with a countdown
- [ ] Rally lands: report for both, loot split by power sent
- [ ] Your march lands while your app is closed: report is there next time you open it

## Painted realm maps
- [ ] Each realm shows its painted map (Crownlands green, Aether Reach misty, Emberfall volcanic)
- [ ] Bastions, nodes and landmarks sit on land, not in lakes or lava
- [ ] Zooming in still looks sharp enough
- [ ] No castles in lakes, lava or mountains (existing ones were moved)
- [ ] Relocating / camping / raising a stronghold in water or lava is refused with a clear message

## Painted map sprites
- [ ] Castles show as painted castles (small keep → stone castle at Keep 6 → grand citadel at Keep 13)
- [ ] Coloured pennant on each castle: gold you, blue alliance, red others; green dome when shielded
- [ ] Shadow Bastions are the purple ruin; the Obsidian Citadel is the volcano fortress
- [ ] Strongholds use the painted fortress with siege ring, aid beacon and wall bar still showing
- [ ] Landmarks (mine, quarry, lumberyard, granary, shrine), resource sites and visitor camps are painted too

## Avatars (SQL part 14)
- [x] Tap the circle at top-left → picker opens
- [x] Pick a crest: it shows in the top bar with the Keep level badge
- [ ] Pick Google: your Google photo shows
- [x] The 10 painted portraits appear in the picker and look right in the top bar
- [x] Your photo (SQL part 17): pick a photo from the phone → it becomes your avatar; others see it on your castle
- [ ] Tap another castle on the map: their avatar shows in the sheet

## Lord profiles (SQL part 16)
- [ ] Tap your avatar → your profile: title, alliance, realm, motto, honor bar, war record
- [ ] Edit motto, rename and change avatar from your profile
- [ ] Tap another castle → View profile → their card (AI lords show their motto)
- [ ] Alliance roster rows show avatars and open profiles
- [ ] As leader/officer, Invite to alliance from an unaffiliated lord's profile

## Citadel
- [ ] Chimney smoke and building colours look right on your phone

## Strongholds & pacts (SQL part 7)
- [x] Leader long-presses the map → raise stronghold (3,000 Stone, 2,000 Gold); it appears for everyone
- [ ] Too close to a bastion/castle/other stronghold is refused
- [x] Member reinforces the stronghold; their name shows under Defenders
- [ ] Withdraw my troops brings them home instantly
- [x] Call for aid works (still to check with a 2nd account: outsider can't reinforce; after Call for aid they can (shows "relief"), gold beacon on the map)
- [ ] Enemy Siege: walls drop, red ring pulses, Herald tells the alliance (and pact allies)
- [ ] Enemy rally on a stronghold
- [ ] Walls hit 0 → stronghold falls, garrison comes home, attacker gets 1,500 gold (rally: 3,000 shared)
- [ ] 30 minutes with no attacks → walls repaired, siege and call for aid end
- [ ] Offer a pact from the Pacts tab; other leader accepts → both chats get "SEALED"
- [ ] Pact allies can't raid each other's castles or strongholds; can reinforce each other
- [ ] End a pact

## Realms & expeditions (SQL part 8)
- [ ] Realm chips at the top of the map switch between Crownlands, Aether Reach and Emberfall Wastes
- [ ] Each realm has its own landscape, kingdoms and strongholds
- [ ] In another realm, long-press → Expedition dialog → camp appears faded, then solid after ~90s
- [ ] From the camp: raid a bastion / gather; survivors return to the camp, loot goes home
- [ ] Raid a local kingdom from the camp
- [ ] Raiding a kingdom in another realm from home is refused ("Set up a camp there first")
- [ ] A local raids your camp: if they win, the camp is routed and survivors come home
- [ ] Recall: banner counts down, troops arrive home
- [ ] Alliance leader camped abroad can raise the stronghold in that realm
- [ ] Rally button is hidden abroad
- [ ] New accounts land in the emptiest realm

## Landmarks & territory (SQL part 9)
- [ ] Six landmarks per realm on the map (mine, quarry, lumberyard, granary, cold mine, shrine)
- [ ] Territory zones tint around castles, strongholds and held landmarks (gold you, blue alliance, red rivals)
- [ ] Defeat a landmark's guardians → it flies your alliance banner, your survivors become its garrison
- [ ] Alliance member reinforces a held landmark
- [ ] After a while the garrison gets paid (check gold/stone/etc. go up); the Shrine raises alliance tech XP
- [ ] Withdraw my troops pays your share and brings them home
- [ ] A rival alliance attacks it: if they win it changes hands and the old garrison goes home
- [ ] Capture a landmark in another realm from your camp

## AI lords (SQL part 12)
- [ ] Six AI lords per realm appear on the map with "(AI)" after their name
- [ ] Their marches move around the map (gathering, bastions, landmarks)
- [ ] Each realm's bot alliance ([IRON], [MIST], [ASH]) holds a couple of landmarks; you can take them
- [ ] Raid a weak AI lord (Keep 2-3): loot and a battle report
- [ ] After you raid a bot, it may strike back within a day
- [ ] AI lords never attack you while your Peace Shield is up

## Red Dragon world boss (SQL part 18)
- [ ] A red dragon with a health bar shows on the map in your realm (it's also first in the target strip)
- [ ] Tap it: the sheet shows level, HP, "flies off in", the hoard and a damage estimate
- [ ] Strike it: the army marches, the HP drops, some troops come home burned, and the report says STRUCK
- [ ] Sound a War Rally on it: alliance members can join; the rally hits harder
- [ ] The leaderboard lists lords and alliances by damage; tapping a lord opens their profile
- [ ] Kill it: everyone who hit it gets resources by share, the killer gets bonus gold, and the alliance chat announces it
- [ ] After a kill, the map says when the next dragon lands (2 hours later, one level higher)
