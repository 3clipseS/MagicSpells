package com.nisovin.magicspells.spells.passive;

import java.util.EnumSet;

import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDeathEvent;

import com.nisovin.magicspells.util.MobUtil;
import com.nisovin.magicspells.util.OverridePriority;
import com.nisovin.magicspells.spells.passive.util.PassiveListener;

// Trigger variable is optional
// If not specified, it will trigger on any entity type
// If specified, it should be a comma separated list of entity types to trigger on
public class KillListener extends PassiveListener {

	private final EnumSet<EntityType> types = EnumSet.noneOf(EntityType.class);

	@Override
	public void initialize(String var) {
		if (var == null || var.isEmpty()) return;

		String[] split = var.replace(" ", "").split(",");
		for (String s : split) {
			EntityType type = MobUtil.getEntityType(s);
			if (type == null) continue;

			types.add(type);
		}
	}

	@OverridePriority
	@EventHandler
	public void onDeath(EntityDeathEvent event) {
		if (!isCancelStateOk(event.isCancelled())) return;

		LivingEntity killer = event.getEntity().getKiller();
		if (killer == null || !hasSpell(killer) || !canTrigger(killer)) return;
		if (!types.isEmpty() && !types.contains(event.getEntityType())) return;

		boolean casted = passiveSpell.activate(killer, event.getEntity());
		if (cancelDefaultAction(casted)) event.setCancelled(true);
	}

}
