package hunternif.mc.atlas.client.gui;

import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.VertexBuffer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import hunternif.mc.atlas.AntiqueAtlasMod;
import hunternif.mc.atlas.api.AtlasAPI;
import hunternif.mc.atlas.client.BiomeTextureMap;
import hunternif.mc.atlas.client.SubTile;
import hunternif.mc.atlas.client.SubTileQuartet;
import hunternif.mc.atlas.client.Textures;
import hunternif.mc.atlas.client.TileRenderIterator;
import hunternif.mc.atlas.client.gui.core.GuiComponent;
import hunternif.mc.atlas.client.gui.core.GuiComponentButton;
import hunternif.mc.atlas.client.gui.core.GuiCursor;
import hunternif.mc.atlas.client.gui.core.GuiStates;
import hunternif.mc.atlas.client.gui.core.GuiStates.IState;
import hunternif.mc.atlas.client.gui.core.GuiStates.SimpleState;
import hunternif.mc.atlas.client.gui.core.IButtonListener;
import hunternif.mc.atlas.core.DimensionData;
import hunternif.mc.atlas.core.DimensionPathsData;
import hunternif.mc.atlas.core.Path;
import hunternif.mc.atlas.core.PathsData;
import hunternif.mc.atlas.marker.DimensionMarkersData;
import hunternif.mc.atlas.marker.Marker;
import hunternif.mc.atlas.marker.MarkersData;
import hunternif.mc.atlas.network.PacketDispatcher;
import hunternif.mc.atlas.network.server.BrowsingPositionPacket;
import hunternif.mc.atlas.registry.MarkerRenderInfo;
import hunternif.mc.atlas.registry.MarkerType;
import hunternif.mc.atlas.registry.PathTypes;
import hunternif.mc.atlas.util.AtlasRenderHelper;
import hunternif.mc.atlas.util.ExportImageUtil;
import hunternif.mc.atlas.util.Log;
import hunternif.mc.atlas.util.MathUtil;
import hunternif.mc.atlas.util.Rect;

public class GuiAtlas extends GuiComponent {
	public static final int WIDTH = 310;
	public static final int HEIGHT = 218;
	private static final int CONTENT_X = 17;
	private static final int CONTENT_Y = 11;
	
	private static final int MAP_WIDTH = WIDTH - 17*2;
	private static final int MAP_HEIGHT = 194;
	private static final float PLAYER_ROTATION_STEPS = 16;
	private static final int PLAYER_ICON_WIDTH = 7;
	private static final int PLAYER_ICON_HEIGHT = 8;
	
	public static final int MARKER_SIZE = 32;
	
	/** If the map scale goes below this value, the tiles will not scale down
	 * visually, but will instead span greater area. */
	public static final double MIN_SCALE_THRESHOLD = 0.5;
	
	private long[] renderTimes = new long[30];
	private int renderTimesIndex = 0;
	
	// States ==================================================================
	
	private final GuiStates state = new GuiStates();
	
	/** If on, navigate the map normally. */
	private final IState NORMAL = new SimpleState();
	
	/** If on, all markers as well as the player icon are hidden. */
	private final IState HIDING_MARKERS = new IState() {
		@Override
		public void onEnterState() {
			// Set the button as not selected so that it can be clicked again:
			btnShowMarkers.setSelected(false);
			btnShowMarkers.setTitle(I18n.format("gui.antiqueatlas.showMarkers"));
			btnShowMarkers.setIconTexture(Textures.ICON_SHOW_MARKERS);
		}
		@Override
		public void onExitState() {
			btnShowMarkers.setSelected(false);
			btnShowMarkers.setTitle(I18n.format("gui.antiqueatlas.hideMarkers"));
			btnShowMarkers.setIconTexture(Textures.ICON_HIDE_MARKERS);
		}
	};
	
	/** If on, a semi-transparent marker is attached to the cursor, and the
	 * player's icon becomes semi-transparent as well. */
	private final IState PLACING_MARKER = new IState() {
		@Override
		public void onEnterState() {
			btnMarker.setSelected(true);
		};
		@Override
		public void onExitState() {
			btnMarker.setSelected(false);
		};
	};
	
	/** If on, the closest marker will be deleted upon mouseclick. */
	private final IState DELETING_MARKER = new IState() {
		@Override
		public void onEnterState() {
			mc.mouseHelper.grabMouseCursor();
			addChild(eraser);
			btnDelMarker.setSelected(true);
		};
		@Override
		public void onExitState() {
			mc.mouseHelper.ungrabMouseCursor();
			removeChild(eraser);
			btnDelMarker.setSelected(false);
		};
	};
	private final GuiCursor eraser = new GuiCursor();
	
	private final IState EXPORTING_IMAGE = new IState() {
		@Override
		public void onEnterState() {
			btnExportPng.setSelected(true);
		}
		@Override
		public void onExitState() {
			btnExportPng.setSelected(false);
		}
	};
	
	// Buttons =================================================================
	
	/** Arrow buttons for navigating the map view via mouse clicks. */
	private final GuiArrowButton btnUp, btnDown, btnLeft, btnRight;
	
	/** Button for exporting PNG image of the Atlas's contents. */
	private final GuiBookmarkButton btnExportPng;
	
	/** Button for placing a marker at current position, local to this Atlas instance. */
	private final GuiBookmarkButton btnMarker;
	
