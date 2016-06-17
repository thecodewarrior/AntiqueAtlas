package hunternif.mc.atlas.network.bidirectional;

import hunternif.mc.atlas.AntiqueAtlasMod;
import hunternif.mc.atlas.api.AtlasAPI;
import hunternif.mc.atlas.marker.MarkersData;
import hunternif.mc.atlas.network.AbstractMessage;
import hunternif.mc.atlas.util.Log;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Deletes a marker. A client sends this packet to the server as a request,
 * and the server sends it back to all players as a response, including the
 * original sender.
 * @author Hunternif
 */
public class DeletePathPacket extends AbstractMessage<DeletePathPacket> {
	private int atlasID;
	private int pathID;

	public DeletePathPacket() {}
	
	public DeletePathPacket(int atlasID, int pathID) {
		this.atlasID = atlasID;
		this.pathID = pathID;
	}

	@Override
	public void read(PacketBuffer buffer) throws IOException {
		atlasID = buffer.readVarIntFromBuffer();
		pathID = buffer.readVarIntFromBuffer();
	}

	@Override
	public void write(PacketBuffer buffer) throws IOException {
		buffer.writeVarIntToBuffer(atlasID);;
		buffer.writeVarIntToBuffer(pathID);
	}

	@Override
	protected void process(EntityPlayer player, Side side) {
		if (side.isServer()) {
			// Make sure it's this player's atlas :^)
			if (side.isServer() && !player.inventory.hasItemStack(new ItemStack(AntiqueAtlasMod.itemAtlas, 1, atlasID))) {
				Log.warn("Player %s attempted to delete path from someone else's Atlas #%d",
						player.getGameProfile().getName(), atlasID);
				return;
			}
			AtlasAPI.paths.deletePath(player.worldObj, atlasID, pathID);
		} else {
			MarkersData data = AntiqueAtlasMod.markersData.getMarkersData(atlasID, player.worldObj);
			data.removeMarker(pathID);
		}
	}
}
