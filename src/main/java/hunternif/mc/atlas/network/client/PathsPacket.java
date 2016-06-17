package hunternif.mc.atlas.network.client;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.relauncher.Side;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.PacketBuffer;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import hunternif.mc.atlas.AntiqueAtlasMod;
import hunternif.mc.atlas.core.Path;
import hunternif.mc.atlas.core.PathsData;
import hunternif.mc.atlas.marker.Marker;
import hunternif.mc.atlas.network.AbstractMessage.AbstractClientMessage;
import hunternif.mc.atlas.registry.PathRegistry;
import hunternif.mc.atlas.registry.PathType;

/**
 * Sends markers set via API from server to client.
 * Only one dimension per packet.
 * The markers in one packet are either all global or all local.
 * @author Hunternif
 */
public class PathsPacket extends AbstractClientMessage<PathsPacket> {
	/** Used in place of atlasID to signify that the marker is global. */
	protected int atlasID;
	protected int dimension;
	protected final ListMultimap<PathType, Path> pathsByType = ArrayListMultimap.create();

	public PathsPacket() {}

	/** Use this constructor when creating a <b>local</b> marker. */
	public PathsPacket(int atlasID, int dimension, Path... paths) {
		this.atlasID = atlasID;
		this.dimension = dimension;
		for (Path path : paths) {
			pathsByType.put(path.getType(), path);
		}
	}

	public PathsPacket putPath(Path path) {
		pathsByType.put(path.getType(), path);
		return this;
	}

	public boolean isEmpty() {
		return pathsByType.isEmpty();
	}

	@Override
	public void read(PacketBuffer buffer) throws IOException {
		atlasID = buffer.readVarIntFromBuffer();
		dimension = buffer.readVarIntFromBuffer();
		int typesLength = buffer.readVarIntFromBuffer();
		for (int i = 0; i < typesLength; i++) {
			PathType type = PathRegistry.find(ByteBufUtils.readUTF8String(buffer));
			int pathsLength = buffer.readVarIntFromBuffer();
			for (int j = 0; j < pathsLength; j++) {
				Path marker = new Path(buffer.readVarIntFromBuffer(),
						type, ByteBufUtils.readUTF8String(buffer),
						dimension,
						buffer.readVarIntArray(), buffer.readVarIntArray());
				pathsByType.put(type, marker);
			}
		}
	}

	@Override
	public void write(PacketBuffer buffer) throws IOException {
		buffer.writeVarIntToBuffer(atlasID);
		buffer.writeVarIntToBuffer(dimension);
		Set<PathType> types = pathsByType.keySet();
		buffer.writeVarIntToBuffer(types.size());
		for (PathType type : types) {
			ByteBufUtils.writeUTF8String(buffer, type.getRegistryName().toString());
			List<Path> paths = pathsByType.get(type);
			buffer.writeVarIntToBuffer(paths.size());
			for (Path path : paths) {
				buffer.writeVarIntToBuffer(path.getId());
				ByteBufUtils.writeUTF8String(buffer, path.getLabel());
				buffer.writeVarIntArray(path.getXs());
				buffer.writeVarIntArray(path.getZs());
			}
		}
	}

	@Override
	protected void process(EntityPlayer player, Side side) {
		PathsData pathsData = AntiqueAtlasMod.pathsData.getPathsData(atlasID, player.worldObj);
		for (Path path : pathsByType.values()) {
			pathsData.loadPath(path);
		}
	}
}
