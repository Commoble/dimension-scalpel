package commoble.dimensionscalpel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.google.common.collect.BiMap;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Lifecycle;

import commoble.dimensionscalpel.ReflectionBuddy.MutableInstanceField;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.SimpleRegistry;
import net.minecraft.world.Dimension;
import net.minecraft.world.World;
import net.minecraft.world.border.IBorderListener;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.gen.settings.DimensionGeneratorSettings;
import net.minecraft.world.server.ServerWorld;

public class DimensionHelper
{	

	/** fields in SimpleRegistry needed for unregistering dimensions **/
	public static class SimpleRegistryAccess
	{
		@SuppressWarnings("rawtypes")
		public static final MutableInstanceField<SimpleRegistry, ObjectList<Dimension>> entryList =
			ReflectionBuddy.getInstanceField(SimpleRegistry.class, "field_243533_bf");
//		@SuppressWarnings("rawtypes")
//		public static final MutableInstanceField<SimpleRegistry, Object2IntMap<Dimension>> entryIndexMap =
//			ReflectionBuddy.getInstanceField(SimpleRegistry.class, "field_243534_bg");
//		@SuppressWarnings("rawtypes")
//		public static final MutableInstanceField<SimpleRegistry, BiMap<ResourceLocation, Dimension>> registryObjects =
//			ReflectionBuddy.getInstanceField(SimpleRegistry.class, "field_82596_a");
		@SuppressWarnings("rawtypes")
		public static final MutableInstanceField<SimpleRegistry, BiMap<RegistryKey<Dimension>, Dimension>> keyToObjectMap =
			ReflectionBuddy.getInstanceField(SimpleRegistry.class, "field_239649_bb_");
		@SuppressWarnings("rawtypes")
		public static final MutableInstanceField<SimpleRegistry, Map<Dimension, Lifecycle>> objectToLifecycleMap =
			ReflectionBuddy.getInstanceField(SimpleRegistry.class, "field_243535_bj");
//		@SuppressWarnings("rawtypes")
//		public static final MutableInstanceField<SimpleRegistry, Object[]> values =
//			ReflectionBuddy.getInstanceField(SimpleRegistry.class, "field_186802_b");
//		@SuppressWarnings("rawtypes")
//		public static final MutableInstanceField<SimpleRegistry, Integer> nextFreeId =
//			ReflectionBuddy.getInstanceField(SimpleRegistry.class, "field_195869_d");
	}
	
	public static class DimensionGeneratorSettingsAccess
	{
		public static final MutableInstanceField<DimensionGeneratorSettings, SimpleRegistry<Dimension>> dimensionRegistry =
			ReflectionBuddy.getInstanceField(DimensionGeneratorSettings.class, "field_236208_h_");
	}
	
	public static class WorldBorderAccess
	{
		public static final Function<WorldBorder, List<IBorderListener>> listeners =
			ReflectionBuddy.getInstanceFieldGetter(WorldBorder.class, "field_177758_a");
	}
	
	public static class BorderListenerAccess
	{
		public static final Function<IBorderListener.Impl, WorldBorder> worldBorder =
			ReflectionBuddy.getInstanceFieldGetter(IBorderListener.Impl.class, "field_219590_a");
	}

	/**
	 * 
	 * @param server A server instance
	 * @param keys The IDs of the dimensions to unregister
	 * @return the set of dimension IDs that were successfully unregistered, and a list of the worlds corresponding to them.
	 * Be aware that these serverworlds will no longer be accessible via the MinecraftServer after calling this method.
	 */
	@SuppressWarnings("deprecation")
	public static Pair<Set<RegistryKey<Dimension>>, List<ServerWorld>> unregisterDimensions(MinecraftServer server, Set<RegistryKey<Dimension>> keys)
	{
		// we need to remove the dimension/world from three places
		// the dimension registry, the world registry, and the world border listener
		// the world registry is just a simple map and the world border listener has a remove() method
		// the dimension registry has five sub-collections that need to be cleaned up
		// we should also probably move players from that world into the overworld if possible
		DimensionGeneratorSettings dimensionGeneratorSettings = server.getServerConfiguration()
			.getDimensionGeneratorSettings();
//		SimpleRegistry<Dimension> dimensionRegistry = server.getServerConfiguration()
//			.getDimensionGeneratorSettings()
//			.func_236224_e_();
		
		// set of keys whose worlds were found and removed
		Set<RegistryKey<Dimension>> removedKeys = new HashSet<>();
		List<ServerWorld> removedWorlds = new ArrayList<>(keys.size());
		for (RegistryKey<Dimension> key : keys)
		{
			RegistryKey<World> worldKey = RegistryKey.getOrCreateKey(Registry.WORLD_KEY, key.getLocation());
			ServerWorld removedWorld = server.forgeGetWorldMap().remove(worldKey);
			if (removedWorld != null)
			{
				removeWorldBorderListener(server, removedWorld);
				removedKeys.add(key);
				removedWorlds.add(removedWorld);
			}
		}

		if (!removedKeys.isEmpty())
		{
			removeRegisteredDimensions(server, dimensionGeneratorSettings, removedKeys);
			server.markWorldsDirty();
		}
		return Pair.of(removedKeys, removedWorlds);
	}
	
