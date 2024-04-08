package org.mastodon.tomancak;

import bdv.viewer.ConverterSetups;
import bdv.viewer.SourceAndConverter;
import graphics.scenery.RichNode;
import graphics.scenery.volumes.Colormap;
import net.imagej.ImageJ;


import net.imglib2.type.numeric.ARGBType;
import org.mastodon.mamut.ProjectModel;
import org.mastodon.mamut.model.ModelGraph;
import org.mastodon.mamut.views.trackscheme.MamutViewTrackScheme;
import org.mastodon.model.tag.ObjTagMap;
import org.mastodon.model.tag.TagSetModel;
import org.mastodon.model.tag.TagSetStructure;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.ui.behaviour.io.gui.CommandDescriptionProvider;
import org.scijava.ui.behaviour.io.gui.CommandDescriptions;
import sc.iview.SciView;
import graphics.scenery.Node;
import graphics.scenery.volumes.Volume;
import org.joml.Vector4f;

import org.mastodon.app.ui.ViewMenuBuilder;
import org.mastodon.mamut.model.Link;
import org.mastodon.mamut.model.Spot;
import org.mastodon.mamut.plugin.MamutPlugin;
import org.mastodon.model.FocusModel;
import org.mastodon.model.HighlightModel;
import org.mastodon.ui.coloring.GraphColorGenerator;
import org.mastodon.ui.keymap.KeyConfigContexts;
import org.scijava.AbstractContextual;
import org.scijava.plugin.Plugin;
import org.scijava.ui.behaviour.util.AbstractNamedAction;
import org.scijava.ui.behaviour.util.Actions;
import org.scijava.ui.behaviour.util.RunnableAction;
import org.scijava.event.EventService;

import org.scijava.event.EventHandler;
import sc.iview.event.*;
import org.joml.Vector3f;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantReadWriteLock;


import static org.mastodon.app.ui.ViewMenuBuilder.item;
import static org.mastodon.app.ui.ViewMenuBuilder.menu;
@Plugin( type = MamutPlugin.class )
public class SciViewPlugin extends AbstractContextual implements MamutPlugin
{
	private static final String SCIVIEW_CONNECTOR = "[tomancak] start sciview";
	private static final String[] SCIVIEW_CONNECTOR_KEYS = { "not mapped" };


	private static final Map< String, String > menuTexts = new HashMap<>();
	static
	{
		menuTexts.put( SCIVIEW_CONNECTOR, "Start SciView" );
	}

	@Parameter
	LogService log;
	
	@Override
	public Map< String, String > getMenuTexts()
	{
		return menuTexts;
	}

	@Override
	public List< ViewMenuBuilder.MenuItem > getMenuItems()
	{
		return Collections.singletonList(
				menu( "Plugins",
						item( SCIVIEW_CONNECTOR ) ) );
	}

	/** Command descriptions for all provided commands */
	@Plugin( type = CommandDescriptionProvider.class )
	public static class Descriptions extends CommandDescriptionProvider
	{
		public Descriptions()
		{
			super(new Scope(KeyConfigContexts.TRACKSCHEME), KeyConfigContexts.BIGDATAVIEWER );
		}

		@Override
		public void getCommandDescriptions( final CommandDescriptions descriptions )
		{
			descriptions.add( SCIVIEW_CONNECTOR, SCIVIEW_CONNECTOR_KEYS, "Start SciView and a special Mastodon->SciView connector control panel." );
		}
	}


	private final AbstractNamedAction startSciViewConnectorAction;

	public SciViewPlugin()
	{
		startSciViewConnectorAction = new RunnableAction( SCIVIEW_CONNECTOR, this::startSciViewConnector );
		updateEnabledActions();
	}

	private ProjectModel projectModel;
	private ReentrantReadWriteLock lock;

	@Override
	public void setAppPluginModel( final ProjectModel model )
	{
		this.projectModel = model;
		updateEnabledActions();
	}

