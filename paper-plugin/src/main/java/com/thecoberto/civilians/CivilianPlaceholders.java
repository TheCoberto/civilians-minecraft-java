package com.thecoberto.civilians;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * PlaceholderAPI placeholders, for TAB and other plugins:
 * %civilians_civilian% is "yes" for a civilian, otherwise "no".
 * %civilians_protected% is "yes" for a civilian or anyone in an adventure zone.
 */
final class CivilianPlaceholders extends PlaceholderExpansion {
	private final CiviliansPlugin plugin;

	CivilianPlaceholders(CiviliansPlugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public String getIdentifier() {
		return "civilians";
	}

	@Override
	public String getAuthor() {
		return "TheCoberto";
	}

	@Override
	public String getVersion() {
		return plugin.getPluginMeta().getVersion();
	}

	@Override
	public boolean persist() {
		return true; // stays registered when PlaceholderAPI reloads
	}

	@Override
	public String onRequest(OfflinePlayer offline, String params) {
		if (!(offline instanceof Player player) || !player.isOnline()) return null;
		return switch (params.toLowerCase(java.util.Locale.ROOT)) {
			case "civilian" -> yesNo(player.getScoreboardTags().contains(CiviliansPlugin.CIVILIAN_TAG));
			case "protected" -> yesNo(player.getScoreboardTags().contains(CiviliansPlugin.CIVILIAN_TAG)
					|| player.getScoreboardTags().contains(CiviliansPlugin.ADVENTURE_ZONE_TAG));
			default -> null;
		};
	}

	private static String yesNo(boolean b) {
		return b ? "yes" : "no";
	}
}
