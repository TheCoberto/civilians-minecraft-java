# Civilians (Paper plugin)

Paper version of the Civilians mod, for servers. Same rules and tags as the Fabric mod:

- Civilians (`Civilian` tag) and players in an adventure zone (`inside_adventure_zone` tag) can't hurt players or be hurt by them. Arrows, tridents, pets, lit TNT and splash potions count as the player behind them.
- Lava, flint and steel and fire charges can't be used within 5 blocks of them, and civilians can't use them within 5 blocks of other players.

Commands:

- `/civilian join`, `/civilian leave`, `/civilian menu` and `/civilian` (status): players choose for themselves (permission `civilians.self`, everyone by default). They can't switch within 30 seconds of a PvP hit, or within 30 minutes of their last switch.
- `/civilian <players> true|false`: set it for others (permission `civilians.admin`, ops by default; admins skip the cooldown).
- `/civilians`: list the civilians online (anyone).

**Peaceful or Fighter menu:** new players get a menu 5 seconds after their first join to choose Peaceful (civilian) or Fighter. `/civilian menu` opens it again.

`config.yml`: `combat-seconds` (30), `cooldown-minutes` (30), `first-join-menu` (true) and `first-join-menu-delay-seconds` (5).

Teams use the built-in `/team` command. Set `/team modify <team> friendlyFire false` on each team.

Requires Paper 1.21.1 or newer. No dependencies.

## Build
```
./gradlew build
```
The jar ends up in `build/libs/`.
