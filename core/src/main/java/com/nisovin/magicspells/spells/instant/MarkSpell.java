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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.MagicLocation;
import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedLocationSpell;

public class MarkSpell extends InstantSpell implements TargetedLocationSpell {

	private Map<UUID, MagicLocation> marks;

	private MagicLocation defaultMark = null;

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
	public PostCastAction castSpell(Player player, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			marks.put(getPlayerKey(player), new MagicLocation(player.getLocation()));
			if (permanentMarks) saveMarks();
			playSpellEffects(EffectPosition.CASTER, player);
		}
		return PostCastAction.HANDLE_NORMALLY;		
	}

	@Override
	public boolean castAtLocation(Player caster, Location target, float power) {
		marks.put(getPlayerKey(caster), new MagicLocation(target));
		if (caster != null) playSpellEffects(caster, target);
		return true;
	}

	@Override
	public boolean castAtLocation(Location target, float power) {
		return false;
	}
	
	@EventHandler(priority=EventPriority.MONITOR)
	public void onPlayerQuit(PlayerQuitEvent event) {
		if (!permanentMarks) marks.remove(getPlayerKey(event.getPlayer()));
	}
	
	@EventHandler
	public void onPlayerRespawn(PlayerRespawnEvent event) {
		if (!useAsRespawnLocation) return;
		MagicLocation loc = marks.get(getPlayerKey(event.getPlayer()));
		if (loc != null) event.setRespawnLocation(loc.getLocation());
		else if (enableDefaultMarks && defaultMark != null) event.setRespawnLocation(defaultMark.getLocation());
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
	
	public UUID getPlayerKey(Player player) {
		if (player == null) return null;
		return player.getUniqueId();
	}
	
	public boolean usesDefaultMark() {
		return enableDefaultMarks;
	}
	
	public Location getEffectiveMark(Player player) {
		MagicLocation m = marks.get(getPlayerKey(player));
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
