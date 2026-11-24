package com.thecoberto.civilians;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;
import java.util.stream.Collectors;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Stops player-vs-player damage for civilians and inside adventure zones.
 * Paper port of the Civilians Fabric mod. It uses the same tags, so /tag and
 * command blocks work the same. Teams use the built-in /team command.
 */
public final class CiviliansPlugin extends JavaPlugin implements Listener {
	static final String CIVILIAN_TAG = "Civilian";
	static final String ADVENTURE_ZONE_TAG = "inside_adventure_zone";
	private static final double FIRE_RADIUS = 5;

	/** Player -> when they last hit or were hit by another player (ms). */
	private final Map<UUID, Long> lastPvp = new HashMap<>();
	private NamespacedKey lastChangeKey;
	private CivilianMenu menu;

	@Override
	public void onEnable() {
		saveDefaultConfig();
		lastChangeKey = new NamespacedKey(this, "last_change");
		getServer().getPluginManager().registerEvents(this, this);
		// Lets TAB show [Civilian] above heads and in the tab list.
		if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) new CivilianPlaceholders(this).register();
		menu = new CivilianMenu(this);
	}

	// ---------- Blocking PvP ----------

	/** The player behind the damage: the player, an arrow's shooter, a pet's owner or whoever lit the TNT. */
	private static Player responsiblePlayer(Entity entity) {
		if (entity == null) return null;
		if (entity instanceof Player player) return player;
		if (entity instanceof Projectile projectile) {
			ProjectileSource shooter = projectile.getShooter();
			return shooter instanceof Entity e ? responsiblePlayer(e) : null;
		}
		if (entity instanceof Tameable pet && pet.getOwner() instanceof Player owner) return owner;
		if (entity instanceof TNTPrimed tnt) return responsiblePlayer(tnt.getSource());
		return null;
	}

	private static boolean isProtected(Entity entity) {
		return entity.getScoreboardTags().contains(CIVILIAN_TAG) || entity.getScoreboardTags().contains(ADVENTURE_ZONE_TAG);
	}

	private static boolean pvpBlocked(Player victim, Player attacker) {
		// Hurting yourself is fine.
		return attacker != victim && (isProtected(victim) || isProtected(attacker));
	}

	// Cancelling the damage cancels the knockback too.
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onDamage(EntityDamageByEntityEvent event) {
		if (!(event.getEntity() instanceof Player victim)) return;
		Player attacker = responsiblePlayer(event.getDamager());
		if (attacker != null && pvpBlocked(victim, attacker)) event.setCancelled(true);
	}

	/** Remembers real PvP hits, so nobody can turn civilian to escape a fight. */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPvpHit(EntityDamageByEntityEvent event) {
		if (!(event.getEntity() instanceof Player victim)) return;
		Player attacker = responsiblePlayer(event.getDamager());
		if (attacker == null || attacker == victim) return;
		long now = System.currentTimeMillis();
		lastPvp.put(victim.getUniqueId(), now);
		lastPvp.put(attacker.getUniqueId(), now);
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onSplash(PotionSplashEvent event) {
		Player thrower = responsiblePlayer(event.getPotion());
		if (thrower == null) return;
		for (LivingEntity hit : event.getAffectedEntities()) {
			if (hit instanceof Player victim && pvpBlocked(victim, thrower)) event.setIntensity(hit, 0);
		}
	}

	// ---------- Lava and fire ----------
	// They hurt without an attacker, so they can't be used near protected players (or by them near anyone).

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBucket(PlayerBucketEmptyEvent event) {
		if (event.getBucket() != Material.LAVA_BUCKET) return;
		if (nearProtected(event.getPlayer(), event.getBlock().getLocation().add(0.5, 0.5, 0.5))) event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onIgnite(BlockIgniteEvent event) {
		BlockIgniteEvent.IgniteCause cause = event.getCause();
		if (cause != BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL && cause != BlockIgniteEvent.IgniteCause.FIREBALL) return;
		Player user = event.getPlayer() != null ? event.getPlayer() : responsiblePlayer(event.getIgnitingEntity());
		if (user != null && nearProtected(user, event.getBlock().getLocation().add(0.5, 0.5, 0.5))) event.setCancelled(true);
	}

	/** True (and tells the player why) if lava or fire here could hurt someone it shouldn't. */
	private static boolean nearProtected(Player user, Location at) {
		boolean userProtected = isProtected(user);
		for (Player other : at.getWorld().getPlayers()) {
			if (other == user || other.getGameMode() == GameMode.SPECTATOR || other.getLocation().distance(at) > FIRE_RADIUS) continue;
			if (userProtected || isProtected(other)) {
				user.sendActionBar(prefix().append(Component.text("You can't use lava or fire this close to " + other.getName() + ".")));
				return true;
			}
		}
		return false;
	}

	// ---------- Commands ----------

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (command.getName().equals("civilians")) {
			List<Player> civilians = Bukkit.getOnlinePlayers().stream()
					.filter(p -> p.getScoreboardTags().contains(CIVILIAN_TAG)).map(p -> (Player) p).toList();
			sender.sendMessage(civilians.isEmpty()
					? prefix().append(Component.text("No civilians online."))
					: prefix().append(Component.text("Civilians", NamedTextColor.GREEN)).append(Component.text(": " + names(civilians))));
			return true;
		}

		// Players choosing for themselves: /civilian, /civilian join, /civilian leave.
		if (args.length <= 1 && sender instanceof Player self) {
			String choice = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
			switch (choice) {
				case "join", "on" -> choose(self, true);
				case "leave", "off" -> choose(self, false);
				case "menu" -> {
					if (self.hasPermission("civilians.self")) menu.open(self);
					else self.sendMessage(Component.text("You can't change that yourself.", NamedTextColor.RED));
				}
				case "status" -> self.sendMessage(prefix().append(Component.text(isCivilian(self)
						? "You're a civilian. Leave with /civilian leave."
						: "You're not a civilian. Join with /civilian join.")));
				default -> {
					return false;
				}
			}
			return true;
		}

		if (!sender.hasPermission("civilians.admin")) {
			sender.sendMessage(Component.text("Use /civilian join or /civilian leave.", NamedTextColor.RED));
			return true;
		}
		if (args.length != 2 || !(args[1].equalsIgnoreCase("true") || args[1].equalsIgnoreCase("false"))) return false;
		boolean civilian = args[1].equalsIgnoreCase("true");
		List<Player> players = new ArrayList<>();
		try {
			for (Entity e : Bukkit.selectEntities(sender, args[0])) {
				if (e instanceof Player p) players.add(p);
			}
		} catch (IllegalArgumentException e) {
			sender.sendMessage(Component.text("Unknown player or selector: " + args[0], NamedTextColor.RED));
			return true;
		}
		if (players.isEmpty()) {
			sender.sendMessage(Component.text("No players matched.", NamedTextColor.RED));
			return true;
		}
		for (Player p : players) {
			if (civilian) {
				p.addScoreboardTag(CIVILIAN_TAG);
				p.sendMessage(prefix().append(Component.text("You're now a "))
						.append(Component.text("civilian", NamedTextColor.GREEN))
						.append(Component.text(": players can't hurt you and you can't hurt them.")));
			} else {
				p.removeScoreboardTag(CIVILIAN_TAG);
				p.sendMessage(prefix().append(Component.text("You're no longer a civilian. PvP applies to you again.")));
			}
		}
		sender.sendMessage(Component.text(names(players) + (civilian ? " are now civilians." : " are no longer civilians.")));
		return true;
	}

	static boolean isCivilian(Player p) {
		return p.getScoreboardTags().contains(CIVILIAN_TAG);
	}

	/**
	 * A player making themselves a civilian or not, unless they were just fighting or changed recently.
	 * Returns whether it changed.
	 */
	boolean choose(Player player, boolean civilian) {
		if (isCivilian(player) == civilian) {
			player.sendMessage(prefix().append(Component.text(civilian ? "You're already a civilian." : "You're not a civilian.")));
			return false;
		}
		if (!player.hasPermission("civilians.self")) {
			player.sendMessage(Component.text("You can't change that yourself.", NamedTextColor.RED));
			return false;
		}
		long now = System.currentTimeMillis();
		long combatMs = getConfig().getLong("combat-seconds", 30) * 1000;
		long fought = lastPvp.getOrDefault(player.getUniqueId(), 0L);
		if (now - fought < combatMs) {
			player.sendMessage(Component.text("You were just in a fight. Try again in "
					+ duration(combatMs - (now - fought)) + ".", NamedTextColor.RED));
			return false;
		}
		long cooldownMs = getConfig().getLong("cooldown-minutes", 30) * 60_000;
		Long changed = player.getPersistentDataContainer().get(lastChangeKey, PersistentDataType.LONG);
		if (changed != null && now - changed < cooldownMs && !player.hasPermission("civilians.admin")) {
			player.sendMessage(Component.text("You changed this recently. Try again in "
					+ duration(cooldownMs - (now - changed)) + ".", NamedTextColor.RED));
			return false;
		}
		player.getPersistentDataContainer().set(lastChangeKey, PersistentDataType.LONG, now);
		if (civilian) {
			player.addScoreboardTag(CIVILIAN_TAG);
			player.sendMessage(prefix().append(Component.text("You're now a "))
					.append(Component.text("civilian", NamedTextColor.GREEN))
					.append(Component.text(": players can't hurt you and you can't hurt them.")));
		} else {
			player.removeScoreboardTag(CIVILIAN_TAG);
			player.sendMessage(prefix().append(Component.text("You're no longer a civilian. PvP applies to you again.")));
		}
		return true;
	}

	private static String duration(long ms) {
		long s = (ms + 999) / 1000;
		return s >= 60 ? (s / 60) + "m " + (s % 60) + "s" : s + "s";
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (!command.getName().equals("civilian")) return List.of();
		List<String> options = new ArrayList<>();
		if (args.length == 1) {
			options.addAll(List.of("join", "leave", "menu"));
			if (sender.hasPermission("civilians.admin")) {
				options.addAll(List.of("@a", "@p", "@s"));
				Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
			}
		} else if (args.length == 2 && sender.hasPermission("civilians.admin")) {
			options.addAll(List.of("true", "false"));
		}
		String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
		return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(typed)).toList();
	}

	// ---------- Messages ----------

	private static Component prefix() {
		return Component.text("Civilians:", NamedTextColor.GOLD).append(Component.text(" ", NamedTextColor.WHITE));
	}

	private static String names(Collection<Player> players) {
		return players.stream().map(Player::getName).collect(Collectors.joining(", "));
	}
}
