package ca.shu.ui.lib.world;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Collection;
import java.util.Vector;

import javax.swing.JPopupMenu;

import ca.shu.ui.lib.Style.Style;
import ca.shu.ui.lib.actions.StandardAction;
import ca.shu.ui.lib.exceptions.ActionException;
import ca.shu.ui.lib.handlers.EventConsumer;
import ca.shu.ui.lib.handlers.Interactable;
import ca.shu.ui.lib.handlers.KeyboardFocusHandler;
import ca.shu.ui.lib.handlers.MouseHandler;
import ca.shu.ui.lib.handlers.ScrollZoomHandler;
import ca.shu.ui.lib.handlers.SelectionHandler;
import ca.shu.ui.lib.handlers.StatusBarHandler;
import ca.shu.ui.lib.handlers.TooltipHandler;
import ca.shu.ui.lib.objects.Window;
import ca.shu.ui.lib.objects.widgets.TooltipWrapper;
import ca.shu.ui.lib.util.Grid;
import ca.shu.ui.lib.util.MenuBuilder;
import ca.shu.ui.lib.util.PopupMenuBuilder;
import ca.shu.ui.lib.util.UIEnvironment;
import edu.umd.cs.piccolo.PLayer;
import edu.umd.cs.piccolo.PNode;
import edu.umd.cs.piccolo.activities.PTransformActivity;
import edu.umd.cs.piccolo.event.PBasicInputEventHandler;
import edu.umd.cs.piccolo.event.PInputEvent;
import edu.umd.cs.piccolo.event.PPanEventHandler;
import edu.umd.cs.piccolo.event.PZoomEventHandler;
import edu.umd.cs.piccolo.util.PBounds;
import edu.umd.cs.piccolo.util.PNodeFilter;

/**
 * Implementation of World. World holds World Objects and has navigation and
 * interaction handlers.
 * 
 * @author Shu Wu
 */
public class World extends WorldObject implements Interactable {

	/**
	 * Padding to use around objects when zooming in on them
	 */
	private static final double OBJECT_ZOOM_PADDING = 100;

	private static final long serialVersionUID = 1L;

	/**
	 * Whether tooltips are enabled
	 */
	private static boolean tooltipsEnabled = true;

	public static boolean isTooltipsVisible() {
		return tooltipsEnabled;
	}

	public static void setTooltipsVisible(boolean tooltipsVisible) {
		World.tooltipsEnabled = tooltipsVisible;

	}

	/**
	 * If true, then selection mode. If false, then navigation mode.
	 */
	private boolean isSelectionMode;

	/**
	 * PLayer which holds the ground layer
	 */
	private PLayer layer;

	/**
	 * Ground which can be zoomed and navigated
	 */
	private WorldGround myGround;

	/**
	 * Sky, which looks at the ground and whose position and scale remains
	 * static
	 */
	private WorldSky mySkyCamera;

	/**
	 * Panning handler
	 */
	private final PPanEventHandler panHandler;

	/**
	 * Selection handler
	 */
	private SelectionHandler selectionEventHandler;

	/**
	 * Status bar handler
	 */
	private PBasicInputEventHandler statusBarHandler;

	/**
	 * Zoom handler
	 */
	private final PZoomEventHandler zoomHandler;

	/**
	 * Layer attached to the camera which shows the zoomable grid
	 */
	PLayer gridLayer;

	/**
	 * Default constructor
	 * 
	 * @param name
	 *            Name of this world
	 */
	public World(String name) {
		super(name);

		/*
		 * Create layer
		 */
		layer = new PLayer();
		UIEnvironment.getInstance().getCanvas().getRoot().addChild(layer);

		/*
		 * Create ground
		 */
		myGround = createGround();
		myGround.setSelectable(false);
		layer.addChild(myGround);

		/*
		 * Create camera
		 */
		mySkyCamera = new WorldSky(this);
		mySkyCamera.setPaint(Style.COLOR_BACKGROUND);
		mySkyCamera.addLayer(layer);
		addChild(mySkyCamera);

		/*
		 * Create handlers
		 */
		panHandler = new PPanEventHandler();

		zoomHandler = new PZoomEventHandler();
		zoomHandler.setMinDragStartDistance(20);
		zoomHandler.setMinScale(0.02);
		zoomHandler.setMaxScale(4);

		selectionEventHandler = new SelectionHandler(this);
		selectionEventHandler.setMarqueePaint(Style.COLOR_BORDER_SELECTED);
		selectionEventHandler
				.setMarqueeStrokePaint(Style.COLOR_BORDER_SELECTED);
		selectionEventHandler.setMarqueePaintTransparency(0.1f);

		/*
		 * Attach handlers
		 */
		mySkyCamera.addInputEventListener(new SwitchSelectionModeHandler());
		mySkyCamera.addInputEventListener(new KeyboardFocusHandler());
		mySkyCamera.addInputEventListener(new TooltipHandler(this));
		mySkyCamera.addInputEventListener(new MouseHandler(this));
		mySkyCamera.addInputEventListener(new ScrollZoomHandler());

		addInputEventListener(new EventConsumer());
		setStatusBarHandler(new StatusBarHandler(this));

		/*
		 * Set position and scale
		 */
		setSkyPosition(0, 0);
		setSkyViewScale(0.7f);

		/*
		 * Create the grid
		 */
		gridLayer = Grid.createGrid(getSky(), UIEnvironment.getInstance()
				.getCanvas().getRoot(), Style.COLOR_DARKBORDER, 1500);

		/*
		 * Let the top canvas have a handle on this world
		 */
		UIEnvironment.getInstance().getCanvas().addWorld(this);

		/*
		 * Miscellaneous
		 */
		setSelectable(false);
		setBounds(0, 0, 800, 600);

		initSelectionMode();

	}

