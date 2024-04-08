package org.mastodon.tomancak;

import bdv.tools.brightness.ConverterSetup;
import bdv.util.Bdv;
import bdv.viewer.*;


import graphics.scenery.*;

import graphics.scenery.attribute.material.Material;
import graphics.scenery.attribute.material.DefaultMaterial;
import graphics.scenery.controls.TrackerRole;
import graphics.scenery.controls.behaviours.Selectable;
import graphics.scenery.controls.behaviours.Touchable;
import graphics.scenery.primitives.Cylinder;
import graphics.scenery.volumes.TransferFunction;
import graphics.scenery.volumes.Volume;
import org.joml.Quaternionf;
import org.joml.Vector3f;


import org.mastodon.mamut.ProjectModel;
import org.mastodon.mamut.model.Link;
import org.mastodon.mamut.model.Spot;
import org.mastodon.mamut.views.bdv.MamutViewBdv;
import org.mastodon.spatial.SpatialIndex;
import org.mastodon.tomancak.dialogs.SpotsDisplayParamsDialog;
import org.mastodon.tomancak.dialogs.SynchronizeChoiceDialog;
import org.mastodon.ui.coloring.GraphColorGenerator;

import org.scijava.Context;
import org.scijava.command.CommandService;
import org.scijava.event.EventService;
import sc.iview.SciView;
import sc.iview.event.NodeChangedEvent;
//import sc.iview.commands.edit.add.AddOrientationCompass;
import graphics.scenery.controls.TrackedDevice;


