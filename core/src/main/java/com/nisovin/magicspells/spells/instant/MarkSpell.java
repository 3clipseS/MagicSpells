package com.nisovin.magicspells.spells.instant;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;
import java.util.Scanner;
import java.io.FileWriter;
import java.io.BufferedWriter;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import com.nisovin.magicspells.util.*;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.spells.TargetedLocationSpell;

public class MarkSpell extends InstantSpell implements TargetedLocationSpell {

	private Map<UUID, MagicLocation> marks;

	private MagicLocation defaultMark;

	private boolean permanentMarks;
	private boolean enableDefaultMarks;
	private boolean useAsRespawnLocation;

	public MarkSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		marks = new HashMap<>();

		permanentMarks = getConfigBoolean("permanent-marks", true);
		enableDefaultMarks = getConfigBoolean("enable-default-marks", false);
		useAsRespawnLocation = getConfigBoolean("use-as-respawn-location", false);

		if (enableDefaultMarks) {
			String s = getConfigString("default-mark", "world,0,0,0");
			try {
				String[] split = s.split(",");
				String world = split[0];
				double x = Double.parseDouble(split[1]);
				double y = Double.parseDouble(split[2]);
				double z = Double.parseDouble(split[3]);
				float yaw = 0;
				float pitch = 0;
				if (split.length > 4) yaw = Float.parseFloat(split[4]);
				if (split.length > 5) pitch = Float.parseFloat(split[5]);
				defaultMark = new MagicLocation(world, x, y, z, yaw, pitch);
			} catch (Exception e) {
				MagicSpells.error("MarkSpell '" + internalName + "' has an invalid default-mark defined!");
				MagicSpells.error("Invalid default mark on MarkSpell '" + spellName + '\'');
			}
		}

		if (permanentMarks) loadMarks();
	}

	@Override
	public CastResult cast(SpellData data) {
		marks.put(getKey(data.caster()), new MagicLocation(data.caster().getLocation()));
		if (permanentMarks) saveMarks();
		playSpellEffects(data);

		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public CastResult castAtLocation(SpellData data) {
		if (!data.hasCaster()) return new CastResult(PostCastAction.ALREADY_HANDLED, data);

		marks.put(getKey(data.caster()), new MagicLocation(data.location()));
		if (permanentMarks) saveMarks();
		playSpellEffects(data);

		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerQuit(PlayerQuitEvent event) {
		if (!permanentMarks) marks.remove(getKey(event.getPlayer()));
	}

	@EventHandler
	public void onPlayerRespawn(PlayerRespawnEvent event) {
		if (!useAsRespawnLocation) return;
		MagicLocation loc = marks.get(getKey(event.getPlayer()));
		if (loc != null) event.setRespawnLocation(loc.getLocation());
		else if (enableDefaultMarks && defaultMark != null) event.setRespawnLocation(defaultMark.getLocation());
	}

	public MagicLocation getDefaultMark() {
		return defaultMark;
	}

	public void setDefaultMark(MagicLocation defaultMark) {
		this.defaultMark = defaultMark;
	}

	public boolean shouldMarksBePermanent() {
		return permanentMarks;
	}

	public void setPermanentMarks(boolean permanentMarks) {
		this.permanentMarks = permanentMarks;
	}

	public boolean areDefaultMarksEnabled() {
		return enableDefaultMarks;
	}

	public void setEnableDefaultMarks(boolean enableDefaultMarks) {
		this.enableDefaultMarks = enableDefaultMarks;
	}

	public boolean shouldUseAsRespawnLocation() {
		return useAsRespawnLocation;
	}

	public void setUseAsRespawnLocation(boolean useAsRespawnLocation) {
		this.useAsRespawnLocation = useAsRespawnLocation;
	}

	public Map<UUID, MagicLocation> getMarks() {
		return marks;
	}

	public void setMarks(Map<UUID, MagicLocation> newMarks) {
		marks = newMarks;
		if (permanentMarks) saveMarks();
	}

	private void loadMarks() {
		try {
			Scanner scanner = new Scanner(new File(MagicSpells.plugin.getDataFolder(), "marks-" + internalName + ".txt"));
			while (scanner.hasNext()) {
				String line = scanner.nextLine();
				if (!line.isEmpty()) {
					try {
						String[] data = line.split(":");
						MagicLocation loc = new MagicLocation(data[1], Double.parseDouble(data[2]), Double.parseDouble(data[3]), Double.parseDouble(data[4]), Float.parseFloat(data[5]), Float.parseFloat(data[6]));
						marks.put(UUID.fromString(data[0].toLowerCase()), loc);
					} catch (Exception e) {
						MagicSpells.plugin.getServer().getLogger().severe("MarkSpell '" + internalName + "' failed to load mark:" + line);
					}
				}
			}
			scanner.close();
		} catch (Exception e) {
			MagicSpells.debug("Failed to load marks file (does it exist?) " + e.getCause() + " " + e.getMessage());
		}
	}

	private void saveMarks() {
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(new File(MagicSpells.plugin.getDataFolder(), "marks-" + internalName + ".txt"), false));
			for (Map.Entry<UUID, MagicLocation> stringMagicLocationEntry : marks.entrySet()) {
				Entity entity = Bukkit.getEntity(stringMagicLocationEntry.getKey());
				if (!(entity instanceof Player)) continue;
				MagicLocation loc = stringMagicLocationEntry.getValue();
				writer.append(stringMagicLocationEntry.getKey().toString())
					.append(String.valueOf(':'))
					.append(loc.getWorld())
					.append(String.valueOf(':'))
					.append(String.valueOf(loc.getX()))
					.append(String.valueOf(':'))
					.append(String.valueOf(loc.getY()))
					.append(String.valueOf(':'))
					.append(String.valueOf(loc.getZ()))
					.append(String.valueOf(':'))
					.append(String.valueOf(loc.getYaw()))
					.append(String.valueOf(':'))
					.append(String.valueOf(loc.getPitch()));
				writer.newLine();
			}
			writer.close();
		} catch (Exception e) {
			MagicSpells.plugin.getServer().getLogger().severe("MagicSpells: Error saving marks");
		}
	}

	public UUID getKey(LivingEntity livingEntity) {
		if (livingEntity == null) return null;
		return livingEntity.getUniqueId();
	}

	public boolean usesDefaultMark() {
		return enableDefaultMarks;
	}

	public Location getEffectiveMark(LivingEntity livingEntity) {
		MagicLocation m = marks.get(getKey(livingEntity));
		if (m == null) {
			if (enableDefaultMarks) return defaultMark.getLocation();
			return null;
		}
		return m.getLocation();
	}

	public Location getEffectiveMark(String player) {
		Player pl = Bukkit.getPlayer(player);
		if (pl == null) return null;
		if (!pl.isOnline()) return null;
		MagicLocation m = marks.get(pl.getUniqueId());
		if (m == null) {
			if (enableDefaultMarks) return defaultMark.getLocation();
			return null;
		}
		return m.getLocation();
	}

}
