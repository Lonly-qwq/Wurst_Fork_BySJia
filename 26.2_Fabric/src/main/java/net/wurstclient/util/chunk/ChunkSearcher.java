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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.dimension.DimensionType;
import net.wurstclient.util.MinPriorityThreadFactory;

/**
 * Searches the given {@link ChunkAccess} for blocks matching the given query.
 */
public final class ChunkSearcher
{
	private static final ExecutorService BACKGROUND_THREAD_POOL =
		MinPriorityThreadFactory.newFixedThreadPool();
	
	private final BiPredicate<BlockPos, BlockState> query;
	private final Predicate<BlockState> sectionPredicate;
	private final ChunkAccess chunk;
	private final DimensionType dimension;
	
	private CompletableFuture<ArrayList<Result>> future;
	private boolean interrupted;
	
	public ChunkSearcher(BiPredicate<BlockPos, BlockState> query,
		ChunkAccess chunk, DimensionType dimension)
	{
		this(query, chunk, dimension, null);
	}
	
	public ChunkSearcher(BiPredicate<BlockPos, BlockState> query,
		ChunkAccess chunk, DimensionType dimension,
		Predicate<BlockState> sectionPredicate)
	{
		this.query = query;
		this.chunk = chunk;
		this.dimension = dimension;
		this.sectionPredicate = sectionPredicate;
	}
	
	public void start()
	{
		if(future != null || interrupted)
			throw new IllegalStateException();
		
		future = CompletableFuture.supplyAsync(this::searchNow,
			BACKGROUND_THREAD_POOL);
	}
	
	private ArrayList<Result> searchNow()
	{
		ArrayList<Result> results = new ArrayList<>();
		if(sectionPredicate != null)
		{
			try
			{
				chunk.findBlocks(sectionPredicate, (pos, state) -> {
					if(interrupted || Thread.currentThread().isInterrupted())
						throw new SearchCancelledException();
					if(query.test(pos, state))
						results.add(new Result(pos.immutable(), state));
				});
			}catch(SearchCancelledException e)
			{
				// Return the results found before cancellation.
			}
			return results;
		}
		
		ChunkPos chunkPos = chunk.getPos();
		
		int minX = chunkPos.getMinBlockX();
		int minY = chunk.getMinY();
		int minZ = chunkPos.getMinBlockZ();
		int maxX = chunkPos.getMaxBlockX();
		int maxY = ChunkUtils.getHighestNonEmptySectionYOffset(chunk) + 16;
		int maxZ = chunkPos.getMaxBlockZ();
		
		for(int x = minX; x <= maxX; x++)
			for(int y = minY; y <= maxY; y++)
				for(int z = minZ; z <= maxZ; z++)
				{
					if(interrupted || Thread.currentThread().isInterrupted())
						return results;
					
					BlockPos pos = new BlockPos(x, y, z);
					BlockState state = chunk.getBlockState(pos);
					if(!query.test(pos, state))
						continue;
					
					results.add(new Result(pos, state));
				}
			
		return results;
	}
	
	public void cancel()
	{
		if(future == null || future.isDone())
			return;
		
		interrupted = true;
		future.cancel(true);
	}
	
	public boolean isInterrupted()
	{
		return interrupted;
	}
	
	public ChunkPos getPos()
	{
		return chunk.getPos();
	}
	
	public DimensionType getDimension()
	{
		return dimension;
	}
	
	public Stream<Result> getMatches()
	{
		if(future == null || future.isCancelled())
			return Stream.empty();
		
		return future.join().stream();
	}
	
	public List<Result> getMatchesList()
	{
		if(future == null || future.isCancelled())
			return List.of();
		
		return Collections.unmodifiableList(future.join());
	}
	
	public boolean isDone()
	{
		return future != null && future.isDone();
	}
	
	public record Result(BlockPos pos, BlockState state)
	{}
	
	private static final class SearchCancelledException extends RuntimeException
	{}
}
