package com.nisovin.magicspells.spells.buff;

import java.util.*;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventPriority;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.player.PlayerMoveEvent;

import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.spells.BuffSpell;
import com.nisovin.magicspells.util.MagicConfig;

public class LightwalkSpell extends BuffSpell {
	
	private final Map<UUID, Block> entities;

	private final Set<Material> allowedTypes;

	private int yOffset;
	private BlockData blockType;

	public LightwalkSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		yOffset = getConfigInt("y-offset", 0);

		String blockTypeString = getConfigString("block-type", "light");
		try {
			blockType = Bukkit.createBlockData(blockTypeString.trim().toLowerCase());
		} catch (IllegalArgumentException e) {
			MagicSpells.error("LightwalkSpell " + internalName + " has an invalid 'block-type' defined.");
		}
		if (blockType == null) blockType = Material.LIGHT.createBlockData();

		entities = new HashMap<>();

		allowedTypes = new HashSet<>();

		List<String> blockList = getConfigStringList("allowed-types", Collections.singletonList("air"));
		if (blockList == null) return;
		for (String str : blockList) {
			Material material = Util.getMaterial(str);
			if (material == null) MagicSpells.error("LightwalkSpell " + internalName + " has an invalid block defined: " + str);
			else allowedTypes.add(material);
		}
	}

	private Block getBlockToChange(LivingEntity entity) {
		return entity.getLocation().clone().add(0, yOffset + 0.5, 0).getBlock();
	}

	@Override
	public boolean castBuff(LivingEntity entity, float power, String[] args) {
		entities.put(entity.getUniqueId(), getBlockToChange(entity));
		return true;
	}

	@Override
	public boolean isActive(LivingEntity entity) {
		return entities.containsKey(entity.getUniqueId());
	}

	@Override
	public void turnOffBuff(LivingEntity entity) {
		Block block = entities.remove(entity.getUniqueId());
		if (block == null) return;
		if (!(entity instanceof Player player)) return;
		player.sendBlockChange(block.getLocation(), block.getBlockData());
	}

	@Override
	protected void turnOff() {
		for (UUID id : entities.keySet()) {
			Entity entity = Bukkit.getEntity(id);
			if (!(entity instanceof Player player)) continue;
			Block block = entities.get(id);
			if (block == null) continue;
			player.sendBlockChange(block.getLocation(), block.getBlockData());
		}

		entities.clear();
	}

	@EventHandler(priority=EventPriority.MONITOR)
	public void onPlayerMove(PlayerMoveEvent event) {
		Player player = event.getPlayer();
		if (!isActive(player)) return;

		Block oldBlock = entities.get(player.getUniqueId());
		Block newBlock = getBlockToChange(player);
		if (oldBlock == null) return;
		if (oldBlock.equals(newBlock)) return;
		if (!allowedTypes.contains(newBlock.getType())) return;
		if (isExpired(player)) {
			turnOff(player);
			return;
		}

		addUseAndChargeCost(player);
		entities.put(player.getUniqueId(), newBlock);
		player.sendBlockChange(newBlock.getLocation(), blockType);
		player.sendBlockChange(oldBlock.getLocation(), oldBlock.getBlockData());
	}

	public Map<UUID, Block> getEntities() {
		return entities;
	}

	public Set<Material> getAllowedTypes() {
		return allowedTypes;
	}

	public int getYOffset() {
		return yOffset;
	}

	public void setYOffset(int yOffset) {
		this.yOffset = yOffset;
	}

	public BlockData getBlockType() {
		return blockType;
	}

	public void setBlockType(BlockData blockTyoe) {
		this.blockType = blockTyoe;
	}

}