	/** Button for deleting local markers. */
	private final GuiBookmarkButton btnDelMarker;
	
	/** Button for showing/hiding all markers. */
	private final GuiBookmarkButton btnShowMarkers;
	
	/** Button for restoring player's position at the center of the Atlas. */
	private final GuiPositionButton btnPosition;
	
	
	// Navigation ==============================================================
	
	/** Pause between after the arrow button is pressed and continuous
	 * navigation starts, in ticks. */
	private static final int BUTTON_PAUSE = 8;
	
	/** How much the map view is offset, in blocks, per click (or per tick). */
	public static int navigateStep = 24;
	
	/** The button which is currently being pressed. Used for continuous
	 * navigation using the arrow buttons. Also used to prevent immediate
	 * canceling of placing marker. */
	private GuiComponentButton selectedButton = null;
	
	/** Time in world ticks when the button was pressed. Used to create a pause
	 * before continuous navigation using the arrow buttons. */
	private long timeButtonPressed = 0;
	
	/** Set to true when dragging the map view. */
	private boolean isDragging = false;
	/** The starting cursor position when dragging. */
	private int dragMouseX, dragMouseY;
	/** Map offset at the beginning of drag. */
	private int dragMapOffsetX, dragMapOffsetY;
	
	/** Offset to the top left corner of the tile at (0, 0) from the center of
	 * the map drawing area, in pixels. */
	private int mapOffsetX, mapOffsetY;
	/** If true, the player's icon will be in the center of the GUI, and the
	 * offset of the tiles will be calculated accordingly. Otherwise it's the
	 * position of the player that will be calculated with respect to the
	 * offset. */
	private boolean followPlayer = true;
	
	private GuiScaleBar scaleBar = new GuiScaleBar();
	/** Pixel-to-block ratio. */
	private double mapScale;
	/** The visual size of a tile in pixels. */
	private int tileHalfSize;
	/** The number of chunks a tile spans. */
	private int tile2ChunkScale;
	
	
	// Markers =================================================================
	
	/** Local markers in the current dimension */
	private DimensionMarkersData localMarkersData;
	/** Global markers in the current dimension */
	private DimensionMarkersData globalMarkersData;
	/** The marker highlighted by the eraser. Even though multiple markers may
	 * be highlighted at the same time, only one of them will be deleted. */
	private Marker toDelete;
	
	private GuiMarkerFinalizer markerFinalizer = new GuiMarkerFinalizer();
	/** Displayed where the marker is about to be placed when the Finalizer GUI is on. */
	private GuiBlinkingMarker blinkingIcon = new GuiBlinkingMarker();
	
	
	// Misc stuff ==============================================================
	
	private EntityPlayer player;
	private ItemStack stack;
	private DimensionData biomeData;
	
	/** Coordinate scale factor relative to the actual screen size. */
	private int screenScale;
	
	/** Progress bar for exporting images. */
	private ProgressBarOverlay progressBar = new ProgressBarOverlay(100, 2);
	
	/** Local paths in the current dimension */
	private DimensionPathsData localPathsData;
	
	private int scaleAlpha = 255;
	private long lastUpdateMillis = System.currentTimeMillis();
	private int scaleClipIndex = 0;
	private int zoomLevelOne = 8;
	private int zoomLevel = zoomLevelOne;
	private String[] zoomNames = new String[] { "256", "128", "64", "32", "16", "8", "4", "2", "1", "1/2", "1/4", "1/8", "1/16", "1/32", "1/64", "1/128", "1/256" };
	
	private Thread exportThread;
	