	@Override
	public void installGlobalActions( final Actions actions )
	{
		actions.namedAction( startSciViewConnectorAction, SCIVIEW_CONNECTOR_KEYS );
	}

	private void updateEnabledActions()
	{
		final ProjectModel appModel = ( projectModel == null ) ? null : projectModel;
		startSciViewConnectorAction.setEnabled( appModel != null );
	}

	private void startSciViewConnector()
	{
		new Thread("Mastodon's SciView")
		{
			final DisplayMastodonData dmd = new DisplayMastodonData(projectModel);
			final ModelGraph graph = projectModel.getModel().getGraph();
			@Override
			public void run()
			{
				dmd.controllingBdvWindow.setupFrom(projectModel);
				try {
					dmd.sv = SciView.create();
					dmd.sv.setInterpreterWindowVisibility(false);
					Thread.sleep(2000); //a bit of a grace time before we continue
					log.info("SciView started...");
				} catch (Exception e) {
					log.info("SciView has a problem:");
					e.printStackTrace();
					dmd.sv = null;
				}
				if (dmd.sv == null) return;

				//find the event service to be able to notify the inspector
				dmd.events = dmd.sv.getScijavaContext().getService(EventService.class);
				log.info("Found an event service: " + dmd.events);

				//show full volume
				Volume v = dmd.showTimeSeries();
				dmd.makeSciViewReadBdvSetting(v);
				dmd.makeBdvReadSciViewSetting(v);


				//show spots...
				final RichNode spotsNode = new RichNode("Mastodon spots");
				v.addChild(spotsNode);
				//...and links
				final RichNode linksNode = new RichNode("Mastodon links");
				v.addChild(linksNode);
				DisplayMastodonData.showSpotsDisplayParamsDialog(getContext(),spotsNode,linksNode,dmd.spotVizuParams,v);
				DisplayMastodonData.showSynchronizeChoiceDialog(getContext(), dmd.synChoiceParams, projectModel,v);
				//make sure both node update synchronously
				spotsNode.getUpdate().add( () -> { linksNode.setNeedsUpdate(true); return null; } );
				linksNode.getUpdate().add( () -> { spotsNode.setNeedsUpdate(true); return null; } );

				//now, the spots are click-selectable in SciView and we want to listen if some spot was
				//selected/activated and consequently select it in the Mastodon itself
				dmd.events.subscribe(notifierOfMastodonWhenSpotIsSelectedInSciView);

				if (dmd.controllingBdvWindow.isThereSome())
				{
					//setup coloring
					final MamutViewTrackScheme tsWin = projectModel.getWindowManager().createView(MamutViewTrackScheme.class);
					tsWin.getColoringModel().listeners().add( () -> {
						log.info("coloring changed");
						setColorGeneratorFrom(tsWin);
					});

					//setup highlighting
					sRef = projectModel.getModel().getGraph().vertexRef();
					final HighlightModel<Spot, Link> highlighter = projectModel.getHighlightModel();
					highlighter.listeners().add( () -> {
						if (highlighter.getHighlightedVertex(sRef) != null)
						{
							log.info("focused on "+sRef.getLabel());
							updateFocus( dmd.sv.find(sRef.getLabel()) );
						}
						else
						{
							log.info("defocused");
							updateFocus( null );
						}
					});

					//setup updating of spots to the currently viewed timepoint
					dmd.controllingBdvWindow.get()
							.getViewerPanelMamut()
							.addTimePointListener( tp -> {
								updateFocus(null);
								log.info("detect to a new time point");
								dmd.showSpots(tp,spotsNode,linksNode,colorGenerator);
							} );
					//setup updating of spots when they are created in the current view
					projectModel
							.getModel().getGraph()
							.addGraphChangeListener(()-> {
								log.info("some nodes are added");
								updateFocus(null);
								dmd.showSpots( v.getCurrentTimepoint(),spotsNode,linksNode,null);
							} );

					//setup updating of spots when they are dragged in the BDV
					projectModel
						.getModel().getGraph()
						.addVertexPositionListener( l -> dmd.updateSpotPosition(spotsNode,l) );

					//setup "activating" of a Node in SciView in response
					//to focusing its counterpart Spot in Mastodon
					fRef = projectModel.getModel().getGraph().vertexRef();
					final FocusModel<Spot> focuser = projectModel.getFocusModel();
					focuser.listeners().add(() -> {
						if (focuser.getFocusedVertex(fRef) != null)
						{
							final Node n = dmd.sv.find(fRef.getLabel());
							if (n != null) dmd.sv.setActiveCenteredNode(n);
						}
					});
				}
				else
				{
					//just show spots w/o any additional "services"
					//DISABLED ON 22/04/2021//
					dmd.showSpots( v.getCurrentTimepoint(), spotsNode,linksNode);
				}

				//big black box
				Vector3f bbbP = new Vector3f(spotsNode.getPosition().x,spotsNode.getPosition().y,spotsNode.getPosition().z);
				Vector3f bbbS = new Vector3f(spotsNode.getPosition().x,spotsNode.getPosition().y,spotsNode.getPosition().z);
				bbbS.setComponent(0, bbbS.get(0) *2);
				bbbS.setComponent(1, bbbS.get(1) *2);
				bbbS.setComponent(2, bbbS.get(2) *2);
				final Node box = dmd.sv.addBox(bbbP,bbbS);
				box.setName("BLOCKING BOX");
				box.setVisible(false);

				dmd.sv.getFloor().setVisible(false);
				dmd.sv.centerOnNode(spotsNode);
			}

			//this object cannot be created within the scope of the run() method, otherwise
			//it will be GC'ed after run() is over.. and the listener will never get activated...
			final EventListener notifierOfMastodonWhenSpotIsSelectedInSciView = new EventListener();

			public static final String TRACKING_TAGSET_NAME = "Tracking";
			public static final String TRACKING_APPROVED_TAG_NAME = "Approved";
			public static final String TRACKING_UNLABELED_TAG_NAME = "unlabeled";
			class EventListener extends AbstractContextual
			{
				private TagSetModel< Spot, Link > getTagSetModel()
				{
					return projectModel.getModel().getTagSetModel();
				}

				private TagSetStructure.TagSet getTrackingTagSet()
				{
					/**
					 * The following functions used for srtting up Elephant color system.
					 */
					final TagSetStructure tss = getTagSetModel().getTagSetStructure();
					final TagSetStructure.TagSet tagSet = tss.getTagSets().stream().filter(ts -> ts.getName().equals( TRACKING_TAGSET_NAME ) ).findFirst().orElseGet( () -> {
						final TagSetStructure tssCopy = new TagSetStructure();
						tssCopy.set( tss );
						final TagSetStructure.TagSet tagSetCopy = tssCopy.createTagSet( TRACKING_TAGSET_NAME );
						tagSetCopy.createTag( TRACKING_APPROVED_TAG_NAME, Color.CYAN.getRGB() );
						tagSetCopy.createTag( TRACKING_UNLABELED_TAG_NAME, Color.GREEN.getRGB() );
						getTagSetModel().pauseListeners();
						try
						{
							getTagSetModel().setTagSetStructure( tssCopy );
						}
						finally
						{
							getTagSetModel().resumeListeners();
						}
						return tagSetCopy;
					} );
					return tagSet;
				}
				private TagSetStructure.Tag getTag(final TagSetStructure.TagSet tagSet, final String name )
				{
					return tagSet.getTags().stream().filter( t -> t.label().equals( name ) ).findFirst().orElse( null );
				}
				private ObjTagMap< Spot, TagSetStructure.Tag> getVertexTagMap(final TagSetStructure.TagSet tagSet )
				{
					return getTagSetModel().getVertexTags().tags( tagSet );
				}

				/**
				 * when tag event is triggered in sciview (See VRHeadTrackingExample in sciview), notify the mastondon to change the color and Elephant tag the spots
				 */
				@EventHandler
				public void onEvent(NodeTaggedEvent event) {
					if(event.getNode() == null) return;
					projectModel.getModel().getGraph().vertices()
							.stream()
							.filter(s -> (s.getLabel().equals(event.getNode().getMetadata().get("Label"))))
							.forEach(s ->
							{
								projectModel.getModel().getGraph().getLock().writeLock().lock();
								try
								{
									final ObjTagMap< Spot, TagSetStructure.Tag> spotTagMap = getVertexTagMap( getTrackingTagSet() );
									final TagSetStructure.Tag approvedTag = getTag( getTrackingTagSet(), TRACKING_APPROVED_TAG_NAME );
									spotTagMap.set(s, approvedTag);

									log.info("tag spot: " + s.getInternalPoolIndex());
								}
								finally
								{
									projectModel.getModel().setUndoPoint();
									projectModel.getModel().getGraph().getLock().writeLock().unlock();
									if ( EventQueue.isDispatchThread() )
									{
										projectModel.getModel().getGraph().notifyGraphChanged();
									}
									else
									{
										SwingUtilities.invokeLater( () -> projectModel.getModel().getGraph().notifyGraphChanged() );
									}
								}
							});
				}


				//highlight the spot when sphere is selected in sciview
				@EventHandler
				public void onEvent(NodeActivatedEvent event) {
					if (event.getNode() == null) return;
					projectModel.getSelectionModel().clearSelection();
					projectModel.getModel().getGraph().vertices()
							.stream()
							.filter(s -> (s.getLabel().equals(event.getNode().getMetadata().get("Label"))))
							.forEach(s ->
							{

								//log.info("sciview tells bdv highlight");
								projectModel.getSelectionModel().setSelected(s,true);
								projectModel.getHighlightModel().highlightVertex(s);

							});
				}

				//delete spot from mastodon when sphere if deleted from sciview
				@EventHandler
				public void onEvent(NodeRemovedEvent event) {
					if(event.getNode() == null) return;
					projectModel.getModel().getGraph().vertices()
							.stream()
							.filter(s -> (s.getLabel().equals(event.getNode().getMetadata().get("Label"))))
							.forEach(s ->
							{
								lock = projectModel.getModel().getGraph().getLock();
								lock.writeLock().lock();
								try{
									projectModel.getModel().getGraph().remove(s);
								}
								finally {
									lock.writeLock().unlock();
								}
								graph.notifyGraphChanged();
							});
				}


				//add spots when there are tracks generated in sciview
				@EventHandler
				public void onEvent(NodeAddedEvent event) {
					final Node n = event.getNode();

					if (n == null) return;

					if (n.getName().startsWith("Track-") )
					{
							log.info("add track" + n.getName());
							final Spot previousSpot = graph.vertexRef();
							final Spot spot = graph.vertexRef();
							final Link link = graph.edgeRef();
						    final int[] flag = {0};

							n.getChildren().stream().filter(t -> (t.getMetadata().get("Type") == "node")).forEach( node -> {
								final Vector3f pos = (Vector3f) node.getMetadata().get("NodePosition");
								final int tp = (int) node.getMetadata().get("NodeTimepoint");

								lock = projectModel.getModel().getGraph().getLock();
								lock.writeLock().lock();

								try {
									graph.addVertex(spot).init(tp,
											new double[]{pos.x, pos.y, pos.z},
											new double[][]{
													{210, 100, 0},
													{100, 110, 10},
													{0, 10, 100}
											});
									log.info("create spot with label " + spot.getLabel());
									node.getMetadata().put("Label", spot.getLabel());
									if (flag[0] != 0) {
										log.info("add edge from " + previousSpot.getLabel() + " to " + spot.getLabel());
										graph.addEdge(previousSpot, spot, link).init();
									}
									else
									{
										flag[0] = 1;
										log.info("previousSpot is still null");
									}
									previousSpot.refTo(spot);
								} finally {
									lock.writeLock().unlock();

								}
							});

						graph.notifyGraphChanged();
						graph.releaseRef( previousSpot );
						graph.releaseRef( spot );
						graph.releaseRef( link );
					}
				}

				//change location of spots and color of volume in Mastodon when spheres and volume is changed in sciview
				@EventHandler
				public void onEvent(NodeChangedEvent event) {
					if (event.getNode() == null) return;
					if (event.getNode().getName().equals("volume"))
					{
						if(!dmd.synChoiceParams.synColor||!dmd.synChoiceParams.synDisRange)
							return;

						log.info("mastodon raw data 's change");

						Volume volume = (Volume) event.getNode();

						log.info(volume.getColormap());
						Colormap cp = volume.getColormap();

						final ConverterSetups setups = projectModel.getSharedBdvData().getConverterSetups();
						final ArrayList<SourceAndConverter<?>> sacs = projectModel.getSharedBdvData().getSources();
						for(SourceAndConverter sac:sacs){
							Vector4f col = cp.sample(0.9f);
//							log.info(col);
							col = col.mul(256f,256f,256f,256f);
							log.info("before"+setups.getConverterSetup(sac).getColor());
							setups.getConverterSetup(sac).setColor(new ARGBType(ARGBType.rgba((float)col.x,(float)col.y,(float)col.z,(float)col.w)));
							log.info("after"+setups.getConverterSetup(sac).getColor());
						}
						projectModel.getWindowManager().forEachWindow(
								view -> {
									view.repaint();
								});
						return;
					}
					projectModel.getModel().getGraph().vertices()
							.stream()
							.filter(s -> (s.getLabel().equals(event.getNode().getMetadata().get("Label"))))
							.forEach(s ->
							{
								if(!dmd.synChoiceParams.synSpotLoc)
									return;

								log.info(event.getNode().spatialOrNull().getPosition());
								Vector3f realPosition = event.getNode().spatialOrNull().getPosition();
								float[] pos = new float[3];
								pos[0] = realPosition.x;
								pos[1] = realPosition.y;
								pos[2] = realPosition.z;

								s.setPosition(pos);

							});
				}
			}
		}.start();
	}

