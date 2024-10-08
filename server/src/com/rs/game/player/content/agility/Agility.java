package com.rs.game.player.content.agility;

import com.rs.game.player.Player;
import com.rs.game.player.Skills;

public class Agility {

	public static boolean hasLevel(Player player, int level) {
		if (player.getSkills().getLevel(Skills.AGILITY) < level) {
			player.getSocialManager().sendGameMessage("You need an Agility level of " + level + " to use this obstacle.", true);
			return false;
		}
		return true;
	}

}
