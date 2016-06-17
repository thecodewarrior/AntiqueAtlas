package hunternif.mc.atlas.registry;

import java.util.List;
import java.util.Set;

import net.minecraft.util.ResourceLocation;

import hunternif.mc.atlas.AntiqueAtlasMod;
import hunternif.mc.atlas.util.SaveData;

public class PathRegistry extends SaveData {
	public static PathRegistry INSTANCE = new PathRegistry();

	private final ResourceLocation DEFAULT_LOC = new ResourceLocation("antiqueatlas:dotted_small");
	
	private final MarkerRegistryImpl<PathType> registry;
	
	private PathRegistry() {
		registry = new MarkerRegistryImpl<PathType>(DEFAULT_LOC);
	}
	
	public static void register(ResourceLocation location, PathType type) {
		type.setRegistryName(location);
		register(type);
	}
	
	public static void register(PathType type) {
		INSTANCE.registry.register(type);
		INSTANCE.markDirty();
	}
	
	public static ResourceLocation getLoc(String type) {
		if(!type.contains(":"))
			type = AntiqueAtlasMod.ID + ":" + type;
		return new ResourceLocation(type);
	}
	
	public static PathType find(String type) {
		return find(getLoc(type));
	}
	
	public static PathType find(ResourceLocation type) {
		return INSTANCE.registry.getObject(type);
	}
	
	public static boolean hasKey(String type) {
		return hasKey(getLoc(type));
	}
	
	public static boolean hasKey(ResourceLocation loc) {
		return INSTANCE.registry.containsKey(loc);
	}
	
	public static List<PathType> getValues() {
		return INSTANCE.registry.getValues();
	}
	
	public static Set<ResourceLocation> getKeys() {
		return INSTANCE.registry.getKeys();
	}
}
