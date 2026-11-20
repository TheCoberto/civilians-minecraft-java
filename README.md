# Teams
**Minecraft Java Teams Mod (Fabric)**

Java Edition port of [teams](https://github.com/TheCoberto/teams) (Broken Crown Factions Script) for Bedrock. Controls player-vs-player damage. Teams are set up in game; nothing is hard-coded.

PvP between two players is blocked when:

- **They're on the same team.** Teammates can't hurt each other. Different teams can.
- **Either one is a civilian** (has the `Civilian` tag). Civilians can't hurt players and players can't hurt them. Mobs, falls and so on still hurt them.
- **Either one is in an adventure zone** (has the `inside_adventure_zone` tag). Give and remove that tag with command blocks around the zone.

Arrows, tridents and tamed pets count as the player who fired or owns them. Only PvP is blocked: players still take damage from mobs, falling, lava and everything else.

Requires Minecraft Java **1.21.1**, [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://modrinth.com/mod/fabric-api). Server-side: it has to be installed on the server (or in single player). Players who join don't need it.

## Commands

Operators:

- `/teamcreate <team>` creates a team. Names are 1-24 letters, numbers, `_` or `-`.
- `/teamdelete <team>` deletes a team and takes everyone online off it.
- `/teamjoin <players> <team>` puts players on a team, taking them off any other.
- `/teamleave <players>` takes players off their team.
- `/civilian <players> true|false` makes players civilians or not.

Anyone: `/teams` lists the teams and who's online on each.

A player is on a team when they have a tag with the team's name, so `/tag <player> add <team>` and selectors like `@a[tag=<team>]` work too. Teams are saved in the world.

## Differences from the Bedrock version
- Commands have no `ev:` prefix: `/teamcreate` instead of `/ev:teamcreate`, and so on.
- These teams are separate from Java's built-in `/team` scoreboard teams.

## Build
```
./gradlew build
```
Building needs JDK 25 or newer to run Gradle (the mod itself targets Java 21, which Gradle downloads automatically). The jar ends up in `build/libs/`. Put it in your `.minecraft/mods` folder along with Fabric API. `./gradlew runClient` starts a test game with the mod loaded.
