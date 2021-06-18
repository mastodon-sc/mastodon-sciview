package org.mastodon.tomancak.dialogs;

import java.awt.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import javax.swing.*;

import graphics.scenery.volumes.Volume;
import org.mastodon.model.HighlightModel;
import org.scijava.command.*;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.mastodon.grouping.GroupHandle;
import org.mastodon.app.ui.GroupLocksPanel;
import org.mastodon.mamut.model.Link;
import org.mastodon.mamut.model.Spot;
import org.mastodon.mamut.plugin.MamutPluginAppModel;


@Plugin(type = Command.class, name = "Synchronize Choice Dialog")
public class SynchronizeChoiceDialog implements Command {

    public static class ParamsWrapper {
        public boolean synColor = true;
        public boolean synDisRange = true;

        public boolean synTimestamp = true;
        public boolean synSpotLoc = true;

    }


    @Parameter(persist = false)
    private MamutPluginAppModel mamutPluginAppModel;

    @Parameter(persist = false)
    private Volume volume;

    @Parameter
    private ParamsWrapper params;

    @Parameter(label = "synchronize the color:",style = "group:synchronization")
    boolean synColor = true;

    @Parameter(label = "synchronize the display range:",style = "group:synchronization")
    boolean synDisRange = true;

    @Parameter(label = "synchronize the timestamp:",style = "group:synchronization")
    boolean synTimestamp = true;

    @Parameter(label = "synchronize the location of spot:",style = "group:synchronization")
    boolean synSpotLoc = true;

    @Parameter
    private CommandService commandService;

    private GroupHandle myGroupHandle = null;

//    @Override
//    public
//    void preview()
//    {
//        params.synColor=this.synColor;
//        params.synDisRange=this.synDisRange;
//        params.synTimestamp=this.synTimestamp;
//        params.synSpotLoc=this.synSpotLoc;
//
//    }


    private
    void updateVolumeSetting()
    {
        CommandInfo commandInfo = commandService.getCommand(getClass());
        System.out.println("commandInfo: "+ commandInfo);

        System.out.println("initial:" + volume.getMetadata().get("sciview-inspector"));
        List<Object> list =new LinkedList<>();
        list.add(commandInfo.getInput("synColor"));
        list.add(commandInfo.getInput("synDisRange"));
        list.add(commandInfo.getInput("synTimestamp"));
        list.add(commandInfo.getInput("synSpotLoc"));

        HashMap<String, Object> hm = new HashMap<>();
        List<Object> listAll =new LinkedList<>();
        listAll.addAll(list);
        if(volume.getMetadata().get("sciview-inspector") != null)
        {
            listAll.addAll((List<Object>)(volume.getMetadata().get("sciview-inspector")));
        }
        hm.put("sciview-inspector", listAll);
        volume.setMetadata(hm);
        System.out.println("set:"+ volume.getMetadata());
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
                myGroupHandle.getModel(mamutPluginAppModel.getAppModel().NAVIGATION).notifyNavigateToVertex(sRef);
            }
        });

        updateVolumeSetting();
    }


}


