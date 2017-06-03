package hunternif.mc.atlas.markup;

/**
 * Created by TheCodeWarrior
 */
public enum EnumMarkupColor {
	RED(175, 0, 0, 134, 0, 0),
	BLUE(0, 243, 0, 0, 156, 0),
	GREEN(0, 0, 210, 0, 0, 156);
	
	public final int r, g, b, r2, g2, b2;
	
	EnumMarkupColor(int r, int g, int b, int r2, int g2, int b2) {
		this.r = r;
		this.g = g;
		this.b = b;
		
		this.r2 = r2;
		this.g2 = g2;
		this.b2 = b2;
	}
}
