package hunternif.mc.atlas.markup;

import hunternif.mc.atlas.network.PacketDispatcher;
import hunternif.mc.atlas.network.client.MarkupPacket;
import hunternif.mc.atlas.util.Log;
import hunternif.mc.atlas.util.ShortVec2;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Contains markers, mapped to dimensions, and then to their chunk coordinates.
 * <p>
 * On the server a separate instance of MarkupData contains all the global
 * markers, which are also copied to atlases, but not saved with them.
 * At runtime clients have both types of markers in the same collection..
 * </p>
 * @author Hunternif
 */
public class MarkupData extends WorldSavedData {
	private static final int VERSION = 1;
	private static final String TAG_VERSION = "aaVersion";
	private static final String TAG_DIMENSION_MAP_LIST = "dimMap";
	private static final String TAG_DIMENSION_ID = "dimID";
	private static final String TAG_MARKUP = "markup";
	private static final String TAG_MARKUP_X = "x";
	private static final String TAG_MARKUP_Z = "z";
	private static final String TAG_MARKUP_PIXELS = "pixels";
	private static final String TAG_MARKUP_PIXELS_X = "x";
	private static final String TAG_MARKUP_PIXELS_Z = "z";
	private static final String TAG_MARKUP_PIXELS_C = "c";
	
	/** Set of players this data has been sent to, only once after they connect. */
	private final Set<EntityPlayer> playersSentTo = new HashSet<>();
	
	private final AtomicInteger largestID = new AtomicInteger(0);
	
	private int getNewID() {
		return largestID.incrementAndGet();
	}
	
	/**
	 * Maps a list of markers in a square to the square's coordinates, then to
	 * dimension ID. It exists in case someone needs to quickly find markers
	 * located in a square.
	 * Within the list markers are ordered by the Z coordinate, so that markers
	 * placed closer to the south will appear in front of those placed closer to
	 * the north.
	 * TODO: consider using Quad-tree. At small zoom levels iterating through
	 * chunks to render markers gets very slow.
	 */
	private final Map<Integer /*dimension ID*/, DimensionMarkupData> dimensionMap =
			new ConcurrentHashMap<>(2, 0.75f, 2);
	
	public MarkupData(String key) {
		super(key);
	}

	@Override
	public void readFromNBT(NBTTagCompound compound) {
		int version = compound.getInteger(TAG_VERSION);
		if (version < VERSION) {
			Log.warn("Outdated atlas data format! Was %d but current is %d", version, VERSION);
			this.markDirty();
		}
		NBTTagList dimensionMapList = compound.getTagList(TAG_DIMENSION_MAP_LIST, Constants.NBT.TAG_COMPOUND);
		for (int d = 0; d < dimensionMapList.tagCount(); d++) {
			NBTTagCompound tag = dimensionMapList.getCompoundTagAt(d);
			int dimensionID = tag.getInteger(TAG_DIMENSION_ID);
			NBTTagList tagList = tag.getTagList(TAG_MARKUP, Constants.NBT.TAG_COMPOUND);
			for (int i = 0; i < tagList.tagCount(); i++) {
				NBTTagCompound markupChunkTag = tagList.getCompoundTagAt(i);
				Markup markup = new Markup(
					dimensionID,
					markupChunkTag.getInteger(TAG_MARKUP_X),
					markupChunkTag.getInteger(TAG_MARKUP_Z)
				);
				NBTTagList pixelsList = markupChunkTag.getTagList(TAG_MARKUP_PIXELS, Constants.NBT.TAG_COMPOUND);
				for (int j = 0; j < pixelsList.tagCount(); j++) {
					NBTTagCompound pixel = pixelsList.getCompoundTagAt(j);
					markup.setColor(
						(int)pixel.getByte(TAG_MARKUP_PIXELS_X),
						(int)pixel.getByte(TAG_MARKUP_PIXELS_Z),
						EnumMarkupColor.values()[ pixel.getByte(TAG_MARKUP_PIXELS_C) % EnumMarkupColor.values().length ]
					);
				}
				insertMarkup(markup);
			}
		}
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound compound) {
		Log.info("Saving local markup data to NBT");
		compound.setInteger(TAG_VERSION, VERSION);
		NBTTagList dimensionMapList = new NBTTagList();
		for (Integer dimension : dimensionMap.keySet()) {
			NBTTagCompound tag = new NBTTagCompound();
			tag.setInteger(TAG_DIMENSION_ID, dimension);
			DimensionMarkupData data = getMarkupDataInDimension(dimension);
			NBTTagList tagList = new NBTTagList();
			for (Markup markup : data.getMarkupList()) {
				Log.debug("Saving markup %s", markup.toString());
				NBTTagCompound markupTag = new NBTTagCompound();
				markupTag.setInteger(TAG_MARKUP_X, markup.getX());
				markupTag.setInteger(TAG_MARKUP_Z, markup.getZ());
				
				
				
				NBTTagList pixelsList = new NBTTagList();
				
				markup.forEach((key, color) -> {
					NBTTagCompound pixelTag = new NBTTagCompound();
					
					ShortVec2 vec = markup.pos(key);
					pixelTag.setByte(TAG_MARKUP_PIXELS_X, (byte)vec.x);
					pixelTag.setByte(TAG_MARKUP_PIXELS_Z, (byte)vec.y);
					pixelTag.setByte(TAG_MARKUP_PIXELS_C, (byte)color.ordinal());
					
					pixelsList.appendTag(pixelTag);
					return true;
				});
				
				markupTag.setTag(TAG_MARKUP_PIXELS, pixelsList);
				
				tagList.appendTag(markupTag);
			}
			tag.setTag(TAG_MARKUP, tagList);
			dimensionMapList.appendTag(tag);
		}
		compound.setTag(TAG_DIMENSION_MAP_LIST, dimensionMapList);
		
		return compound;
	}
	
