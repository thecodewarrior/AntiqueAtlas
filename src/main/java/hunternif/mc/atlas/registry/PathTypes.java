package hunternif.mc.atlas.registry;

import net.minecraft.util.ResourceLocation;

import hunternif.mc.atlas.AntiqueAtlasMod;
import hunternif.mc.atlas.client.Textures;

public class PathTypes {

	public static PathTypes instance = new PathTypes();
	
	public static PathType DOTS;
	
	public PathTypes() {
		DOTS = new PathType(loc("dots"), Textures.PATH_DOTS);
	}
	
	public ResourceLocation loc(String name) {
		return new ResourceLocation(AntiqueAtlasMod.ID, name);
	}
	
}
