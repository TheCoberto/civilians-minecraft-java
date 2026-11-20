package com.thecoberto.teams;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

/** The team names, saved in the world (data/coberto_teams.dat). */
public class TeamsState extends PersistentState {
	private static final String FILE = "coberto_teams";
	private static final PersistentState.Type<TeamsState> TYPE =
			new PersistentState.Type<>(TeamsState::new, TeamsState::fromNbt, null);

	private final List<String> teams = new ArrayList<>();

	public static TeamsState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, FILE);
	}

	private static TeamsState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		TeamsState state = new TeamsState();
		NbtList list = nbt.getList("teams", NbtElement.STRING_TYPE);
		for (int i = 0; i < list.size(); i++) state.teams.add(list.getString(i));
		return state;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		NbtList list = new NbtList();
		for (String team : teams) list.add(NbtString.of(team));
		nbt.put("teams", list);
		return nbt;
	}

	public List<String> teams() {
		return List.copyOf(teams);
	}

	/** Team names are matched without caring about capitals. */
	public Optional<String> find(String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		return teams.stream().filter(t -> t.toLowerCase(Locale.ROOT).equals(lower)).findFirst();
	}

	public void add(String team) {
		teams.add(team);
		markDirty();
	}

	public void remove(String team) {
		teams.remove(team);
		markDirty();
	}
}