	private void initSelectionMode() {
		isSelectionMode = false;
		mySkyCamera.addInputEventListener(zoomHandler);
		mySkyCamera.addInputEventListener(panHandler);
		mySkyCamera.addInputEventListener(selectionEventHandler);
	}

	/**
	 * Create context menu
	 * 
	 * @return Menu builder
	 */
	protected PopupMenuBuilder constructMenu() {
		PopupMenuBuilder menu = new PopupMenuBuilder(getName());

		menu.addAction(new ZoomToFitAction());
		MenuBuilder windowsMenu = menu.createSubMenu("Windows");
		windowsMenu.addAction(new CloseAllWindows("Close all"));
		windowsMenu.addAction(new MinimizeAllWindows("Minmize all"));

		return menu;

	}

	/**
	 * Create the ground
	 * 
	 * @return ground
	 */
	protected WorldGround createGround() {
		return new WorldGround(this);

	}

	@Override
	protected void prepareForDestroy() {
		UIEnvironment.getInstance().getCanvas().removeWorld(this);

		gridLayer.removeFromParent();
		layer.removeFromParent();

		super.prepareForDestroy();
	}

	/**
	 * Closes all windows which exist in this world
	 */
	public void closeAllWindows() {
		for (Window window : getAllWindows()) {
			window.close();
		}

	}

	/**
	 * @return A collection of all the windows in this world
	 */
	public Collection<Window> getAllWindows() {
		Vector<Window> windows = new Vector<Window>(10);

		PNodeFilter filter = new PNodeFilter() {

			public boolean accept(PNode node) {
				if (node instanceof Window)
					return true;
				else
					return false;

			}

			public boolean acceptChildrenOf(PNode node) {
				if (node instanceof Window) {
					return false;
				} else {
					return true;
				}
			}

		};

		getSky().getAllNodes(filter, windows);
		getGround().getAllNodes(filter, windows);

		return windows;
	}

	/**
	 * @return ground
	 */
	public WorldGround getGround() {
		return myGround;
	}

	/**
	 * @return sky
	 */
	public WorldSky getSky() {
		return mySkyCamera;
	}

