package hunternif.mc.atlas.markup;

import hunternif.mc.atlas.AntiqueAtlasMod;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientConnectedToServerEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides access to {@link MarkupData}. Maintains a cache on the client side,
 * because WorldClient is reset along with all WorldSavedData when the player
 * changes dimension (fixes #67).
 * @author Hunternif
 */
public class MarkupDataHandler {
	private static final String MARKUP_DATA_PREFIX = "aaMarkup_";
	
	private final Map<String, MarkupData> markupDataClientCache = new ConcurrentHashMap<>();
	
	/** Loads data for the given atlas or creates a new one. */
	public MarkupData getMarkupData(ItemStack stack, World world) {
		if (stack.getItem() == AntiqueAtlasMod.itemAtlas) {
			return getMarkupData(stack.getItemDamage(), world);
		} else {
			return null;
		}
	}
	
	/** Loads data for the given atlas ID or creates a new one. */
	public MarkupData getMarkupData(int atlasID, World world) {
		String key = getMarkupDataKey(atlasID);
		MarkupData data = null;
		if (world.isRemote) {
			// Since atlas data doesn't really belong to a single world-dimension,
			// it can be cached. This should fix #67
			data = markupDataClientCache.get(key);
		}
		if (data == null) {
			data = (MarkupData) world.loadData(MarkupData.class, key);
			if (data == null) {
				data = new MarkupData(key);
				world.setData(key, data);
			}
			if (world.isRemote) markupDataClientCache.put(key, data);
		}
		return data;
	}
	
	private String getMarkupDataKey(int atlasID) {
		return MARKUP_DATA_PREFIX + atlasID;
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
		markupDataClientCache.clear();
	}
}
