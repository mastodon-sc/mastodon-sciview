package org.mastodon.tomancak;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

import javax.swing.UIManager;
import javax.swing.WindowConstants;

import org.mastodon.mamut.MainWindow;
import org.mastodon.mamut.WindowManager;
import org.mastodon.mamut.project.MamutProject;
import org.mastodon.mamut.project.MamutProjectIO;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Plugin;

import mpicbg.spim.data.SpimDataException;
@Plugin( type = Command.class, menuPath = "Plugins>Mastodon (preview)" )
public class Mastodon extends ContextCommand
{
    private WindowManager windowManager;

    private MainWindow mainWindow;

    @Override
    public void run()
    {
        System.setProperty( "apple.laf.useScreenMenuBar", "true" );
        windowManager = new WindowManager( getContext() );
        mainWindow = new MainWindow( windowManager );
        mainWindow.setVisible( true );
    }

    // FOR TESTING ONLY!
    public void openProject( final MamutProject project ) throws IOException, SpimDataException
    {
        windowManager.getProjectManager().open( project );
    }

    // FOR TESTING ONLY!
    public void setExitOnClose()
    {
        mainWindow.setDefaultCloseOperation( WindowConstants.EXIT_ON_CLOSE );
    }

    // FOR TESTING ONLY!
    public WindowManager getWindowManager()
    {
        return windowManager;
    }
}
