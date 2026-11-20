package com.thecoberto.teams;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Teams: stops player-vs-player damage between teammates, for civilians and
 * inside adventure zones. Java port of the Bedrock Teams add-on.
 *
 * A team is a name saved in the world. A player is on a team when they have a
 * command tag with the team's name, so /tag and command blocks work too, and
 * selectors like @a[tag=Cobertica] pick out a team.
 */
public class Teams implements ModInitializer {
	static final String CIVILIAN_TAG = "Civilian";
	static final String ADVENTURE_ZONE_TAG = "inside_adventure_zone";
	private static final List<String> RESERVED = List.of("civilian", "inside_adventure_zone");
	private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,24}$");

	private static final SuggestionProvider<ServerCommandSource> TEAM_SUGGESTIONS = (context, builder) ->
			CommandSource.suggestMatching(TeamsState.get(context.getSource().getServer()).teams(), builder);

	@Override
	public void onInitialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayerEntity victim)) return true;
			PlayerEntity attacker = responsiblePlayer(source.getAttacker());
			if (attacker == null || attacker == victim) return true; // hurting yourself is fine
			return !pvpBlocked(victim, attacker);
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
		MinecraftServer server = victim.getServer();
		if (server == null) return false;
		return TeamsState.get(server).teams().stream().anyMatch(t -> hasTag(victim, t) && hasTag(attacker, t));
	}

	private static boolean hasTag(Entity entity, String tag) {
		return entity.getCommandTags().contains(tag);
	}

	private static Optional<String> teamOf(Entity entity, TeamsState state) {
		return state.teams().stream().filter(t -> hasTag(entity, t)).findFirst();
	}

	// ---------- Commands ----------

	private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("teamcreate")
				.requires(s -> s.hasPermissionLevel(2))
				.then(CommandManager.argument("team", StringArgumentType.word())
						.executes(Teams::create)));

		dispatcher.register(CommandManager.literal("teamdelete")
				.requires(s -> s.hasPermissionLevel(2))
				.then(CommandManager.argument("team", StringArgumentType.word())
						.suggests(TEAM_SUGGESTIONS)
						.executes(Teams::delete)));

		dispatcher.register(CommandManager.literal("teamjoin")
				.requires(s -> s.hasPermissionLevel(2))
				.then(CommandManager.argument("players", EntityArgumentType.players())
						.then(CommandManager.argument("team", StringArgumentType.word())
								.suggests(TEAM_SUGGESTIONS)
								.executes(Teams::join))));

		dispatcher.register(CommandManager.literal("teamleave")
				.requires(s -> s.hasPermissionLevel(2))
				.then(CommandManager.argument("players", EntityArgumentType.players())
						.executes(Teams::leave)));

		dispatcher.register(CommandManager.literal("civilian")
				.requires(s -> s.hasPermissionLevel(2))
				.then(CommandManager.argument("players", EntityArgumentType.players())
						.then(CommandManager.argument("civilian", BoolArgumentType.bool())
								.executes(Teams::civilian))));

		dispatcher.register(CommandManager.literal("teams")
				.executes(Teams::list));
	}

	private static int create(CommandContext<ServerCommandSource> context) {
		ServerCommandSource source = context.getSource();
		String name = StringArgumentType.getString(context, "team");
		TeamsState state = TeamsState.get(source.getServer());
		if (!NAME_PATTERN.matcher(name).matches()) {
			return fail(source, "Team names are 1-24 letters, numbers, _ or -, with no spaces.");
		}
		if (RESERVED.contains(name.toLowerCase(Locale.ROOT))) return fail(source, name + " is already used for something else.");
		Optional<String> existing = state.find(name);
		if (existing.isPresent()) return fail(source, "There's already a team called " + existing.get() + ".");

		state.add(name);
		broadcast(source.getServer(), prefix().append("Created team ").append(team(name))
				.append(". Teammates can't hurt each other."));
		source.sendFeedback(() -> Text.literal("Add players with /teamjoin <players> " + name), false);
		return 1;
	}

	private static int delete(CommandContext<ServerCommandSource> context) {
		ServerCommandSource source = context.getSource();
		String name = StringArgumentType.getString(context, "team");
		TeamsState state = TeamsState.get(source.getServer());
		Optional<String> found = state.find(name);
		if (found.isEmpty()) return fail(source, "There's no team called " + name + ". See /teams.");

		String team = found.get();
		state.remove(team);
		for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
			player.removeCommandTag(team);
		}
		broadcast(source.getServer(), prefix().append("Deleted team ").append(team(team)).append("."));
		return 1;
	}

	private static int join(CommandContext<ServerCommandSource> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource source = context.getSource();
		Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(context, "players");
		String name = StringArgumentType.getString(context, "team");
		TeamsState state = TeamsState.get(source.getServer());
		Optional<String> found = state.find(name);
		if (found.isEmpty()) return fail(source, "There's no team called " + name + ". Create it with /teamcreate " + name);

		String team = found.get();
		for (ServerPlayerEntity player : players) {
			for (String other : state.teams()) {
				if (!other.equals(team)) player.removeCommandTag(other);
			}
			player.addCommandTag(team);
			player.sendMessage(prefix().append("You're now on team ").append(team(team)).append("."));
		}
		source.sendFeedback(() -> Text.literal("Added " + names(players) + " to " + team + "."), true);
		return players.size();
	}

	private static int leave(CommandContext<ServerCommandSource> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource source = context.getSource();
		Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(context, "players");
		TeamsState state = TeamsState.get(source.getServer());
		for (ServerPlayerEntity player : players) {
			Optional<String> team = teamOf(player, state);
			if (team.isEmpty()) continue;
			for (String t : state.teams()) player.removeCommandTag(t);
			player.sendMessage(prefix().append("You're no longer on team ").append(team(team.get())).append("."));
		}
		source.sendFeedback(() -> Text.literal("Took " + names(players) + " off their team."), true);
		return players.size();
	}

	private static int civilian(CommandContext<ServerCommandSource> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
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
		MinecraftServer server = source.getServer();
		List<String> teams = TeamsState.get(server).teams();
		List<ServerPlayerEntity> online = server.getPlayerManager().getPlayerList();

		MutableText message;
		if (teams.isEmpty()) {
			message = prefix().append("No teams yet. Operators can make one with /teamcreate <name>.");
		} else {
			message = prefix().append(teams.size() + " team(s). Online members:");
			for (String t : teams) {
				List<ServerPlayerEntity> members = online.stream().filter(p -> hasTag(p, t)).toList();
				message.append("\n").append(team(t)).append(": ")
						.append(members.isEmpty()
								? Text.literal("nobody online").formatted(Formatting.GRAY)
								: Text.literal(names(members)));
			}
		}
		List<ServerPlayerEntity> civilians = online.stream().filter(p -> hasTag(p, CIVILIAN_TAG)).toList();
		if (!civilians.isEmpty()) {
			message.append("\n").append(Text.literal("Civilians").formatted(Formatting.GREEN))
					.append(": " + names(civilians));
		}
		MutableText finalMessage = message;
		source.sendFeedback(() -> finalMessage, false);
		return teams.size();
	}

	// ---------- Messages ----------

	private static MutableText prefix() {
		// An unstyled root, so text appended after the prefix isn't gold too.
		return Text.empty().append(Text.literal("Teams:").formatted(Formatting.GOLD)).append(" ");
	}

	private static MutableText team(String name) {
		return Text.literal(name).formatted(Formatting.YELLOW);
	}

	private static String names(Collection<ServerPlayerEntity> players) {
		return players.stream().map(p -> p.getName().getString()).collect(Collectors.joining(", "));
	}

	private static void broadcast(MinecraftServer server, Text message) {
		server.getPlayerManager().broadcast(message, false);
	}

	private static int fail(ServerCommandSource source, String message) {
		source.sendError(Text.literal(message));
		return 0;
	}
}
