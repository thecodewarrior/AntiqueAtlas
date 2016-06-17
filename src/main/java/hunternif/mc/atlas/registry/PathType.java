package hunternif.mc.atlas.registry;

import net.minecraft.util.ResourceLocation;

public class PathType extends IRegistryEntry.Impl {

	protected ResourceLocation tex;
	
	public PathType(ResourceLocation loc, ResourceLocation tex) {
		setRegistryName(loc);
		this.tex = tex;
	}
	
	public ResourceLocation getTexture() {
		return tex;
	}
	
}
