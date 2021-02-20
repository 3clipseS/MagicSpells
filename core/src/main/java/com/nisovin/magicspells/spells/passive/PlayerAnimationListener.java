package com.nisovin.magicspells.spells.passive;

import java.util.Set;
import java.util.HashSet;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.player.PlayerAnimationEvent;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.OverridePriority;
import com.nisovin.magicspells.util.magicitems.MagicItems;
import com.nisovin.magicspells.util.magicitems.MagicItemData;
import com.nisovin.magicspells.spells.passive.util.PassiveListener;

// Trigger variable of a pipe separated list of items to accept
public class PlayerAnimationListener extends PassiveListener {

    private final Set<MagicItemData> items = new HashSet<>();

    @Override
    public void initialize(String var) {
        if (var == null || var.isEmpty()) return;

        String[] split = var.split("\\|");
        for (String s : split) {
            s = s.trim();

            MagicItemData itemData = MagicItems.getMagicItemDataFromString(s);
            if (itemData == null) {
                MagicSpells.error("Invalid magic item '" + s + "' in playeranimate trigger on passive spell '" + passiveSpell.getInternalName() + "'");
                continue;
            }

            items.add(itemData);
        }
    }

    @OverridePriority
    @EventHandler
    public void onAnimate(PlayerAnimationEvent event) {
        if (!isCancelStateOk(event.isCancelled())) return;

        Player caster = event.getPlayer();
        if (!hasSpell(event.getPlayer()) || !canTrigger(caster)) return;

        if (!items.isEmpty()) {
            ItemStack item = caster.getInventory().getItemInMainHand();
            MagicItemData itemData = MagicItems.getMagicItemDataFromItemStack(item);
            if (itemData == null || !contains(itemData)) return;
        }

        boolean casted = passiveSpell.activate(caster);
        if (cancelDefaultAction(casted)) event.setCancelled(true);
    }

    private boolean contains(MagicItemData itemData) {
        for (MagicItemData data : items) {
            if (data.matches(itemData)) return true;
        }
        return false;
    }

}
