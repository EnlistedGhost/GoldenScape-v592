package com.rs.game.player.controllers;

import com.rs.game.Animation;
import com.rs.game.WorldObject;
import com.rs.game.WorldTile;
import com.rs.game.player.Player;
import com.rs.game.tasks.WorldTask;
import com.rs.game.tasks.WorldTasksManager;
import com.rs.utils.Utils;

public class JailControler extends Controller {

	@Override
	public void start() {
		if (player.getJailed() > Utils.currentTimeMillis())
			player.sendRandomJail(player);
	}

	@Override
	public void process() {
		if (player.getJailed() <= Utils.currentTimeMillis()) {
			player.getControlerManager().getControler().removeControler();
			player.getSocialManager().sendGameMessage("Your account has been unjailed.", true);
			player.setNextWorldTile(new WorldTile(2677, 10379, 0));
		}
	}

	public static void stopControler(Player p) {
		p.getControlerManager().getControler().removeControler();
	}

	@Override
	public boolean sendDeath() {
		WorldTasksManager.schedule(new WorldTask() {
			int loop;

			@Override
			public void run() {
				player.stopAll();
				if (loop == 0) {
					player.setNextAnimation(new Animation(2304));
				} else if (loop == 1) {
					player.getSocialManager().sendGameMessage("Oh dear, you have died.");
				} else if (loop == 3) {
					player.setNextAnimation(new Animation(-1));
					player.reset();
					player.setCanPvp(false);
					player.sendRandomJail(player);
					player.getControlerManager().startControler("JailControler");
					player.unlock();
				}
				loop++;
			}
		}, 0, 1);
		return true;
	}

	@Override
	public boolean login() {

		return false;
	}

	@Override
	public boolean logout() {

		return false;
	}

	@Override
	public boolean processMagicTeleport(WorldTile toTile) {
		player.getSocialManager().sendGameMessage("You are currently jailed for your delinquent acts.");
		return false;
	}

	@Override
	public boolean processItemTeleport(WorldTile toTile) {
		player.getSocialManager().sendGameMessage("You are currently jailed for your delinquent acts.");
		return false;
	}

	@Override
	public boolean processObjectClick1(WorldObject object) {
		player.getSocialManager().sendGameMessage("You cannot do any activities while being jailed.");
		return false;
	}

}
