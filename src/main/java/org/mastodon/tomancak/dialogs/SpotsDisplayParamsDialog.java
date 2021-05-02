package org.mastodon.tomancak.dialogs;

import org.scijava.command.Command;
import org.scijava.command.InteractiveCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.NumberWidget;

import graphics.scenery.Node;
import org.mastodon.tomancak.DisplayMastodonData;

@Plugin(type = Command.class, name = "Spots Display Parameters Dialog")
public class SpotsDisplayParamsDialog extends InteractiveCommand
{
	public static class ParamsWrapper {
		public float spotSize = 1.0f;
		public float spotAlpha = 1.0f;

		public float linkSize = 1.0f;
		public float linkAlpha = 1.0f;
		public int link_TPsInPast = 0;
		public int link_TPsAhead = 0;
	}

	/** the object with parameters shared between this dialog and its caller */
	@Parameter
	private ParamsWrapper params;

	@Parameter
	private Node spotsGatheringNode;

	@Parameter
	private Node linksGatheringNode;


	@Parameter(label = "Spots size", style = NumberWidget.SLIDER_STYLE,
	           min = "0.1", max = "10.0", stepSize = "0.05", callback = "adjustSpotSize")
	private float spotSize = 1.0f;

	@Parameter(label = "Spots alpha", style = NumberWidget.SLIDER_STYLE,
	           min = "0.1", max = "1.0", stepSize = "0.1", callback = "adjustSpotAlpha")
	private float spotAlpha = 1.0f;

	private
	void adjustSpotSize()
	{
		//tell back to our caller about the new value of this attribute
		params.spotSize = spotSize;

		for (Node s : spotsGatheringNode.getChildren())
			s.getScale().set(spotSize,spotSize,spotSize);
		spotsGatheringNode.updateWorld(true,true);
	}

	private
	void adjustSpotAlpha()
	{
		params.spotAlpha = spotAlpha;

		for (Node s : spotsGatheringNode.getChildren())
			s.getMaterial().getBlending().setOpacity(spotAlpha);
		spotsGatheringNode.updateWorld(true,true);
	}


	@Parameter(label = "Links size", style = NumberWidget.SLIDER_STYLE,
	           min = "0.01", max = "10.0", stepSize = "0.01", callback = "adjustLinkSize")
	private float linkSize = 1.0f;

	@Parameter(label = "Links alpha", style = NumberWidget.SLIDER_STYLE,
	           min = "0.1", max = "1.0", stepSize = "0.1", callback = "adjustLinkAlpha")
	private float linkAlpha = 1.0f;

	private
	void adjustLinkSize()
	{
		//tell back to our caller about the new value of this attribute
		params.linkSize = linkSize;

		for (Node links : linksGatheringNode.getChildren()) //over tracks
			for (Node c : links.getChildren())              //over links of a track
				c.getScale().set(linkSize,1,linkSize);
		linksGatheringNode.updateWorld(true,true);
	}

	private
	void adjustLinkAlpha()
	{
		params.linkAlpha = linkAlpha;

		for (Node s : linksGatheringNode.getChildren())
			for (Node c : s.getChildren())
				c.getMaterial().getBlending().setOpacity(linkAlpha);
		linksGatheringNode.updateWorld(true,true);
	}


	@Parameter(label = "Show past links", style = NumberWidget.SLIDER_STYLE,
	           min = "0", max = "100", stepSize = "1", callback = "adjustLinkCounts")
	private int link_TPsInPast = 0;

	@Parameter(label = "Show future links", style = NumberWidget.SLIDER_STYLE,
	           min = "0", max = "100", stepSize = "1", callback = "adjustLinkCounts")
	private int link_TPsAhead = 0;

	private
	void adjustLinkCounts()
	{
		params.link_TPsInPast = link_TPsInPast;
		params.link_TPsAhead  = link_TPsAhead;

		for (Node s : spotsGatheringNode.getChildren())
			((DisplayMastodonData.SphereWithLinks)s).updateLinks(link_TPsInPast,link_TPsAhead);
	}

	/* a hack to promote this dialog's values into the common shared object RIGHT AFTER the
	   dialog is initiated from the prefs store, which (luckily for this story) happens when
	   the dialog is created/opened (and also with every param change) -- when Sciview starts
	   up, the vizu settings continues to be the same as it was when Sciview was run last */
	@Override
	public
	void preview()
	{
		if (!wasSharedParamsObjInitiatedFromThisDialog)
		{
			params.spotSize       = this.spotSize;
			params.spotAlpha      = this.spotAlpha;
			params.linkSize       = this.linkSize;
			params.linkAlpha      = this.linkAlpha;
			params.link_TPsInPast = this.link_TPsInPast;
			params.link_TPsAhead  = this.link_TPsAhead;
			wasSharedParamsObjInitiatedFromThisDialog = true;
		}
	}
	private boolean wasSharedParamsObjInitiatedFromThisDialog = false;
}