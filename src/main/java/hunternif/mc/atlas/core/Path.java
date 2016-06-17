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
	private int[] x, z;
	private int minX=Integer.MIN_VALUE, minZ=Integer.MIN_VALUE, maxX=Integer.MAX_VALUE, maxZ=Integer.MAX_VALUE;
	
	//TODO make an option for the marker to disappear at a certain scale.
	
	public Path(int id, PathType type, String label, int dimension, int[] x, int[] z) {
		this.id = id;
		this.type = type;
		this.label = label == null ? "" : label;
		this.dim = dimension;
		this.x = x;
		this.z = z;
		for (int i = 0; i < x.length; i++) {
			minX = Math.min(minX, x[i]);
			maxX = Math.max(maxX, x[i]);
		}
		for (int i = 0; i < z.length; i++) {
			minZ = Math.min(minZ, z[i]);
			maxZ = Math.max(maxZ, z[i]);
		}
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
	
	public int getLength() {
		return Math.min(x.length, z.length);
	}
	
	public int[] getXs() {
		return x;
	}
	
	public int[] getZs() {
		return z;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Path)) return false;
		Path marker = (Path) obj;
		return this.id == marker.id;
	}
	
	@Override
	public String toString() {
		return "#" + id + "\"" + label + "\"" + "@(" + x + ", " + z + ")";
	}
}
