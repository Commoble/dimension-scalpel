package commoble.dimensionscalpel;

import java.util.Collection;
import java.util.Set;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.SimpleRegistry;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.Dimension;
import net.minecraft.world.server.ServerWorld;

public class PrintCommands
{
	public static final String PRINT_COMMAND = "print";
	public static final String PRINT_WORLDS = "worlds";
	public static final String PRINT_DIMENSIONS = "dimensions";
	
	public static ArgumentBuilder<CommandSource,?> build()
	{
		return Commands.literal(PRINT_COMMAND)
			.requires(DimensionScalpel.isPermissionSufficient(DimensionScalpel.INSTANCE.serverConfig.printPermission))
			.then(
				Commands.literal(PRINT_WORLDS)
					.executes(PrintCommands::doPrintWorldsCommand))
			.then(
				Commands.literal(PRINT_DIMENSIONS)
					.executes(PrintCommands::doPrintDimensionsCommand));
	}
	
	/**
	 * Logs the id of each world in the server's world registry
	 * @param context command context
	 * @return The number of world IDs logged
	 * @throws CommandSyntaxException
	 */
	@SuppressWarnings("deprecation")
	public static int doPrintWorldsCommand(CommandContext<CommandSource> context) throws CommandSyntaxException
	{
		CommandSource source = context.getSource();
		Collection<ServerWorld> worlds = source.getServer()
			.forgeGetWorldMap()
			.values();
		worlds.forEach(world -> source.sendFeedback(new StringTextComponent(world.getDimensionKey().getLocation().toString()), true));
		return worlds.size();
	}

	/**
	 * Logs the id of each world in the server's dimension registry 
	 * @param context command context
	 * @return The number of dimension IDs logged
	 * @throws CommandSyntaxException
	 */
	public static int doPrintDimensionsCommand(CommandContext<CommandSource> context) throws CommandSyntaxException
	{
		CommandSource source = context.getSource();
		SimpleRegistry<Dimension> dimensionRegistry = source.getServer()
			.getServerConfiguration()
			.getDimensionGeneratorSettings()
			.func_236224_e_();
		Set<ResourceLocation> ids = dimensionRegistry.keySet();
		ids.forEach(id -> source.sendFeedback(new StringTextComponent(id.toString()), true));
		return ids.size();
	}
}
