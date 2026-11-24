package com.thecoberto.civilians;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
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

	@Override
	public void onEnable() {
		getServer().getPluginManager().registerEvents(this, this);
		// Lets TAB show [Civilian] above heads and in the tab list.
		if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) new CivilianPlaceholders(this).register();
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

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (!command.getName().equals("civilian")) return List.of();
		List<String> options = new ArrayList<>();
		if (args.length == 1) {
			options.addAll(List.of("@a", "@p", "@s"));
			Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
		} else if (args.length == 2) {
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
