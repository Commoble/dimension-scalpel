package commoble.dimensionscalpel;

import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableSet;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;

import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.DimensionArgument;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.registry.DynamicRegistries;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.SimpleRegistry;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.Dimension;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraft.world.gen.settings.DimensionGeneratorSettings;
import net.minecraft.world.server.ServerWorld;

public class RemoveDimensionsCommands
{	
	// we're using stringtextcomponents here because if people use the
	// commands on a server without having it installed on their client, then
	// they just get the untranslated keys as feedback anyway
	public static final String REMOVE_COMMAND = "remove";
	public static final String REMOVE_ALL_COMMAND = "remove_all";
	public static final String RESET_DIMENSIONS_COMMAND = "reset_dimension_registry";
	public static final String WORLD_ARG = "world";
	public static final String REMOVE_WORLD_FEEDBACK = "Removed dimension %s -- kicked %s players";
	public static final String REMOVE_WORLDS_FEEDBACK = "Removed %s dimensions, %s players were kicked";
	public static final String KICK_COUNT_FEEDBACK = "%s players were kicked from dimension %s";
	public static final String REMOVE_VANILLA_FAIL = "Cannot remove vanilla dimension %s";
	public static final String REMOVE_UNKNOWN_FAIL = "Cannot remove dimension %s for unknown reasons";
	public static final ITextComponent DIMENSION_REMOVAL_KICK_REASON = new StringTextComponent("Dimension was removed");
	public static final ITextComponent RESET_DIMENSIONS_FEEDBACK = new StringTextComponent("The Dimension Registry has been reset to factory defaults. Restart the server to restore vanilla dimensions. Restart the server twice to restore custom dimensions.");
	
	public static ArgumentBuilder<CommandSource,?> buildRemoveDimensionCommand()
	{
		return Commands.literal(REMOVE_COMMAND)
			.requires(DimensionScalpel.isPermissionSufficient(DimensionScalpel.INSTANCE.serverConfig.removePermission))
			.then(
				Commands.argument(WORLD_ARG, DimensionArgument.getDimension())
					.executes(RemoveDimensionsCommands::doRemoveDimensionCommand));
	}
	public static ArgumentBuilder<CommandSource,?> buildRemoveAllCommand()
	{
		return Commands.literal(REMOVE_ALL_COMMAND)
			.requires(DimensionScalpel.isPermissionSufficient(DimensionScalpel.INSTANCE.serverConfig.removePermission))
			.executes(RemoveDimensionsCommands::doRemoveAllCommand);
	}
	
	public static ArgumentBuilder<CommandSource,?> buildResetDimensionsCommand()
	{
		return Commands.literal(RESET_DIMENSIONS_COMMAND)
			.requires(DimensionScalpel.isPermissionSufficient(DimensionScalpel.INSTANCE.serverConfig.printPermission))
			.executes(RemoveDimensionsCommands::doResetDimensions);
	}
	
	/**
	 * 
	 * @param context command context, must have a valid "world" argument
	 * @return The number of dimensions that were removed (1 or 0)
	 * @throws CommandSyntaxException
	 */
	public static int doRemoveDimensionCommand(CommandContext<CommandSource> context) throws CommandSyntaxException
	{
		CommandSource source = context.getSource();
		ServerWorld world = DimensionArgument.getDimensionArgument(context, WORLD_ARG);
		RegistryKey<World> worldKey = world.getDimensionKey();
		
		// prevent unregistering of vanilla dimensions for safety purposes (vanilla tends to crash otherwise)
		if (DimensionScalpel.VANILLA_WORLDS.contains(worldKey))
		{
			source.sendFeedback(new StringTextComponent(String.format(REMOVE_VANILLA_FAIL, worldKey.getLocation())).mergeStyle(TextFormatting.RED), true);
			return 0;
		}
		
		RegistryKey<Dimension> dimensionKey = RegistryKey.getOrCreateKey(Registry.DIMENSION_KEY, worldKey.getLocation());
		return removeDimensionsAndKickPlayers(source, ImmutableSet.of(dimensionKey));
	}
	
	public static int doRemoveAllCommand(CommandContext<CommandSource> context) throws CommandSyntaxException
	{
		CommandSource source = context.getSource();
		Set<RegistryKey<Dimension>> dimensions = source.getServer()
			.getServerConfiguration()
			.getDimensionGeneratorSettings()
			.func_236224_e_()
			.getEntries()
			.stream()
			.map(Entry::getKey)
			.collect(Collectors.toSet());
		return removeDimensionsAndKickPlayers(source, dimensions);
	}
	
	public static int doResetDimensions(CommandContext<CommandSource> context) throws CommandSyntaxException
	{
		CommandSource source = context.getSource();
		MinecraftServer server = source.getServer();
		
		int removedDimensions = doRemoveAllCommand(context);
		
		DimensionGeneratorSettings dimensionGeneratorSettings = server
			.getServerConfiguration()
			.getDimensionGeneratorSettings();
		DynamicRegistries registries = server.func_244267_aX();
		SimpleRegistry<Dimension> newDimensionRegistry = DimensionType.getDefaultSimpleRegistry(
			registries.func_230520_a_(),
			registries.getRegistry(Registry.BIOME_KEY),
			registries.getRegistry(Registry.NOISE_SETTINGS_KEY),
			server.getWorld(World.OVERWORLD).getSeed());
		DimensionHelper.DimensionGeneratorSettingsAccess.dimensionRegistry.set(dimensionGeneratorSettings, newDimensionRegistry);
		
		source.sendFeedback(RESET_DIMENSIONS_FEEDBACK, true);
		
		return removedDimensions;
	}
	
	/**
	 * Removes and unregisters a set of dimensions, kicking any players remaining in those dimensions
	 * @param source command source
	 * @param dimensions Set of dimension IDs to unregister. Vanilla dimensions are ignored, and will not be unregistered.
	 * @return The total number of unregistered dimensions
	 */
	public static int removeDimensionsAndKickPlayers(CommandSource source, Set<RegistryKey<Dimension>> dimensions)
	{
		// ensure vanilla dimensions aren't unregistered
		Set<RegistryKey<Dimension>> dimensionsToRemove = dimensions.stream()
			.filter(dim -> !DimensionScalpel.VANILLA_DIMENSIONS.contains(dim))
			.collect(Collectors.toSet());
		
		MinecraftServer server = source.getServer();
		int kickedPlayerTotal = 0;
		
		Pair<Set<RegistryKey<Dimension>>, List<ServerWorld>> results = DimensionHelper.unregisterDimensions(server, dimensionsToRemove);
		List<ServerWorld> removedWorlds = results.getSecond();
		
		// kick players from removed worlds
		for (ServerWorld world : removedWorlds)
		{
			List<ServerPlayerEntity> players = world.getPlayers();
			int playerCount = players.size();
			for (ServerPlayerEntity serverplayerentity : world.getPlayers())
			{
				serverplayerentity.connection.disconnect(DIMENSION_REMOVAL_KICK_REASON);
			}
			source.sendFeedback(new StringTextComponent(String.format(REMOVE_WORLD_FEEDBACK, world.getDimensionKey().getLocation(), playerCount)), true);
			kickedPlayerTotal += playerCount;
		}
		source.sendFeedback(new StringTextComponent(String.format(REMOVE_WORLDS_FEEDBACK, removedWorlds.size(), kickedPlayerTotal)), true);
		
		return removedWorlds.size();
	}
}