	@SuppressWarnings("rawtypes")
	public GuiAtlas() {
		setSize(WIDTH, HEIGHT);
		setMapScale(0.5);
		followPlayer = true;
		setInterceptKeyboard(false);
		
		btnUp = GuiArrowButton.up();
		addChild(btnUp).offsetGuiCoords(148, 10);
		btnDown = GuiArrowButton.down();
		addChild(btnDown).offsetGuiCoords(148, 194);
		btnLeft = GuiArrowButton.left();
		addChild(btnLeft).offsetGuiCoords(15, 100);
		btnRight = GuiArrowButton.right();
		addChild(btnRight).offsetGuiCoords(283, 100);
		btnPosition = new GuiPositionButton();
		btnPosition.setEnabled(!followPlayer);
		addChild(btnPosition).offsetGuiCoords(283, 194);
		IButtonListener positionListener = new IButtonListener() {
			@Override
			public void onClick(GuiComponentButton button) {
				selectedButton = button;
				if (button.equals(btnPosition)) {
					followPlayer = true;
					btnPosition.setEnabled(false);
				} else {
					// Navigate once, before enabling pause:
					navigateByButton(selectedButton);
					timeButtonPressed = player.worldObj.getTotalWorldTime();
				}
			}
		};
		btnUp.addListener(positionListener);
		btnDown.addListener(positionListener);
		btnLeft.addListener(positionListener);
		btnRight.addListener(positionListener);
		btnPosition.addListener(positionListener);
		
		btnExportPng = new GuiBookmarkButton(1, Textures.ICON_EXPORT, I18n.format("gui.antiqueatlas.exportImage")) {
			@Override
			public boolean isEnabled() {
				return !ExportImageUtil.isExporting;
			}
		};
		addChild(btnExportPng).offsetGuiCoords(300, 75);
		btnExportPng.addListener(new IButtonListener<GuiBookmarkButton>() {
			@Override
			public void onClick(GuiBookmarkButton button) {
				if (stack != null) {
					exportThread = new Thread(new Runnable() {
						@Override
						public void run() {
							exportImage(stack.copy());
						}
					}, "Atlas file export thread");
					exportThread.start();
				}
			}
		});
		
		btnMarker = new GuiBookmarkButton(0, Textures.ICON_ADD_MARKER, I18n.format("gui.antiqueatlas.addMarker"));
		addChild(btnMarker).offsetGuiCoords(300, 14);
		btnMarker.addListener(new IButtonListener() {
			@Override
			public void onClick(GuiComponentButton button) {
				if (stack != null) {
					if (state.is(PLACING_MARKER)) {
						selectedButton = null;
						state.switchTo(NORMAL);
					} else {
						selectedButton = button;
						state.switchTo(PLACING_MARKER);
					}
				}
			}
		});
		btnDelMarker = new GuiBookmarkButton(2, Textures.ICON_DELETE_MARKER, I18n.format("gui.antiqueatlas.delMarker"));
		addChild(btnDelMarker).offsetGuiCoords(300, 33);
		btnDelMarker.addListener(new IButtonListener() {
			@Override
			public void onClick(GuiComponentButton button) {
				if (stack != null) {
					if (state.is(DELETING_MARKER)) {
						selectedButton = null;
						state.switchTo(NORMAL);
					} else {
						selectedButton = button;
						state.switchTo(DELETING_MARKER);
					}
				}
			}
		});
		btnShowMarkers = new GuiBookmarkButton(3, Textures.ICON_HIDE_MARKERS, I18n.format("gui.antiqueatlas.hideMarkers"));
		addChild(btnShowMarkers).offsetGuiCoords(300, 52);
		btnShowMarkers.addListener(new IButtonListener() {
			@Override
			public void onClick(GuiComponentButton button) {
				if (stack != null) {
					selectedButton = null;
					state.switchTo(state.is(HIDING_MARKERS) ? NORMAL : HIDING_MARKERS);
				}
			}
		});
		
		addChild(scaleBar).offsetGuiCoords(20, 198);
		scaleBar.setMapScale(1);
		
		markerFinalizer.addListener(blinkingIcon);
		
		eraser.setTexture(Textures.ERASER, 12, 14, 2, 11);
	}
	
	public GuiAtlas setAtlasItemStack(ItemStack stack) {
		this.player = Minecraft.getMinecraft().thePlayer;
		this.stack = stack;
		updateAtlasData();
		if (!followPlayer && AntiqueAtlasMod.settings.doSaveBrowsingPos) {
			loadSavedBrowsingPosition();
		}
		return this;
	}
	public void loadSavedBrowsingPosition() {
		// Apply zoom first, because browsing position depends on it:
		setMapScale(biomeData.getBrowsingZoom());
		mapOffsetX = biomeData.getBrowsingX();
		mapOffsetY = biomeData.getBrowsingY();
		isDragging = false;
	}
	
	@Override
	public void initGui() {
		super.initGui();
		if(!state.equals(EXPORTING_IMAGE))
			state.switchTo(NORMAL); //TODO: his causes the Export PNG progress bar to disappear when resizing game window
		Keyboard.enableRepeatEvents(true);
		screenScale = new ScaledResolution(mc).getScaleFactor();
		setCentered();
	}
	
	@Override
	protected void mouseClicked(int mouseX, int mouseY, int mouseState) throws IOException {
		super.mouseClicked(mouseX, mouseY, mouseState);
		if (state.is(EXPORTING_IMAGE)) {
			return; // Don't remove the progress bar.
		}
		
		// If clicked on the map, start dragging
		int mapX = (width - MAP_WIDTH)/2;
		int mapY = (height - MAP_HEIGHT)/2;
		boolean isMouseOverMap = mouseX >= mapX && mouseX <= mapX + MAP_WIDTH &&
				mouseY >= mapY && mouseY <= mapY + MAP_HEIGHT;
		if (!state.is(NORMAL) && !state.is(HIDING_MARKERS)) {
			if (state.is(PLACING_MARKER) // If clicked on the map, place marker:
					&& isMouseOverMap && mouseState == 0 /* left click */) {
				markerFinalizer.setMarkerData(player.worldObj,
						stack.getItemDamage(), player.dimension,
						screenXToWorldX(mouseX), screenYToWorldZ(mouseY));
				addChild(markerFinalizer);
				
				blinkingIcon.setTexture(markerFinalizer.selectedType.getIcon(),
						MARKER_SIZE, MARKER_SIZE);
				addChildBehind(markerFinalizer, blinkingIcon)
					.setRelativeCoords(mouseX - getGuiX() - MARKER_SIZE/2,
									   mouseY - getGuiY() - MARKER_SIZE/2);
				
				// Need to intercept keyboard events to type in the label:
				setInterceptKeyboard(true);
				
				// Un-press all keys to prevent player from walking infinitely: 
				KeyBinding.unPressAllKeys();
				
			} else if (state.is(DELETING_MARKER) // If clicked on a marker, delete it:
					&& toDelete != null && isMouseOverMap && mouseState == 0) {
				AtlasAPI.markers.deleteMarker(player.worldObj,
						stack.getItemDamage(), toDelete.getId());
			}
			state.switchTo(NORMAL);
		} else if (isMouseOverMap && selectedButton == null) {
			isDragging = true;
			dragMouseX = mouseX;
			dragMouseY = mouseY;
			dragMapOffsetX = mapOffsetX;
			dragMapOffsetY = mapOffsetY;
		}
	}
	
