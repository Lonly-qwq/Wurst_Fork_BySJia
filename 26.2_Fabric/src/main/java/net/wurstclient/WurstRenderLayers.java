/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient;

import java.util.List;

import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.ModelBakery;

import com.mojang.blaze3d.pipeline.RenderPipeline;

public enum WurstRenderLayers
{
	;
	
	/**
	 * Similar to {@link RenderType#getLines()}, but with line width 2.
	 */
	public static final RenderType LINES = RenderType.create("wurst:lines",
		RenderSetup.builder(WurstShaderPipelines.DEPTH_TEST_LINES)
			.setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
			.setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
			.createRenderSetup());
	
	/**
	 * Similar to {@link RenderType#getLines()}, but with line width 2 and no
	 * depth test.
	 */
	public static final RenderType ESP_LINES =
		RenderType.create("wurst:esp_lines",
			RenderSetup.builder(WurstShaderPipelines.ESP_LINES)
				.setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
				.setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
				.createRenderSetup());
	
	/**
	 * Similar to {@link RenderType#getDebugQuads()}, but with culling enabled.
	 */
	public static final RenderType QUADS = RenderType.create("wurst:quads",
		RenderSetup.builder(WurstShaderPipelines.QUADS).sortOnUpload()
			.createRenderSetup());
	
	/**
	 * Similar to {@link RenderType#getDebugQuads()}, but with culling enabled
	 * and no depth test.
	 */
	public static final RenderType ESP_QUADS = RenderType.create(
		"wurst:esp_quads", RenderSetup.builder(WurstShaderPipelines.ESP_QUADS)
			.sortOnUpload().createRenderSetup());
	
	/**
	 * Similar to {@link RenderType#getDebugQuads()}, but with no depth test.
	 */
	public static final RenderType ESP_QUADS_NO_CULLING =
		RenderType.create("wurst:esp_quads_no_culling",
			RenderSetup.builder(WurstShaderPipelines.ESP_QUADS_NO_CULLING)
				.sortOnUpload().useLightmap().createRenderSetup());
	
	/**
	 * Block-textured Search overlay using a block/entity Iris-compatible
	 * pipeline. This is important for ray-tracing shaderpacks whose textured
	 * program is only an intermediate data pass.
	 * The see-through pipeline keeps the original atlas texture and does not
	 * apply the world depth test.
	 */
	public static final RenderType SEARCH_BLOCKS = createSearchBlocks(
		"wurst:search_blocks", WurstShaderPipelines.SEARCH_BLOCKS, false);
	public static final RenderType SEARCH_BLOCKS_FULLBRIGHT =
		createSearchBlocks("wurst:search_blocks_fullbright",
			WurstShaderPipelines.SEARCH_BLOCKS_FULLBRIGHT, true);
	public static final List<RenderType> SEARCH_DESTROY_TYPES =
		ModelBakery.DESTROY_TYPES;
	
	public static RenderType getSearchBlocks(boolean fullbright,
		boolean shaderPackInUse)
	{
		if(shaderPackInUse)
		{
			// Re-apply both bindings after shaderpack reloads.
			registerWithIris(WurstShaderPipelines.SEARCH_BLOCKS, false);
			registerWithIris(WurstShaderPipelines.SEARCH_BLOCKS_FULLBRIGHT,
				true);
		}
		return fullbright ? SEARCH_BLOCKS_FULLBRIGHT : SEARCH_BLOCKS;
	}
	
	public static boolean isShaderPackInUse()
	{
		try
		{
			Class<?> apiClass =
				Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			Object api = apiClass.getMethod("getInstance").invoke(null);
			return (Boolean)apiClass.getMethod("isShaderPackInUse").invoke(api);
		}catch(ReflectiveOperationException | LinkageError e)
		{
			return false;
		}
	}
	
	public static boolean isSearchBlocks(RenderType renderType)
	{
		return renderType == SEARCH_BLOCKS
			|| renderType == SEARCH_BLOCKS_FULLBRIGHT;
	}
	
	private static RenderType createSearchBlocks(String name,
		RenderPipeline pipeline, boolean fullbright)
	{
		registerWithIris(pipeline, fullbright);
		return RenderType.create(name,
			RenderSetup.builder(pipeline)
				.withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS)
				.setOutline(RenderSetup.OutlineProperty.NONE).sortOnUpload()
				.useLightmap().createRenderSetup());
	}
	
	private static void registerWithIris(RenderPipeline pipeline,
		boolean fullbright)
	{
		try
		{
			Class<?> apiClass =
				Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			Object api = apiClass.getMethod("getInstance").invoke(null);
			Class<?> programClass =
				Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
			if(assignBlockEntityProgram(pipeline, fullbright))
				return;
			
			Object texturedProgram = findIrisProgram(programClass);
			if(texturedProgram == null)
				return;
			apiClass
				.getMethod("assignPipeline", RenderPipeline.class, programClass)
				.invoke(api, pipeline, texturedProgram);
		}catch(ReflectiveOperationException | LinkageError e)
		{
			// Iris is optional. The same pipeline works with vanilla rendering.
		}
	}
	
	private static boolean assignBlockEntityProgram(RenderPipeline pipeline,
		boolean fullbright)
	{
		try
		{
			Class<?> keyClass = Class
				.forName("net.irisshaders.iris.pipeline.programs.ShaderKey");
			Class<?> pipelinesClass =
				Class.forName("net.irisshaders.iris.pipeline.IrisPipelines");
			java.lang.reflect.Method method = pipelinesClass.getDeclaredMethod(
				"assignPipeline", RenderPipeline.class, keyClass);
			method.setAccessible(true);
			String[] names =
				fullbright ? new String[]{"BLOCK_ENTITY_BRIGHT", "BLOCK_ENTITY"}
					: new String[]{"BLOCK_ENTITY"};
			for(String name : names)
				try
				{
					java.lang.reflect.Field keyField =
						keyClass.getDeclaredField(name);
					keyField.setAccessible(true);
					method.invoke(null, pipeline, keyField.get(null));
					return true;
				}catch(NoSuchFieldException e)
				{
					// Try the next Iris version-specific key.
				}
			return false;
		}catch(ReflectiveOperationException | LinkageError e)
		{
			return false;
		}
	}
	
	private static Object findIrisProgram(Class<?> programClass)
	{
		// BLOCK_ENTITY_BRIGHT is an internal Iris shader key and is not present
		// in every public IrisProgram API version.
		for(String name : new String[]{"BLOCK_ENTITY", "BLOCK", "TEXTURED"})
			try
			{
				@SuppressWarnings({"unchecked", "rawtypes"})
				Object program = Enum.valueOf((Class)programClass, name);
				return program;
			}catch(IllegalArgumentException e)
			{
				// Try the next program supported by this Iris API version.
			}
		return null;
	}
	
	/**
	 * Returns either {@link #QUADS} or {@link #ESP_QUADS} depending on the
	 * value of {@code depthTest}.
	 */
	public static RenderType getQuads(boolean depthTest)
	{
		return depthTest ? QUADS : ESP_QUADS;
	}
	
	/**
	 * Returns either {@link #LINES} or {@link #ESP_LINES} depending on the
	 * value of {@code depthTest}.
	 */
	public static RenderType getLines(boolean depthTest)
	{
		return depthTest ? LINES : ESP_LINES;
	}
}
