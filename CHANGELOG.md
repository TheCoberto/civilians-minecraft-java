# Changelog

Fabric mod versions (0.x) and the Paper plugin (`paper-plugin/`, "Paper x.y") are listed together, newest first.

## Paper 1.3.0
- New players get a **Peaceful or Fighter** menu 5 seconds after their first join. `/civilian menu` opens it again. It follows the same combat lock and cooldown as `/civilian join|leave`. Config: `first-join-menu`, `first-join-menu-delay-seconds`.

## Paper 1.2.0
- Players can choose for themselves with `/civilian join` and `/civilian leave` (permission `civilians.self`, everyone by default). Not within 30 seconds of a PvP hit or 30 minutes of their last switch (`combat-seconds`, `cooldown-minutes`; admins skip the cooldown). `/civilian <players> true|false` stays admin-only.

## Paper 1.1.0
- PlaceholderAPI placeholders `%civilians_civilian%` and `%civilians_protected%`.

## 0.3.1
- Works with Fabric Loader 0.19 too (it needed 0.20.1).

## 0.3.0
- Now for Minecraft 26.2 (Fabric). No longer works on 1.21.1.

## Paper 1.0.0
- First Paper plugin version: civilians and adventure zones, PvP blocking (arrows, tridents, pets, lit TNT and splash potions count), the lava and fire rule, `/civilian` and `/civilians`.

## 0.2.0
- Renamed from Teams to Civilians. Teams now use Java's built-in `/team` command (set `friendlyFire false` on each team).
- New `/civilians` lists the civilians online.
- Lava, flint and steel and fire charges can't be used within 5 blocks of a civilian or someone in an adventure zone, and civilians can't use them near other players.

## 0.1.0
- First Java (Fabric 1.21.1) version of the Bedrock Teams add-on: teams, civilians and adventure zones that block PvP.
