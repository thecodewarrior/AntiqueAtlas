package hunternif.mc.atlas.markup;

import hunternif.mc.atlas.util.ShortVec2;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DimensionMarkupData {
	private final MarkupData parent;
	private final int dimension;
	
	private final Map<ShortVec2 /*chunk coords*/, Markup> chunkMap =
			new ConcurrentHashMap<>(2, 0.75f, 2);
	
	/** Maps threads to the temporary key for thread-safe access to chunkMap. */
	private final Map<Thread, ShortVec2> thread2KeyMap = new ConcurrentHashMap<>(2, 0.75f, 2);
	
	/** Temporary key for thread-safe access to chunkMap. */
	private ShortVec2 getKey() {
		return thread2KeyMap.computeIfAbsent(Thread.currentThread(), k -> new ShortVec2(0, 0));
	}
	
	public DimensionMarkupData(MarkupData parent, int dimension) {
		this.parent = parent;
		this.dimension = dimension;
	}
	
	public int getDimension() {
		return dimension;
	}
	
	public Markup getMarkupAtChunk(int x, int z) {
		return chunkMap.get(getKey().set(x, z));
	}
	
	/** Insert markup into a list at chunk coordinates, maintaining the ordering
	 * of the list by Z coordinate. */
	public void setColor(int x, int z, EnumMarkupColor color) {
		ShortVec2 key = getKey().set(
				x >> 4,
				z >> 4);
		
		Markup markup = chunkMap.get(key);
		if (markup == null) {
			markup = new Markup(getDimension(), x >> 4, z >> 4);
			chunkMap.put(key.clone(), markup);
		}
		markup.setColor(x & 0x0F, z & 0x0F, color);
		if(markup.isEmpty()) {
			chunkMap.remove(key);
		}
		parent.markDirty();
	}
	
	/** Get markup into a list at chunk coordinates, maintaining the ordering
	 * of the list by Z coordinate. */
	public EnumMarkupColor getColor(int x, int z) {
		ShortVec2 key = getKey().set(
			x >> 4,
			z >> 4);
		
		Markup markup = chunkMap.get(key);
		if (markup == null) {
			return null;
		}
		return markup.getColor(x & 0x0F, z & 0x0F);
	}
	
	public Collection<Markup> getMarkupList() {
		return chunkMap.values();
	}
	
	public void insertMarkup(Markup markup) {
		ShortVec2 key = getKey().set(
			markup.getX(),
			markup.getZ());
		
		chunkMap.put(key, markup);
	}
}
