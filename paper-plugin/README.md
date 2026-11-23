# Civilians (Paper plugin)

Paper version of the Civilians mod, for servers. Same rules and tags as the Fabric mod:

- Civilians (`Civilian` tag) and players in an adventure zone (`inside_adventure_zone` tag) can't hurt players or be hurt by them. Arrows, tridents, pets, lit TNT and splash potions count as the player behind them.
- Lava, flint and steel and fire charges can't be used within 5 blocks of them, and civilians can't use them within 5 blocks of other players.

Commands: `/civilian <players> true|false` (permission `civilians.admin`, ops by default) and `/civilians` (anyone).

Teams use the built-in `/team` command. Set `/team modify <team> friendlyFire false` on each team.

Requires Paper 1.21.1 or newer. No dependencies.

## Build
```
./gradlew build
```
The jar ends up in `build/libs/`.
