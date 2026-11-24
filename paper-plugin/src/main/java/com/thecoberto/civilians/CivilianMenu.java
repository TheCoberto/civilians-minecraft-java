package com.thecoberto.civilians;

import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The Peaceful or Fighter menu: a chest menu where players pick civilian (Peaceful, no PvP)
 * or not (Fighter). Opens on a player's first join (config first-join-menu) and with
 * /civilian menu. The buttons go through the same checks as /civilian join|leave.
 */
final class CivilianMenu implements Listener {
	private static final int PEACEFUL_SLOT = 11;
	private static final int STATUS_SLOT = 13;
	private static final int FIGHTER_SLOT = 15;

	private final CiviliansPlugin plugin;

	CivilianMenu(CiviliansPlugin plugin) {
		this.plugin = plugin;
		Bukkit.getPluginManager().registerEvents(this, plugin);
	}

	/** Marks our inventories, so clicks in them are handled and nothing can be taken out. */
	private static final class Holder implements InventoryHolder {
		private Inventory inventory;

		@Override
		public Inventory getInventory() {
			return inventory;
		}
	}

	void open(Player player) {
		Holder holder = new Holder();
		Inventory inv = Bukkit.createInventory(holder, 27, Component.text("Peaceful or Fighter?"));
		holder.inventory = inv;

		ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
		for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

		inv.setItem(PEACEFUL_SLOT, item(Material.WHITE_BANNER,
				Component.text("Peaceful", NamedTextColor.GREEN).append(Component.text(" (civilian)", NamedTextColor.GRAY)),
				List.of(line("Other players can't hurt you,"), line("and you can't hurt them."), Component.empty(),
						Component.text("Click to be Peaceful", NamedTextColor.YELLOW))));

		boolean civilian = CiviliansPlugin.isCivilian(player);
		inv.setItem(STATUS_SLOT, item(Material.SHIELD,
				Component.text("You're ", NamedTextColor.GOLD).append(civilian
						? Component.text("Peaceful", NamedTextColor.GREEN)
						: Component.text("a Fighter", NamedTextColor.RED)),
				List.of(line("You can't switch right after a fight,"), line("or too soon after your last switch."),
						Component.empty(), line("Reopen this with /civilian menu."))));

		inv.setItem(FIGHTER_SLOT, item(Material.IRON_SWORD,
				Component.text("Fighter", NamedTextColor.RED).append(Component.text(" (PvP on)", NamedTextColor.GRAY)),
				List.of(line("You can fight other Fighters,"), line("and they can fight you."), Component.empty(),
						Component.text("Click to be a Fighter", NamedTextColor.YELLOW))));

		player.openInventory(inv);
	}

	@EventHandler
	public void onClick(InventoryClickEvent event) {
		if (!(event.getInventory().getHolder() instanceof Holder)) return;
		event.setCancelled(true);
		if (!(event.getWhoClicked() instanceof Player player)) return;
		if (event.getClickedInventory() != event.getInventory()) return;
		int slot = event.getRawSlot();
		if (slot != PEACEFUL_SLOT && slot != FIGHTER_SLOT) return;
		boolean peaceful = slot == PEACEFUL_SLOT;
		player.closeInventory();
		if (plugin.choose(player, peaceful)) {
			player.playSound(player.getLocation(), peaceful ? Sound.BLOCK_NOTE_BLOCK_CHIME : Sound.ITEM_ARMOR_EQUIP_IRON, 1, 1);
		}
	}

	@EventHandler
	public void onDrag(InventoryDragEvent event) {
		if (event.getInventory().getHolder() instanceof Holder) event.setCancelled(true);
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		if (player.hasPlayedBefore() || !plugin.getConfig().getBoolean("first-join-menu", true)) return;
		if (!player.hasPermission("civilians.self")) return;
		long delayTicks = Math.max(0, plugin.getConfig().getLong("first-join-menu-delay-seconds", 5)) * 20;
		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			if (player.isOnline()) open(player);
		}, delayTicks);
	}

	private static Component line(String text) {
		return Component.text(text, NamedTextColor.GRAY);
	}

	private static ItemStack item(Material material, Component name, List<Component> lore) {
		ItemStack stack = new ItemStack(material);
		ItemMeta meta = stack.getItemMeta();
		meta.displayName(name.decoration(TextDecoration.ITALIC, false));
		meta.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
		stack.setItemMeta(meta);
		return stack;
	}
}
