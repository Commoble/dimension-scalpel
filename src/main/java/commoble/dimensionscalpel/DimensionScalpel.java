package commoble.dimensionscalpel;

import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.google.common.collect.ImmutableSet;

import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.RegistryKey;
import net.minecraft.world.Dimension;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(DimensionScalpel.MODID)
public class DimensionScalpel
{
	public static final String MODID = "dimensionscalpel";
	public static DimensionScalpel INSTANCE;
	
	public static final Set<RegistryKey<World>> VANILLA_WORLDS = ImmutableSet.of(World.OVERWORLD, World.THE_NETHER, World.THE_END);
	public static final Set<RegistryKey<Dimension>> VANILLA_DIMENSIONS = ImmutableSet.of(Dimension.OVERWORLD, Dimension.THE_NETHER, Dimension.THE_END);
	
	public final ServerConfig serverConfig;
	
	public DimensionScalpel()
	{
		INSTANCE = this;
		
		this.serverConfig= ConfigHelper.register(Type.SERVER, ServerConfig::new);
		
		IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
		IEventBus forgeBus = MinecraftForge.EVENT_BUS;
		
		forgeBus.addListener(EventPriority.HIGH, this::onRegisterCommands);
	}
	
	void onRegisterCommands(RegisterCommandsEvent event)
	{
		event.getDispatcher()
			.register(
				Commands.literal(MODID) // all commands start with /dimensionscalpel
					.then(RemoveDimensionsCommands.buildRemoveDimensionCommand())
					.then(RemoveDimensionsCommands.buildRemoveAllCommand())
					.then(RemoveDimensionsCommands.buildResetDimensionsCommand())
					.then(PrintCommands.build()));
	}
	
	
	
	public static Predicate<CommandSource> isPermissionSufficient(Supplier<Integer> requiredPermissionGetter)
	{
		return source -> source.hasPermissionLevel(requiredPermissionGetter.get());
	}
}