	/*
	 * Returns true if the node exists in this world (non-Javadoc)
	 * 
	 * @see edu.umd.cs.piccolo.PNode#isAncestorOf(edu.umd.cs.piccolo.PNode)
	 */
	@Override
	public boolean isAncestorOf(PNode node) {
		if (getGround().isAncestorOf(node))
			return true;
		else
			return super.isAncestorOf(node);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ca.shu.ui.lib.handlers.Interactable#isContextMenuEnabled()
	 */
	public boolean isContextMenuEnabled() {
		return true;
	}

	/**
	 * @return if true, selection mode is enabled. if false, navigation mode is
	 *         enabled instead.
	 */
	public boolean isSelectionMode() {
		return isSelectionMode;
	}

	/**
	 * Minimizes all windows that exist in this world
	 */
	public void minimizeAllWindows() {
		for (Window window : getAllWindows()) {
			window.setWindowState(Window.WindowState.MINIMIZED);
		}
	}

	/*
	 * Set the bounds of the sky be the same as that of the world
	 * 
	 * @see edu.umd.cs.piccolo.PNode#setBounds(double, double, double, double)
	 */
	@Override
	public boolean setBounds(double x, double y, double w, double h) {
		mySkyCamera.setBounds(x, y, w, h);

		return super.setBounds(x, y, w, h);
	}

	/**
	 * @param enabled
	 *            True if selection mode is enabled, False if navigation
	 */
	public void setSelectionMode(boolean enabled) {
		if (isSelectionMode != enabled) {
			isSelectionMode = enabled;
			mySkyCamera.removeInputEventListener(selectionEventHandler);
			if (!isSelectionMode) {

				initSelectionMode();
			} else {

				mySkyCamera.removeInputEventListener(zoomHandler);
				mySkyCamera.removeInputEventListener(panHandler);
				mySkyCamera.addInputEventListener(selectionEventHandler);
			}

			layoutChildren();
		}
	}

	/**
	 * Sets the view position of the sky, and animates to it.
	 * 
	 * @param x
	 *            X Position relative to ground
	 * @param y
	 *            Y Position relative to ground
	 */
	public void setSkyPosition(double x, double y) {
		Rectangle2D newBounds = new Rectangle2D.Double(x, y, 0, 0);

		mySkyCamera.animateViewToCenterBounds(newBounds, false, 600);
	}

	/**
	 * Set the scale at which to view the ground from the sky
	 * 
	 * @param scale
	 *            Scale at which to view the ground
	 */
	public void setSkyViewScale(float scale) {
		getSky().setViewScale(scale);

	}

	/**
	 * Set the status bar handler, there can be only one.
	 * 
	 * @param statusHandler
	 *            New Status bar handler
	 */
	public void setStatusBarHandler(StatusBarHandler statusHandler) {
		if (statusBarHandler != null) {
			getSky().removeInputEventListener(statusBarHandler);
		}

		statusBarHandler = statusHandler;

		if (statusBarHandler != null) {
			getSky().addInputEventListener(statusBarHandler);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ca.shu.ui.lib.handlers.Interactable#showContextMenu(edu.umd.cs.piccolo.event.PInputEvent)
	 */
	public JPopupMenu showContextMenu(PInputEvent event) {
		return constructMenu().getJPopupMenu();
	}

	/**
	 * @param objectSelected
	 *            Object to show the tooltip for
	 * @return Tooltip shown
	 */
	public TooltipWrapper showTooltip(WorldObject objectSelected) {

		TooltipWrapper tooltip = new TooltipWrapper(getSky(), objectSelected
				.getTooltip(), objectSelected);
		addChild(tooltip);

		tooltip.fadeIn();
		tooltip.updatePosition();
		return tooltip;

	}

	/**
	 * @param position
	 *            Position in sky
	 * @return Position on ground
	 */
	public Point2D skyToGround(Point2D position) {
		mySkyCamera.localToView(position);

		return position;
	}

	/**
	 * Animate the sky to look at a portion of the ground at bounds
	 * 
	 * @param bounds
	 *            Bounds to look at
	 * @return Reference to the activity which is animating the zoom and
	 *         positioning
	 */
	public PTransformActivity zoomToBounds(Rectangle2D bounds) {
		PBounds biggerBounds = new PBounds(bounds.getX() - OBJECT_ZOOM_PADDING,
				bounds.getY() - OBJECT_ZOOM_PADDING, bounds.getWidth()
						+ OBJECT_ZOOM_PADDING * 2, bounds.getHeight()
						+ OBJECT_ZOOM_PADDING * 2);

		return getSky().animateViewToCenterBounds(biggerBounds, true, 1000);

	}

	/**
	 * Animate the sky to view all object on the ground
	 * 
	 * @return reference to animation activity
	 */
	public PTransformActivity zoomToFit() {
		return zoomToBounds(getGround().getUnionOfChildrenBounds(null));

	}

	/**
	 * @param object
	 *            Object to zoom to
	 * @return reference to animation activity
	 */
	public PTransformActivity zoomToObject(WorldObject object) {
		Rectangle2D bounds = object.getParent().localToGlobal(
				object.getFullBounds());

		return zoomToBounds(bounds);
	}

	/**
	 * Action to close all windows
	 * 
	 * @author Shu Wu
	 */
	class CloseAllWindows extends StandardAction {

		private static final long serialVersionUID = 1L;

		public CloseAllWindows(String actionName) {
			super("Close all windows", actionName);
		}

		@Override
		protected void action() throws ActionException {
			closeAllWindows();

		}

	}

	/**
	 * Action to minimize all windows
	 * 
	 * @author Shu Wu
	 */
	class MinimizeAllWindows extends StandardAction {

		private static final long serialVersionUID = 1L;

		public MinimizeAllWindows(String actionName) {
			super("Minimize all windows", actionName);
		}

		@Override
		protected void action() throws ActionException {
			minimizeAllWindows();

		}

	}

	/**
	 * Action to switch the selection mode
	 * 
	 * @author Shu Wu
	 */
	class SwitchSelectionModeHandler extends PBasicInputEventHandler {

		@Override
		public void keyTyped(PInputEvent event) {
			if (event.getKeyChar() == 's' || event.getKeyChar() == 'S') {
				UIEnvironment.getInstance()
						.setSelectionMode(!isSelectionMode());

			}

		}

	}

	/**
	 * Action to zoom to fit
	 * 
	 * @author Shu Wu
	 */
	class ZoomToFitAction extends StandardAction {

		private static final long serialVersionUID = 1L;

		public ZoomToFitAction() {
			super("Fit on screen");
		}

		@Override
		protected void action() throws ActionException {
			zoomToFit();
		}

	}

}
