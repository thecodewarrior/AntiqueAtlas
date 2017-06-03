package hunternif.mc.atlas.markup;

import hunternif.mc.atlas.network.client.MarkupPacket;
import net.minecraft.entity.player.EntityPlayer;

/** Holds global markers, i.e. ones that appear in all atlases. */
public class GlobalMarkupData extends MarkupData {

	public GlobalMarkupData(String key) {
		super(key);
	}
	
	@Override
	public Markup insertMarkup(Markup markup) {
		return super.insertMarkup(markup).setGlobal(true);
	}
	
	/** Send all data to the player in several packets. */
    void syncOnPlayer(EntityPlayer player) {
		syncOnPlayer(-1, player);
	}
	
	@Override
	MarkupPacket newMarkupPacket(int atlasID, int dimension) {
		return new MarkupPacket(dimension);
	}
	
}
