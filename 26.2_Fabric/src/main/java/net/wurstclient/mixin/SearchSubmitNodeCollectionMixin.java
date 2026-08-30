/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.feature.BlockModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.wurstclient.WurstRenderLayers;

@Mixin(SubmitNodeCollection.class)
public abstract class SearchSubmitNodeCollectionMixin
{
	@Inject(method = "submitBlockModel", at = @At("HEAD"), cancellable = true)
	private void moveSearchModelsAfterTerrain(PoseStack poseStack,
		RenderType renderType, List<BlockStateModelPart> modelParts,
		int[] tintLayers, int lightCoords, int overlayCoords, int tintColor,
		CallbackInfo ci)
	{
		if(renderType != WurstRenderLayers.SEARCH_BLOCKS)
			return;
		
		SubmitNodeCollection collection = (SubmitNodeCollection)(Object)this;
		collection.afterTerrain.submit(new BlockModelFeatureRenderer.Submit(
			poseStack.last().copy(), renderType, modelParts, tintLayers,
			lightCoords, overlayCoords, tintColor, null));
		ci.cancel();
	}
	
}
