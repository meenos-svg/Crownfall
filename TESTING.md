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
