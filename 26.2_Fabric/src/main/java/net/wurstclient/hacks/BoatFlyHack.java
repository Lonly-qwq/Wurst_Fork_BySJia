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
	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"Normal keeps the original BoatFly behavior. Safe only applies to boats"
			+ " and uses capped, smooth movement to reduce abnormal motion.",
		Mode.values(), Mode.NORMAL);
	
	private final EnumSetting<SafeProfile> safeProfile =
		new EnumSetting<>("Safe profile",
			"Controls the speed and acceleration limits used by Safe mode. This"
				+ " setting does nothing in Normal mode.",
			SafeProfile.values(), SafeProfile.BALANCED);
	
	private final CheckboxSetting changeForwardSpeed = new CheckboxSetting(
		"Change Forward Speed",
		"Allows \u00a7eForward Speed\u00a7r to be changed, disables smooth acceleration.",
		false);
	
	private final SliderSetting forwardSpeed = new SliderSetting(
		"Forward Speed", 1, 0.05, 5, 0.05, SliderSetting.ValueDisplay.DECIMAL);
	
	private final SliderSetting upwardSpeed = new SliderSetting("Upward Speed",
		0.3, 0, 5, 0.05, SliderSetting.ValueDisplay.DECIMAL);
	
	public BoatFlyHack()
	{
		super("BoatFly");
		setCategory(Category.MOVEMENT);
		addSetting(mode);
		addSetting(safeProfile);
		addSetting(changeForwardSpeed);
		addSetting(forwardSpeed);
		addSetting(upwardSpeed);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
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
		
		double targetY;
		if(MC.options.keyJump.isDown())
			targetY = Math.min(upwardSpeed.getValue(), profile.verticalCap);
		else if(MC.options.keySprint.isDown())
			targetY = -profile.verticalCap;
		else
			targetY = Math.min(velocity.y, 0);
		
		targetY = Mth.clamp(targetY, -profile.verticalCap, profile.verticalCap);
		
		double motionX =
			approach(velocity.x, targetX, profile.horizontalAcceleration);
		double motionY =
			approach(velocity.y, targetY, profile.verticalAcceleration);
		double motionZ =
			approach(velocity.z, targetZ, profile.horizontalAcceleration);
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
		CONSERVATIVE("Conservative", 1.2, 0.10, 0.05, 0.05),
		BALANCED("Balanced", 1.6, 0.14, 0.08, 0.08),
		AGGRESSIVE("Aggressive", 2.0, 0.18, 0.11, 0.11);
		
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
