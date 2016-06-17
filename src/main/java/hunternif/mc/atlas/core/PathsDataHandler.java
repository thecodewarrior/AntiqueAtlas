package hunternif.mc.atlas.core;

import hunternif.mc.atlas.AntiqueAtlasMod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientConnectedToServerEvent;

/**
 * Provides access to {@link PathsData}. Maintains a cache on the client side,
 * because WorldClient is reset along with all WorldSavedData when the player
 * changes dimension (fixes #67).
 * @author Hunternif
 */
public class PathsDataHandler {
	protected static final String Paths_DATA_PREFIX = "aaPaths_";
	
	private final Map<String, PathsData> pathsDataClientCache = new ConcurrentHashMap<String, PathsData>();
	
	/** Loads data for the given atlas or creates a new one. */
	public PathsData getPathsData(ItemStack stack, World world) {
		if (stack.getItem() == AntiqueAtlasMod.itemAtlas) {
			return getPathsData(stack.getItemDamage(), world);
		} else {
			return null;
		}
	}
	
	/** Loads data for the given atlas ID or creates a new one. */
	public PathsData getPathsData(int atlasID, World world) {
		String key = getPathsDataKey(atlasID);
		PathsData data = null;
		if (world.isRemote) {
			// Since atlas data doesn't really belong to a single world-dimension,
			// it can be cached. This should fix #67
			data = pathsDataClientCache.get(key);
		}
		if (data == null) {
			data = (PathsData) world.loadItemData(PathsData.class, key);
			if (data == null) {
				data = new PathsData(key);
				world.setItemData(key, data);
			}
			if (world.isRemote) pathsDataClientCache.put(key, data);
		}
		return data;
	}
	
	protected String getPathsDataKey(int atlasID) {
		return Paths_DATA_PREFIX + atlasID;
	}
	
	/**
	 * This method resets the cache when the client loads a new world.
	 * It is required in order that old markers data is not
	 * transferred from a previous world the client visited.
	 * <p>
	 * Using a "connect" event instead of "disconnect" because according to a
	 * form post, the latter event isn't actually fired on the client.
	 * </p>
	 */
	@SubscribeEvent
	public void onClientConnectedToServer(ClientConnectedToServerEvent event) {
		pathsDataClientCache.clear();
	}
}