	public Set<Integer> getVisitedDimensions() {
		return dimensionMap.keySet();
	}
	
	/** Creates a new instance of {@link DimensionMarkupData}, if necessary. */
	public DimensionMarkupData getMarkupDataInDimension(int dimension) {
		return dimensionMap.computeIfAbsent(dimension, k -> new DimensionMarkupData(this, dimension));
	}
	
	public EnumMarkupColor getColor(int dimension, int x, int z) {
		Markup markup = getMarkupAtChunk(dimension, x >> 4, z >> 4);
		if(markup == null)
			return null;
		return markup.getColor(x & 0x0F, z & 0x0F);
	}
	public Markup setColor(int dimension, int x, int z, EnumMarkupColor color) {
		Markup markup = getOrCreateMarkupAtChunk(dimension, x >> 4, z >> 4);
		markup.setColor(x & 0x0F, z & 0x0F, color);
		Log.info("Setting pixel %d, %d. Chunk %d, %d. Pos %d, %d", x, z, x >> 4, z >> 4, x & 0x0F, z & 0x0F);
		this.markDirty();
		return markup;
	}
	
	public Markup getMarkupAtChunk(int dimension, int x, int z) {
		return getMarkupDataInDimension(dimension).getMarkupAtChunk(x, z);
	}
	public Markup getOrCreateMarkupAtChunk(int dimension, int x, int z) {
		Markup markup = getMarkupAtChunk(dimension, x, z);
		if(markup == null) {
			markup = new Markup(dimension, x, z);
			insertMarkup(markup);
		}
		return markup;
	}
	
	/**
	 * For internal use, when markers are loaded from NBT or sent from the
	 * server. IF a markup's id is conflicting, the markup will not load!
	 * @return the markup instance that was added.
	 */
	public Markup insertMarkup(Markup markup) {
		getMarkupDataInDimension(markup.getDimension()).insertMarkup(markup);
		return markup;
	}
	
	public boolean isSyncedOnPlayer(EntityPlayer player) {
		return playersSentTo.contains(player);
	}
	
	/** Send all data to the player in several packets. Called once during the
	 * first run of ItemAtals.onUpdate(). */
	public void syncOnPlayer(int atlasID, EntityPlayer player) {
		for (Integer dimension : dimensionMap.keySet()) {
			MarkupPacket packet = newMarkupPacket(atlasID, dimension);
			DimensionMarkupData data = getMarkupDataInDimension(dimension);
			for (Markup markup : data.getMarkupList()) {
				packet.putMarkup(markup);
			}
			PacketDispatcher.sendTo(packet, (EntityPlayerMP) player);
		}
		Log.info("Sent markup data #%d to player %s", atlasID, player.getName());
		playersSentTo.add(player);
	}
	
	/** To be overridden in GlobalMarkupData. */
	MarkupPacket newMarkupPacket(int atlasID, int dimension) {
		return new MarkupPacket(atlasID, dimension);
	}
	
}
