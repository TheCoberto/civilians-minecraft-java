package com.thecoberto.civilians;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Stops player-vs-player damage for civilians and inside adventure zones.
 * Java port of the Bedrock Teams add-on. Teams themselves use Java's built-in
 * /team command (with friendlyFire false), so they aren't part of this mod.
 */
public class Civilians implements ModInitializer {
	static final String CIVILIAN_TAG = "Civilian";
	static final String ADVENTURE_ZONE_TAG = "inside_adventure_zone";

	@Override
	public void onInitialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayerEntity victim)) return true;
			PlayerEntity attacker = responsiblePlayer(source.getAttacker());
			if (attacker == null || attacker == victim) return true; // hurting yourself is fine
			return !pvpBlocked(victim, attacker);
		});

		// Lava and fire hurt without an attacker, so stop them being placed near protected players instead.
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			ItemStack stack = player.getStackInHand(hand);
			if (world.isClient() || !(stack.isOf(Items.FLINT_AND_STEEL) || stack.isOf(Items.FIRE_CHARGE))) return ActionResult.PASS;
			return nearProtected(player, hit.getPos()) ? ActionResult.FAIL : ActionResult.PASS;
		});
		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack stack = player.getStackInHand(hand);
			if (world.isClient() || !stack.isOf(Items.LAVA_BUCKET)) return TypedActionResult.pass(stack);
			if (!nearProtected(player, player.getPos())) return TypedActionResult.pass(stack);
			player.playerScreenHandler.syncState(); // so the client shows the lava bucket again
			return TypedActionResult.fail(stack);
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	// ---------- Blocking PvP ----------

	/**
	 * The player behind the damage: the player themselves, or the owner of the
	 * tamed pet that did it. Arrows and tridents already report their shooter
	 * as the attacker.
	 */
	private static PlayerEntity responsiblePlayer(Entity entity) {
		if (entity instanceof PlayerEntity player) return player;
		if (entity instanceof Tameable pet && pet.getOwner() instanceof PlayerEntity owner) return owner;
		return null;
	}

	private static boolean pvpBlocked(PlayerEntity victim, PlayerEntity attacker) {
		for (String tag : List.of(CIVILIAN_TAG, ADVENTURE_ZONE_TAG)) {
			if (hasTag(victim, tag) || hasTag(attacker, tag)) return true;
		}
		return false;
	}

	private static final double FIRE_RADIUS = 5;

	private static boolean isProtected(PlayerEntity player) {
		return hasTag(player, CIVILIAN_TAG) || hasTag(player, ADVENTURE_ZONE_TAG);
	}

	/**
	 * True (and tells the player why) if lava or fire used at this spot could hurt
	 * someone it shouldn't: a protected player nearby, or anyone nearby when the
	 * user is protected.
	 */
	private static boolean nearProtected(PlayerEntity user, Vec3d pos) {
		boolean userProtected = isProtected(user);
		for (PlayerEntity other : user.getWorld().getPlayers()) {
			if (other == user || other.isSpectator() || other.getPos().distanceTo(pos) > FIRE_RADIUS) continue;
			if (userProtected || isProtected(other)) {
				user.sendMessage(prefix().append("You can't use lava or fire this close to " + other.getName().getString() + "."), true);
				return true;
			}
		}
		return false;
	}

	private static boolean hasTag(Entity entity, String tag) {
		return entity.getCommandTags().contains(tag);
	}

	// ---------- Commands ----------

	private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("civilian")
				.requires(s -> s.hasPermissionLevel(2))
				.then(CommandManager.argument("players", EntityArgumentType.players())
						.then(CommandManager.argument("civilian", BoolArgumentType.bool())
								.executes(Civilians::civilian))));

		dispatcher.register(CommandManager.literal("civilians")
				.executes(Civilians::list));
	}

	private static int civilian(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerCommandSource source = context.getSource();
		Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(context, "players");
		boolean civilian = BoolArgumentType.getBool(context, "civilian");
		for (ServerPlayerEntity player : players) {
			if (civilian) {
				player.addCommandTag(CIVILIAN_TAG);
				player.sendMessage(prefix().append("You're now a ")
						.append(Text.literal("civilian").formatted(Formatting.GREEN))
						.append(": players can't hurt you and you can't hurt them."));
			} else {
				player.removeCommandTag(CIVILIAN_TAG);
				player.sendMessage(prefix().append("You're no longer a civilian. PvP applies to you again."));
			}
		}
		String result = names(players) + (civilian ? " are now civilians." : " are no longer civilians.");
		source.sendFeedback(() -> Text.literal(result), true);
		return players.size();
	}

	private static int list(CommandContext<ServerCommandSource> context) {
		ServerCommandSource source = context.getSource();
		List<ServerPlayerEntity> civilians = source.getServer().getPlayerManager().getPlayerList().stream()
				.filter(p -> hasTag(p, CIVILIAN_TAG)).toList();
		MutableText message = civilians.isEmpty()
				? prefix().append("No civilians online.")
				: prefix().append(Text.literal("Civilians").formatted(Formatting.GREEN)).append(": " + names(civilians));
		source.sendFeedback(() -> message, false);
		return civilians.size();
	}

	// ---------- Messages ----------

	private static MutableText prefix() {
		// An unstyled root, so text appended after the prefix isn't gold too.
		return Text.empty().append(Text.literal("Civilians:").formatted(Formatting.GOLD)).append(" ");
	}

	private static String names(Collection<ServerPlayerEntity> players) {
		return players.stream().map(p -> p.getName().getString()).collect(Collectors.joining(", "));
	}
}
