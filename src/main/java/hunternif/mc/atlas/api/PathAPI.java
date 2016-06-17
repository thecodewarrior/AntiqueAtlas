package hunternif.mc.atlas.api;

import net.minecraft.world.World;

import hunternif.mc.atlas.registry.PathType;

public interface PathAPI {

	/**
	 * Put a path in the specified Atlas instance between the two specified block coordinates.
	 * <p>
	 * If called from the client, the player must have the atlas in their inventory. To prevent greifing.
	 * </p>
	 */
	void putPath(World world, int atlasID,
			PathType pathType, String label, int x1, int z1, int x2, int z2);
	
	/**
	 * Put a path in the specified Atlas instance between the two specified block coordinates
	 * 
	 * If this is called on the client with a negative ID, it will delete the temporary marker with that ID
	 * <p>
	 * If called from the client, the player must have the atlas in their inventory. To prevent greifing.
	 * </p>
	 */
	void deletePath(World world, int atlasID, int pathID);
	
}