	/** Opens a dialog window to select which file to save to, then performs
	 * rendering of the map of current dimension into a PNG image. */
	private void exportImage(ItemStack stack) {
		boolean showMarkers = !state.is(HIDING_MARKERS);
		state.switchTo(EXPORTING_IMAGE);
		// Default file name is "Atlas <N>.png"
		ExportImageUtil.isExporting = true;
		File file = ExportImageUtil.selectPngFileToSave("Atlas " + stack.getItemDamage());
		if (file != null) {
			try {
				Log.info("Exporting image from Atlas #%d to file %s", stack.getItemDamage(), file.getAbsolutePath());
				ExportImageUtil.exportPngImage(biomeData, globalMarkersData, localMarkersData, file, showMarkers);
				Log.info("Finished exporting image");
			} catch (OutOfMemoryError e) {
				Log.warn(e, "Image is too large, trying to export in strips");
				try {
					ExportImageUtil.exportPngImageTooLarge(biomeData, globalMarkersData, localMarkersData, file, showMarkers);
				} catch (OutOfMemoryError e2) {
					int minX = (biomeData.getScope().minX - 1) * ExportImageUtil.TILE_SIZE;
					int minY = (biomeData.getScope().minY - 1) * ExportImageUtil.TILE_SIZE;
					int outWidth = (biomeData.getScope().maxX + 2) * ExportImageUtil.TILE_SIZE - minX;
					int outHeight = (biomeData.getScope().maxY + 2) * ExportImageUtil.TILE_SIZE - minY;
					
					Log.error(e2, "Image is STILL too large, how massive is this map?! Answer: (%dx%d)", outWidth, outHeight);
					
					ExportUpdateListener.INSTANCE.setStatusString(I18n.format("gui.antiqueatlas.export.tooLarge"));
					ExportImageUtil.isExporting = false;
					return; //Don't switch to normal state yet so that the error message can be read.
				}
			}
		}
		ExportImageUtil.isExporting = false;
		state.switchTo(showMarkers ? NORMAL : HIDING_MARKERS);
	}
	
	@Override
	public void handleKeyboardInput() throws IOException {
		super.handleKeyboardInput();
		if (Keyboard.getEventKeyState()) {
			int key = Keyboard.getEventKey();
			if (key == Keyboard.KEY_UP) {
				navigateMap(0, navigateStep);
			} else if (key == Keyboard.KEY_DOWN) {
				navigateMap(0, -navigateStep);
			} else if (key == Keyboard.KEY_LEFT) {
				navigateMap(navigateStep, 0);
			} else if (key == Keyboard.KEY_RIGHT) {
				navigateMap(-navigateStep, 0);
			} else if (key == Keyboard.KEY_ADD || key == Keyboard.KEY_EQUALS) {
				setMapScale(mapScale * 2);
			} else if (key == Keyboard.KEY_SUBTRACT || key == Keyboard.KEY_MINUS) {
				setMapScale(mapScale / 2);
			}
			// Close the GUI if a hotbar key is pressed
			else {
				KeyBinding[] hotbarKeys = mc.gameSettings.keyBindsHotbar;
				for (KeyBinding bind : hotbarKeys) {
					if (key == bind.getKeyCode()) {
						close();
						break;
					}
				}
			}
		}
	}
	
	@Override
	public void handleMouseInput() throws IOException {
		super.handleMouseInput();
		int wheelMove = Mouse.getEventDWheel();
		if (wheelMove != 0) {
			wheelMove = wheelMove > 0 ? 1 : -1;
			if (AntiqueAtlasMod.settings.doReverseWheelZoom) wheelMove *= -1;
			setMapScale(mapScale * Math.pow(2, wheelMove));
		}
	}
	
	@Override
	protected void mouseReleased(int mouseX, int mouseY, int mouseState) {
		super.mouseReleased(mouseX, mouseY, mouseState);
		if (mouseState != -1) {
			selectedButton = null;
			isDragging = false;
		}
	}
	
	@Override
	protected void mouseClickMove(int mouseX, int mouseY, int lastMouseButton, long timeSinceMouseClick) {
		super.mouseClickMove(mouseX, mouseY, lastMouseButton, timeSinceMouseClick);
		if (isDragging) {
			followPlayer = false;
			btnPosition.setEnabled(true);
			mapOffsetX = dragMapOffsetX + mouseX - dragMouseX;
			mapOffsetY = dragMapOffsetY + mouseY - dragMouseY;
		}
	}
	
	@Override
	public void updateScreen() {
		super.updateScreen();
		if (player == null) return;
		if (followPlayer) {
			mapOffsetX = (int)(- player.posX * mapScale);
			mapOffsetY = (int)(- player.posZ * mapScale);
		}
		if (player.worldObj.getTotalWorldTime() > timeButtonPressed + BUTTON_PAUSE) {
			navigateByButton(selectedButton);
		}
		
		updateAtlasData();
	}
	
