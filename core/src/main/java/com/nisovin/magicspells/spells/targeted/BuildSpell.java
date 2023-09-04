package com.nisovin.magicspells.spells.targeted;

import java.util.Set;
import java.util.List;
import java.util.HashSet;
import java.util.ArrayList;

import org.bukkit.Effect;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.ItemStack;

import com.nisovin.magicspells.util.*;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spells.TargetedLocationSpell;
import com.nisovin.magicspells.events.MagicSpellsBlockPlaceEvent;

public class BuildSpell extends TargetedSpell implements TargetedLocationSpell {

	private final Set<Material> allowedTypes;

	private final String strCantBuild;
	private final String strInvalidBlock;

	private final ConfigData<Integer> slot;

	private final ConfigData<Boolean> consumeBlock;
	private final ConfigData<Boolean> checkPlugins;
	private final ConfigData<Boolean> playBreakEffect;

	public BuildSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		strCantBuild = getConfigString("str-cant-build", "You can't build there.");
		strInvalidBlock = getConfigString("str-invalid-block", "You can't build that block.");

		slot = getConfigDataInt("slot", 0);

		consumeBlock = getConfigDataBoolean("consume-block", true);
		checkPlugins = getConfigDataBoolean("check-plugins", true);
		playBreakEffect = getConfigDataBoolean("show-effect", true);

		List<String> materials = getConfigStringList("allowed-types", null);
		if (materials == null) {
			materials = new ArrayList<>();
			materials.add("GRASS_BLOCK");
			materials.add("STONE");
			materials.add("DIRT");
		}

		allowedTypes = new HashSet<>();
		for (String str : materials) {
			Material material = Util.getMaterial(str);
			if (material == null) {
				MagicSpells.error("BuildSpell '" + internalName + "' has an invalid material '" + str + "' defined!");
				continue;
			}
			if (!material.isBlock()) {
				MagicSpells.error("BuildSpell '" + internalName + "' has a non block material '" + str + "' defined!");
				continue;
			}

			allowedTypes.add(material);
		}
	}

	@Override
	public CastResult cast(SpellData data) {
		if (!(data.caster() instanceof Player player)) return new CastResult(PostCastAction.ALREADY_HANDLED, data);

		int slot = this.slot.get(data);

		ItemStack item = player.getInventory().getItem(slot);
		if (item == null || isDenied(item.getType())) return noTarget(strInvalidBlock, data);

		List<Block> blocks = getLastTwoTargetedBlocks(data);
		if (blocks.size() != 2 || blocks.get(1).getType().isAir()) return noTarget(strCantBuild, data);

		Block block = blocks.get(0);
		Block against = blocks.get(1);
		data = data.location(block.getLocation());

		boolean built = build(player, block, against, item, slot, data);
		return built ? new CastResult(PostCastAction.HANDLE_NORMALLY, data) : noTarget(strCantBuild, data);
	}

	@Override
	public CastResult castAtLocation(SpellData data) {
		if (!(data.caster() instanceof Player player)) return new CastResult(PostCastAction.ALREADY_HANDLED, data);

		int slot = this.slot.get(data);

		ItemStack item = player.getInventory().getItem(slot);
		if (item == null || isDenied(item.getType())) return noTarget(strInvalidBlock, data);

		Block block = data.location().getBlock();

		boolean built = build(player, block, block, item, slot, data);
		return built ? new CastResult(PostCastAction.HANDLE_NORMALLY, data) : noTarget(strCantBuild, data);
	}

	private boolean isDenied(Material mat) {
		return !mat.isBlock() || allowedTypes == null || !allowedTypes.contains(mat);
	}

	private boolean build(Player player, Block block, Block against, ItemStack item, int slot, SpellData data) {
		BlockState previousState = block.getState();
		block.setType(item.getType());

		if (checkPlugins.get(data)) {
			MagicSpellsBlockPlaceEvent event = new MagicSpellsBlockPlaceEvent(block, previousState, against, player.getEquipment().getItemInMainHand(), player, true);
			EventUtil.call(event);
			if (event.isCancelled() && block.getType() == item.getType()) {
				previousState.update(true);
				return false;
			}
		}

		if (playBreakEffect.get(data)) block.getWorld().playEffect(block.getLocation(), Effect.STEP_SOUND, block.getType());

		if (consumeBlock.get(data)) {
			int amt = item.getAmount() - 1;
			if (amt > 0) {
				item.setAmount(amt);
				player.getInventory().setItem(slot, item);
			} else player.getInventory().setItem(slot, null);
		}

		playSpellEffects(data);

		return true;
	}

}
