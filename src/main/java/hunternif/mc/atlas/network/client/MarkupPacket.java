package hunternif.mc.atlas.network.client;

import hunternif.mc.atlas.AntiqueAtlasMod;
import hunternif.mc.atlas.markup.EnumMarkupColor;
import hunternif.mc.atlas.markup.Markup;
import hunternif.mc.atlas.markup.MarkupData;
import hunternif.mc.atlas.network.AbstractMessage.AbstractClientMessage;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Sends markers set via API from server to client.
 * Only one dimension per packet.
 * The markers in one packet are either all global or all local.
 * @author Hunternif
 */
public class MarkupPacket extends AbstractClientMessage<MarkupPacket> {
	/** Used in place of atlasID to signify that the marker is global. */
	private static final int GLOBAL = -1;
	private int atlasID;
	private int dimension;
	private final List<Markup> markups = new ArrayList<>();

	public MarkupPacket() {}

	/** Use this constructor when creating a <b>local</b> marker. */
	public MarkupPacket(int atlasID, int dimension, Markup... markups) {
		this.atlasID = atlasID;
		this.dimension = dimension;
		for (Markup markup : markups) {
			this.markups.add(markup);
		}
	}

	/** Use this constructor when creating a <b>global</b> marker. */
	public MarkupPacket(int dimension, Markup... markups) {
		this(GLOBAL, dimension, markups);
	}

	public MarkupPacket putMarkup(Markup markup) {
		markups.add(markup);
		return this;
	}

	private boolean isGlobal() {
		return atlasID == GLOBAL;
	}

	@Override
	public void read(PacketBuffer buffer) throws IOException {
		atlasID = buffer.readVarInt();
		dimension = buffer.readVarInt();
		int markersLength = buffer.readVarInt();
		for (int j = 0; j < markersLength; j++) {
			Markup markup = new Markup(dimension, buffer.readInt(), buffer.readInt());
			this.markups.add(markup);
			
			int pixelsLength = buffer.readVarInt();
			for (int k = 0; k < pixelsLength; k++) {
				byte pos = buffer.readByte();
				markup.setColor(pos >>> 4, pos & 0x0F, EnumMarkupColor.values()[buffer.readByte() % EnumMarkupColor.values().length]);
			}
		}
	}

	@Override
	public void write(PacketBuffer buffer) throws IOException {
		buffer.writeVarInt(atlasID);
		buffer.writeVarInt(dimension);
		buffer.writeVarInt(markups.size());
		for (Markup markup : markups) {
			buffer.writeInt(markup.getX());
			buffer.writeInt(markup.getZ());
			
			buffer.writeVarInt(markup.pixelCount());
			markup.forEach((k, v) -> {
				buffer.writeByte(k);
				buffer.writeByte(v.ordinal());
				return true;
			});
		}
	}

	@Override
	protected void process(EntityPlayer player, Side side) {
		MarkupData markupData = isGlobal() ?
				AntiqueAtlasMod.globalMarkupData.getData() :
					AntiqueAtlasMod.markupData.getMarkupData(atlasID, player.getEntityWorld());
		for (Markup markup : markups) {
			markupData.insertMarkup(markup);
		}
	}
}
