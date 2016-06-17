package hunternif.mc.atlas.api.impl;

import net.minecraft.world.World;

import hunternif.mc.atlas.AntiqueAtlasMod;
import hunternif.mc.atlas.api.PathAPI;
import hunternif.mc.atlas.core.DimensionPathsData;
import hunternif.mc.atlas.core.Path;
import hunternif.mc.atlas.core.PathsData;
import hunternif.mc.atlas.network.PacketDispatcher;
import hunternif.mc.atlas.network.bidirectional.DeletePathPacket;
import hunternif.mc.atlas.network.client.PathsPacket;
import hunternif.mc.atlas.network.server.AddPathPacket;
import hunternif.mc.atlas.registry.PathType;
import hunternif.mc.atlas.util.Log;

public class PathApiImpl implements PathAPI {

	@Override
	public void putPath(World world, int atlasID, PathType pathType, String label, int x1, int z1, int x2, int z2) {
		if (world.isRemote) {
			DimensionPathsData data = AntiqueAtlasMod.pathsData.getPathsData(atlasID, world).getPathsDataInDimension(world.provider.getDimension());
			int tmpID = data.nextTempID();
			data.insertTempPath(new Path(tmpID, pathType, label, world.provider.getDimension(), x1, z1, x2, z2));
			PacketDispatcher.sendToServer(new AddPathPacket(atlasID, world.provider.getDimension(), pathType, label, x1, z1, x2, z2, tmpID));

		} else {
			PathsData data = AntiqueAtlasMod.pathsData.getPathsData(atlasID, world);
			Path path = data.createAndSavePath(pathType, label, world.provider.getDimension(), x1, z1, x2, z2);
			PacketDispatcher.sendToAll(new PathsPacket(atlasID, world.provider.getDimension(), path));
		}
	}

	@Override
	public void deletePath(World world, int atlasID, int pathID) {
		DeletePathPacket packet = new DeletePathPacket(atlasID, pathID);
		if (world.isRemote) {
			if(pathID < 0) {
				Log.warn("Client tried to delete temporary path! they don't exist on servers dumbo.");
			}
			PacketDispatcher.sendToServer(packet);
		} else {
			PathsData data = AntiqueAtlasMod.pathsData.getPathsData(atlasID, world);
			data.removeMarker(pathID);
			PacketDispatcher.sendToAll(packet);
		}
	}
	
}
