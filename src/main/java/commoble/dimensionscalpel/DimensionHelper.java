package commoble.dimensionscalpel;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.google.common.collect.BiMap;
import com.mojang.serialization.Lifecycle;

import commoble.dimensionscalpel.ReflectionBuddy.MutableInstanceField;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.SimpleRegistry;
import net.minecraft.world.Dimension;
import net.minecraft.world.World;
import net.minecraft.world.border.IBorderListener;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.server.ServerWorld;

public class DimensionHelper
{	

	/** fields in SimpleRegistry needed for unregistering dimensions **/
	static class SimpleRegistryAccess
	{
		@SuppressWarnings("rawtypes")
		public static final MutableInstanceField<SimpleRegistry, ObjectList<Dimension>> entryList =
			ReflectionBuddy.getInstanceField(SimpleRegistry.class, "field_243533_bf");
		@SuppressWarnings("rawtypes")
		public static final MutableInstanceField<SimpleRegistry, Object2IntMap<Dimension>> entryIndexMap =
			ReflectionBuddy.getInstanceField(SimpleRegistry.class, "field_243534_bg");
		@SuppressWarnings("rawtypes")
		public static final MutableInstanceField<SimpleRegistry, BiMap<ResourceLocation, Dimension>> registryObjects =
			ReflectionBuddy.getInstanceField(SimpleRegistry.class, "field_82596_a");
		@SuppressWarnings("rawtypes")
		public static final MutableInstanceField<SimpleRegistry, BiMap<RegistryKey<Dimension>, Dimension>> keyToObjectMap =
			ReflectionBuddy.getInstanceField(SimpleRegistry.class, "field_239649_bb_");
		@SuppressWarnings("rawtypes")
		public static final MutableInstanceField<SimpleRegistry, Map<Dimension, Lifecycle>> objectToLifecycleMap =
			ReflectionBuddy.getInstanceField(SimpleRegistry.class, "field_243535_bj");
		@SuppressWarnings("rawtypes")
		public static final MutableInstanceField<SimpleRegistry, Object[]> values =
			ReflectionBuddy.getInstanceField(SimpleRegistry.class, "field_186802_b");
		@SuppressWarnings("rawtypes")
		public static final MutableInstanceField<SimpleRegistry, Integer> nextFreeId =
			ReflectionBuddy.getInstanceField(SimpleRegistry.class, "field_195869_d");
	}
	
	static class WorldBorderAccess
	{
		public static final Function<WorldBorder, List<IBorderListener>> listeners =
			ReflectionBuddy.getInstanceFieldGetter(WorldBorder.class, "field_177758_a");
	}
	
	static class BorderListenerAccess
	{
		public static final Function<IBorderListener.Impl, WorldBorder> worldBorder =
			ReflectionBuddy.getInstanceFieldGetter(IBorderListener.Impl.class, "field_219590_a");
	}

	/**
	 * 
	 * @param server A server instance
	 * @param key The ID of the dimension to unregister
	 * @return true if the dimension was successfully unregistered, false otherwise
	 */
	@SuppressWarnings("deprecation")
	public static boolean unregisterDimension(MinecraftServer server, RegistryKey<Dimension> key)
	{
		// we need to remove the dimension/world from three places
		// the dimension registry, the world registry, and the world border listener
		// the world registry is just a simple map and the world border listener has a remove() method
		// the dimension registry has five sub-collections that need to be cleaned up
		// we should also probably move players from that world into the overworld if possible
		SimpleRegistry<Dimension> dimensionRegistry = server.getServerConfiguration()
			.getDimensionGeneratorSettings()
			.func_236224_e_();
		
		RegistryKey<World> worldKey = RegistryKey.getOrCreateKey(Registry.WORLD_KEY, key.getLocation());
		ServerWorld removedWorld = server.forgeGetWorldMap().remove(worldKey);
		if (removedWorld != null)
		{
			removeWorldBorderListener(server, removedWorld);
			removeRegisteredDimension(server, key, dimensionRegistry);
			server.markWorldsDirty();
			return true;
		}
		return false;
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
	
	private static void removeRegisteredDimension(MinecraftServer server, RegistryKey<Dimension> key, SimpleRegistry<Dimension> dimensionRegistry)
	{
		// okay, here we go
		// the registry has no API for removing objects, so we need to manually do that
		// there are five sub-collections that refer to the entry in some way
		// we also need to clear the value cache array and reset the next free ID
		Dimension removedDimension = dimensionRegistry.getValueForKey(key);
		SimpleRegistryAccess.registryObjects.get(dimensionRegistry).remove(key.getLocation());
		SimpleRegistryAccess.keyToObjectMap.get(dimensionRegistry).remove(key);
		SimpleRegistryAccess.objectToLifecycleMap.get(dimensionRegistry).remove(removedDimension);
		SimpleRegistryAccess.entryIndexMap.get(dimensionRegistry).removeInt(removedDimension);
		ObjectList<Dimension> oldList = SimpleRegistryAccess.entryList.get(dimensionRegistry);
		int oldSize = oldList.size();
		ObjectList<Dimension> newList = new ObjectArrayList<>(oldSize - 1);
		Object2IntMap<Dimension> newMap = new Object2IntOpenCustomHashMap<>(Util.identityHashStrategy()); 
		int nextFreeIndex=0;
		for (Dimension dimensionEntry : oldList)
		{
			if (dimensionEntry != removedDimension)
			{
				newList.add(nextFreeIndex, dimensionEntry);
				newMap.put(dimensionEntry, nextFreeIndex);
				nextFreeIndex++;
			}
		}
		SimpleRegistryAccess.entryIndexMap.set(dimensionRegistry, newMap);
		SimpleRegistryAccess.entryList.set(dimensionRegistry, newList);
		SimpleRegistryAccess.values.set(dimensionRegistry, null);
		SimpleRegistryAccess.nextFreeId.set(dimensionRegistry, nextFreeIndex);
	}
}
