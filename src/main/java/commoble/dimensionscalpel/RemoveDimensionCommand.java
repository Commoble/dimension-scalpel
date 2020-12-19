package commoble.dimensionscalpel;

import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.DimensionArgument;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.Dimension;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

public class RemoveDimensionCommand
{	
	public static final String REMOVE_COMMAND = "remove";
	public static final String WORLD_ARG = "world";
	public static final String REMOVE_SUCCESS_FEEDBACK = "dimensionscalpel.commands.remove.success";
	public static final String KICK_COUNT_FEEDBACK = "dimensionscalpel.commands.remove.kick_count";
	public static final String REMOVE_VANILLA_FAIL = "dimensionscalpel.commands.remove.vanilla_fail";
	public static final ITextComponent DIMENSION_REMOVAL_KICK_REASON = new TranslationTextComponent("dimensionscalpel.commands.remove.kick_reason");
	public static final Set<RegistryKey<World>> VANILLA_WORLDS = ImmutableSet.of(World.OVERWORLD, World.THE_NETHER, World.THE_END);
	
	public static ArgumentBuilder<CommandSource,?> build()
	{
		return Commands.literal(REMOVE_COMMAND)
			.requires(DimensionScalpel.isPermissionSufficient(DimensionScalpel.INSTANCE.serverConfig.removePermission))
			.then(
				Commands.argument(WORLD_ARG, DimensionArgument.getDimension())
					.executes(RemoveDimensionCommand::doRemoveDimensionCommand));
	}
	
	/**
	 * 
	 * @param context command context
	 * @return If the dimension was removed, returns 1 + the number of players kicked from that dimension;
	 * If the dimension could not be removed, returns 0
	 * @throws CommandSyntaxException
	 */
	public static int doRemoveDimensionCommand(CommandContext<CommandSource> context) throws CommandSyntaxException
	{
		CommandSource source = context.getSource();
		ServerWorld world = DimensionArgument.getDimensionArgument(context, WORLD_ARG);
		RegistryKey<World> worldKey = world.getDimensionKey();
		
		// prevent unregistering of vanilla dimensions for safety purposes (vanilla tends to crash otherwise)
		if (VANILLA_WORLDS.contains(worldKey))
		{
			source.sendFeedback(new TranslationTextComponent(REMOVE_VANILLA_FAIL, worldKey.getLocation()).mergeStyle(TextFormatting.RED), true);
			return 0;
		}
		
		MinecraftServer server = context.getSource().getServer();
		RegistryKey<Dimension> dimensionKey = RegistryKey.getOrCreateKey(Registry.DIMENSION_KEY, worldKey.getLocation());
		if (DimensionHelper.unregisterDimension(server, dimensionKey))
		{
			List<ServerPlayerEntity> players = world.getPlayers();
			int playerCount = players.size();
			for (ServerPlayerEntity serverplayerentity : world.getPlayers())
			{
				serverplayerentity.connection.disconnect(DIMENSION_REMOVAL_KICK_REASON);
			}
			source.sendFeedback(new TranslationTextComponent(REMOVE_SUCCESS_FEEDBACK, dimensionKey.getLocation()), true);
			source.sendFeedback(new TranslationTextComponent(KICK_COUNT_FEEDBACK, playerCount), true);
			
			return playerCount+1;
		}
		else
		{
			return 0;
		}
	}
}