import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DisplayMastodonData {
	//Mastodon connection
	final ProjectModel projectModel;
	final FocusedBdvWindow controllingBdvWindow = new FocusedBdvWindow();
	private ReentrantReadWriteLock lock;
	private Node selectionStorage = null;

	//the overall coordinate scale factor from Mastodon to SciView coords
	//NB: the smaller scale the better! with scale 1, pixels look terrible....

	public static float scale_x;
	public static float scale_y;
	public static float scale_z;


	//SciView connection + EventService that is used to update SciView's inspector panel
	SciView sv = null;
	EventService events = null;

	//shared cache of colormaps for volumes (to prevent that they are re-created over and over again)
	final CachedColorTables volumeColormaps = new CachedColorTables();


	public
	DisplayMastodonData(final ProjectModel projectModel)
	{
		this(projectModel,false);
	}

	public
	DisplayMastodonData(final ProjectModel projectModel, final boolean startSciView)
	{
		this.projectModel = projectModel;
		if (startSciView) startSciView();
	}

	//aid class to find out if we are started from some BDV window
	class FocusedBdvWindow
	{
		MamutViewBdv focusedBdvWindow = null;

		boolean isThereSome() { return focusedBdvWindow != null; }
		MamutViewBdv get() { return focusedBdvWindow; }

		void setPossiblyTo(MamutViewBdv adept)
		{ if (adept.getFrame().isFocused()) focusedBdvWindow = adept; }

		void setupFrom(final ProjectModel mastodon)
		{
			focusedBdvWindow = null;
			mastodon.getWindowManager().forEachView(MamutViewBdv.class, bdvView -> setPossiblyTo(bdvView) );

			if (focusedBdvWindow != null)
				System.out.println("Controlling window found: "+focusedBdvWindow.getFrame().getTitle());
			else
				System.out.println("No controlling window found");
		}
	}

	//returns if SciView has been started sucessfully
	public
	boolean startSciView()
	{
		new Thread("Mastodon's SciView")
		{
			@Override
			public void run()
			{
				controllingBdvWindow.setupFrom(projectModel);
				try {
					sv = SciView.create();
					sv.setInterpreterWindowVisibility(false);
					Thread.sleep(2000); //a bit of a grace time before we continue
					System.out.println("SciView started...");
				} catch (Exception e) {
					System.out.println("SciView has a problem:");
					e.printStackTrace();
					sv = null;
					events = null;
				}

				if (sv != null)
				{
					//find the event service to be able to notify the inspector
					events = sv.getScijavaContext().getService(EventService.class);
					System.out.println("Found an event service: "+events);
				}
			}
		};

		return sv != null;
	}

	// ============================================================================================

	public static
	String volumeName = "volume";
	public static
	float volumeHeight = 0.0f;

	// ============================================================================================

	public Volume showTimeSeries()
	{
		return showTimeSeries(projectModel,sv);
	}

	/**
	 *  Most of settings to volume are set here.
	 */
	public
	Volume showTimeSeries(final ProjectModel mastodonPlugin, final SciView sv)
	{
		final SourceAndConverter<?> sac = mastodonPlugin.getSharedBdvData().getSources().get(0);
		int np =mastodonPlugin.getSharedBdvData().getNumTimepoints();
		final Volume v = (Volume)sv.addVolume((SourceAndConverter)sac,np,volumeName, new float[]{1.0f, 1.0f, 1.0f});

		//adjust the transfer function to a "diagonal"
		setTransferFunction(v);

		//override SciView's initial LUT
		final CachedColorTables volumeColormaps = new CachedColorTables();
		restoreVolumeColor(v,volumeColormaps);

		//initial min-max display range comes from BDV
		//... comes from BDV transiently since we're using its data directly ...
		final ConverterSetup cs = mastodonPlugin.getSharedBdvData().getConverterSetups().getConverterSetup(sac);
		v.getConverterSetups().get(0).setDisplayRange(cs.getDisplayRangeMin(), cs.getDisplayRangeMax());

		//prepare per axis scaling factors to maintain the data voxel ratio
		//... isotropy scaling is taken care of in the BDV data too ...

		v.setName(volumeName);


		//v.spatial().setWantsComposeModel(true); //makes position,scale,rotation be ignored, also pxToWrld scale is ignored
		scale_x = 1f;
		scale_y = 1f;
		scale_z = 1f;
//		scale_x = 0.5f;
//		scale_y = 0.5f;
//		scale_z = 7.5f;
		v.spatial().setScale(new Vector3f(scale_x,scale_y,scale_z));
		//v.setColormap(Colormap.get("jet"));

		v.spatial().setNeedsUpdateWorld(true);
		v.getViewerState().setInterpolation(Interpolation.NLINEAR);
		v.getVolumeManager().requestRepaint();

		System.out.println("volumes.getDimension: " + v.getDimensions());
		System.out.println("volumes.model: " + v.getModel());
		volumeHeight = v.getDimensions().y;

		return v;
	}

	public
	void makeSciViewReadBdvSetting(final Volume v)
	{

		//watch when BDV (through its color&brightness dialog) changes display range or volume's color
		projectModel.getSharedBdvData().getConverterSetups().listeners().add(t -> {
			System.out.println("BDV says display range: " + t.getDisplayRangeMin() + " -> " + t.getDisplayRangeMax());
			if(synChoiceParams.synColor)
			{
				System.out.println("BDV says new color    : " + t.getColor());
				restoreVolumeColor(v,volumeColormaps);
			}
		});

		//this block may set up a listener for TP change in a BDV from which SciView was started, if any...
			if (controllingBdvWindow.isThereSome())
			{
				System.out.println("Will be syncing timepoints with "+controllingBdvWindow.get().getFrame().getTitle());
				controllingBdvWindow.get().getViewerPanelMamut().addTimePointListener(tp -> {
						if(synChoiceParams.synTimestamp)
						{
							System.out.println("BDV says new timepoint "+tp);
							//v.getViewerState().setCurrentTimepoint(tp);
							//v.getVolumeManager().requestRepaint();
							//v.goToTimepoint(tp);

							//also notify the inspector panel
//							events.publish(new NodeChangedEvent(v));
						}
					});
			}
		else System.out.println("Will NOT be syncing timepoints or rotation with any BDV window");

		//To do -- set up a listener to pointer
	}

	public
	void makeBdvReadSciViewSetting(final Volume v)
	{
		//watch when SciView's inspector panel adjusts the color
		//and re-reset it back to that of Mastodon
		v.getViewerState().getState().changeListeners().add(new ViewerStateChangeListener() {
			@Override
			public void viewerStateChanged(ViewerStateChange viewerStateChange) {
				if(synChoiceParams.synTimestamp) {
					final int TP = v.getViewerState().getCurrentTimepoint();
					System.out.println("SciView says new timepoint " + TP);
					System.out.println("color is affected");
					//also keep ignoring the SciView's color/LUT and enforce color from BDV
					restoreVolumeColor(v, volumeColormaps);
					System.out.println("effect is removed ");
					projectModel.getWindowManager().forEachView(MamutViewBdv.class,
							view -> {
								view.getViewerPanelMamut().setTimepoint(TP);
								view.getViewerPanelMamut().requestRepaint();
							});
				}
			}
		});
	}

	// ============================================================================================

	public
	final SynchronizeChoiceDialog.ParamsWrapper synChoiceParams = new SynchronizeChoiceDialog.ParamsWrapper();

	public
	final SpotsDisplayParamsDialog.ParamsWrapper spotVizuParams = new SpotsDisplayParamsDialog.ParamsWrapper();

	public
	float linkRadius = 0.01f;

	//define a customed classs with some functions called SphereWithLinks
	public
	class SphereWithLinks extends Icosphere
	{
		SphereWithLinks(float radius, int segments)
		{
			super(radius, segments);
		}

		public Node linksNodesHub;     // gathering node in sciview -- a links node associated to its spots node
		public List<LinkNode> links;   // list of links of this spot

		Node selectionStorage = new RichNode();
		public Spot refSpot = null;
		public int minTP, maxTP;

		void addLink(final Spot from, final Spot to)
		{
			from.localize(pos);
			to.localize(pos);

			posT.sub( posF );

			//NB: posF is base of the "vector" link, posT is the "vector" link itself
			Cylinder node = new Cylinder(linkRadius, posT.length(), 8);
			node.spatial().getScale().set( spotVizuParams.linkSize*100,1,spotVizuParams.linkSize*100 );
			node.spatial().setRotation( new Quaternionf().rotateTo( new Vector3f(0,1,0), posT ).normalize() );
			node.spatial().setPosition( new Vector3f(posF) );

			node.setName(from.getLabel() + " --> " + to.getLabel());
			//node.setMaterial( linksNodesHub.getMaterial() );
			System.out.println("add node : " + node.getName());
			linksNodesHub.addChild( node );
			links.add( new LinkNode(node,from.getTimepoint(),to.getTimepoint()) );

			minTP = Math.min(minTP, from.getTimepoint());
			maxTP = Math.max(maxTP,   to.getTimepoint());
		}

		private final float[] pos = new float[3];
		private final Vector3f posF = new Vector3f();
		private final Vector3f posT = new Vector3f();

		void clearLinksOutsideRange(final int TPfrom, final int TPtill)
		{
		    final Iterator<LinkNode> it = links.iterator();
		    while (it.hasNext())
			{
				final LinkNode link = it.next();
				if (link.TPfrom < TPfrom || link.TPtill > TPtill)
				{
					linksNodesHub.removeChild(link.node);
					it.remove();
				}
			}

		    minTP = TPfrom;
			maxTP = TPtill;
		}

		void clearAllLinks()
		{
			linksNodesHub.getChildren().removeIf(f -> true);
			links.clear();
			minTP = 999999;
			maxTP = -1;
		}

		void setupEmptyLinks()
		{
			linksNodesHub = new RichNode();
			links = new LinkedList<>();
			minTP = 999999;
			maxTP = -1;
		}

		void registerNewSpot(final Spot spot)
		{
			if (refSpot != null) refSpot.getModelGraph().releaseRef(refSpot);
			refSpot = spot.getModelGraph().vertexRef();
			refSpot.refTo(spot);

			minTP = spot.getTimepoint();
			maxTP = minTP;
		}

		public
		void updateLinks(final int TPsInPast, final int TPsAhead)
		{
			System.out.println("updatelinks!");
			clearLinksOutsideRange(refSpot.getTimepoint(),refSpot.getTimepoint());
			backwardSearch(refSpot, refSpot.getTimepoint()-TPsInPast);
			forwardSearch( refSpot, refSpot.getTimepoint()+TPsAhead);
			events.publish(new NodeChangedEvent(linksNodesHub));
		}

		private
		void forwardSearch(final Spot spot, final int TPtill)
		{
			System.out.println("spot.getTimepoint():"+spot.getTimepoint());
			System.out.println("TPtill:"+TPtill);

			if (spot.getTimepoint() >= TPtill) return;
			System.out.println("forward search!");
			//enumerate all forward links
			final Spot s = spot.getModelGraph().vertexRef();
			for (Link l : spot.incomingEdges())
			{
				System.out.println("forward search: incoming edges");
				if (l.getSource(s).getTimepoint() > spot.getTimepoint() && s.getTimepoint() <= TPtill)
				{
					addLink(spot,s);
					forwardSearch(s,TPtill);
				}
			}
			for (Link l : spot.outgoingEdges())
			{
				System.out.println("forward search: outgoing edges");
				if (l.getTarget(s).getTimepoint() > spot.getTimepoint() && s.getTimepoint() <= TPtill)
				{
					addLink(spot,s);
					forwardSearch(s,TPtill);
				}
			}
			spot.getModelGraph().releaseRef(s);
		}

		private
		void backwardSearch(final Spot spot, final int TPfrom)
		{
			if (spot.getTimepoint() <= TPfrom) return;
			//enumerate all backward links
			final Spot s = spot.getModelGraph().vertexRef();
			for (Link l : spot.incomingEdges())
			{
				System.out.println("backward search: incoming edges");
				if (l.getSource(s).getTimepoint() < spot.getTimepoint() && s.getTimepoint() >= TPfrom)
				{
					addLink(s,spot);
					backwardSearch(s,TPfrom);
				}
			}
			for (Link l : spot.outgoingEdges())
			{
				System.out.println("backward search: outgoing edges");
				if (l.getTarget(s).getTimepoint() < spot.getTimepoint() && s.getTimepoint() >= TPfrom)
				{
					addLink(s,spot);
					backwardSearch(s,TPfrom);
				}
			}
			spot.getModelGraph().releaseRef(s);
		}
	}

	class LinkNode
	{
		public Cylinder node;
		public int TPfrom, TPtill;

		LinkNode(final Cylinder node, final int TPfrom, final int TPtill)
		{ this.node = node; this.TPfrom = TPfrom; this.TPtill = TPtill; }
	}

	// ============================================================================================

	public
	float spotRadius = 10f;

	public
	void showSpots(final int timepoint, final RichNode spotsNode, final RichNode linksNode)
	{
		showSpots(timepoint,spotsNode,linksNode,null);
	}

	public
	void showSpots(final int timepoint, final RichNode spotsHubNode, final RichNode linksHubNode, final GraphColorGenerator<Spot, Link> colorGenerator)
	{
		SpatialIndex<Spot> spots = projectModel.getModel().getSpatioTemporalIndex().getSpatialIndex(timepoint);
		final Vector3f hubPos = spotsHubNode.getPosition();

		//list of existing nodes that shall be updated
		if (spotsHubNode.getChildren().size() > spots.size())
		{
			//removing spots from the children list is somewhat funky,
			//we better remove all and add all anew
			spotsHubNode.getChildren().removeIf(f -> true);
			linksHubNode.getChildren().removeIf(f -> true);
		}
		Iterator<Node> existingNodes = spotsHubNode.getChildren().iterator();

		//list of new nodes beyond the existing nodes, we better add at the very end
		//to make sure the iterator can remain consistent
		List<SphereWithLinks> extraNodes = new LinkedList<>();

		//reference vector with diffuse color from the gathering node
		//NB: relying on the fact that Scenery keeps only a reference (does not make own copies)
		final Material sharedMaterialObj = spotsHubNode.getMaterial();
		final Material sharedLinksMatObj = linksHubNode.getMaterial();

		for (Spot spot : spots)
		{
			System.out.println("spot: "+spot.toString());
			//find a Sphere to represent this spot
			SphereWithLinks sph;
			if (existingNodes.hasNext())
			{
				//update existing one
				sph = (SphereWithLinks)existingNodes.next();
				sph.clearAllLinks(); //because they're inappropriate for this new spot
			}
			else
			{
				//create a new one
				sph = new SphereWithLinks(spotRadius, 2);
				sph.spatial().getScale().set( spotVizuParams.spotSize );
				sph.setupEmptyLinks();
				//
				sph.spatial().setScale(new Vector3f(1.0f,1.0f,1.0f));
				extraNodes.add(sph);
			}

			//setup the spot
			lock = projectModel.getModel().getGraph().getLock();
			lock.readLock().lock();
			try{
				spot.localize(pos);
			}
			finally {
				lock.readLock().unlock();
			}

			//System.out.println(pos[0] + " " + pos[1] + " " + pos[2]);
			sph.spatial().setPosition( pos ); //adjust coords to the current volume scale

			//System.out.println(sph.spatial().getPosition());
			if (colorGenerator != null)
			{
				int rgbInt = colorGenerator.color(spot);
				if ((rgbInt&0x00FFFFFF) > 0)
				{
					//before we set the color from the colorGenerator,
					//we have to have for sure own material object (not the shared one)
					if (sph.getMaterial() == sharedMaterialObj) sph.setMaterial(new DefaultMaterial());
					Vector3f rgb = sph.getMaterial().getDiffuse();
					rgb.x = (float)((rgbInt >> 16) & 0xFF) / 255f;
					rgb.y = (float)((rgbInt >>  8) & 0xFF) / 255f;
					rgb.z = (float)((rgbInt      ) & 0xFF) / 255f;
				}
				else
				{
					//share the same color settings of the gathering node (this allows user to choose once and "set" for all)
					//NB: relying on the fact that Scenery keeps only a reference (does not make own copy)
					sph.setMaterial(sharedMaterialObj);
				}
			}
			else
			{
				//share the same color settings of the gathering node (this allows user to choose once and "set" for all)
				//NB: relying on the fact that Scenery keeps only a reference (does not make own copy)
				sph.setMaterial(sharedMaterialObj);
			}

			sph.setName(spot.getLabel());
			sph.getMetadata().put("Label",spot.getLabel());
			sph.addAttribute(Selectable.class, 	new Selectable(() -> { selectionStorage = sph; return null;}));
			sph.addAttribute(Touchable.class, new Touchable(
				(TrackedDevice device)-> {
				if (device.getRole() == TrackerRole.LeftHand) {
					if (device.getVelocity() != null)
					{
						Vector3f position = new Vector3f((device.getVelocity()).mul(new Vector3f(5f))).add(sph.spatial().getPosition());
						sph.spatial().setPosition(position);
					}
					events.publish(new NodeChangedEvent(sph));
				}
				return null;},
			null,
			null,
			null
			));

			System.out.println("add nodes to sciview");
			sph.linksNodesHub.setName("Track of " + spot.getLabel());
			sph.linksNodesHub.setMaterial( sph.getMaterial() != sharedMaterialObj ? sph.getMaterial() : sharedLinksMatObj );
			sph.linksNodesHub.setParent( linksHubNode ); //NB: required for sph.updateLinks() -> sph.addNode()

			sph.registerNewSpot(spot);
			sph.updateLinks(spotVizuParams.link_TPsInPast, spotVizuParams.link_TPsAhead);
			System.out.println("finish updatelink");
		}

		//register the extra new spots (only after they are fully prepared)
		for (SphereWithLinks s : extraNodes) {
			spotsHubNode.addChild(s);
			linksHubNode.addChild(s.linksNodesHub);
		}

		//notify the inspector to update the hub node
		spotsHubNode.setName("Mastodon spots at "+timepoint);
		spotsHubNode.updateWorld(true,true);
		events.publish(new NodeChangedEvent(spotsHubNode));
	}


	public
	void updateSpotPosition(final RichNode spotsNode, final Spot updatedSpot)
	{
		if(!synChoiceParams.synSpotLoc)
			return;
		final Node spotNode = sv.find(updatedSpot.getLabel()); //KILLER! TODO
		if (spotNode != null)
		{
			final Vector3f hubPos = spotsNode.spatial().getPosition();
			updatedSpot.localize(pos);
			spotNode.spatialOrNull().setPosition( pos); //adjust coords to the current volume scale
			spotNode.spatialOrNull().setNeedsUpdate(true);
		}
	}

	//aux array to aid transferring of float positions (and avoid re-allocating it)
	final float[] pos = new float[3];

	// ============================================================================================
	// ============================================================================================

	static
	void setTransferFunction(final Volume v)
	{
		final TransferFunction tf = v.getTransferFunction();
		tf.clear();
		tf.addControlPoint(0.00f, 0.0f);
		tf.addControlPoint(0.05f, 0.1f);
		tf.addControlPoint(0.90f, 0.7f);
		tf.addControlPoint(1.00f, 0.8f);
	}

	static
	void restoreVolumeColor(final Volume v, final CachedColorTables colormapsCache)
	{
		int rgba = v.getConverterSetups().get(0).getColor().get();
		v.setColormap( colormapsCache.getColormap(rgba) );
	}

	//call Dialog "synchronizeChoice"
	public static
	void showSynchronizeChoiceDialog(final Context ctx,final SynchronizeChoiceDialog.ParamsWrapper synChoiceParams,final ProjectModel ProjectModel
	, final Volume volume)
	{
		//start the TransferFunction modifying dialog
		ctx.getService(CommandService.class).run(SynchronizeChoiceDialog.class,true,
				"params",synChoiceParams,"ProjectModel",ProjectModel, "volume",volume,"sciView",volume.getVolumeManager().getHub().getApplication()
		);
	}

	//call Dialog "synchronizeChoice"
	public static
	void showSpotsDisplayParamsDialog(final Context ctx, final Node spots, final Node links,
	                                  final SpotsDisplayParamsDialog.ParamsWrapper vizuParams
			, final Volume volume)
	{
		//start the TransferFunction modifying dialog
		ctx.getService(CommandService.class).run(SpotsDisplayParamsDialog.class,true,
				"params",vizuParams,
				"spotsGatheringNode",spots, "linksGatheringNode",links,
				 "volume",volume,"sciView",volume.volumeManager.getHub().getApplication());
	}



}
