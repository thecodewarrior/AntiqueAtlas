package hunternif.mc.atlas.network.server;

import java.io.IOException;

import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.relauncher.Side;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;

import hunternif.mc.atlas.AntiqueAtlasMod;
import hunternif.mc.atlas.core.Path;
import hunternif.mc.atlas.core.PathsData;
import hunternif.mc.atlas.network.AbstractMessage.AbstractServerMessage;
import hunternif.mc.atlas.network.PacketDispatcher;
import hunternif.mc.atlas.network.bidirectional.DeletePathPacket;
import hunternif.mc.atlas.network.client.PathsPacket;
import hunternif.mc.atlas.registry.PathRegistry;
import hunternif.mc.atlas.registry.PathType;
import hunternif.mc.atlas.util.Log;

/**
 * A request from a client to create a new marker. In order to prevent griefing,
 * the marker has to be local.
 * @author Hunternif
 */
public class AddPathPacket extends AbstractServerMessage<AddPathPacket> {
	private int atlasID;
	private int dimension;
	private PathType type;
	private String label;
	private int x1, z1, x2, z2;
	private int tempID;
	
	public AddPathPacket() {}

	/** Use this constructor when creating a <b>local</b> path. */
	public AddPathPacket(int atlasID, int dimension, PathType type, String label, int x1, int z1, int x2, int z2, int tempID) {
		this.atlasID = atlasID;
		this.dimension = dimension;
		this.type = type;
		this.label = label;
		this.x1 = x1;
		this.z1 = z1;
		this.x2 = x2;
		this.z2 = z2;
		this.tempID = tempID;
	}

	@Override
	public void read(PacketBuffer buffer) throws IOException {
		atlasID = buffer.readVarIntFromBuffer();
		dimension = buffer.readVarIntFromBuffer();
		type = PathRegistry.find( ByteBufUtils.readUTF8String(buffer) );
		label = ByteBufUtils.readUTF8String(buffer);
		x1 = buffer.readInt();
		z1 = buffer.readInt();
		x2 = buffer.readInt();
		z2 = buffer.readInt();
		tempID = buffer.readInt();
	}

	@Override
	public void write(PacketBuffer buffer) throws IOException {
		buffer.writeVarIntToBuffer(atlasID);
		buffer.writeVarIntToBuffer(dimension);
		ByteBufUtils.writeUTF8String(buffer, type.getRegistryName().toString());
		ByteBufUtils.writeUTF8String(buffer, label);
		buffer.writeInt(x1);
		buffer.writeInt(z1);
		buffer.writeInt(x2);
		buffer.writeInt(z2);
		buffer.writeInt(tempID);
	}

	@Override
	protected void process(EntityPlayer player, Side side) {
		// Make sure it's this player's atlas :^)
		if (!player.inventory.hasItemStack(new ItemStack(AntiqueAtlasMod.itemAtlas, 1, atlasID))) {
			Log.warn("Player %s attempted to put path into someone else's Atlas #%d",
					player.getGameProfile().getName(), atlasID);
			return;
		}
		PathsData pathsData = AntiqueAtlasMod.pathsData.getPathsData(atlasID, player.worldObj);
		Path path = pathsData.createAndSavePath(type, label, dimension, x1, z1, x2, z2);
		// If these are a manually set markers sent from the client, forward
		// them to other players. Including the original sender, because he
		// waits on the server to verify his path.
		PathsPacket packetForClients = new PathsPacket(atlasID, dimension, path);
		DeletePathPacket tmpPacket = new DeletePathPacket(atlasID, -tempID);
		PacketDispatcher.sendToAll(packetForClients);
		PacketDispatcher.sendTo(tmpPacket, (EntityPlayerMP)player);
	}
}
