/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;

@SearchTags({"boat fly", "BoatFlight", "boat flight", "EntitySpeed",
	"entity speed"})
public final class BoatFlyHack extends Hack implements UpdateListener
{
	private static final double SAFE_BREAK_SPEED = 0.01;
	
	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"Normal keeps the original BoatFly behavior. Safe only applies to boats"
			+ " and uses capped, smooth movement to reduce abnormal motion.",
		Mode.values(), Mode.NORMAL);
	
	private final EnumSetting<SafeProfile> safeProfile =
		new EnumSetting<>("Safe profile",
			"Controls the speed and acceleration limits used by Safe mode. This"
				+ " setting does nothing in Normal mode.",
			SafeProfile.values(), SafeProfile.BALANCED);
	
	private final SliderSetting ascentTicks = new SliderSetting("Ascent Ticks",
		35, 1, 100, 1, SliderSetting.ValueDisplay.INTEGER.withSuffix(" ticks"));
	
	private final SliderSetting breakTicks = new SliderSetting("Break Ticks", 1,
		1, 10, 1, SliderSetting.ValueDisplay.INTEGER.withSuffix(" ticks"));
	
	private final CheckboxSetting changeForwardSpeed = new CheckboxSetting(
		"Change Forward Speed",
		"Allows \u00a7eForward Speed\u00a7r to be changed, disables smooth acceleration.",
		false);
	
	private final SliderSetting forwardSpeed = new SliderSetting(
		"Forward Speed", 1, 0.05, 5, 0.05, SliderSetting.ValueDisplay.DECIMAL);
	
	private final SliderSetting upwardSpeed = new SliderSetting("Upward Speed",
		0.3, 0, 5, 0.05, SliderSetting.ValueDisplay.DECIMAL);
	private int safeAscentTickCount;
	private int safeBreakTickCount;
	
	public BoatFlyHack()
	{
		super("BoatFly");
		setCategory(Category.MOVEMENT);
		addSetting(mode);
		addSetting(safeProfile);
		addSetting(ascentTicks);
		addSetting(breakTicks);
		addSetting(changeForwardSpeed);
		addSetting(forwardSpeed);
		addSetting(upwardSpeed);
	}
	
	@Override
	protected void onEnable()
	{
		safeAscentTickCount = 0;
		safeBreakTickCount = 0;
		EVENTS.add(UpdateListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		safeAscentTickCount = 0;
		safeBreakTickCount = 0;
	}
	
	@Override
	public void onUpdate()
	{
		// check if riding
		if(!MC.player.isPassenger())
			return;
		
		Entity vehicle = MC.player.getVehicle();
		if(mode.getSelected() == Mode.SAFE)
		{
			applySafeMotion(vehicle);
			return;
		}
		
		Vec3 velocity = vehicle.getDeltaMovement();
		
		// default motion
		double motionX = velocity.x;
		double motionY = 0;
		double motionZ = velocity.z;
		
		// up/down
		if(MC.options.keyJump.isDown())
			motionY = upwardSpeed.getValue();
		else if(MC.options.keySprint.isDown())
			motionY = velocity.y;
		
		// forward
		if(MC.options.keyUp.isDown() && changeForwardSpeed.isChecked())
		{
			double speed = forwardSpeed.getValue();
			float yawRad = vehicle.getYRot() * Mth.DEG_TO_RAD;
			
			motionX = Mth.sin(-yawRad) * speed;
			motionZ = Mth.cos(yawRad) * speed;
		}
		
		// apply motion
		vehicle.setDeltaMovement(motionX, motionY, motionZ);
	}
	
	private void applySafeMotion(Entity vehicle)
	{
		if(!(vehicle instanceof Boat))
			return;
		
		SafeProfile profile = safeProfile.getSelected();
		Vec3 velocity = vehicle.getDeltaMovement();
		boolean grounded = vehicle.onGround() || vehicle.isInWater();
		if(grounded)
		{
			safeAscentTickCount = 0;
			safeBreakTickCount = 0;
		}
		
		double targetX = velocity.x;
		double targetZ = velocity.z;
		double horizontalSpeed = Math.hypot(targetX, targetZ);
		if(horizontalSpeed > profile.horizontalCap)
		{
			double scale = profile.horizontalCap / horizontalSpeed;
			targetX *= scale;
			targetZ *= scale;
		}
		
		if(MC.options.keyUp.isDown() && changeForwardSpeed.isChecked())
		{
			double speed =
				Math.min(forwardSpeed.getValue(), profile.horizontalCap);
			float yawRad = vehicle.getYRot() * Mth.DEG_TO_RAD;
			targetX = Mth.sin(-yawRad) * speed;
			targetZ = Mth.cos(yawRad) * speed;
		}
		
		boolean neutralPhase = safeBreakTickCount > 0;
		double targetY;
		if(safeBreakTickCount > 0)
		{
			// A tiny downward tick breaks the server's continuous ascent check
			// without producing the more visible hard stop at zero velocity.
			targetY = -Math.min(SAFE_BREAK_SPEED, profile.verticalCap);
			safeBreakTickCount--;
			if(safeBreakTickCount == 0)
				safeAscentTickCount = 0;
		}else if(MC.options.keyJump.isDown())
		{
			targetY = Math.min(upwardSpeed.getValue(), profile.verticalCap);
			if(!grounded
				&& ++safeAscentTickCount >= (int)ascentTicks.getValue())
				safeBreakTickCount = (int)breakTicks.getValue();
		}else if(MC.options.keySprint.isDown())
			targetY = -profile.verticalCap;
		else
			targetY = Math.min(velocity.y, 0);
		targetY = Mth.clamp(targetY, -profile.verticalCap, profile.verticalCap);
		
		double motionX =
			approach(velocity.x, targetX, profile.horizontalAcceleration);
		double motionY =
			approach(velocity.y, targetY, profile.verticalAcceleration);
		if(neutralPhase)
			motionY = targetY;
		double motionZ =
			approach(velocity.z, targetZ, profile.horizontalAcceleration);
		// Clamp the final motion too, including externally applied velocity.
		double finalHorizontalSpeed = Math.hypot(motionX, motionZ);
		if(finalHorizontalSpeed > profile.horizontalCap)
		{
			double scale = profile.horizontalCap / finalHorizontalSpeed;
			motionX *= scale;
			motionZ *= scale;
		}
		motionY = Mth.clamp(motionY, -profile.verticalCap, profile.verticalCap);
		vehicle.setDeltaMovement(motionX, motionY, motionZ);
	}
	
	private double approach(double current, double target, double maxChange)
	{
		double difference = target - current;
		if(Math.abs(difference) <= maxChange)
			return target;
		
		return current + Math.copySign(maxChange, difference);
	}
	
	private enum Mode
	{
		NORMAL("Normal"),
		SAFE("Safe");
		
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
	
	private enum SafeProfile
	{
		CONSERVATIVE("Conservative", 1.5, 1.5, 0.05, 0.05),
		BALANCED("Balanced", 3.0, 3.0, 0.10, 0.10),
		AGGRESSIVE("Aggressive", 5.0, 5.0, 0.20, 0.20);
		
		private final String name;
		private final double horizontalCap;
		private final double verticalCap;
		private final double horizontalAcceleration;
		private final double verticalAcceleration;
		
		private SafeProfile(String name, double horizontalCap,
			double verticalCap, double horizontalAcceleration,
			double verticalAcceleration)
		{
			this.name = name;
			this.horizontalCap = horizontalCap;
			this.verticalCap = verticalCap;
			this.horizontalAcceleration = horizontalAcceleration;
			this.verticalAcceleration = verticalAcceleration;
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
}
