/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient;

import java.util.Optional;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import com.mojang.blaze3d.platform.CompareOp;

import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public enum WurstShaderPipelines
{
	;
	
	/**
	 * Similar to the RENDERTYPE_LINES Snippet, but without fog.
	 */
	public static final Snippet FOGLESS_LINES_SNIPPET = RenderPipeline
		.builder(RenderPipelines.LINES_SNIPPET)
		.withVertexShader(Identifier.parse("wurst:core/fogless_lines"))
		.withFragmentShader(Identifier.parse("wurst:core/fogless_lines"))
		.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
		.withCull(false)
		.withVertexBinding(0,
			DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
		.withPrimitiveTopology(PrimitiveTopology.LINES).buildSnippet();
	
	/**
	 * Similar to the LINES ShaderPipeline, but with no fog.
	 */
	public static final RenderPipeline DEPTH_TEST_LINES =
		RenderPipelines.register(RenderPipeline.builder(FOGLESS_LINES_SNIPPET)
			.withLocation(
				Identifier.parse("wurst:pipeline/wurst_depth_test_lines"))
			.withDepthStencilState(DepthStencilState.DEFAULT).build());
	
	/**
	 * Similar to the LINES ShaderPipeline, but with no depth test or fog.
	 */
	public static final RenderPipeline ESP_LINES =
		RenderPipelines.register(RenderPipeline.builder(FOGLESS_LINES_SNIPPET)
			.withLocation(Identifier.parse("wurst:pipeline/wurst_esp_lines"))
			.withDepthStencilState(Optional.empty()).build());
	
	/**
	 * Similar to the DEBUG_QUADS ShaderPipeline, but with culling enabled.
	 */
	public static final RenderPipeline QUADS = RenderPipelines
		.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(Identifier.parse("wurst:pipeline/wurst_quads"))
			.withDepthStencilState(DepthStencilState.DEFAULT).withCull(true)
			.build());
	
	/**
	 * Similar to the DEBUG_QUADS ShaderPipeline, but with culling enabled
	 * and no depth test.
	 */
	public static final RenderPipeline ESP_QUADS = RenderPipelines
		.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(Identifier.parse("wurst:pipeline/wurst_esp_quads"))
			.withDepthStencilState(Optional.empty()).withCull(true).build());
	
	/**
	 * Similar to the DEBUG_QUADS ShaderPipeline, but with no depth test.
	 */
	public static final RenderPipeline ESP_QUADS_NO_CULLING = RenderPipelines
		.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(Identifier.parse("wurst:pipeline/wurst_esp_quads"))
			.withDepthStencilState(Optional.empty()).withCull(false).build());
	
	private static final Snippet SEARCH_BLOCKS_SNIPPET =
		RenderPipeline.builder().withBindGroupLayout(BindGroupLayouts.GLOBALS)
			.withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
			.withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER2)
			// Search textures are an opaque inspection overlay. Blending here
			// makes
			// iteration's ray-traced intermediate color reduce the texture to a
			// nearly transparent result when the camera is inside a block.
			.withColorTargetState(ColorTargetState.DEFAULT)
			.withVertexBinding(0, DefaultVertexFormat.ENTITY)
			.withPrimitiveTopology(PrimitiveTopology.QUADS).buildSnippet();
	
	/**
	 * A textured block overlay with local gamma correction and no depth test.
	 * Iris is assigned to the normal block program at runtime.
	 */
	public static final RenderPipeline SEARCH_BLOCKS =
		RenderPipelines.register(RenderPipeline.builder(SEARCH_BLOCKS_SNIPPET)
			.withLocation(Identifier.parse("wurst:pipeline/search_blocks"))
			.withVertexShader(Identifier.parse("wurst:core/search_blocks"))
			.withFragmentShader(Identifier.parse("wurst:core/search_blocks"))
			.withShaderDefine("NO_OVERLAY")
			.withDepthStencilState(
				new DepthStencilState(CompareOp.ALWAYS_PASS, false))
			.withCull(false).build());
	
}
