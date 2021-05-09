package org.mastodon.tomancak.dialogs;


import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.command.InteractiveCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.NumberWidget;

import graphics.scenery.Node;
import org.mastodon.tomancak.DisplayMastodonData;

@Plugin(type = Command.class, name = "Synchronize Choice Dialog")
public class SynchronizeChoiceDialog extends DynamicCommand {

    public static class ParamsWrapper {
        public boolean synColor = true;
        public boolean synDisRange = true;

        public boolean synTimestamp = true;
        public boolean synSpotLoc = true;

    }

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

    @Override
    public
    void preview()
    {
        params.synColor=this.synColor;
        params.synDisRange=this.synDisRange;
        params.synTimestamp=this.synTimestamp;
        params.synSpotLoc=this.synSpotLoc;

    }

}


