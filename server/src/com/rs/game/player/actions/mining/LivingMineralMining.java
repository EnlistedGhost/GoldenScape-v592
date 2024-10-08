package com.rs.game.player.actions.mining;

import com.rs.game.Animation;
import com.rs.game.npc.others.LivingRock;
import com.rs.game.player.Player;
import com.rs.game.player.Skills;
import com.rs.utils.Utils;

public class LivingMineralMining extends MiningBase {

	private LivingRock rock;
	private PickAxeDefinitions axeDefinitions;

	public LivingMineralMining(LivingRock rock) {
		this.rock = rock;
	}

	@Override
	public boolean start(Player player) {
		axeDefinitions = getPickAxeDefinitions(player);
		if (!checkAll(player))
			return false;
		setActionDelay(player, getMiningDelay(player));
		return true;
	}

	private int getMiningDelay(Player player) {
		int oreBaseTime = 50;
		int oreRandomTime = 20;
		int mineTimer = oreBaseTime - player.getSkills().getLevel(Skills.MINING) - Utils.getRandom(axeDefinitions.getPickAxeTime());
		if (mineTimer < 1 + oreRandomTime)
			mineTimer = 1 + Utils.getRandom(oreRandomTime);
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
		if (!rock.canMine(player)) {
			player.getSocialManager().sendGameMessage("You must wait at least one minute before you can mine a living rock creature that someone else defeated.");
			return false;
		}
		return true;
	}

	private boolean hasMiningLevel(Player player) {
		if (73 > player.getSkills().getLevel(Skills.MINING)) {
			player.getSocialManager().sendGameMessage("You need a mining level of 73 to mine this rock.");
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
		rock.takeRemains();
		player.setNextAnimation(new Animation(-1));
		return -1;
	}

	private void addOre(Player player) {
		player.getSkills().addXp(Skills.MINING, 25);
		player.getInventory().addItem(15263, Utils.random(5, 25));
		player.getSocialManager().sendGameMessage("You manage to mine some living minerals.", true);
	}

	private boolean checkRock(Player player) {
		return !rock.hasFinished();
	}
}
