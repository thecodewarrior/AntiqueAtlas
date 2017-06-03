package hunternif.mc.atlas.network.server;

import hunternif.mc.atlas.AntiqueAtlasMod;
import hunternif.mc.atlas.markup.EnumMarkupColor;
import hunternif.mc.atlas.markup.Markup;
import hunternif.mc.atlas.markup.MarkupData;
import hunternif.mc.atlas.network.AbstractMessage.AbstractServerMessage;
import hunternif.mc.atlas.network.PacketDispatcher;
import hunternif.mc.atlas.network.client.MarkupPacket;
import hunternif.mc.atlas.util.Log;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;

import java.io.IOException;

/**
 * A request from a client to create a new marker. In order to prevent griefing,
 * the marker has to be local.
 * @author Hunternif
 */
public class SetMarkupPacket extends AbstractServerMessage<SetMarkupPacket> {
	private int atlasID;
	private int dimension;
	private EnumMarkupColor color;
	private int x, y;

	public SetMarkupPacket() {}

	/** Use this constructor when creating a <b>local</b> marker. */
	public SetMarkupPacket(int atlasID, int dimension, EnumMarkupColor color, int x, int y) {
		this.atlasID = atlasID;
		this.dimension = dimension;
		this.color = color;
		this.x = x;
		this.y = y;
	}

	@Override
	public void read(PacketBuffer buffer) throws IOException {
		atlasID = buffer.readVarInt();
		dimension = buffer.readVarInt();
		byte colorId = buffer.readByte();
		color = colorId < 0 ? null : EnumMarkupColor.values()[colorId % EnumMarkupColor.values().length];
		x = buffer.readInt();
		y = buffer.readInt();
	}

	@Override
	public void write(PacketBuffer buffer) throws IOException {
		buffer.writeVarInt(atlasID);
		buffer.writeVarInt(dimension);
		if(color == null) {
			buffer.writeByte(-1);
		} else {
			buffer.writeByte(color.ordinal());
		}
		buffer.writeInt(x);
		buffer.writeInt(y);
	}

	@Override
	protected void process(EntityPlayer player, Side side) {
		// Make sure it's this player's atlas :^)
		if (AntiqueAtlasMod.settings.itemNeeded && !player.inventory.hasItemStack(new ItemStack(AntiqueAtlasMod.itemAtlas, 1, atlasID))) {
			Log.warn("Player %s attempted to draw into someone else's Atlas #%d",
					player.getGameProfile().getName(), atlasID);
			return;
		}
		MarkupData markupData = AntiqueAtlasMod.markupData.getMarkupData(atlasID, player.getEntityWorld());
		Markup markup = markupData.setColor(dimension, x, y, color);
		// If these are a manually set markers sent from the client, forward
		// them to other players. Including the original sender, because he
		// waits on the server to verify his marker.
		MarkupPacket packetForClients = new MarkupPacket(atlasID, dimension, markup);
		PacketDispatcher.sendToAll(packetForClients);
	}
}
