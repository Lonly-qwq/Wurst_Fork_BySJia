/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.feature.BlockModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.WurstRenderLayers;
import net.wurstclient.events.ChunkUpdateListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.BlockListSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ChunkAreaSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.BlockVertexCompiler;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.EasyVertexBuffer;
import net.wurstclient.util.RegionPos;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.RotationUtils;
import net.wurstclient.util.chunk.ChunkSearcher;
import net.wurstclient.util.chunk.ChunkSearcherCoordinator;

@SearchTags({"BlockESP", "block esp"})
public final class SearchHack extends Hack
	implements UpdateListener, RenderListener
{
	private static final int EXPOSED_CHECKS_PER_TICK = 1024;
	private static final int BALANCED_SHADER_LIGHT_FLOOR = 12;
	
	private final BlockListSetting blocks = new BlockListSetting("Blocks",
		"The blocks to search for. You can select multiple blocks.",
		"minecraft:diamond_ore");
	private ArrayList<String> lastBlocks;
	private Set<Block> targetBlocks = Set.of();
	private final Map<BlockPos, TextureModel> textureModels =
		new LinkedHashMap<>(256, 0.75F, true)
		{
			@Override
			protected boolean removeEldestEntry(
				Map.Entry<BlockPos, TextureModel> eldest)
			{
				return size() > 16384;
			}
		};
	private final SliderSetting textureDistance =
		new SliderSetting("Texture render distance",
			"Maximum distance for Search block textures.", 128, 16, 512, 16,
			ValueDisplay.INTEGER.withSuffix(" blocks"));
	
	private final CheckboxSetting showTextures = new CheckboxSetting(
		"Show block textures",
		"Shows matching blocks with their original textures without making other"
			+ " blocks transparent.",
		false);
	private boolean prevShowTextures;
	
	private final EnumSetting<TextureBrightness> textureBrightness =
		new EnumSetting<>("Texture brightness",
			"Balanced preserves environmental lighting while preventing Search"
				+ " textures from becoming too dark.\n"
				+ "Fullbright uses X-Ray gamma in vanilla and an Iris fullbright"
				+ " pipeline with adjustable exposure in shader packs.",
			TextureBrightness.values(), TextureBrightness.FULLBRIGHT);
	
	private final SliderSetting fullbrightExposure =
		new SliderSetting("Fullbright exposure",
			"Controls Search texture brightness in shader packs when Texture"
				+ " brightness is set to Fullbright.",
			0.7, 0.4, 1, 0.05, ValueDisplay.PERCENTAGE);
	
	private final CheckboxSetting onlyExposed =
		new CheckboxSetting("Only show exposed",
			"Only shows blocks with at least one side exposed to a non-opaque"
				+ " block, using the same check as X-Ray.",
			false);
	private boolean prevOnlyExposed;
	
	private final ChunkAreaSetting area = new ChunkAreaSetting("Area",
		"The area around the player to search in.\n"
			+ "Higher values require a faster computer.");
	
	private final SliderSetting limit = new SliderSetting("Limit",
		"The maximum number of blocks to display.\n"
			+ "Higher values require a faster computer.",
		4, 3, 6, 1, ValueDisplay.LOGARITHMIC);
	private int prevLimit;
	private boolean notify;
	
	private final ChunkSearcherCoordinator coordinator =
		new ChunkSearcherCoordinator(area);
	
	private ForkJoinPool forkJoinPool;
	private ForkJoinTask<ArrayList<BlockPos>> getMatchingBlocksTask;
	private ForkJoinTask<ArrayList<int[]>> compileVerticesTask;
	private ArrayList<BlockPos> candidateBlocks;
	private final HashSet<BlockPos> matchingBlocks = new HashSet<>();
	private volatile List<BlockPos> renderBlocksSnapshot = List.of();
	private int candidateIndex;
	private boolean matchesPrepared;
	private int lastCompletedSearcherCount;
	private volatile long buildGeneration;
	private long compileVerticesTaskGeneration;
	private boolean compileVerticesTaskComplete;
	private boolean compileVerticesTaskHasMatches;
	
	private EasyVertexBuffer vertexBuffer;
	private RegionPos bufferRegion;
	private boolean bufferUpToDate;
	
	public SearchHack()
	{
		super("Search");
		setCategory(Category.RENDER);
		addSetting(blocks);
		addSetting(showTextures);
		addSetting(textureDistance);
		addSetting(textureBrightness);
		addSetting(fullbrightExposure);
		addSetting(onlyExposed);
		addSetting(area);
		addSetting(limit);
	}
	
	@Override
	public String getRenderName()
	{
		return getName() + " [" + blocks.size() + "]";
	}
	
	@Override
	protected void onEnable()
	{
		lastBlocks = new ArrayList<>(blocks.getBlockNames());
		updateTargetBlocks();
		prevOnlyExposed = onlyExposed.isChecked();
		prevShowTextures = showTextures.isChecked();
		prevLimit = limit.getValueI();
		notify = true;
		
		forkJoinPool = new ForkJoinPool();
		
		bufferUpToDate = false;
		lastCompletedSearcherCount = 0;
		
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(ChunkUpdateListener.class, coordinator);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(ChunkUpdateListener.class, coordinator);
		EVENTS.remove(RenderListener.class, this);
		
		stopBuildingBuffer();
		coordinator.reset();
		forkJoinPool.shutdownNow();
		
		if(vertexBuffer != null)
			vertexBuffer.close();
		vertexBuffer = null;
		bufferRegion = null;
		renderBlocksSnapshot = List.of();
		textureModels.clear();
	}
	
	@Override
	public void onUpdate()
	{
		boolean searchersChanged = false;
		
		// clear ChunkSearchers if the block list has changed
		ArrayList<String> currentBlocks =
			new ArrayList<>(blocks.getBlockNames());
		if(!currentBlocks.equals(lastBlocks))
		{
			lastBlocks = currentBlocks;
			updateTargetBlocks();
			searchersChanged = true;
		}
		
		if(coordinator.update())
			searchersChanged = true;
		if(coordinator.hasRemovedChunks())
			clearRenderCache();
		
		int completedSearcherCount = coordinator.getCompletedSearcherCount();
		boolean searchProgressed =
			completedSearcherCount != lastCompletedSearcherCount;
		lastCompletedSearcherCount = completedSearcherCount;
		
		if(onlyExposed.isChecked() != prevOnlyExposed)
		{
			prevOnlyExposed = onlyExposed.isChecked();
			searchersChanged = true;
		}
		if(showTextures.isChecked() != prevShowTextures)
		{
			prevShowTextures = showTextures.isChecked();
			searchersChanged = true;
			clearRenderCache();
		}
		
		// check if limit has changed
		if(limit.getValueI() != prevLimit)
		{
			prevLimit = limit.getValueI();
			notify = true;
			searchersChanged = true;
		}
		
		if(searchersChanged)
			stopBuildingBuffer();
		else if(searchProgressed)
			restartBuildingBuffer();
		
		if(bufferUpToDate || completedSearcherCount == 0)
			return;
		
		// build the buffer
		
		if(getMatchingBlocksTask == null)
			startGetMatchingBlocksTask();
		
		if(!getMatchingBlocksTask.isDone())
			return;
		
		if(!prepareMatchingBlocks())
			return;
		
		if(showTextures.isChecked())
		{
			if(!matchingBlocks.isEmpty() || coordinator.isDone())
			{
				renderBlocksSnapshot = List.copyOf(matchingBlocks);
				textureModels.keySet().retainAll(matchingBlocks);
			}
			bufferUpToDate = true;
			return;
		}
		
		if(compileVerticesTask == null)
			startCompileVerticesTask();
		
		if(!compileVerticesTask.isDone())
			return;
		
		if(!bufferUpToDate)
			setBufferFromTask();
	}
	
	private void updateTargetBlocks()
	{
		targetBlocks = lastBlocks.stream().map(BlockUtils::getBlockFromNameOrID)
			.filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
		coordinator.setTargetBlocks(targetBlocks);
	}
	
	private boolean matchesBlock(BlockPos pos, BlockState state)
	{
		return targetBlocks.contains(state.getBlock());
	}
	
	public void submitTextureModels(PoseStack matrixStack,
		SubmitNodeCollector collector, LevelRenderState levelRenderState)
	{
		if(!isEnabled() || !showTextures.isChecked() || MC.level == null)
			return;
		boolean fullbright =
			textureBrightness.getSelected() == TextureBrightness.FULLBRIGHT;
		boolean shaderPackInUse = WurstRenderLayers.isShaderPackInUse();
		RenderType searchLayer =
			WurstRenderLayers.getSearchBlocks(fullbright, shaderPackInUse);
		CameraRenderState camera = levelRenderState.cameraRenderState;
		SubmitNodeCollection collection =
			(SubmitNodeCollection)collector.order(0);
		BlockPos breakingPos = MC.gameMode.isDestroying()
			? IMC.getInteractionManager().getDestroyBlockPos() : null;
		int breakingStage = MC.gameMode.getDestroyStage();
		int tintColor = 0xFFFFFFFF;
		if(shaderPackInUse && fullbright)
		{
			int exposure = Math.round(fullbrightExposure.getValueF() * 255);
			tintColor =
				0xFF000000 | (exposure << 16) | (exposure << 8) | exposure;
		}
		
		double maxDistanceSq = textureDistance.getValueSq();
		for(BlockPos pos : renderBlocksSnapshot)
		{
			double dx = pos.getX() + 0.5 - camera.pos.x();
			double dy = pos.getY() + 0.5 - camera.pos.y();
			double dz = pos.getZ() + 0.5 - camera.pos.z();
			if(dx * dx + dy * dy + dz * dz > maxDistanceSq
				|| !MC.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
				|| !RenderUtils.isVisible(new AABB(pos).inflate(0.5)))
				continue;
			BlockState state = MC.level.getBlockState(pos);
			if(!matchesBlock(pos, state))
				continue;
			
			BlockStateModel model =
				MC.getModelManager().getBlockStateModelSet().get(state);
			TextureModel cached = textureModels.get(pos);
			// Model identity changes on resource reload; state/position retain
			// the original deterministic model variants.
			if(cached == null || cached.state() != state
				|| cached.model() != model)
			{
				BlockModelRenderState renderState = new BlockModelRenderState();
				List<BlockStateModelPart> modelParts =
					renderState.setupModel(new org.joml.Matrix4f(), false);
				model.collectParts(
					renderState.scratchRandomSource(state.getSeed(pos)),
					modelParts);
				cached = new TextureModel(state, model, List.copyOf(modelParts),
					renderState.tintLayers().toIntArray());
				textureModels.put(pos, cached);
			}
			List<BlockStateModelPart> parts = cached.parts();
			int lightCoords = LightCoordsUtil.getLightCoords(MC.level, pos);
			if(shaderPackInUse && !fullbright)
				lightCoords = LightCoordsUtil.withBlock(lightCoords,
					Math.max(LightCoordsUtil.block(lightCoords),
						BALANCED_SHADER_LIGHT_FLOOR));
			int[] tintLayers = cached.tints();
			
			matrixStack.pushPose();
			matrixStack.translate(pos.getX() - camera.pos.x(),
				pos.getY() - camera.pos.y(), pos.getZ() - camera.pos.z());
			// Use the vanilla collector method directly. The Fabric convenience
			// method maps its boolean argument to a vanilla layer and does not
			// forward a custom RenderType; Sodium's compatibility path can also
			// receive a null layer there.
			collector.submitBlockModel(matrixStack, searchLayer, parts,
				tintLayers, lightCoords, 0, tintColor);
			if(pos.equals(breakingPos) && breakingStage >= 0
				&& breakingStage < WurstRenderLayers.SEARCH_DESTROY_TYPES
					.size())
			{
				PoseStack.Pose crackPose = matrixStack.last().copy();
				collection.afterTerrain
					.submit(new BlockModelFeatureRenderer.Submit(crackPose,
						WurstRenderLayers.SEARCH_DESTROY_TYPES
							.get(breakingStage),
						List.copyOf(parts), BlockModelRenderState.EMPTY_TINTS,
						net.minecraft.util.LightCoordsUtil.FULL_BRIGHT,
						net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
						0, crackPose));
			}
			matrixStack.popPose();
		}
	}
	
	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(showTextures.isChecked())
			return;
		
		if(vertexBuffer == null || bufferRegion == null)
			return;
		
		matrixStack.pushPose();
		RenderUtils.applyRegionalRenderOffset(matrixStack, bufferRegion);
		
		float[] rainbow = RenderUtils.getRainbowColor();
		vertexBuffer.draw(matrixStack, WurstRenderLayers.ESP_QUADS, rainbow,
			0.5F);
		
		matrixStack.popPose();
	}
	
	private void clearRenderCache()
	{
		if(vertexBuffer != null)
			vertexBuffer.close();
		vertexBuffer = null;
		bufferRegion = null;
		renderBlocksSnapshot = List.of();
		textureModels.clear();
	}
	
	private void stopBuildingBuffer()
	{
		restartBuildingBuffer();
		
		// Keep the last complete buffer visible while the replacement is built.
		// The new buffer is swapped in atomically by setBufferFromTask().
	}
	
	private void restartBuildingBuffer()
	{
		buildGeneration++;
		
		if(getMatchingBlocksTask != null)
			getMatchingBlocksTask.cancel(true);
		getMatchingBlocksTask = null;
		
		if(compileVerticesTask != null)
			compileVerticesTask.cancel(true);
		compileVerticesTask = null;
		
		candidateBlocks = null;
		matchesPrepared = false;
		matchingBlocks.clear();
		candidateIndex = 0;
		
		bufferUpToDate = false;
	}
	
	private void startGetMatchingBlocksTask()
	{
		BlockPos eyesPos = BlockPos.containing(RotationUtils.getEyesPos());
		Comparator<BlockPos> comparator =
			Comparator.comparingInt(pos -> eyesPos.distManhattan(pos));
		boolean exposedOnly = onlyExposed.isChecked();
		int limitValue = limit.getValueLog();
		long taskGeneration = buildGeneration;
		
		getMatchingBlocksTask =
			forkJoinPool.submit((Callable<ArrayList<BlockPos>>)() -> {
				if(taskGeneration != buildGeneration)
					return new ArrayList<>();
				
				Stream<BlockPos> positions =
					coordinator.getCompletedMatches().parallel()
						.map(ChunkSearcher.Result::pos).sorted(comparator);
				if(!exposedOnly)
					positions = positions.limit(limitValue);
				
				return positions
					.collect(Collectors.toCollection(ArrayList::new));
			});
	}
	
	private boolean prepareMatchingBlocks()
	{
		if(matchesPrepared)
			return true;
		
		if(candidateBlocks == null)
		{
			try
			{
				candidateBlocks = getMatchingBlocksTask.join();
			}catch(CancellationException e)
			{
				getMatchingBlocksTask = null;
				return false;
			}
			
			candidateIndex = 0;
			matchingBlocks.clear();
		}
		
		int limitValue = limit.getValueLog();
		if(!onlyExposed.isChecked())
		{
			matchingBlocks.addAll(candidateBlocks);
			matchesPrepared = true;
			updateNotification(limitValue);
			return true;
		}
		
		int checks = EXPOSED_CHECKS_PER_TICK;
		while(candidateIndex < candidateBlocks.size()
			&& matchingBlocks.size() < limitValue && checks-- > 0)
		{
			BlockPos pos = candidateBlocks.get(candidateIndex++);
			if(BlockUtils.isExposed(pos))
				matchingBlocks.add(pos);
		}
		
		if(candidateIndex < candidateBlocks.size()
			&& matchingBlocks.size() < limitValue)
			return false;
		
		updateNotification(limitValue);
		matchesPrepared = true;
		return true;
	}
	
	private void updateNotification(int limitValue)
	{
		
		if(matchingBlocks.size() < limitValue)
			notify = true;
		else if(notify)
		{
			ChatUtils.warning("Search found \u00a7lA LOT\u00a7r of blocks!"
				+ " To prevent lag, it will only show the closest \u00a76"
				+ limit.getValueString() + "\u00a7r results.");
			notify = false;
		}
	}
	
	private void startCompileVerticesTask()
	{
		HashSet<BlockPos> blocks = new HashSet<>(matchingBlocks);
		long taskGeneration = buildGeneration;
		compileVerticesTaskGeneration = taskGeneration;
		compileVerticesTaskComplete = coordinator.isDone();
		compileVerticesTaskHasMatches = !blocks.isEmpty();
		
		compileVerticesTask = forkJoinPool.submit(() -> {
			if(taskGeneration != buildGeneration)
				return new ArrayList<>();
			return BlockVertexCompiler.compile(blocks);
		});
	}
	
	private void setBufferFromTask()
	{
		if(compileVerticesTaskGeneration != buildGeneration)
			return;
		
		ArrayList<int[]> vertices;
		try
		{
			vertices = compileVerticesTask.join();
		}catch(CancellationException e)
		{
			return;
		}
		
		if(!compileVerticesTaskHasMatches)
		{
			if(compileVerticesTaskComplete)
			{
				if(vertexBuffer != null)
					vertexBuffer.close();
				vertexBuffer = null;
				bufferRegion = null;
				renderBlocksSnapshot = List.of();
			}
			bufferUpToDate = true;
			return;
		}
		
		RegionPos region = RenderUtils.getCameraRegion();
		
		if(vertexBuffer != null)
			vertexBuffer.close();
		renderBlocksSnapshot = List.copyOf(matchingBlocks);
		
		vertexBuffer = EasyVertexBuffer.createAndUpload(PrimitiveTopology.QUADS,
			DefaultVertexFormat.POSITION_COLOR, buffer -> {
				for(int[] vertex : vertices)
					buffer.addVertex(vertex[0] - region.x(), vertex[1],
						vertex[2] - region.z()).setColor(0xFFFFFFFF);
			});
		
		bufferUpToDate = true;
		bufferRegion = region;
	}
	
	private record TextureModel(BlockState state, BlockStateModel model,
		List<BlockStateModelPart> parts, int[] tints)
	{}
	
	private enum TextureBrightness
	{
		BALANCED("Balanced"),
		FULLBRIGHT("Fullbright");
		
		private final String name;
		
		private TextureBrightness(String name)
		{
			this.name = name;
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
}
