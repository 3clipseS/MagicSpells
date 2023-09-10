package com.nisovin.magicspells.spells.targeted;

import java.util.*;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.NumberConversions;

import com.nisovin.magicspells.util.*;
import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.events.SpellTargetEvent;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedLocationSpell;
import com.nisovin.magicspells.events.SpellTargetLocationEvent;

import org.apache.commons.math4.core.jdkmath.AccurateMath;

public class AreaEffectSpell extends TargetedSpell implements TargetedLocationSpell {

	private List<Subspell> spells;
	private List<String> spellNames;

	private final ConfigData<Integer> maxTargets;

	private final ConfigData<Double> cone;
	private final ConfigData<Double> vRadius;
	private final ConfigData<Double> hRadius;
	private final ConfigData<Double> horizontalCone;

	private final ConfigData<Boolean> pointBlank;
	private final ConfigData<Boolean> circleShape;
	private final ConfigData<Boolean> useProximity;
	private final ConfigData<Boolean> ignoreRadius;
	private final ConfigData<Boolean> passTargeting;
	private final ConfigData<Boolean> failIfNoTargets;
	private final ConfigData<Boolean> reverseProximity;
	private final ConfigData<Boolean> spellSourceInCenter;

	public AreaEffectSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		spellNames = getConfigStringList("spells", null);

		maxTargets = getConfigDataInt("max-targets", 0);

		cone = getConfigDataDouble("cone", 0);
		horizontalCone = getConfigDataDouble("horizontal-cone", 0);

		vRadius = getConfigDataDouble("vertical-radius", 5);
		hRadius = getConfigDataDouble("horizontal-radius", 10);

