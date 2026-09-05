/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.BlockBreakingProgressListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;

@SearchTags({"FastMine", "SpeedMine", "SpeedyGonzales", "fast break",
	"fast mine", "speed mine", "speedy gonzales", "NoBreakDelay",
	"no break delay"})
public final class FastBreakHack extends Hack
	implements UpdateListener, BlockBreakingProgressListener
{
	private final SliderSetting activationChance = new SliderSetting(
		"Activation chance",
		"Only FastBreaks some of the blocks you break with the given chance,"
			+ " which makes it harder for anti-cheat plugins to detect.\n\n"
			+ "This setting does nothing if Legit mode is enabled.",
		1, 0, 1, 0.01, ValueDisplay.PERCENTAGE);
	
	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"Normal breaks blocks instantly without a crack animation. Animated"
			+ " multiplies the vanilla breaking progress so that the crack"
			+ " animation and normal block-breaking synchronization remain"
			+ " intact. Legit only removes the delay between blocks and does"
			+ " not speed up the breaking process.",
		Mode.values(), Mode.NORMAL);
	
	private final SliderSetting progressMultiplier =
		new SliderSetting("Progress multiplier",
			"Multiplies the vanilla block-breaking progress in Animated mode."
				+ " Values above 1.4x may finish before a vanilla server can"
				+ " accept the break, causing a short delay or block rollback."
				+ " Lower values improve compatibility with anti-cheat plugins."
				+ " This setting does nothing in Normal and Legit modes.",
			1.3, 1, 2, 0.1, ValueDisplay.DECIMAL.withSuffix("x"));
	
	private final Random random = new Random();
	private BlockPos lastBlockPos;
	private boolean fastBreakBlock;
	private Mode lastMode = Mode.NORMAL;
	
	public FastBreakHack()
	{
		super("FastBreak");
		setCategory(Category.BLOCKS);
		addSetting(activationChance);
		addSetting(mode);
		addSetting(progressMultiplier);
	}
	
	@Override
	public String getRenderName()
	{
		Mode selectedMode = mode.getSelected();
		if(selectedMode == Mode.NORMAL)
			return getName();
		
		return getName() + selectedMode;
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(BlockBreakingProgressListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(BlockBreakingProgressListener.class, this);
		lastBlockPos = null;
	}
	
	@Override
	public void onUpdate()
	{
		MC.gameMode.destroyDelay = 0;
	}
	
	@Override
	public void onBlockBreakingProgress(BlockBreakingProgressEvent event)
	{
		Mode selectedMode = mode.getSelected();
		if(selectedMode != lastMode)
		{
			lastMode = selectedMode;
			lastBlockPos = null;
		}
		
		if(selectedMode == Mode.LEGIT)
			return;
		
		if(MC.gameMode.destroyProgress >= 1)
			return;
		
		BlockPos blockPos = event.getBlockPos();
		if(!blockPos.equals(lastBlockPos))
		{
			lastBlockPos = blockPos;
			fastBreakBlock = random.nextDouble() < activationChance.getValue();
		}
		
		// Ignore unbreakable blocks to avoid slowdown issue
		if(BlockUtils.isUnbreakable(blockPos))
			return;
		
		if(!fastBreakBlock)
			return;
		
		if(selectedMode == Mode.ANIMATED)
		{
			applyProgressMultiplier(blockPos);
			return;
		}
		
		Action action = ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK;
		Direction direction = event.getDirection();
		IMC.getInteractionManager().sendPlayerActionC2SPacket(action, blockPos,
			direction);
	}
	
	private void applyProgressMultiplier(BlockPos pos)
	{
		float multiplier = progressMultiplier.getValueF();
		if(multiplier <= 1)
			return;
		
		float current = MC.gameMode.destroyProgress;
		float normalStep = BlockUtils.getHardness(pos);
		float extra = normalStep * (multiplier - 1);
		MC.gameMode.destroyProgress =
			Math.min(current + extra, Math.nextDown(1.0F));
	}
	
	private enum Mode
	{
		NORMAL("Normal"),
		ANIMATED("Animated"),
		LEGIT("Legit");
		
		private final String name;
		
		private Mode(String name)
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
