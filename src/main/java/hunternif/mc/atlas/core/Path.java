package hunternif.mc.atlas.core;

import net.minecraft.client.resources.I18n;

import hunternif.mc.atlas.registry.PathType;
import hunternif.mc.atlas.util.ShortVec2;

/**
 * Marker on the map in an atlas. Has a type and a text label.
 * @author Hunternif
 */
public class Path {
	/** Id is unique only within a MarkersData instance, i.e. within one atlas
	 * or among global markers in a world. */
	private final int id, dim;
	
	private PathType type;
	private String label;
	private int x1, z1, x2, z2;
	private int minX=Integer.MIN_VALUE, minZ=Integer.MIN_VALUE, maxX=Integer.MAX_VALUE, maxZ=Integer.MAX_VALUE;
		
	public Path(int id, PathType type, String label, int dimension, int x1, int z1, int x2, int z2) {
		this.id = id;
		this.type = type;
		this.label = label == null ? "" : label;
		this.dim = dimension;
		this.x1 = x1;
		this.z1 = z1;
		this.x2 = x2;
		this.z2 = z2;
		minX = Math.min(x1, x2);
		maxX = Math.max(x1, x2);
		minZ = Math.min(z1, z2);
		maxZ = Math.max(z1, z2);
	}
	
	public int getId() {
		return id;
	}

	public PathType getType() {
		return type;
	}

	/** The label "as is", it might be a placeholder in the format
	 * "gui.antiqueatlas.marker.*" that has to be translated.
	 * @return
	 */
	public String getLabel() {
		return label;
	}
	public String getLocalizedLabel() {
		// Assuming the beginning of the label string until a whitespace (or end)
		// is a traslatable key. What comes after it is assumed to be a single
		// string parameter, i.e. player's name.
		int whitespaceIndex = label.indexOf(' ');
		if (whitespaceIndex == -1) {
			return I18n.format(label);
		} else {
			String key = label.substring(0, whitespaceIndex);
			String param = label.substring(whitespaceIndex + 1);
			String translated = I18n.format(key);
			if (translated != key) { // Make sure translation succeeded
				return String.format(I18n.format(key), param);
			} else {
				return label;
			}
		}
	}
	
	public int getDimension() {
		return dim;
	}
	
	public int getMinX() {
		return minX;
	}
	
	public int getMaxX() {
		return maxX;
	}
	
	public int getMinZ() {
		return minZ;
	}
	
	public int getMaxZ() {
		return maxZ;
	}
	
	public int getX1() {
		return x1;
	}
	
	public int getZ1() {
		return z1;
	}
	
	public int getX2() {
		return x2;
	}
	
	public int getZ2() {
		return z2;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Path)) return false;
		Path marker = (Path) obj;
		return this.id == marker.id;
	}
	
	@Override
	public String toString() {
		return "#" + id + "\"" + label + "\"" + "@(" + x1 + ", " + z1 + ")-(" + x2 + ", " + z2 + ")";
	}
}