	private static void removeWorldBorderListener(MinecraftServer server, ServerWorld removedWorld)
	{
		ServerWorld overworld = server.getWorld(World.OVERWORLD);
		WorldBorder overworldBorder = overworld.getWorldBorder();
		List<IBorderListener> listeners = WorldBorderAccess.listeners.apply(overworldBorder);
		IBorderListener target = null;
		for (IBorderListener listener : listeners)
		{
			if (listener instanceof IBorderListener.Impl)
			{
				IBorderListener.Impl impl = (IBorderListener.Impl)listener;
				WorldBorder border = BorderListenerAccess.worldBorder.apply(impl);
				if (removedWorld.getWorldBorder() == border)
				{
					target = listener;
					break;
				}
			}
		}
		if (target != null)
		{
			overworldBorder.removeListener(target);
		}
	}
	
//	private static void removeRegisteredDimension(MinecraftServer server, RegistryKey<Dimension> key, SimpleRegistry<Dimension> dimensionRegistry)
//	{
//		// okay, here we go
//		// the registry has no API for removing objects, so we need to manually do that
//		// there are five sub-collections that refer to the entry in some way
//		// we also need to clear the value cache array and reset the next free ID
//		Dimension removedDimension = dimensionRegistry.getValueForKey(key);
//		SimpleRegistryAccess.registryObjects.get(dimensionRegistry).remove(key.getLocation());
//		SimpleRegistryAccess.keyToObjectMap.get(dimensionRegistry).remove(key);
//		SimpleRegistryAccess.objectToLifecycleMap.get(dimensionRegistry).remove(removedDimension);
//		SimpleRegistryAccess.entryIndexMap.get(dimensionRegistry).removeInt(removedDimension);
//		ObjectList<Dimension> oldList = SimpleRegistryAccess.entryList.get(dimensionRegistry);
//		int oldSize = oldList.size();
//		ObjectList<Dimension> newList = new ObjectArrayList<>(oldSize - 1);
//		Object2IntMap<Dimension> newMap = new Object2IntOpenCustomHashMap<>(Util.identityHashStrategy()); 
//		int nextFreeIndex=0;
//		for (Dimension dimensionEntry : oldList)
//		{
//			if (dimensionEntry != removedDimension)
//			{
//				newList.add(nextFreeIndex, dimensionEntry);
//				newMap.put(dimensionEntry, nextFreeIndex);
//				nextFreeIndex++;
//			}
//		}
//		SimpleRegistryAccess.entryIndexMap.set(dimensionRegistry, newMap);
//		SimpleRegistryAccess.entryList.set(dimensionRegistry, newList);
//		SimpleRegistryAccess.values.set(dimensionRegistry, null);
//		SimpleRegistryAccess.nextFreeId.set(dimensionRegistry, nextFreeIndex);
//	}
	
	private static void removeRegisteredDimensions(MinecraftServer server, DimensionGeneratorSettings settings, Set<RegistryKey<Dimension>> keysToRemove)
	{
		// get all the old dimensions except the given one, add them to a new registry in the same order
		SimpleRegistry<Dimension> oldRegistry = DimensionGeneratorSettingsAccess.dimensionRegistry.get(settings);
		SimpleRegistry<Dimension> newRegistry = new SimpleRegistry<Dimension>(Registry.DIMENSION_KEY, oldRegistry.getLifecycle());
		
		ObjectList<Dimension> oldList = SimpleRegistryAccess.entryList.get(oldRegistry);
		BiMap<RegistryKey<Dimension>, Dimension> oldMap = SimpleRegistryAccess.keyToObjectMap.get(oldRegistry);
		BiMap<Dimension, RegistryKey<Dimension>> oldInvertedMap = oldMap.inverse();
		Map<Dimension, Lifecycle> oldLifecycles = SimpleRegistryAccess.objectToLifecycleMap.get(oldRegistry);
		
		for (Dimension dimension : oldList)
		{
			RegistryKey<Dimension> key = oldInvertedMap.get(dimension);
			if (!keysToRemove.contains(key))
			{
				newRegistry.register(key, dimension, oldLifecycles.get(dimension));
			}
		}
		
		// then replace the old registry with the new registry
		DimensionGeneratorSettingsAccess.dimensionRegistry.set(settings, newRegistry);
	}
}
