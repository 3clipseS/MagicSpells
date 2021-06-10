package com.nisovin.magicspells.spells.passive;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerBedLeaveEvent;

import com.nisovin.magicspells.util.OverridePriority;
import com.nisovin.magicspells.spells.passive.util.PassiveListener;

// No trigger variable is used here
public class LeaveBedListener extends PassiveListener {

	@Override
	public void initialize(String var) {

	}

	@OverridePriority
	@EventHandler
	public void onDeath(PlayerBedLeaveEvent event) {
		if (!isCancelStateOk(event.isCancelled())) return;

		Player caster = event.getPlayer();
		if (!hasSpell(event.getPlayer()) || !canTrigger(caster)) return;

		boolean casted = passiveSpell.activate(event.getPlayer());
		if (cancelDefaultAction(casted)) event.setCancelled(true);
	}

}
