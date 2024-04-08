package org.mastodon.tomancak.dialogs;

import java.awt.*;
import java.util.LinkedList;
import java.util.List;
import javax.swing.*;

import graphics.scenery.volumes.Volume;
import kotlin.jvm.Volatile;
import org.mastodon.mamut.ProjectModel;
import org.mastodon.model.HighlightModel;
import org.scijava.command.*;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.mastodon.grouping.GroupHandle;
import org.mastodon.app.ui.GroupLocksPanel;
import org.mastodon.mamut.model.Link;
import org.mastodon.mamut.model.Spot;
import sc.iview.SciView;
import sc.iview.ui.CustomPropertyUI;


@Plugin(type = Command.class, name = "Synchronize Choice Dialog")
public class SynchronizeChoiceDialog extends InteractiveCommand{

    public static class ParamsWrapper {
        public boolean synColor = true;
        public boolean synDisRange = true;

        public boolean synTimestamp = true;
        public boolean synSpotLoc = true;
    }

    @Parameter(persist = false)
    private ProjectModel projectModel;

    @Parameter(persist = false)
    private Volume volume;

    @Parameter(persist = false)
    private SciView sciView;

    @Volatile @Parameter
    private ParamsWrapper params;

    @Volatile @Parameter(label = "synchronize the color:", style = "group:synchronization")
    boolean synColor = true;

    @Volatile @Parameter(label = "synchronize the display range:",style = "group:synchronization")
    boolean synDisRange = true;

    @Volatile @Parameter(label = "synchronize the timestamp:",style = "group:synchronization")
    boolean synTimestamp = true;

    @Volatile @Parameter(label = "synchronize the location of spot:",style = "group:synchronization")
    boolean synSpotLoc = true;

    private GroupHandle myGroupHandle = null;

    @Override
    public
    void preview()
    {
//        System.out.println("preview is called");
        params.synColor=this.synColor;
        params.synDisRange=this.synDisRange;
        params.synTimestamp=this.synTimestamp;
        params.synSpotLoc=this.synSpotLoc;
    }

    private
    void updateVolumeSetting()
    {
        List<String> list =new LinkedList<>();
        list.add("synColor");
        list.add("synDisRange");
        list.add("synTimestamp");
        list.add("synSpotLoc");
        //although this function integrates this window to sciview, it actually should not appear as an independent window at beginning at all.
        sciView.attachCustomPropertyUIToNode(volume,new CustomPropertyUI(this,list));
    }

    @Override
    public
    void run()
    {
//      Create a JFrame to put buttons for mastodon group handle. this should be improved by integrating it into sciview main window
        final JFrame pbframe = new JFrame("locate the selected points(sciview) in Mastodon");
        pbframe.setLayout(new BoxLayout(pbframe.getContentPane(), BoxLayout.Y_AXIS));
        myGroupHandle = projectModel.getGroupManager().createGroupHandle();
        pbframe.add( new GroupLocksPanel( myGroupHandle ) );
        pbframe.setMinimumSize(new Dimension(300, 180));
        pbframe.pack();
        pbframe.setLocationByPlatform(true);
        pbframe.setVisible(true);

        Spot sRef = projectModel.getModel().getGraph().vertexRef();
        final HighlightModel<Spot, Link> highlighter = projectModel.getHighlightModel();
        highlighter.listeners().add( () -> {
            if (highlighter.getHighlightedVertex(sRef) != null)
            {
                myGroupHandle.getModel(projectModel.NAVIGATION).notifyNavigateToVertex(sRef);
            }
        });

        updateVolumeSetting();
    }


}


