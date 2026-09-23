# ESN SMP

ESN SMP is a large all-in-one **Paper 1.21.x** survival/RPG server plugin built for the ESN SMP. It combines the server's economy, progression, RPG systems, moderation tools, custom spawn, events, PvP systems, crates, bosses, guilds, player utilities, and endgame content into one plugin.

> **Current server plugin version:** 2.4.5  
> **Java:** 21  
> **Paper API used by the build:** 1.21.4  
> **Latest verified build:** GitHub Actions Build #458 — PASS  
> **Verified commit:** `c29f56b31cf17da7ea7e1a5762fe55b15b8a1e67`

## Download the current JAR

**Current production JAR: ESNSMP Main Build #458**

[Download ESNSMP Main Build #458](https://github.com/sheldonrocks2022-cmyk/ESNSMP/actions/runs/35801465372)

Build #458 was produced from **main** at commit `c29f56b31cf17da7ea7e1a5762fe55b15b8a1e67` and completed successfully. Open the workflow run above and download the **ESNSMP** artifact; it contains the current shaded `ESNSMP.jar`.

Artifact SHA-256: `304285669eddd9e6d084336bc9aac68b7780485e4463f06f9a4ef7039085dd07`

The JAR includes SQLite JDBC, so no separate SQLite plugin is required.

**Replace older ESNSMP JARs with Build #458 and keep only one ESNSMP JAR in the server's plugins folder before a full restart.**

## Requirements

- Paper 1.21.x server (the project currently compiles against Paper 1.21.4)
- Java 21
- A normal Paper `plugins/` directory
- Appropriate filesystem permissions so ESNSMP can create its configuration and SQLite databases

## Installation

1. Download the JAR from the latest successful GitHub Actions **ESNSMP** artifact.
2. Stop the Minecraft server.
3. Put `ESNSMP.jar` in the server's `plugins/` folder.
4. Start the server.
5. Check console for `ESNSMP v2.4.5 enabled.`
6. Verify the command audit reports all declared commands available.
7. Configure permissions/ranks before giving staff access.

Back up the world and plugin data before replacing an existing production build.

---

# Major systems

## Server Championships

Staff can start a server-wide Championship with `/championship start`.

- All online players are entered automatically.
- A Championship is allowed to start with only one online competitor.
- If there is exactly one competitor, that player wins automatically.
- With multiple competitors, two players are staged to fight while remaining competitors are placed on the sidelines.
- Players can leave with `/championship forfeit`.
- Staff can inspect the event with `/championship status`.
- Staff can stop it with `/championship cancel`.
- The Championship system generates a dedicated **150×150** arena near the server spawn area.
- The arena uses a polished-deepslate floor, iron-bar perimeter and torch lighting.
- Creature spawning is blocked around the arena.

> Championship tournament advancement/winner persistence is still being expanded. The current source contains the arena, mandatory entry, staging, forfeiting, staff controls and solo automatic-win behavior.

## Realm progression — 1 to 100

`/realm` provides a 100-Realm progression path.

- 100 Realm levels.
- Exclusive reward for every Realm.
- Realm reward GUI with multiple pages.
- Realm unlock requirements use ESN level and discoveries.
- Reward claims are stored so each Realm reward can only be claimed once.
- Realm-exclusive gear scales with Realm progression.
- Every 10th Realm introduces an **Ascension Trial**.
- Trial completion is stored in SQLite.
- Ascension Trials use a sealed combat cave and zombie objectives.
- `/discoveries` tracks exploration discoveries.
- `/story` exposes the world/story progression.
- `/guildhq` provides Guild Headquarters progression.

## Economy

Built-in ESN Coin economy:

- `/balance` / `/bal` / `/money`
- `/pay <player> <amount>`
- Configurable starting balance.
- Persistent player balances.
- Economy integration throughout rewards, shops, bounties and progression.

## Auction House

`/ah` / `/auction` / `/auctionhouse`

Supports selling, listing management, cancellation and claiming through the ESN Auction House. Listing limits and maximum prices are configurable.

## Outlaw and bounty PvP

ESN includes two related PvP reward systems.

### Outlaws

- Killing a non-Outlaw player can mark the killer as an Outlaw.
- Active Outlaws are viewable through `/outlaws`.
- Killing an Outlaw awards **300,000 ESN Coins**.
- The defeated player's Outlaw status is cleared.
- Outlaw information is persisted in SQLite.

### Player bounties

`/bounty` supports the separate placed-bounty system, allowing ESN Coin bounties to be associated with players.

## ESN Prison / Jail

`/jail` gives staff access to the ESN prison control system.

Current jail features include:

- 12 jail cells.
- Staff jail management GUI/commands.
- Persistent jailed state.
- Prisoner movement restrictions.
- Block breaking/placing restrictions.
- Command restrictions for prisoners.
- Inventory, armor and offhand protection/restoration.
- Re-teleporting jailed players when they reconnect.
- Release support.

Timed sentence/automatic-expiry functionality is being developed and should not be treated as complete until it is present in a verified build.

## Custom ESN spawn

The plugin contains a large generated ESN spawn/hub system.

- `/spawn`
- `/setspawn`
- `/esnspawn build`
- `/esnspawn rebuild`
- `/esnspawn status`
- `/esnspawn rollback`
- Spawn NPC/service integration.
- Spawn protection.
- Large mob-free spawn region.
- Build safety/rollback state.
- Hub services can be respawned after the generated spawn loads.

The generated ESN spawn has safety checks intended to avoid blindly replacing an unrecognized existing generated world. Always keep world backups.

## Mega Castle

The plugin includes ESN's Mega Castle systems, Castle Services and the `/castlecore` staff command for the custom Castle Core item.

## Crates

Multiple generations of crate systems are included:

- Standard crates.
- Mythic crates.
- Extended crates.
- **100 Mega/Realm crate tiers**.
- `/megacratekey <1-100>` for authorized administrators.
- Realm-scaled crate content.
- Custom ESN items via `/esnitems`.

## RPG progression

The plugin combines several generations of ESN RPG systems:

- Levels and ranks.
- Jobs.
- Skills.
- Prestige.
- Titles.
- Collections.
- Bestiary.
- Career statistics.
- Achievements.
- Milestones.
- Gear upgrades.
- Blacksmith.
- Salvage.
- Reforging.
- Forge.
- Relics.
- Runes and Rune Forge.
- Eternal equipment ascension.
- Set bonuses.
- Challenges and reward roads.
- RPG profiles.
- Progression rotations.

Useful commands include `/level`, `/jobs`, `/skills`, `/prestige`, `/titles`, `/collections`, `/bestiary`, `/stats`, `/blacksmith`, `/salvage`, `/reforge`, `/forge`, `/relics`, `/runes`, `/runeforge`, `/gearupgrade`, `/rpgprofile` and `/progression`.

## Adventure Engine

ESN's Adventure Engine adds higher-level RPG activities:

- Combat classes.
- Class ultimates.
- Rift encounters.
- Five-boss Boss Rush.
- Persistent server records.
- Adventure guild progression.
- Trophy rooms.
- Rotating bounty-board content.
- Mythic Hunts.
- Artifact fusion.
- Adventure achievements.
- Teammate revive mechanics.

Commands include `/adventure`, `/class`, `/ultimate`, `/rift`, `/bossrush`, `/records`, `/guild`, `/trophies`, `/bountyboard`, `/hunt`, `/artifactfusion`, `/adventureachievements` and `/revive`.

## Boss and endgame content

Systems include:

- Boss encounters.
- Biome bosses.
- Boss health bars.
- Boss Codex.
- Boss drops.
- Titan/boss summoning.
- Dungeons.
- Raids.
- Endless Trials.
- Realm Mastery.
- Boss pets.
- Treasure systems.
- Artifacts.
- World events.
- Community goals.
- Supply drops.
- Mystery Merchant.
- Fishing events.
- Chaos modifiers.
- KOTH.

## Season systems

ESNSMP contains season/progression-pass systems including:

- `/season`
- `/season2`
- `/seasonpass`
- `/pass`
- 100-tier RPG Season Pass content in the current progression implementation.
- Realm keys, coin rewards and special rewards across progression.

## Adventure Journal and Welcome Guide

`/journal` opens the Adventure Journal, which acts as a central navigation hub for major ESN systems.

The plugin also includes a Welcome Guide for new players covering important server features and progression paths.

## Homes, warps and teleportation

Player utilities include:

- `/sethome [name]`
- `/home [name]`
- `/delhome [name]`
- `/homes`
- `/warp <name>`
- `/warps`
- `/setwarp <name>`
- `/delwarp <name>`
- `/rtp`
- `/tpa <player>`
- `/tpahere <player>`
- `/tpaccept`
- `/tpdeny`
- `/back`
- `/tptoggle`

## Land claims

`/claim` provides base protection with position selection, claim creation/removal, trust/untrust and claim information.

## Chat and messaging

ESN has a custom chat layer with:

- Global chat.
- Local chat.
- Staff chat.
- Private messages.
- Replies.
- Public-chat toggle.
- Chat settings GUI.
- Rank + level + equipped-title identity formatting.
- Staff/owner permissions remain permission-based rather than being determined by cosmetic titles.

Commands include `/msg`, `/reply`, `/staffchat`, `/globalchat`, `/localchat`, `/chattoggle` and `/chatmenu`.

## Teams and secure trading

- Team creation/join/leave/info.
- Secure player trading.
- Trade GUI.
- Party systems and party invitations.

## Daily/player progression utilities

The plugin also contains:

- Daily rewards.
- Login streaks.
- Quests.
- Leaderboards.
- Profiles.
- Graves/last-death location.
- AFK status.
- Event calendar.
- Tutorial/guide systems.
- Server menus.
- Scoreboard.
- Loot drops.

---

# Staff and moderation

ESNSMP contains an integrated staff/moderation suite.

## Staff tools

- `/mod` / `/modmenu` — moderation menu.
- `/admin` — Admin Control Center.
- `/staff` — staff command help.
- `/invsee` — inspect inventories.
- `/ecsee` — inspect Ender Chests.
- `/freeze` — freeze a player.
- `/warn` — warn a player.
- `/mute` — mute a player.
- `/history` — moderation history.
- `/report` — player reports.
- `/note` — persistent staff notes.
- `/jail` — prison controls.
- `/diagnostics` and `/bossdiag` — diagnostics.

## Staff permissions

Important permission nodes declared by the plugin include:

- `esnsmp.owner`
- `esnsmp.staff`
- `esnsmp.staff.helper`
- `esnsmp.staff.trialmod`
- `esnsmp.staff.moderator`
- `esnsmp.staff.seniormod`
- `esnsmp.staff.admin`
- `esnsmp.admin`
- `esnsmp.spawn.bypass`
- `esnsmp.claim.bypass`
- `esnsmp.anticheat.alerts`
- `esnsmp.items`
- `esnsmp.chat.color`
- `esnsmp.chat.mention`

Owner/admin powers are controlled by permissions. Cosmetic titles do **not** remove owner/staff authority.

---

# Persistence and safety

ESNSMP uses persistent data and several SQLite-backed systems. Important progression/economy information is not intended to disappear simply because the server restarts.

The plugin follows several defensive behaviors:

- Core database initialization failure disables the plugin rather than continuing with unsafe player-data behavior.
- Optional subsystem failures are caught so one optional system does not necessarily bring down the entire core.
- A fallback executor warns when a declared command's subsystem failed to initialize.
- Database connections are closed during plugin shutdown.
- Spawn building includes incomplete-build/rollback handling.
- GitHub Actions compiles and verifies builds before the JAR artifact is published.

**Production operators should still make regular backups of the world, plugin folder and SQLite databases.**

# Building from source

Clone the repository, check out the active development branch and run:

```bash
mvn clean verify
```

The Maven project uses Java 21 and the Maven Shade Plugin. The packaged output is:

```text
target/ESNSMP.jar
```

The GitHub Actions workflow performs the repository's normal compile/verify process and uploads the successful JAR as the **ESNSMP** artifact.

# Project structure

Main plugin class:

```text
src/main/java/com/esn/smp/ESNSMPPlugin.java
```

Plugin metadata and commands:

```text
src/main/resources/plugin.yml
```

Major gameplay implementations live primarily under:

```text
src/main/java/com/esn/smp/gameplay/
```

Spawn/hub code is under the spawn package, with listeners, commands, economy, auction and data-storage components separated into their respective packages.

# Current development status

ESNSMP is an actively developed custom server plugin. Some systems are mature while others are still being expanded. A successful compile confirms that the source builds; it does **not** replace testing on a staging Paper server before production deployment.

At the time of this README update:

- GitHub Actions Build #440 passed.
- The verified commit is `ca9a94dadae821c76e58936de429046d8375bd32`.
- Plugin metadata reports ESNSMP **2.4.5**.
- Championship solo-start/automatic-win support is included.
- Realm Ascension Trial SQL compilation was fixed and verified by Build #440.
- Timed prison sentences are planned/in development and are not being represented here as completed.

# License / redistribution

Before redistributing or operating this plugin outside ESN SMP, review the repository's license and any third-party dependency licenses. If no explicit repository license is present, do not assume additional redistribution rights beyond what the repository owner has granted.

---

Built for **ESN SMP**.
