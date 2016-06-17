package hunternif.mc.atlas.core;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraftforge.common.util.Constants;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldSavedData;

import hunternif.mc.atlas.network.PacketDispatcher;
import hunternif.mc.atlas.network.client.PathsPacket;
import hunternif.mc.atlas.registry.PathRegistry;
import hunternif.mc.atlas.registry.PathType;
import hunternif.mc.atlas.registry.PathTypes;
import hunternif.mc.atlas.util.Log;

public class PathsData extends WorldSavedData {
	private static final int VERSION = 1;
	private static final String TAG_VERSION = "aaVersion";
	private static final String TAG_DIMENSION_MAP_LIST = "dimMap";
	private static final String TAG_DIMENSION_ID = "dimID";
	private static final String TAG_PATHS = "paths";
	private static final String TAG_PATH_ID = "id";
	private static final String TAG_PATH_TYPE = "pathType";
	private static final String TAG_PATH_LABEL = "label";
	private static final String TAG_PATH_X = "x";
	private static final String TAG_PATH_Y = "y";
	
	/** Markers are stored in lists within square areas this many MC chunks
	 * across. */
	public static final int CHUNK_STEP = 8;
	
	/** Set of players this data has been sent to, only once after they connect. */
	private final Set<EntityPlayer> playersSentTo = new HashSet<EntityPlayer>();
	
	private final AtomicInteger largestID = new AtomicInteger(0);
	
	protected int getNewID() {
		return largestID.incrementAndGet();
	}
	
	private final Map<Integer /*path ID*/, Path> idMap = new ConcurrentHashMap<Integer, Path>(2, 0.75f, 2);
	
	private final Map<Integer /*dimension ID*/, DimensionPathsData> dimensionMap =
			new ConcurrentHashMap<Integer, DimensionPathsData>(2, 0.75f, 2);
	
	public PathsData(String key) {
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
			NBTTagList tagList = tag.getTagList(TAG_PATHS, Constants.NBT.TAG_COMPOUND);
			for (int i = 0; i < tagList.tagCount(); i++) {
				NBTTagCompound pathTag = tagList.getCompoundTagAt(i);
				int id = pathTag.getInteger(TAG_PATH_ID);
				if (getMarkerByID(id) != null) {
					Log.warn("Loading path with duplicate id %d. Getting new id", id);
					id = getNewID();
				}
				this.markDirty();
				
				if (largestID.intValue() < id) {
					largestID.set(id);
				}
				
				Path path = new Path(
						id,
						PathRegistry.find(pathTag.getString(TAG_PATH_TYPE)),
						pathTag.getString(TAG_PATH_LABEL),
						dimensionID,
						pathTag.getIntArray(TAG_PATH_X),
						pathTag.getIntArray(TAG_PATH_Y));
				loadPath(path);
			}
			Path apath = new Path(1000, PathTypes.DOTS, "ASDF", dimensionID, new int[] { 0, 10, 10, 0 }, new int[] { 0, 10, 20, 20 });
			loadPath(apath);
		}
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound compound) {
		Log.info("Saving local paths data to NBT");
		compound.setInteger(TAG_VERSION, VERSION);
		NBTTagList dimensionMapList = new NBTTagList();
		for (Integer dimension : dimensionMap.keySet()) {
			NBTTagCompound tag = new NBTTagCompound();
			tag.setInteger(TAG_DIMENSION_ID, dimension);
			DimensionPathsData data = getPathsDataInDimension(dimension);
			NBTTagList tagList = new NBTTagList();
			for (Path path : data.getAllPaths()) {
				Log.debug("Saving path %s", path.toString());
				NBTTagCompound pathTag = new NBTTagCompound();
				pathTag.setInteger(TAG_PATH_ID, path.getId());
				pathTag.setString(TAG_PATH_TYPE, path.getType().getRegistryName().toString());
				pathTag.setString(TAG_PATH_LABEL, path.getLabel());
				pathTag.setIntArray(TAG_PATH_X, path.getXs());
				pathTag.setIntArray(TAG_PATH_Y, path.getZs());
				tagList.appendTag(pathTag);
			}
			tag.setTag(TAG_PATHS, tagList);
			dimensionMapList.appendTag(tag);
		}
		compound.setTag(TAG_DIMENSION_MAP_LIST, dimensionMapList);
		
		return compound;
	}
	
	public Set<Integer> getVisitedDimensions() {
		return dimensionMap.keySet();
	}
	
	/** This method is rather inefficient, use it sparingly. */
	public Collection<Path> getPathsInDimension(int dimension) {
		return getPathsDataInDimension(dimension).getAllPaths();
	}
	
	/** Creates a new instance of {@link DimensionPathsData}, if necessary. */
	public DimensionPathsData getPathsDataInDimension(int dimension) {
		DimensionPathsData data = dimensionMap.get(dimension);
		if (data == null) {
			data = new DimensionPathsData(this, dimension);
			dimensionMap.put(dimension, data);
		}
		return data;
	}
	
	public Path getMarkerByID(int id) {
		return idMap.get(id);
	}
	public Path removeMarker(int id) {
		Path path = getMarkerByID(id);
		if (path == null) return null;
		if (idMap.remove(id) != null) {
			getPathsDataInDimension(path.getDimension()).removePath(path);
			markDirty();
		}
		return path;
	}
	
	/** For internal use. Use the {@link PathAPI} to put paths! This method
	 * creates a new path from the given data, saves and returns it.
	 * Server side only! */
	public Path createAndSaveMarker(PathType type, String label, int dimension, int[] x, int[] z, boolean visibleAhead) {
		Path path = new Path(getNewID(), type, label, dimension, x, z);
		Log.info("Created new path %s", path.toString());
		idMap.put(path.getId(), path);
		getPathsDataInDimension(path.getDimension()).insertPath(path);
		markDirty();
		return path;
	}
	
	/**
	 * For internal use, when paths are loaded from NBT or sent from the
	 * server. IF a path's id is conflicting, the path is not loaded!
	 * @return the path instance that was added.
	 */
	public Path loadPath(Path path) {
		if (!idMap.containsKey(path.getId())) {
			idMap.put(path.getId(), path);
			getPathsDataInDimension(path.getDimension()).insertPath(path);
		}
		return path;
	}
	
	public boolean isSyncedOnPlayer(EntityPlayer player) {
		return playersSentTo.contains(player);
	}
	
	/** Send all data to the player in several packets. Called once during the
	 * first run of ItemAtals.onUpdate(). */
	public void syncOnPlayer(int atlasID, EntityPlayer player) {
		for (Integer dimension : dimensionMap.keySet()) {
			PathsPacket packet = new PathsPacket(atlasID, dimension);
			DimensionPathsData data = getPathsDataInDimension(dimension);
			for (Path path : data.getAllPaths()) {
				packet.putPath(path);
			}
			PacketDispatcher.sendTo(packet, (EntityPlayerMP) player);
		}
		Log.info("Sent paths data #%d to player %s", atlasID, player.getName());
		playersSentTo.add(player);
	}
	
	public boolean isEmpty() {
		return idMap.isEmpty();
	}
	
}