	/** Update {@link #biomeData}, {@link #localMarkersData},
	 * {@link #globalMarkersData} */
	private void updateAtlasData() {
		biomeData = AntiqueAtlasMod.atlasData
				.getAtlasData(stack, player.worldObj)
				.getDimensionData(player.dimension);
		globalMarkersData = AntiqueAtlasMod.globalMarkersData.getData()
				.getMarkersDataInDimension(player.dimension);
		MarkersData markersData = AntiqueAtlasMod.markersData
				.getMarkersData(stack, player.worldObj);
		if (markersData != null) {
			localMarkersData = markersData
					.getMarkersDataInDimension(player.dimension);
		} else {
			localMarkersData = null;
		}
		PathsData pathsData = AntiqueAtlasMod.pathsData
				.getPathsData(stack, player.worldObj);
		if (pathsData != null) {
			localPathsData = pathsData
					.getPathsDataInDimension(player.dimension);
		} else {
			localPathsData = null;
		}
	}
	
	/** Offset the map view depending on which button was pressed. */
	private void navigateByButton(GuiComponentButton btn) {
		if (btn == null) return;
		if (btn.equals(btnUp)) {
			navigateMap(0, navigateStep);
		} else if (btn.equals(btnDown)) {
			navigateMap(0, -navigateStep);
		} else if (btn.equals(btnLeft)) {
			navigateMap(navigateStep, 0);
		} else if (btn.equals(btnRight)) {
			navigateMap(-navigateStep, 0);
		}
	}
	
	/** Offset the map view by given values, in blocks. */
	public void navigateMap(int dx, int dy) {
		mapOffsetX += dx;
		mapOffsetY += dy;
		followPlayer = false;
		btnPosition.setEnabled(true);
	}
	
	/** Set the pixel-to-block ratio, maintaining the current center of the screen. */
	public void setMapScale(double scale) {
		double oldScale = mapScale;
		mapScale = scale;
		if (mapScale < AntiqueAtlasMod.settings.minScale) mapScale = AntiqueAtlasMod.settings.minScale;
		if (mapScale > AntiqueAtlasMod.settings.maxScale) mapScale = AntiqueAtlasMod.settings.maxScale;
		if (mapScale >= MIN_SCALE_THRESHOLD) {
			tileHalfSize = (int)Math.round(8 * mapScale);
			tile2ChunkScale = 1;
		} else {
			tileHalfSize = (int)Math.round(8 * MIN_SCALE_THRESHOLD);
			tile2ChunkScale = (int)Math.round(MIN_SCALE_THRESHOLD / mapScale);
		}
		// Times 2 because the contents of the Atlas are rendered at resolution 2 times smaller:
		scaleBar.setMapScale(mapScale*2);
		mapOffsetX *= mapScale / oldScale;
		mapOffsetY *= mapScale / oldScale;
		dragMapOffsetX *= mapScale / oldScale;
		dragMapOffsetY *= mapScale / oldScale;
		// 2^13 = 8192
		scaleClipIndex = MathHelper.calculateLogBaseTwo( (int)( mapScale * 8192 ) ) + 1 - 13;
		zoomLevel = -scaleClipIndex + zoomLevelOne;
		scaleAlpha = 255;
	}
	