	//coloring attributes
	private GraphColorGenerator<Spot, Link> colorGenerator = null;
	private void setColorGeneratorFrom(final MamutViewTrackScheme tsWin)
	{
		colorGenerator = tsWin.getColoringModel().getFeatureGraphColorGenerator();
	}


	//focus attributes
	private Node stillFocusedNode = null;
	private Spot sRef,fRef;

	private void updateFocus(final Node newFocusNode)
	{
		/* DEBUG
		log.info("defocus: "+(stillFocusedNode != null ? stillFocusedNode.getName() : "NONE")
			+", focus newly: "+(newFocusNode != null ? newFocusNode.getName() : "NONE"));
		*/

		//something to defocus?
		if (stillFocusedNode != null && stillFocusedNode.getParent() != null)
		{
			stillFocusedNode.getScale().mul(0.66666f);
			stillFocusedNode.setNeedsUpdate(true);
		}

		//something to focus?
		stillFocusedNode = newFocusNode;
		if (stillFocusedNode != null)
		{
			stillFocusedNode.getScale().mul(1.50f);
			stillFocusedNode.setNeedsUpdate(true);
		}
	}

	public static void main(String[] args)
	{
		try {
			Locale.setDefault( Locale.US );
			UIManager.setLookAndFeel( UIManager.getSystemLookAndFeelClassName() );

			//start up our own Fiji/Imagej2
			final ImageJ ij = new ImageJ();
			ij.ui().showUI();

			final Mastodon mastodon = (Mastodon)ij.command().run(Mastodon.class, true).get().getCommand();

			mastodon.setExitOnClose();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (UnsupportedLookAndFeelException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
	}
}
