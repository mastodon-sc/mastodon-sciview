package org.mastodon.tomancak;

import net.imagej.ImageJ;

public class clearPrefs
{
	public static void main(String[] args)
	{
		final ImageJ IJ = new ImageJ();
		/*
		IJ.prefs().clear(LoadEarlierProgress.class);
		IJ.prefs().clear(ReportProgress.class);
		IJ.prefs().clear(MergeModelDialog.class);
		*/
		System.out.println("RACING plugins' preferences cleared");
	}
}
