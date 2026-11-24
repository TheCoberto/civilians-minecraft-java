# Civilians
**Minecraft Java Civilians Mod (Fabric)**

Java Edition port of the civilian and adventure zone parts of [teams](https://github.com/TheCoberto/teams) (Broken Crown Factions Script) for Bedrock. Adds civilians and adventure zones, where players can't hurt each other. Teams themselves use Java's built-in `/team` command.

PvP between two players is blocked when:

- **Either one is a civilian** (has the `Civilian` tag). Civilians can't hurt players and players can't hurt them. Mobs, falls and so on still hurt them.
- **Either one is in an adventure zone** (has the `inside_adventure_zone` tag). Give and remove that tag with command blocks around the zone.

Arrows, tridents and tamed pets count as the player who fired or owns them. Only PvP is blocked: players still take damage from mobs, falling, lava and everything else.

Lava, flint and steel and fire charges can't be used within 5 blocks of a civilian or someone in an adventure zone, and civilians can't use them within 5 blocks of other players, since lava and fire hurt without counting as a player's attack. Blocked hits don't knock back either.

Requires Minecraft Java **26.2**, [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://modrinth.com/mod/fabric-api). Server-side: it has to be installed on the server (or in single player). Players who join don't need it.

## Commands

- `/civilian <players> true|false` (operators) makes players civilians or not. `/tag <player> add Civilian` works too.
- `/civilians` (anyone) lists the civilians online.

## Teams
Use Java's built-in `/team` command (operators):

- `/team add <team>` creates a team, and `/team remove <team>` deletes it.
- `/team modify <team> friendlyFire false` stops teammates hurting each other. Set this on every team: it's on by default.
- `/team join <team> <players>` and `/team leave <players>`.
- `/team list <team>` lists a team's members. `/team modify <team> color <color>` colors their names.

## Differences from the Bedrock version
- Commands have no `ev:` prefix: `/civilian` instead of `/ev:civilian`.
- Teams use Java's `/team` instead of `/ev:teamcreate`, `/ev:teamjoin` and so on.

## Build
```
./gradlew build
```
Building needs JDK 25 or newer to run Gradle (the mod targets Java 25 too). The jar ends up in `build/libs/`. Put it in your `.minecraft/mods` folder along with Fabric API. `./gradlew runClient` starts a test game with the mod loaded.

This mod used to be called Teams (`coberto_teams`). Remove the old Teams jar from your mods folder when you install it.

## Paper server
A Paper plugin version is in [paper-plugin](paper-plugin).
