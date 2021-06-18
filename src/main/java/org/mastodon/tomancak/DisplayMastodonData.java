package org.mastodon.tomancak;

import bdv.tools.brightness.ConverterSetup;
import bdv.viewer.*;

import graphics.scenery.Material;
import graphics.scenery.Node;
import graphics.scenery.Sphere;
import graphics.scenery.Cylinder;
import graphics.scenery.volumes.TransferFunction;
import graphics.scenery.volumes.Volume;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;


import org.mastodon.mamut.MamutViewBdv;
import org.mastodon.mamut.model.Link;
import org.mastodon.mamut.model.Spot;
import org.mastodon.mamut.plugin.MamutPluginAppModel;
import org.mastodon.spatial.SpatialIndex;
import org.mastodon.tomancak.dialogs.SpotsDisplayParamsDialog;
import org.mastodon.tomancak.dialogs.SynchronizeChoiceDialog;
import org.mastodon.ui.coloring.GraphColorGenerator;

import net.imglib2.RandomAccessibleInterval;
import org.scijava.Context;
import org.scijava.command.CommandService;
import org.scijava.event.EventService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.NumberWidget;
import sc.iview.SciView;
import sc.iview.event.NodeChangedEvent;
import sc.iview.commands.edit.add.AddOrientationCompass;
import sc.iview.commands.view.SetTransferFunction;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.*;

import org.scijava.command.Command;
import org.scijava.command.CommandInfo;

import bdv.util.Bounds;

public class DisplayMastodonData {
	//Mastodon connection
	final MamutPluginAppModel pluginAppModel;
	final FocusedBdvWindow controllingBdvWindow = new FocusedBdvWindow();

	//the overall coordinate scale factor from Mastodon to SciView coords
	//NB: the smaller scale the better! with scale 1, pixels look terrible....
	public static
	final float scale = 0.01f;

	//SciView connection + EventService that is used to update SciView's inspector panel
	SciView sv = null;
	EventService events = null;

	//shared cache of colormaps for volumes (to prevent that they are re-created over and over again)
	final CachedColorTables volumeColormaps = new CachedColorTables();




	public
	DisplayMastodonData(final MamutPluginAppModel pluginAppModel)
	{
		this(pluginAppModel,false);
	}