	@Override
	public void drawScreen(int mouseX, int mouseY, float par3) {
		
		long currentMillis = System.currentTimeMillis();
		long deltaMillis = currentMillis - lastUpdateMillis;
		
		if (AntiqueAtlasMod.settings.debugRender) {
			renderTimes[renderTimesIndex++] = System.currentTimeMillis();
			if (renderTimesIndex == renderTimes.length) {
				renderTimesIndex = 0;
				double elapsed = 0;
				for (int i = 0; i < renderTimes.length - 1; i++) {
					elapsed += renderTimes[i + 1] - renderTimes[i];
				}
				System.out.printf("GuiAtlas avg. render time: %.3f\n", elapsed / renderTimes.length);
			}
		}
		
		GlStateManager.color(1, 1, 1, 1);
		GlStateManager.alphaFunc(GL11.GL_GREATER, 0); // So light detail on tiles is visible
		AtlasRenderHelper.drawFullTexture(Textures.BOOK, getGuiX(), getGuiY(), WIDTH, HEIGHT);
		
		if (stack == null || biomeData == null) return;
		
		
		if (state.is(DELETING_MARKER)) {
			GlStateManager.color(1, 1, 1, 0.5f);
		}
		GL11.glEnable(GL11.GL_SCISSOR_TEST);
		GL11.glScissor((getGuiX() + CONTENT_X)*screenScale,
				mc.displayHeight - (getGuiY() + CONTENT_Y + MAP_HEIGHT)*screenScale,
				MAP_WIDTH*screenScale, MAP_HEIGHT*screenScale);
		GlStateManager.enableBlend();
		GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		// Find chunk coordinates of the top left corner of the map.
		// The 'roundToBase' is required so that when the map scales below the
		// threshold the tiles don't change when map position changes slightly.
		// The +-2 at the end provide margin so that tiles at the edges of
		// the page have their stitched texture correct.
		int mapStartX = MathUtil.roundToBase((int)Math.floor(-((double)MAP_WIDTH/2d + mapOffsetX + 2*tileHalfSize) / mapScale / 16d), tile2ChunkScale);
		int mapStartZ = MathUtil.roundToBase((int)Math.floor(-((double)MAP_HEIGHT/2d + mapOffsetY + 2*tileHalfSize) / mapScale / 16d), tile2ChunkScale);
		int mapEndX = MathUtil.roundToBase((int)Math.ceil(((double)MAP_WIDTH/2d - mapOffsetX + 2*tileHalfSize) / mapScale / 16d), tile2ChunkScale);
		int mapEndZ = MathUtil.roundToBase((int)Math.ceil(((double)MAP_HEIGHT/2d - mapOffsetY + 2*tileHalfSize) / mapScale / 16d), tile2ChunkScale);
		int mapStartScreenX = getGuiX() + WIDTH/2 + (int)((mapStartX << 4) * mapScale) + mapOffsetX;
		int mapStartScreenY = getGuiY() + HEIGHT/2 + (int)((mapStartZ << 4) * mapScale) + mapOffsetY;
		
		TileRenderIterator iter = new TileRenderIterator(biomeData);
		iter.setScope(new Rect().setOrigin(mapStartX, mapStartZ).
				set(mapStartX, mapStartZ, mapEndX, mapEndZ));
		iter.setStep(tile2ChunkScale);
		while (iter.hasNext()) {
			SubTileQuartet subtiles = iter.next();
			for (SubTile subtile : subtiles) {
				if (subtile == null || subtile.tile == null) continue;
				AtlasRenderHelper.drawAutotileCorner(
						BiomeTextureMap.instance().getTexture(subtile.tile),
						mapStartScreenX + subtile.x * tileHalfSize,
						mapStartScreenY + subtile.y * tileHalfSize,
						subtile.getTextureU(), subtile.getTextureV(), tileHalfSize);
			}
		}
		
		int markersStartX = MathUtil.roundToBase(mapStartX, MarkersData.CHUNK_STEP) / MarkersData.CHUNK_STEP - 1;
		int markersStartZ = MathUtil.roundToBase(mapStartZ, MarkersData.CHUNK_STEP) / MarkersData.CHUNK_STEP - 1;
		int markersEndX = MathUtil.roundToBase(mapEndX, MarkersData.CHUNK_STEP) / MarkersData.CHUNK_STEP + 1;
		int markersEndZ = MathUtil.roundToBase(mapEndZ, MarkersData.CHUNK_STEP) / MarkersData.CHUNK_STEP + 1;
		double iconScale = getIconScale();
		
		Rectangle mapRect = new Rectangle(mapStartX, mapStartZ, mapEndX, mapEndZ);
		
		for (Path path : localPathsData.getAllPaths()) {			
			if(mapRect.intersects(new Rectangle(path.getMinX(), path.getMinZ(), path.getMaxX(), path.getMaxZ()))) {
				renderPath(path);
			}
		}
		
		renderPath(new Path(1000, PathTypes.DOTS, "ASDF", 0, new int[] { 0, 10, 12, -30 }, new int[] { 0, 10, 23, 32 }));
		
		// Draw global markers:
		for (int x = markersStartX; x <= markersEndX; x++) {
			for (int z = markersStartZ; z <= markersEndZ; z++) {
				List<Marker> markers = globalMarkersData.getMarkersAtChunk(x, z);
				if (markers == null) continue;
				for (Marker marker : markers) {
					renderMarker(marker, iconScale);
				}
			}
		}
		
		// Draw local markers:
		if (localMarkersData != null) {
			for (int x = markersStartX; x <= markersEndX; x++) {
				for (int z = markersStartZ; z <= markersEndZ; z++) {
					List<Marker> markers = localMarkersData.getMarkersAtChunk(x, z);
					if (markers == null) continue;
					for (Marker marker : markers) {
						renderMarker(marker, iconScale);
					}
				}
			}
		}
		
		GL11.glDisable(GL11.GL_SCISSOR_TEST);
		
		// Overlay the frame so that edges of the map are smooth:
		GlStateManager.color(1, 1, 1, 1);
		AtlasRenderHelper.drawFullTexture(Textures.BOOK_FRAME, getGuiX(), getGuiY(), WIDTH, HEIGHT);
		renderScaleOverlay(deltaMillis);
		iconScale = getIconScale();
		
		// Draw player icon:
		if (!state.is(HIDING_MARKERS)) {
			// How much the player has moved from the top left corner of the map, in pixels:
			int playerOffsetX = (int)(player.posX * mapScale) + mapOffsetX;
			int playerOffsetZ = (int)(player.posZ * mapScale) + mapOffsetY;
			if (playerOffsetX < -MAP_WIDTH/2) playerOffsetX = -MAP_WIDTH/2;
			if (playerOffsetX > MAP_WIDTH/2) playerOffsetX = MAP_WIDTH/2;
			if (playerOffsetZ < -MAP_HEIGHT/2) playerOffsetZ = -MAP_HEIGHT/2;
			if (playerOffsetZ > MAP_HEIGHT/2 - 2) playerOffsetZ = MAP_HEIGHT/2 - 2;
			// Draw the icon:
			GlStateManager.color(1, 1, 1, state.is(PLACING_MARKER) ? 0.5f : 1);
			GlStateManager.pushMatrix();
			GlStateManager.translate(getGuiX() + WIDTH/2 + playerOffsetX, getGuiY() + HEIGHT/2 + playerOffsetZ, 0);
			float playerRotation = (float) Math.round(player.rotationYaw / 360f * PLAYER_ROTATION_STEPS) / PLAYER_ROTATION_STEPS * 360f;
			GlStateManager.rotate(180 + playerRotation, 0, 0, 1);
			GlStateManager.translate(-PLAYER_ICON_WIDTH/2*iconScale, -PLAYER_ICON_HEIGHT/2*iconScale, 0);
			AtlasRenderHelper.drawFullTexture(Textures.PLAYER, 0, 0,
					(int)Math.round(PLAYER_ICON_WIDTH*iconScale), (int)Math.round(PLAYER_ICON_HEIGHT*iconScale));
			GlStateManager.popMatrix();
			GlStateManager.color(1, 1, 1, 1);
		}
		
		// Draw buttons:
		super.drawScreen(mouseX, mouseY, par3);
		
		// Draw the semi-transparent marker attached to the cursor when placing a new marker:
		GlStateManager.enableBlend();
		GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		if (state.is(PLACING_MARKER)) {
			GlStateManager.color(1, 1, 1, 0.5f);
			markerFinalizer.selectedType.calculateMip(iconScale, mapScale, screenScale);
			MarkerRenderInfo renderInfo = markerFinalizer.selectedType.getRenderInfo(iconScale, mapScale, screenScale);
			markerFinalizer.selectedType.resetMip();
			AtlasRenderHelper.drawFullTexture(
					renderInfo.tex,
					mouseX + renderInfo.x, mouseY + renderInfo.y,
					renderInfo.width, renderInfo.height);
			GlStateManager.color(1, 1, 1, 1);
		}
		
		// Draw progress overlay:
		if (state.is(EXPORTING_IMAGE)) {
			drawDefaultBackground();
			progressBar.draw((width - 100)/2, height/2 - 34);
		}
	}
	
