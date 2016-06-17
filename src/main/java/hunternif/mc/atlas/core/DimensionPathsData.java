package hunternif.mc.atlas.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DimensionPathsData {
	public final PathsData parent;
	public final int dimension;
	
	private final List<Path> pathList = new ArrayList<Path>();
	private final List<Path> tempPathList = new ArrayList<Path>();
	private final List<Path> tempUnmod = Collections.unmodifiableList(tempPathList);
	private final List<Path> unmod = Collections.unmodifiableList(pathList);
	
	private int tmpID = 0;
	
	public DimensionPathsData(PathsData parent, int dimension) {
		this.parent = parent;
		this.dimension = dimension;
	}
	
	public int getDimension() {
		return dimension;
	}
	
	public void insertPath(Path marker) {
		pathList.add(marker);
		parent.markDirty();
	}
	
	public int insertTempPath(Path marker) {
		tempPathList.add(marker);
		return tempPathList.size()-1;
	}
	
	public int nextTempID() {
		return tmpID++;
	}
	
	public boolean removePath(Path marker) {
		return pathList.remove(marker);
	}
	
	/** The returned view is immutable, i.e. remove() won't work. */
	public Collection<Path> getAllPaths() {
		return unmod;
	}
	
	/** The returned view is immutable, i.e. remove() won't work. */
	public Collection<Path> getAllTempPaths() {
		return tempUnmod;
	}
}