	public
	DisplayMastodonData(final MamutPluginAppModel pluginAppModel,  final boolean startSciView)
	{
		this.pluginAppModel = pluginAppModel;
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

		void setupFrom(final MamutPluginAppModel mastodon)
		{
			focusedBdvWindow = null;
			mastodon.getWindowManager().forEachBdvView( bdvView -> setPossiblyTo(bdvView) );

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
				controllingBdvWindow.setupFrom(pluginAppModel);
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
	String volumeName = "Mastodon's raw data";


	// ============================================================================================

	public Volume showTimeSeries()
	{
		return showTimeSeries(pluginAppModel,sv);
	}

	public
	Volume showTimeSeries(final MamutPluginAppModel mastodonPlugin, final SciView sv)
	{
		final SourceAndConverter<?> sac = mastodonPlugin.getAppModel().getSharedBdvData().getSources().get(0);
		int np =mastodonPlugin.getAppModel().getSharedBdvData().getNumTimepoints();
		final Volume v = (Volume)sv.addVolume((SourceAndConverter)sac,np,volumeName,1.0f, 1.0f, 1.0f);


		//adjust the transfer function to a "diagonal"
		setTransferFunction(v);

		//override SciView's initial LUT
		final CachedColorTables volumeColormaps = new CachedColorTables();
		restoreVolumeColor(v,volumeColormaps);

		//initial min-max display range comes from BDV
		//... comes from BDV transiently since we're using its data directly ...
		final ConverterSetup cs = mastodonPlugin.getAppModel().getSharedBdvData().getConverterSetups().getConverterSetup(sac);
		v.getConverterSetups().get(0).setDisplayRange(cs.getDisplayRangeMin(), cs.getDisplayRangeMax());

		//prepare per axis scaling factors to maintain the data voxel ratio
		//... isotropy scaling is taken care of in the BDV data too ...

		v.setName(volumeName);

		v.setWantsComposeModel(false); //makes position,scale,rotation be ignored, also pxToWrld scale is ignored
		v.setModel( new Matrix4f(scale,0,0,0,
		                         0,-scale,0,0,
		                         0,0,-scale,0,
		                         0,0,0,1) );
		v.setNeedsUpdateWorld(true);
		//now the volume's diagonal in world coords is now:
		//      [0,0,0] -> [scale*origXSize, -scale*origYSize, -scale*origZSize]

		v.getViewerState().setInterpolation(Interpolation.NLINEAR);
		v.getVolumeManager().requestRepaint();


		return v;
	}

	public
	void makeSciViewReadBdvSetting(final Volume v)
	{

		//watch when BDV (through its color&brightness dialog) changes display range or volume's color
		pluginAppModel.getAppModel().getSharedBdvData().getConverterSetups().listeners().add( t -> {
			System.out.println("BDV says display range: " + t.getDisplayRangeMin() + " -> " + t.getDisplayRangeMax());
			if(synChoiceParams.synColor)
			{
				System.out.println("BDV says new color    : " + t.getColor());
				restoreVolumeColor(v,volumeColormaps);
				v.getVolumeManager().requestRepaint();
			}
			//request that the volume be repainted in SciView
		});

		//this block may set up a listener for TP change in a BDV from which SciView was started, if any...
			if (controllingBdvWindow.isThereSome())
			{
				System.out.println("Will be syncing timepoints with "+controllingBdvWindow.get().getFrame().getTitle());
				controllingBdvWindow.get().getViewerPanelMamut().addTimePointListener(tp -> {
						if(synChoiceParams.synTimestamp)
						{
							System.out.println("BDV says new timepoint "+tp);
							v.getViewerState().setCurrentTimepoint(tp);
							v.getVolumeManager().requestRepaint();

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
					pluginAppModel.getWindowManager().forEachBdvView(
							view -> {
								view.getViewerPanelMamut().setTimepoint(TP);
								view.requestRepaint();
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

	public
	class SphereWithLinks extends Sphere
	{
		SphereWithLinks(float radius, int segments)
		{
			super(radius, segments);
		}

		public Node linksNodesHub;     // gathering node in sciview -- a links node associated to its spots node
		public List<LinkNode> links;   // list of links of this spot

		public Spot refSpot = null;
		public int minTP, maxTP;

		void addLink(final Spot from, final Spot to)
		{
			from.localize(pos);
			toLocalCoords( posF.set(pos), linksNodesHub.getParent().getPosition() );

			to.localize(pos);
			toLocalCoords( posT.set(pos), linksNodesHub.getParent().getPosition() );
			posT.sub( posF );

			//NB: posF is base of the "vector" link, posT is the "vector" link itself
			Cylinder node = new Cylinder(linkRadius, posT.length(), 8);
			node.getScale().set( spotVizuParams.linkSize,1,spotVizuParams.linkSize );
			node.setRotation( new Quaternionf().rotateTo( new Vector3f(0,1,0), posT ).normalize() );
			node.setPosition( new Vector3f(posF) );
			node.setName(from.getLabel() + " --> " + to.getLabel());
			node.setMaterial( linksNodesHub.getMaterial() );

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
			linksNodesHub = new Node();
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
			clearLinksOutsideRange(refSpot.getTimepoint(),refSpot.getTimepoint());
			backwardSearch(refSpot, refSpot.getTimepoint()-TPsInPast);
			forwardSearch( refSpot, refSpot.getTimepoint()+TPsAhead);
			events.publish(new NodeChangedEvent(linksNodesHub));
		}

		private
		void forwardSearch(final Spot spot, final int TPtill)
		{
			if (spot.getTimepoint() >= TPtill) return;

			//enumerate all forward links
			final Spot s = spot.getModelGraph().vertexRef();
			for (Link l : spot.incomingEdges())
			{
				if (l.getSource(s).getTimepoint() > spot.getTimepoint() && s.getTimepoint() <= TPtill)
				{
					addLink(spot,s);
					forwardSearch(s,TPtill);
				}
			}
			for (Link l : spot.outgoingEdges())
			{
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
				if (l.getSource(s).getTimepoint() < spot.getTimepoint() && s.getTimepoint() >= TPfrom)
				{
					addLink(s,spot);
					backwardSearch(s,TPfrom);
				}
			}
			for (Link l : spot.outgoingEdges())
			{
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
	float spotRadius = 0.1f;

	public
	void showSpots(final int timepoint, final Node spotsHubNode, final Node linksHubNode)
	{
		showSpots(timepoint,spotsHubNode,linksHubNode,null);
	}

	public
	void showSpots(final int timepoint, final Node spotsHubNode, final Node linksHubNode, final GraphColorGenerator<Spot, Link> colorGenerator)
	{
		SpatialIndex<Spot> spots = pluginAppModel.getAppModel().getModel().getSpatioTemporalIndex().getSpatialIndex(timepoint);
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
			System.out.println(spot);
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
				sph = new SphereWithLinks(spotRadius, 8);
				sph.getScale().set( spotVizuParams.spotSize );
				sph.setupEmptyLinks();
				extraNodes.add(sph);
			}

			//setup the spot
			spot.localize(pos);
			sph.setPosition( toLocalCoords(pos,hubPos) ); //adjust coords to the current volume scale

			if (colorGenerator != null)
			{
				int rgbInt = colorGenerator.color(spot);
				if ((rgbInt&0x00FFFFFF) > 0)
				{
					//before we set the color from the colorGenerator,
					//we have to have for sure own material object (not the shared one)
					if (sph.getMaterial() == sharedMaterialObj) sph.setMaterial(new Material());
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
			sph.linksNodesHub.setName("Track of " + spot.getLabel());
			sph.linksNodesHub.setMaterial( sph.getMaterial() != sharedMaterialObj ? sph.getMaterial() : sharedLinksMatObj );
			sph.linksNodesHub.setParent( linksHubNode ); //NB: required for sph.updateLinks() -> sph.addNode()

			sph.registerNewSpot(spot);
			sph.updateLinks(spotVizuParams.link_TPsInPast, spotVizuParams.link_TPsAhead);
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
	void updateSpotPosition(final Node spotsGatheringNode, final Spot updatedSpot)
	{
		if(!synChoiceParams.synSpotLoc)
			return;
		final Node spotNode = sv.find(updatedSpot.getLabel()); //KILLER! TODO
		if (spotNode != null)
		{
			final Vector3f hubPos = spotsGatheringNode.getPosition();
			updatedSpot.localize(pos);
			spotNode.setPosition( toLocalCoords(pos,hubPos) ); //adjust coords to the current volume scale
			spotNode.setNeedsUpdate(true);
		}
	}

	//aux array to aid transferring of float positions (and avoid re-allocating it)
	final float[] pos = new float[3];

	// ============================================================================================

	public static
	Vector3f toLocalCoords(final Vector3f coord, final Vector3f relevantCentre)
	{
		coord.mul( +scale, -scale, -scale );
		coord.sub( relevantCentre );
		return coord;
	}

	public static
	float[] toLocalCoords(final float[] coord, final Vector3f relevantCentre)
	{
		coord[0] = +scale * coord[0]  - relevantCentre.x;
		coord[1] = -scale * coord[1]  - relevantCentre.y;
		coord[2] = -scale * coord[2]  - relevantCentre.z;
		return coord;
	}

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

	public
	void centerNodeOnVolume(final Node n, final Volume v)
	{
		//short cut to the Source of this Volume
		final Source<?> volumeAsSource = v.getViewerState().getSources().get(0).getSpimSource();

		//image size in number of pixels per axis/dimension
		final long[] dims = new long[3];
		volumeAsSource.getSource(0,0).dimensions(dims);

		//pixel size in units of the smallest-pixel-size
		final double[] ratios = new double[3];
		calculateDisplayVoxelRatioAlaBDV(ratios, volumeAsSource);

		n.setPosition(new double[] { 0.5*scale*dims[0]*ratios[0], -0.5*scale*dims[1]*ratios[1], -0.5*scale*dims[2]*ratios[2] });
	}

	public static
	void calculateDisplayVoxelRatioAlaBDV(final double[] vxAxisRatio, final Source<?> forThisSource)
	{
		forThisSource.getVoxelDimensions().dimensions(vxAxisRatio);

		double minLength = vxAxisRatio[0];
		for (int i = 1; i < vxAxisRatio.length; ++i) minLength = Math.min( vxAxisRatio[i], minLength );
		for (int i = 0; i < vxAxisRatio.length; ++i) vxAxisRatio[i] /= minLength;
	}

	// ============================================================================================

	public static
	void showTransferFunctionDialog(final Context ctx, final Volume v)
	{
		//start the TransferFunction modifying dialog
		ctx.getService(CommandService.class).run(SetTransferFunction.class,true,
				"sciView",v.getHub().getApplication(),
				//NB: luckily, getApplication() returns SciView instance
				"volume",v);
	}

	public static
	void showSynchronizeChoiceDialog(final Context ctx,final SynchronizeChoiceDialog.ParamsWrapper synChoiceParams,final MamutPluginAppModel mamutPluginAppModel
	, final Volume volume)
	{
		//start the TransferFunction modifying dialog
		ctx.getService(CommandService.class).run(SynchronizeChoiceDialog.class,true,
				"params",synChoiceParams,"mamutPluginAppModel",mamutPluginAppModel, "volume",volume
		);
	}

	public static
	void showSpotsDisplayParamsDialog(final Context ctx, final Node spots, final Node links,
	                                  final SpotsDisplayParamsDialog.ParamsWrapper vizuParams
			, final Volume volume)
	{
		//start the TransferFunction modifying dialog
		ctx.getService(CommandService.class).run(SpotsDisplayParamsDialog.class,true,
				"params",vizuParams,
				"spotsGatheringNode",spots, "linksGatheringNode",links,
				 "volume",volume);
	}

	public static
	void showCompassAxes(final DisplayMastodonData dmd, final Vector3f atThisCenter)
	{
		dmd.sv.getScijavaContext()
			.getService(CommandService.class)
			.run(AddOrientationCompass.class,true,
				"sciView",dmd.sv,
				"axisLength",0.1f, "axisBarRadius",0.001f,
				"attachToCam",true, "showInTheScene",false);

		//NB: we're not displaying axes-compass in the data, so we don't use this now
		//compassMainNode.setPosition(atThisCenter);
		//compassMainNode.getScale().set(scale,-scale,-scale);
	}

}