	private void renderPath(Path path) {
		Tessellator tessellator = Tessellator.getInstance();
		VertexBuffer vb = tessellator.getBuffer();
		GlStateManager.glLineWidth((int)( 4*mapScale ));
		Minecraft.getMinecraft().renderEngine.bindTexture(path.getType().getTexture());
		vb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
		double u = 0;
		for (int i = 0; i+1 < path.getLength(); i ++) {
			int wX = path.getXs()[i], wZ = path.getZs()[i];
			int wX2 = path.getXs()[i+1], wZ2 = path.getZs()[i+1];
			
			Vec3d p1 = new Vec3d(worldXToScreenX(wX), worldZToScreenY(wZ), 0);
			Vec3d p2 = new Vec3d(worldXToScreenX(wX2), worldZToScreenY(wZ2), 0);
			
			Vec3d dir = p2.subtract(p1);
			Vec3d perp = new Vec3d(dir.yCoord, -dir.xCoord, dir.zCoord).normalize().scale(1);
			
			
			
			vb.pos(p1.xCoord+perp.xCoord, p1.yCoord+perp.yCoord, 0).tex(u, 0).endVertex();
			vb.pos(p1.xCoord-perp.xCoord, p1.yCoord-perp.yCoord, 0).tex(u, 1).endVertex();
			
			double distance = dir.lengthVector();
			u += distance * 0.1;
			
			vb.pos(p2.xCoord-perp.xCoord, p2.yCoord-perp.yCoord, 0).tex(u, 1).endVertex();
			vb.pos(p2.xCoord+perp.xCoord, p2.yCoord+perp.yCoord, 0).tex(u, 0).endVertex();
		}
		
		tessellator.draw();
	}
	
	private void renderScaleOverlay(long deltaMillis) {
		if(scaleAlpha > 0) {			
			GlStateManager.translate(getGuiX()+WIDTH-13, getGuiY()+12, 0);
			
			String text;
			int textWidth, xWidth;
			
			text = "x";
			xWidth = textWidth = fontRendererObj.getStringWidth(text); xWidth++;
			fontRendererObj.drawString(text, -textWidth, 0, scaleAlpha << 24 | 0x000000);
			
			text = zoomNames[zoomLevel];
			if(text.contains("/")) {
				
				String[] parts = text.split("/");
				
				text = parts[0];
				int centerXtranslate = Math.max(fontRendererObj.getStringWidth(parts[0]), fontRendererObj.getStringWidth(parts[1]) )/2;
				GlStateManager.translate(-xWidth-centerXtranslate, -fontRendererObj.FONT_HEIGHT/2, 0);
				
				GlStateManager.disableTexture2D();
				Tessellator t = Tessellator.getInstance();
				VertexBuffer vb = t.getBuffer();
                vb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
                vb.pos( centerXtranslate,   fontRendererObj.FONT_HEIGHT - 1, 0.0D).endVertex();
                vb.pos(-centerXtranslate-1, fontRendererObj.FONT_HEIGHT - 1, 0.0D).endVertex();
                vb.pos(-centerXtranslate-1, fontRendererObj.FONT_HEIGHT    , 0.0D).endVertex();
                vb.pos( centerXtranslate,   fontRendererObj.FONT_HEIGHT    , 0.0D).endVertex();
                t.draw();
                GlStateManager.enableTexture2D();
				
				textWidth = fontRendererObj.getStringWidth(text);
				fontRendererObj.drawString(text, -textWidth/2, 0, scaleAlpha << 24 | 0x000000);
				
				text = parts[1];
				GlStateManager.translate(0, fontRendererObj.FONT_HEIGHT + 1, 0);
				
				textWidth = fontRendererObj.getStringWidth(text);
				fontRendererObj.drawString(text, -textWidth/2, 0, scaleAlpha << 24 | 0x000000);
				
				GlStateManager.translate(xWidth+centerXtranslate, ( -fontRendererObj.FONT_HEIGHT/2 ) -2, 0);
			} else {
				textWidth = fontRendererObj.getStringWidth(text);
				fontRendererObj.drawString(text, -textWidth-xWidth+1, 1, scaleAlpha << 24 | 0x000000);
			}
			
			GlStateManager.translate(-(getGuiX()+WIDTH-13), -(getGuiY()+12), 0);
			
			int deltaScaleAlpha = (int)( deltaMillis * ( 256 / 1000) );
			if(deltaScaleAlpha == 0) // because of some crazy high framerate
				deltaScaleAlpha = 1;
			
			scaleAlpha -= 20*deltaScaleAlpha;
			
			if(scaleAlpha < 0)
				scaleAlpha = 0;
		}
	}
	
