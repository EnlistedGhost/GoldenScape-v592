package com.rs.game.player.actions.mining;

import com.rs.cache.loaders.ItemDefinitions;
import com.rs.game.Animation;
import com.rs.game.World;
import com.rs.game.WorldObject;
import com.rs.game.player.Player;
import com.rs.game.player.Skills;
import com.rs.utils.Utils;

public class EssenceMining extends MiningBase {

	public static enum EssenceDefinitions {
		Rune_Essence(1, 5, 1436, 1, 1), Pure_Essence(30, 5, 7936, 1, 1);
		private int level;
		private double xp;
		private int oreId;
		private int oreBaseTime;
		private int oreRandomTime;

		private EssenceDefinitions(int level, double xp, int oreId, int oreBaseTime, int oreRandomTime) {
			this.level = level;
			this.xp = xp;
			this.oreId = oreId;
			this.oreBaseTime = oreBaseTime;
			this.oreRandomTime = oreRandomTime;
		}

		public int getLevel() {
			return level;
		}

		public double getXp() {
			return xp;
		}

		public int getOreId() {
			return oreId;
		}

		public int getOreBaseTime() {
			return oreBaseTime;
		}

		public int getOreRandomTime() {
			return oreRandomTime;
		}

	}

	private WorldObject rock;
	private EssenceDefinitions definitions;
	private PickAxeDefinitions axeDefinitions;

	public EssenceMining(WorldObject rock, EssenceDefinitions definitions) {
		this.rock = rock;
		this.definitions = definitions;
	}

	@Override
	public boolean start(Player player) {
		axeDefinitions = getPickAxeDefinitions(player);
		if (!checkAll(player))
			return false;
		player.getSocialManager().sendGameMessage("You swing your pickaxe at the rock.");
		setActionDelay(player, getMiningDelay(player));
		return true;
	}

	private int getMiningDelay(Player player) {
		int mineTimer = definitions.getOreBaseTime() - player.getSkills().getLevel(Skills.MINING) - Utils.getRandom(axeDefinitions.getPickAxeTime());
		if (mineTimer < 1 + definitions.getOreRandomTime())
			mineTimer = 1 + Utils.getRandom(definitions.getOreRandomTime());
		return mineTimer;
	}

	private boolean checkAll(Player player) {
		if (axeDefinitions == null) {
			player.getSocialManager().sendGameMessage("You do not have a pickaxe or do not have the required level to use the pickaxe.");
			return false;
		}
		if (!hasMiningLevel(player))
			return false;
		if (!player.getInventory().hasFreeSlots()) {
			player.getSocialManager().sendGameMessage("Not enough space in your inventory.");
			return false;
		}
		return true;
	}

	private boolean hasMiningLevel(Player player) {
		if (definitions.getLevel() > player.getSkills().getLevel(Skills.MINING)) {
			player.getSocialManager().sendGameMessage("You need a mining level of " + definitions.getLevel() + " to mine this rock.");
			return false;
		}
		return true;
	}

	@Override
	public boolean process(Player player) {
		player.setNextAnimation(new Animation(axeDefinitions.getAnimationId()));
		return checkRock(player);
	}

	@Override
	public int processWithDelay(Player player) {
		addOre(player);
		if (!player.getInventory().hasFreeSlots()) {
			player.setNextAnimation(new Animation(-1));
			player.getSocialManager().sendGameMessage("Not enough space in your inventory.");
			return -1;
		}
		return getMiningDelay(player);
	}

	private void addOre(Player player) {
		double xpBoost = 1.0;
		player.getSkills().addXp(Skills.MINING, definitions.getXp() * xpBoost);
		player.getInventory().addItem(definitions.getOreId(), 1);
		String oreName = ItemDefinitions.getItemDefinitions(definitions.getOreId()).getName().toLowerCase();
		player.getSocialManager().sendGameMessage("You mine some " + oreName + ".", true);
	}

	private boolean checkRock(Player player) {
		return World.containsObjectWithId(rock, rock.getId());
	}
}
