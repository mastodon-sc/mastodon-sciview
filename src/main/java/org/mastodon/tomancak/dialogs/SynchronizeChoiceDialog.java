package org.mastodon.tomancak.dialogs;

import java.awt.*;
import java.util.concurrent.TimeUnit;
import javax.swing.*;

import org.mastodon.collection.RefCollections;
import org.mastodon.collection.RefList;
import org.mastodon.collection.RefSet;
import org.mastodon.model.HighlightModel;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.command.InteractiveCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.NumberWidget;
import org.mastodon.grouping.GroupHandle;
import org.mastodon.app.ui.GroupLocksPanel;
import org.mastodon.mamut.model.Link;
import org.mastodon.mamut.model.Spot;
import graphics.scenery.Node;
import org.mastodon.tomancak.DisplayMastodonData;
import org.mastodon.mamut.MamutAppModel;
import org.mastodon.mamut.plugin.MamutPluginAppModel;


@Plugin(type = Command.class, name = "Synchronize Choice Dialog")
public class SynchronizeChoiceDialog extends InteractiveCommand {

    public static class ParamsWrapper {
        public boolean synColor = true;
        public boolean synDisRange = true;

        public boolean synTimestamp = true;
        public boolean synSpotLoc = true;

    }

    @Parameter(persist = false)
    private MamutPluginAppModel mamutPluginAppModel;

    @Parameter
    private ParamsWrapper params;

    @Parameter(label = "synchronize the color:")
    boolean synColor = true;

    @Parameter(label = "synchronize the display range:")
    boolean synDisRange = true;

    @Parameter(label = "synchronize the timestamp:")
    boolean synTimestamp = true;

    @Parameter(label = "synchronize the location of spot:")
    boolean synSpotLoc = true;

    private GroupHandle myGroupHandle = null;
    @Override
    public
    void preview()
    {
        params.synColor=this.synColor;
        params.synDisRange=this.synDisRange;
        params.synTimestamp=this.synTimestamp;
        params.synSpotLoc=this.synSpotLoc;

    }

    @Override
    public
    void run()
    {
        final JFrame pbframe = new JFrame("locate the selected points(sciview) in Mastodon");
        pbframe.setLayout(new BoxLayout(pbframe.getContentPane(), BoxLayout.Y_AXIS));


        myGroupHandle = mamutPluginAppModel.getAppModel().getGroupManager().createGroupHandle();
        pbframe.add( new GroupLocksPanel( myGroupHandle ) );
        pbframe.setMinimumSize(new Dimension(300, 180));
        pbframe.pack();
        pbframe.setLocationByPlatform(true);
        pbframe.setVisible(true);

        Spot sRef = mamutPluginAppModel.getAppModel().getModel().getGraph().vertexRef();
        final HighlightModel<Spot, Link> highlighter = mamutPluginAppModel.getAppModel().getHighlightModel();
        highlighter.listeners().add( () -> {
            if (highlighter.getHighlightedVertex(sRef) != null)
            {
                System.out.println("there is a highlighted spot 222");
                myGroupHandle.getModel(mamutPluginAppModel.getAppModel().NAVIGATION).notifyNavigateToVertex(sRef);
            }
        });
    }


}