	private void renderMarker(Marker marker, double scale) {
		MarkerType type = marker.getType();
		
		if (type.shouldHide(state.is(HIDING_MARKERS), scaleClipIndex)) {
			return;
		}
		
		int markerX = worldXToScreenX(marker.getX());
		int markerY = worldZToScreenY(marker.getZ());
		if (!marker.isVisibleAhead() &&
				!biomeData.hasTileAt(marker.getChunkX(), marker.getChunkZ())) {
			return;
		}
		type.calculateMip(scale, mapScale, screenScale);
		MarkerRenderInfo info = type.getRenderInfo(scale, mapScale, screenScale);
		
		boolean mouseIsOverMarker = type.shouldHover((getMouseX()-(markerX+info.x))/info.width, (getMouseY()-(markerY+info.y))/info.height);
		type.resetMip();
		
		if (state.is(PLACING_MARKER)) {
			GlStateManager.color(1, 1, 1, 0.5f);
		} else if (state.is(DELETING_MARKER)) {
			if (marker.isGlobal()) {
				GlStateManager.color(1, 1, 1, 0.5f);
			} else {
				if (mouseIsOverMarker) {
					GlStateManager.color(0.5f, 0.5f, 0.5f, 1);
					toDelete = marker;
				} else {
					GlStateManager.color(1, 1, 1, 1);
					if (toDelete == marker) {
						toDelete = null;
					}
				}
			}
		} else {
			GlStateManager.color(1, 1, 1, 1);
		}
		
		AtlasRenderHelper.drawFullTexture(
				info.tex,
				markerX + info.x,
				markerY + info.y,
				info.width, info.height);
		if (isMouseOver && mouseIsOverMarker && marker.getLabel().length() > 0) {
			drawTooltip(Arrays.asList(marker.getLocalizedLabel()), mc.fontRendererObj);
		}
	}
	
	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}
	
	@Override
	public void onGuiClosed() {
		super.onGuiClosed();
		removeChild(markerFinalizer);
		removeChild(blinkingIcon);
		Keyboard.enableRepeatEvents(false);
		biomeData.setBrowsingPosition(mapOffsetX, mapOffsetY, mapScale);
		PacketDispatcher.sendToServer(new BrowsingPositionPacket(stack.getItemDamage(), player.dimension, mapOffsetX, mapOffsetY, mapScale));
	}
	
	/** Returns the Y coordinate that the cursor is pointing at. */
	private int screenXToWorldX(int mouseX) {
		return (int)Math.round((double)(mouseX - this.width/2 - mapOffsetX) / mapScale);
	}
	/** Returns the Y block coordinate that the cursor is pointing at. */
	private int screenYToWorldZ(int mouseY) {
		return (int)Math.round((double)(mouseY - this.height/2 - mapOffsetY) / mapScale);
	}
	
	private int worldXToScreenX(int x) {
		return (int)Math.round((double)x * mapScale + this.width/2 + mapOffsetX);
	}
	private int worldZToScreenY(int z) {
		return (int)Math.round((double)z * mapScale + this.height/2 + mapOffsetY);
	}
	
	@Override
	protected void onChildClosed(GuiComponent child) {
		if (child.equals(markerFinalizer)) {
			setInterceptKeyboard(false);
			removeChild(blinkingIcon);
		}
	}

	/** Update all text labels to current localization. */
	public void updateL18n() {
		btnExportPng.setTitle(I18n.format("gui.antiqueatlas.exportImage"));
		btnMarker.setTitle(I18n.format("gui.antiqueatlas.addMarker"));
	}
	
	@Override
	public void setWorldAndResolution(Minecraft mc, int width, int height) {
		super.setWorldAndResolution(mc, width, height);
//		resolution = new ScaledResolution(mc);
	}
	
	/** Returns the scale of markers and player icon at given mapScale. */
	private double getIconScale() {
		return AntiqueAtlasMod.settings.doScaleMarkers ? (mapScale < 0.5 ? 0.5 : mapScale > 1 ? 2 : 1) : 1;
	}
}
