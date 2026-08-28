/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.events;

import java.util.ArrayList;

import net.minecraft.util.math.ChunkPos;
import net.wurstclient.event.Event;
import net.wurstclient.event.Listener;

public interface ChunkUpdateListener extends Listener
{
	public void onChunkUpdated(ChunkPos chunkPos);
	
	public static class ChunkUpdateEvent extends Event<ChunkUpdateListener>
	{
		private final ChunkPos chunkPos;
		
		public ChunkUpdateEvent(ChunkPos chunkPos)
		{
			this.chunkPos = chunkPos;
		}
		
		@Override
		public void fire(ArrayList<ChunkUpdateListener> listeners)
		{
			for(ChunkUpdateListener listener : listeners)
				listener.onChunkUpdated(chunkPos);
		}
		
		@Override
		public Class<ChunkUpdateListener> getListenerType()
		{
			return ChunkUpdateListener.class;
		}
	}
}
