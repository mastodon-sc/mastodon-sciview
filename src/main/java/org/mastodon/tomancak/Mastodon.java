package org.mastodon.tomancak;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

import javax.swing.UIManager;
import javax.swing.WindowConstants;

import org.mastodon.mamut.MainWindow;
import org.mastodon.mamut.ProjectModel;
import org.mastodon.mamut.WindowManager;
import org.mastodon.mamut.io.ProjectLoader;
import org.mastodon.mamut.io.project.MamutProject;
import org.mastodon.mamut.io.project.MamutProjectIO;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Plugin;

import mpicbg.spim.data.SpimDataException;
@Plugin( type = Command.class, menuPath = "Plugins>Mastodon (preview)" )
public class Mastodon extends ContextCommand
{
    private WindowManager windowManager;
    ProjectModel appModel;
    private MainWindow mainWindow;

    @Override
    public void run()
    {
        System.setProperty( "apple.laf.useScreenMenuBar", "true" );

        try {
            appModel = ProjectLoader.open( "C:/Software/datasets/MastodonTutorialDataset1/datasethdf5.mastodon", getContext(), true, false );
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (SpimDataException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Sources: " + appModel.getSharedBdvData().getSources() + " , num TPs: " + appModel.getSharedBdvData().getNumTimepoints());
        //windowManager = new WindowManager( getContext() );
        mainWindow = new MainWindow( appModel );
        mainWindow.setVisible( true );
    }

    // FOR TESTING ONLY!
    public void openProject( final MamutProject project ) throws IOException, SpimDataException
    {
        appModel = ProjectLoader.open(project, getContext());
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