		pointBlank = getConfigDataBoolean("point-blank", true);
		circleShape = getConfigDataBoolean("circle-shape", false);
		useProximity = getConfigDataBoolean("use-proximity", false);
		ignoreRadius = getConfigDataBoolean("ignore-radius", false);
		passTargeting = getConfigDataBoolean("pass-targeting", false);
		failIfNoTargets = getConfigDataBoolean("fail-if-no-targets", true);
		reverseProximity = getConfigDataBoolean("reverse-proximity", false);
		spellSourceInCenter = getConfigDataBoolean("spell-source-in-center", false);
	}

	@Override
	public void initialize() {
		super.initialize();

		spells = new ArrayList<>();

		if (spellNames == null || spellNames.isEmpty()) {
			MagicSpells.error("AreaEffectSpell '" + internalName + "' has no spells defined!");
			return;
		}

		for (String spellName : spellNames) {
			Subspell spell = new Subspell(spellName);

			if (!spell.process()) {
				MagicSpells.error("AreaEffectSpell '" + internalName + "' attempted to use invalid spell '" + spellName + '\'');
				continue;
			}

			spells.add(spell);
		}

		spellNames.clear();
		spellNames = null;
	}

	@Override
	public CastResult cast(SpellData data) {
		if (pointBlank.get(data)) {
			SpellTargetLocationEvent event = new SpellTargetLocationEvent(this, data, data.caster().getLocation());
			if (!event.callEvent()) return noTarget(event);

			data = event.getSpellData();
		} else {
			TargetInfo<Location> info = getTargetedBlockLocation(data, 0.5, 0, 0.5, false);
			if (info.noTarget()) return noTarget(info);

			data = info.spellData();
		}

		return doAoe(data) ? new CastResult(PostCastAction.HANDLE_NORMALLY, data) : noTarget(data);
	}

	@Override
	public CastResult castAtLocation(SpellData data) {
		return doAoe(data) ? new CastResult(PostCastAction.HANDLE_NORMALLY, data) : noTarget(data);
	}

	private boolean doAoe(SpellData data) {
		int count = 0;

		LivingEntity caster = data.caster();

		Location location = Util.makeFinite(data.location());
		data = data.location(location);

		boolean spellSourceInCenter = this.spellSourceInCenter.get(data);
		data = data.location(spellSourceInCenter ? location : (caster == null ? null : caster.getLocation()));

		boolean circleShape = this.circleShape.get(data);
		boolean useProximity = this.useProximity.get(data);
		boolean ignoreRadius = this.ignoreRadius.get(data);
		boolean passTargeting = this.passTargeting.get(data);
		boolean failIfNoTargets = this.failIfNoTargets.get(data);
		boolean reverseProximity = this.reverseProximity.get(data);

		int maxTargets = this.maxTargets.get(data);

		double cone = this.cone.get(data);
		double horizontalCone = this.horizontalCone.get(data);

		double vRadius = Math.min(this.vRadius.get(data), MagicSpells.getGlobalRadius());
		double hRadius = Math.min(this.hRadius.get(data), MagicSpells.getGlobalRadius());
		double hRadiusSquared = hRadius * hRadius;

		if (validTargetList.canTargetOnlyCaster()) {
			if (caster == null) return false;

			LivingEntity target = caster;
			if (!target.getWorld().equals(location.getWorld())) return false;

			Location targetLocation = target.getLocation();
			if (circleShape) {
				double hDistance = NumberConversions.square(targetLocation.getX() - location.getX()) + NumberConversions.square(targetLocation.getZ() - location.getZ());
				if (hDistance > hRadiusSquared) return false;
			} else {
				double hDistance = Math.abs(targetLocation.getX() - location.getX()) + Math.abs(targetLocation.getZ() - location.getZ());
				if (hDistance > hRadius) return false;
			}

			double vDistance = Math.abs(targetLocation.getY() - location.getY());
			if (vDistance > vRadius) return false;

			SpellTargetEvent event = new SpellTargetEvent(this, data, target);
			if (!event.callEvent()) return false;

			SpellData subData = event.getSpellData();
			target = subData.target();

			castSpells(subData, passTargeting);

			if (spellSourceInCenter) playSpellEffects(caster, location, target, subData);
			else playSpellEffects(caster, target, subData);

			return true;
		}

		List<LivingEntity> entities = new ArrayList<>();
		if (ignoreRadius) Bukkit.getWorlds().forEach(world -> entities.addAll(world.getLivingEntities()));
		else entities.addAll(location.getWorld().getNearbyLivingEntities(location, hRadius, vRadius, hRadius));

		if (useProximity) {
			// check world before distance
			for (LivingEntity entity : new ArrayList<>(entities)) {
				if (entity.getWorld().equals(location.getWorld())) continue;
				entities.remove(entity);
			}
			Comparator<LivingEntity> comparator = Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(location));
			if (reverseProximity) comparator = comparator.reversed();
			entities.sort(comparator);
		}

		for (LivingEntity target : entities) {
			if (target.isDead()) continue;
			if (!validTargetList.canTarget(caster, target)) continue;

			if (circleShape && !ignoreRadius) {
				Location targetLocation = target.getLocation();

				double hDistance = NumberConversions.square(targetLocation.getX() - location.getX()) + NumberConversions.square(targetLocation.getZ() - location.getZ());
				if (hDistance > hRadiusSquared) continue;
			}

			if (horizontalCone > 0 && horizontalAngle(location, target.getLocation()) > horizontalCone) continue;

			if (cone > 0) {
				Vector dir = target.getLocation().toVector().subtract(location.toVector());
				if (AccurateMath.toDegrees(AccurateMath.abs(dir.angle(location.getDirection()))) > cone) continue;
			}

			SpellTargetEvent event = new SpellTargetEvent(this, data, target);
			if (!event.callEvent()) continue;

			SpellData subData = event.getSpellData();
			target = subData.target();

			castSpells(subData, passTargeting);

			playSpellEffects(EffectPosition.TARGET, target, subData);
			playSpellEffects(EffectPosition.END_POSITION, target, subData);
			if (spellSourceInCenter) {
				playSpellEffects(EffectPosition.START_POSITION, location, subData);
				playSpellEffectsTrail(location, target.getLocation(), subData);
			} else if (caster != null) {
				playSpellEffects(EffectPosition.START_POSITION, caster, subData);
				playSpellEffectsTrail(caster.getLocation(), target.getLocation(), subData);
			}

			count++;

			if (maxTargets > 0 && count >= maxTargets) break;
		}

		boolean success = count > 0 || !failIfNoTargets;
		if (success) {
			playSpellEffects(EffectPosition.SPECIAL, location, data);
			if (caster != null) playSpellEffects(EffectPosition.CASTER, caster, data);
		}

		return success;
	}

	private void castSpells(SpellData data, boolean passTargeting) {
		for (Subspell spell : spells) spell.subcast(data, passTargeting);
	}

	private double horizontalAngle(Location from, Location to) {
		Location startLoc = from.clone();
		Location endLoc = to.clone();

		startLoc.setY(0.0D);
		startLoc.setPitch(0.0F);

		endLoc.setY(0.0D);
		endLoc.setPitch(0.0F);

		Vector direction = endLoc.toVector().subtract(startLoc.toVector()).normalize();

		return AccurateMath.toDegrees(direction.angle(startLoc.getDirection()));
	}

}
