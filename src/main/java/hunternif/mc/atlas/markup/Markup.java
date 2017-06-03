package hunternif.mc.atlas.markup;

import gnu.trove.map.TByteObjectMap;
import gnu.trove.map.hash.TByteObjectHashMap;
import gnu.trove.procedure.TByteObjectProcedure;
import hunternif.mc.atlas.util.ShortVec2;

/**
 * Markup on the map in an atlas.
 * @author Hunternif
 */
public class Markup {
	private final TByteObjectMap<EnumMarkupColor> pixels;
	private final int dim, x, z;
	private boolean isGlobal;
	
	public Markup(int dimension, int x, int z) {
		pixels = new TByteObjectHashMap<>();
		this.dim = dimension;
		this.x = x;
		this.z = z;
	}
	
	public int getDimension() {
		return dim;
	}
	
	public int getX() {
		return x;
	}
	
	public int getZ() {
		return z;
	}
	
	public int pixelCount() {
		return pixels.size();
	}
	
	public boolean isGlobal() {
		return isGlobal;
	}
	Markup setGlobal(boolean value) {
		this.isGlobal = value;
		return this;
	}
	
	public boolean isEmpty() {
		return pixels.isEmpty();
	}
	
	public EnumMarkupColor getColor(int x, int z) {
		return pixels.get(key(x, z));
	}
	
	public EnumMarkupColor setColor(int x, int z, EnumMarkupColor color) {
		return pixels.put(key(x, z), color);
	}
	
	public void forEach(TByteObjectProcedure<EnumMarkupColor> callback) {
		pixels.forEachEntry(callback);
	}
	
	public byte key(int x, int z) {
		x = x & 0x0F;
		z = z & 0x0F;
		byte result = (byte)( (x << 4) | z );
		return result;
	}
	
	public ShortVec2 pos(byte key) {
		return new ShortVec2(
			(key & 0xF0) >>> 4,
			key & 0x0F
		);
	}
	
	public int worldX(byte key) {
		return (this.x << 4) + (( key & 0xF0 ) >>> 4);
	}
	
	public int worldZ(byte key) {
		return (this.z << 4) + (key & 0x0F);
	}
	
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Markup )) return false;
		Markup markup = (Markup) obj;
		return this.pixels.equals(markup.pixels);
	}
	
	@Override
	public String toString() {
		return "#" + dim + "(" + x + "," + z + ")";
	}
}
