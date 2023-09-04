package com.nisovin.magicspells.spells;

import org.bukkit.Location;

import com.nisovin.magicspells.util.*;
import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.MagicSpells;

public class LocationSpell extends InstantSpell {

	private MagicLocation location;

	private Subspell spellToCast;
	private String spellToCastName;

	public LocationSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		String s = getConfigString("location", "world,0,0,0");
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
			location = new MagicLocation(world, x, y, z, yaw, pitch);
		} catch (Exception e) {
			MagicSpells.error("LocationSpell '" + spellName + "' has an invalid location defined!");
		}

		spellToCastName = getConfigString("spell", "");
	}

	@Override
	public void initialize() {
		super.initialize();

		spellToCast = new Subspell(spellToCastName);
		if (!spellToCast.process()) {
			MagicSpells.error("LocationSpell '" + internalName + "' has an invalid spell defined!");
			spellToCast = null;
		}
		spellToCastName = null;
	}

	@Override
	public CastResult cast(SpellData data) {
		Location loc = location.getLocation();
		if (loc == null) return new CastResult(PostCastAction.ALREADY_HANDLED, data);

		data = data.location(loc);
		if (spellToCast != null) spellToCast.subcast(data);
		playSpellEffects(data);

		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

}
