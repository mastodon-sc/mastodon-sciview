package org.mastodon.tomancak;

import net.imglib2.type.numeric.ARGBType;
import net.imglib2.display.ColorTable8;
import graphics.scenery.volumes.Colormap;

import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;


/** The only purpose of this aux/util class is to avoid re-creating
    of new color palettes in DisplayMastodonData.restoreVolumeColor() */
public class CachedColorTables
{
	public
	CachedColorTables()
	{
		this(10);
	}

	public
	CachedColorTables(final int maxNumberOfStoredPalettes)
	{
		this.maxNumberOfStoredPalettes = maxNumberOfStoredPalettes;
	}

	private
	final int maxNumberOfStoredPalettes;

	//the cache itself
	private
	final Map<Integer,Colormap> colorMaps = new HashMap<>();

	//"orders" the cache items on when they were accessed the last
	private
	final Deque<Integer> colorRecency = new LinkedList<>();


	public
	Colormap getColormap(final int rgba)
	{
		//try to read out from the cache
		final int rgb = rgba & 0x00FFFFFF;
		Colormap c = colorMaps.get(rgb);

		//absent in the cache?
		if (c == null)
		{
			int r = ARGBType.red( rgba );
			int g = ARGBType.green( rgba );
			int b = ARGBType.blue( rgba );
			//int a = ARGBType.alpha( rgba );
			//System.out.println("setVolumeCOlor to "+r+","+g+","+b+","+a);
			c = Colormap.fromColorTable(new ColorTable8( createMapArray(r,g,b) ));

			//place into the cache
			if (colorMaps.size() == maxNumberOfStoredPalettes)
			{
				//...but the cache is full, we have to remove the last recently used item
				colorMaps.remove( colorRecency.removeFirst() );
			}
			colorMaps.put(rgb,c);
		}

		//update recency of this rgb (that is, add/move to the last used position)
		if (colorRecency.contains(rgb))
		{
			//the "move" case, but "move" is to "delete (now) & add (later)"
			colorRecency.removeIf( i -> i == rgb );
		}
		colorRecency.addLast(rgb);
		//debugCacheContent();

		return c;
	}

	private static
	byte[][] createMapArray(int r, int g, int b)
	{
		final byte[][] map = new byte[3][256];
		for (int i = 0; i < 256; ++i)
		{
			float ratio = (float)i / 256f;
			map[0][i] = (byte)(ratio * (float)r);
			map[1][i] = (byte)(ratio * (float)g);
			map[2][i] = (byte)(ratio * (float)b);
		}
		return map;
	}

	private
	void debugCacheContent()
	{
		System.out.println("Current colormap content:");
		System.out.println("Content: "+colorMaps.keySet());
		System.out.println("Order  : "+colorRecency);
	}
}
