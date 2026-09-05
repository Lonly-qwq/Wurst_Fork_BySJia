/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util.chunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.dimension.DimensionType;
import net.wurstclient.WurstClient;
import net.wurstclient.events.ChunkUpdateListener;
import net.wurstclient.settings.ChunkAreaSetting;

public abstract class AbstractChunkCoordinator implements ChunkUpdateListener
{
	protected final ConcurrentHashMap<ChunkPos, ChunkSearcher> searchers =
		new ConcurrentHashMap<>();
	protected final ChunkAreaSetting area;
	private BiPredicate<BlockPos, BlockState> query;
	private Predicate<BlockState> sectionPredicate;
	private boolean removedChunks;
	
	protected final Set<ChunkPos> chunksToUpdate =
		Collections.synchronizedSet(new HashSet<>());
	
	public AbstractChunkCoordinator(BiPredicate<BlockPos, BlockState> query,
		ChunkAreaSetting area)
	{
		this.query = Objects.requireNonNull(query);
		this.area = Objects.requireNonNull(area);
	}
	
	public boolean update()
	{
		DimensionType dimension = WurstClient.MC.level.dimensionType();
		HashSet<ChunkPos> chunkUpdates = clearChunksToUpdate();
		boolean searchersChanged = false;
		removedChunks = false;
		ArrayList<ChunkAccess> chunks = area.getChunksInRange();
		HashMap<ChunkPos, ChunkAccess> loadedChunks = new HashMap<>();
		for(ChunkAccess chunk : chunks)
			loadedChunks.put(chunk.getPos(), chunk);
		
		// remove outdated ChunkSearchers
		for(ChunkSearcher searcher : new ArrayList<>(searchers.values()))
		{
			boolean remove = false;
			ChunkPos searcherPos = searcher.getPos();
			
			// Unloaded, replaced, or from a previous world.
			if(!searcher.isForChunk(loadedChunks.get(searcherPos)))
			{
				remove = true;
				removedChunks = true;
			}
			// wrong dimension
			else if(dimension != searcher.getDimension())
				remove = true;
			
			// out of range
			else if(!area.isInRange(searcherPos))
				remove = true;
			
			// chunk update
			else if(chunkUpdates.contains(searcherPos))
				remove = true;
			
			if(remove)
			{
				searchers.remove(searcherPos);
				searcher.cancel();
				onRemove(searcher);
				searchersChanged = true;
			}
		}
		
		ChunkPos center = WurstClient.MC.player.chunkPosition();
		chunks.sort(Comparator.comparingInt(
			chunk -> ChunkUtils.getManhattanDistance(center, chunk.getPos())));
		
		// add new ChunkSearchers
		for(ChunkAccess chunk : chunks)
		{
			ChunkPos chunkPos = chunk.getPos();
			if(searchers.containsKey(chunkPos))
				continue;
			
			ChunkSearcher searcher =
				new ChunkSearcher(query, chunk, dimension, sectionPredicate);
			searcher.start();
			searchers.put(chunkPos, searcher);
			searchersChanged = true;
		}
		
		return searchersChanged;
	}
	
	public boolean hasRemovedChunks()
	{
		return removedChunks;
	}
	
	protected void onRemove(ChunkSearcher searcher)
	{
		// Overridden in ChunkVertexBufferCoordinator
	}
	
	@Override
	public void onChunkUpdated(ChunkPos chunkPos)
	{
		chunksToUpdate.add(chunkPos);
	}
	
	public List<ChunkSearcher> getSearchersSnapshot()
	{
		return List.copyOf(searchers.values());
	}
	
	public int getCompletedSearcherCount()
	{
		int completed = 0;
		for(ChunkSearcher searcher : searchers.values())
			if(searcher.isDone())
				completed++;
			
		return completed;
	}
	
	public void reset()
	{
		searchers.values().forEach(ChunkSearcher::cancel);
		searchers.clear();
		chunksToUpdate.clear();
	}
	
	public boolean isDone()
	{
		return searchers.values().stream().allMatch(ChunkSearcher::isDone);
	}
	
	public void setQuery(BiPredicate<BlockPos, BlockState> query)
	{
		this.query = Objects.requireNonNull(query);
		sectionPredicate = null;
		searchers.values().forEach(ChunkSearcher::cancel);
		searchers.clear();
	}
	
	public void setTargetBlock(Block block)
	{
		setQuery((pos, state) -> block == state.getBlock());
		sectionPredicate = state -> block == state.getBlock();
	}
	
	public void setTargetBlocks(Set<Block> blocks)
	{
		Set<Block> snapshot = Set.copyOf(blocks);
		setQuery((pos, state) -> snapshot.contains(state.getBlock()));
		sectionPredicate = state -> snapshot.contains(state.getBlock());
	}
	
	protected HashSet<ChunkPos> clearChunksToUpdate()
	{
		synchronized(chunksToUpdate)
		{
			HashSet<ChunkPos> chunks = new HashSet<>(chunksToUpdate);
			chunksToUpdate.clear();
			return chunks;
		}
	}
}
