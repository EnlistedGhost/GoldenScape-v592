package com.rs.game.player;

import java.security.MessageDigest;

import com.rs.Settings;
import com.rs.game.SecondaryBar;
import com.rs.game.World;
import com.rs.io.OutputStream;
import com.rs.utils.Utils;
import com.rs.utils.huffman.Huffman;

public final class LocalPlayerUpdate {

	/**
	 * The maximum amount of local players being added per tick. This is to decrease time it takes to load crowded places (such
	 * as home).
	 */
	private static final int MAX_PLAYER_ADD = 15;

	private Player player;

	private byte[] slotFlags;

	private Player[] localPlayers;
	private int[] localPlayersIndexes;
	private int localPlayersIndexesCount;

	private int[] outPlayersIndexes;
	private int outPlayersIndexesCount;

	private int[] regionHashes;

	private byte[][] cachedAppearencesHashes;
	private int totalRenderDataSentLength;

	/**
	 * The amount of local players added this tick.
	 */
	private int localAddedPlayers;

	public Player[] getLocalPlayers() {
		return localPlayers;
	}

	public boolean needAppearenceUpdate(int index, byte[] hash) {
		if (totalRenderDataSentLength > ((Settings.PACKET_SIZE_LIMIT - 500) / 2) || hash == null)
			return false;
		return cachedAppearencesHashes[index] == null || !MessageDigest.isEqual(cachedAppearencesHashes[index], hash);
	}

	public LocalPlayerUpdate(Player player) {
		this.player = player;
		slotFlags = new byte[2048];
		localPlayers = new Player[2048];
		localPlayersIndexes = new int[Settings.PLAYERS_LIMIT];
		outPlayersIndexes = new int[2048];
		regionHashes = new int[2048];
		cachedAppearencesHashes = new byte[Settings.PLAYERS_LIMIT][];
	}

	public void init(OutputStream stream) {
		stream.initBitAccess();
		stream.writeBits(30, player.getTileHash());
		localPlayers[player.getIndex()] = player;
		localPlayersIndexes[localPlayersIndexesCount++] = player.getIndex();
		for (int playerIndex = 1; playerIndex < 2048; playerIndex++) {
			if (playerIndex == player.getIndex())
				continue;
			Player player = World.getPlayers().get(playerIndex);
			stream.writeBits(18, regionHashes[playerIndex] = player == null ? 0 : player.getRegionHash());
			outPlayersIndexes[outPlayersIndexesCount++] = playerIndex;

		}
		stream.finishBitAccess();
	}

	private boolean needsRemove(Player p) {
		return (p.hasFinished() || !player.withinDistance(p, player.hasLargeSceneView() ? 126 : 14));
	}

	private boolean needsAdd(Player p) {
		return p != null && !p.hasFinished() && player.withinDistance(p, player.hasLargeSceneView() ? 126 : 14) && localAddedPlayers < MAX_PLAYER_ADD;
	}

	private void updateRegionHash(OutputStream stream, int lastRegionHash, int currentRegionHash) {
		int lastRegionX = lastRegionHash >> 8;
		int lastRegionY = 0xff & lastRegionHash;
		int lastPlane = lastRegionHash >> 16;
		int currentRegionX = currentRegionHash >> 8;
		int currentRegionY = 0xff & currentRegionHash;
		int currentPlane = currentRegionHash >> 16;
		int planeOffset = currentPlane - lastPlane;
		if (lastRegionX == currentRegionX && lastRegionY == currentRegionY) {
			stream.writeBits(2, 1);
			stream.writeBits(2, planeOffset);
		} else if (Math.abs(currentRegionX - lastRegionX) <= 1 && Math.abs(currentRegionY - lastRegionY) <= 1) {
			int opcode;
			int dx = currentRegionX - lastRegionX;
			int dy = currentRegionY - lastRegionY;
			if (dx == -1 && dy == -1)
				opcode = 0;
			else if (dx == 1 && dy == -1)
				opcode = 2;
			else if (dx == -1 && dy == 1)
				opcode = 5;
			else if (dx == 1 && dy == 1)
				opcode = 7;
			else if (dy == -1)
				opcode = 1;
			else if (dx == -1)
				opcode = 3;
			else if (dx == 1)
				opcode = 4;
			else
				opcode = 6;
			stream.writeBits(2, 2);
			stream.writeBits(5, (planeOffset << 3) + (opcode & 0x7));
		} else {
			int xOffset = currentRegionX - lastRegionX;
			int yOffset = currentRegionY - lastRegionY;
			stream.writeBits(2, 3);
			stream.writeBits(18, (yOffset & 0xff) + ((xOffset & 0xff) << 8) + (planeOffset << 16));
		}
	}

	private void processOutsidePlayers(OutputStream stream, OutputStream updateBlockData, boolean nsn2) {
		stream.initBitAccess();
		int skip = 0;
		localAddedPlayers = 0;
		for (int i = 0; i < outPlayersIndexesCount; i++) {
			int playerIndex = outPlayersIndexes[i];
			if (nsn2 ? (0x1 & slotFlags[playerIndex]) == 0 : (0x1 & slotFlags[playerIndex]) != 0)
				continue;
			if (skip > 0) {
				skip--;
				slotFlags[playerIndex] = (byte) (slotFlags[playerIndex] | 2);
				continue;
			}
			Player p = World.getPlayers().get(playerIndex);
			if (needsAdd(p)) {
				stream.writeBits(1, 1);
				stream.writeBits(2, 0); // request add
				int hash = p.getRegionHash();
				if (hash == regionHashes[playerIndex])
					stream.writeBits(1, 0);
				else {
					stream.writeBits(1, 1);
					updateRegionHash(stream, regionHashes[playerIndex], hash);
					regionHashes[playerIndex] = hash;
				}
				stream.writeBits(6, p.getXInRegion());
				stream.writeBits(6, p.getYInRegion());
				boolean needAppearenceUpdate = needAppearenceUpdate(p.getIndex(), p.getAppearence().getMD5AppeareanceDataHash());
				appendUpdateBlock(p, updateBlockData, needAppearenceUpdate, true);
				stream.writeBits(1, 1);
				localAddedPlayers++;
				localPlayers[p.getIndex()] = p;
				slotFlags[playerIndex] = (byte) (slotFlags[playerIndex] | 2);
			} else {
				int hash = p == null ? regionHashes[playerIndex] : p.getRegionHash();
				if (p != null && hash != regionHashes[playerIndex]) {
					stream.writeBits(1, 1);
					updateRegionHash(stream, regionHashes[playerIndex], hash);
					regionHashes[playerIndex] = hash;
				} else {
					stream.writeBits(1, 0); // no update needed
					for (int i2 = i + 1; i2 < outPlayersIndexesCount; i2++) {
						int p2Index = outPlayersIndexes[i2];
						if (nsn2 ? (0x1 & slotFlags[p2Index]) == 0 : (0x1 & slotFlags[p2Index]) != 0)
							continue;
						Player p2 = World.getPlayers().get(p2Index);
						if (needsAdd(p2) || (p2 != null && p2.getRegionHash() != regionHashes[p2Index]))
							break;
						skip++;
					}
					skipPlayers(stream, skip);
					slotFlags[playerIndex] = (byte) (slotFlags[playerIndex] | 2);
				}
			}
		}
		stream.finishBitAccess();
	}

	private void processLocalPlayers(OutputStream stream, OutputStream updateBlockData, boolean nsn0) {
		stream.initBitAccess();
		int skip = 0;
		for (int i = 0; i < localPlayersIndexesCount; i++) {
			int playerIndex = localPlayersIndexes[i];
			if (nsn0 ? (0x1 & slotFlags[playerIndex]) != 0 : (0x1 & slotFlags[playerIndex]) == 0)
				continue;
			if (skip > 0) {
				skip--;
				slotFlags[playerIndex] = (byte) (slotFlags[playerIndex] | 2);
				continue;
			}
			Player p = localPlayers[playerIndex];
			if (needsRemove(p)) {
				stream.writeBits(1, 1); // needs update
				stream.writeBits(1, 0); // no masks update needeed
				stream.writeBits(2, 0); // request remove
				regionHashes[playerIndex] = p.getLastWorldTile() == null ? p.getRegionHash() : p.getLastWorldTile().getRegionHash();
				int hash = p.getRegionHash();
				if (hash == regionHashes[playerIndex])
					stream.writeBits(1, 0);
				else {
					stream.writeBits(1, 1);
					updateRegionHash(stream, regionHashes[playerIndex], hash);
					regionHashes[playerIndex] = hash;
				}
				localPlayers[playerIndex] = null;
			} else {
				boolean needAppearenceUpdate = needAppearenceUpdate(p.getIndex(), p.getAppearence().getMD5AppeareanceDataHash());
				boolean needUpdate = p.needMasksUpdate() || needAppearenceUpdate;
				if (needUpdate)
					appendUpdateBlock(p, updateBlockData, needAppearenceUpdate, false);
				if (p.hasTeleported()) {
					stream.writeBits(1, 1); // needs update
					stream.writeBits(1, needUpdate ? 1 : 0);
					stream.writeBits(2, 3);
					int xOffset = p.getX() - p.getLastWorldTile().getX();
					int yOffset = p.getY() - p.getLastWorldTile().getY();
					int planeOffset = p.getPlane() - p.getLastWorldTile().getPlane();
					if (Math.abs(p.getX() - p.getLastWorldTile().getX()) <= 14 // 14
																				// for
																				// safe
							&& Math.abs(p.getY() - p.getLastWorldTile().getY()) <= 14) { // 14
																							// for
																							// safe
						stream.writeBits(1, 0);
						if (xOffset < 0) // viewport used to be 15 now 16
							xOffset += 32;
						if (yOffset < 0)
							yOffset += 32;
						stream.writeBits(12, yOffset + (xOffset << 5) + (planeOffset << 10));
					} else {
						stream.writeBits(1, 1);
						stream.writeBits(30, (yOffset & 0x3fff) + ((xOffset & 0x3fff) << 14) + ((planeOffset & 0x3) << 28));
					}
				} else if (p.getNextWalkDirection() != -1) {
					int dx = Utils.DIRECTION_DELTA_X[p.getNextWalkDirection()];
					int dy = Utils.DIRECTION_DELTA_Y[p.getNextWalkDirection()];
					boolean running;
					int opcode;
					if (p.getNextRunDirection() != -1) {
						dx += Utils.DIRECTION_DELTA_X[p.getNextRunDirection()];
						dy += Utils.DIRECTION_DELTA_Y[p.getNextRunDirection()];
						opcode = Utils.getPlayerRunningDirection(dx, dy);
						if (opcode == -1) {
							running = false;
							opcode = Utils.getPlayerWalkingDirection(dx, dy);
						} else
							running = true;
					} else {
						running = false;
						opcode = Utils.getPlayerWalkingDirection(dx, dy);
					}
					stream.writeBits(1, 1);
					if ((dx == 0 && dy == 0)) {
						stream.writeBits(1, 1); // quick fix
						stream.writeBits(2, 0);
						if (!needUpdate) // hasnt been sent yet
							appendUpdateBlock(p, updateBlockData, needAppearenceUpdate, false);
					} else {
						stream.writeBits(1, needUpdate ? 1 : 0);
						stream.writeBits(2, running ? 2 : 1);
						stream.writeBits(running ? 4 : 3, opcode);
					}
				} else if (needUpdate) {
					stream.writeBits(1, 1); // needs update
					stream.writeBits(1, 1);
					stream.writeBits(2, 0);
				} else { // skip
					stream.writeBits(1, 0); // no update needed
					for (int i2 = i + 1; i2 < localPlayersIndexesCount; i2++) {
						int p2Index = localPlayersIndexes[i2];
						if (nsn0 ? (0x1 & slotFlags[p2Index]) != 0 : (0x1 & slotFlags[p2Index]) == 0)
							continue;
						Player p2 = localPlayers[p2Index];
						if (needsRemove(p2) || p2.hasTeleported() || p2.getNextWalkDirection() != -1 || (p2.needMasksUpdate() || needAppearenceUpdate(p2.getIndex(), p2.getAppearence().getMD5AppeareanceDataHash())))
							break;
						skip++;
					}
					skipPlayers(stream, skip);
					slotFlags[playerIndex] = (byte) (slotFlags[playerIndex] | 2);
				}

			}
		}
		stream.finishBitAccess();
	}

	private void skipPlayers(OutputStream stream, int amount) {
		stream.writeBits(2, amount == 0 ? 0 : amount > 255 ? 3 : (amount > 31 ? 2 : 1));
		if (amount > 0)
			stream.writeBits(amount > 255 ? 11 : (amount > 31 ? 8 : 5), amount);
	}

	private void appendUpdateBlock(Player p, OutputStream data, boolean needAppearenceUpdate, boolean added) {
		int maskData = 0;

		if (added || (p.getNextFaceWorldTile() != null && p.getNextRunDirection() == -1 && p.getNextWalkDirection() == -1)) // 1
			maskData |= 0x40;

		if (p.getNextForceTalk() != null) // 2
			maskData |= 0x400;

		if (p.getNextSecondaryBar() != null) // 4
			maskData |= 0x800;

		if (p.getNextGraphics2() != null) // 6
			maskData |= 0x40000;

		if (added || p.isUpdateMovementType()) // 7
			maskData |= 0x1;

		if (p.getNextFaceEntity() != -2 || (added && p.getLastFaceEntity() != -1)) // 8
			maskData |= 0x2;

		if (p.getNextPublicChatMessage() != null) // 9
			maskData |= 0x80;

		if (p.getNextGraphics1() != null) // 10
			maskData |= 0x1000;

		if (p.getNextAnimation() != null) // 11
			maskData |= 0x8;

		if (p.getTemporaryMoveType() != -1) // 13
			maskData |= 0x200;

		if (p.getNextHit1() != null) // 14
			maskData |= 0x10;

		if (needAppearenceUpdate) // 16
			maskData |= 0x20;

		if (p.getNextHit2() != null) // 17
			maskData |= 0x100;

		if (maskData > 128)
			maskData |= 0x4;
		if (maskData > 32768)
			maskData |= 0x2000;
		data.writeByte(maskData);
		if (maskData > 128)
			data.writeByte(maskData >> 8);
		if (maskData > 32768)
			data.writeByte(maskData >> 16);

		if (added || (p.getNextFaceWorldTile() != null && p.getNextRunDirection() == -1 && p.getNextWalkDirection() == -1)) // 1
			applyFaceDirectionMask(p, data);

		if (p.getNextForceTalk() != null) // 2
			applyForceTalkMask(p, data);

		if (p.getNextSecondaryBar() != null) // 4
			applySecondaryBarMask(p, data);

		if (p.getNextGraphics2() != null) // 6
			applyGraphicsMask2(p, data);

		if (added || p.isUpdateMovementType()) // 7
			applyMoveTypeMask(p, data);

		if (p.getNextFaceEntity() != -2 || (added && p.getLastFaceEntity() != -1)) // 8
			applyFaceEntityMask(p, data);

		if (p.getNextPublicChatMessage() != null) // 9
			applyPublicChatMessageMask(p, data);

		if (p.getNextGraphics1() != null) // 10
			applyGraphicsMask1(p, data);

		if (p.getNextAnimation() != null) // 11
			applyAnimationMask(p, data);

		if (p.getTemporaryMoveType() != -1) // 13
			applyTemporaryMoveTypeMask(p, data);

		if (p.getNextHit1() != null) // 14
			applyHitsMask(p, data, false);

		if (needAppearenceUpdate) // 16
			applyAppearanceMask(p, data);

		if (p.getNextHit2() != null) // 17
			applyHitsMask(p, data, true);

		/*
		 * 
		 * 
		 * 
		 * 
		 * 
		 * 
		 * 
		 * 
		 * 
		 * 
		 * 
		 * 
		 * // force movement here
		 * 
		 * 
		 * 
		 * 
		 * 
		 * 
		 * 
		 * 
		 * 
		 * 
		 * 
		 * 
		 * 
		 * 
		 * 
		 * 
		 * 
		 * /
		 */
	}

	private void applyPublicChatMessageMask(Player p, OutputStream data) {
		data.writeShort128((p.getNextPublicChatMessage().getColors() << 8) | p.getNextPublicChatMessage().getEffects());
		data.writeByte128(p.getRights());
		if (p.getNextPublicChatMessage() instanceof QuickChatMessage) {
			// TODO quick chat

			// QuickChatMessage message = (QuickChatMessage) p
			// .getNextPublicChatMessage();
			// data.write128Byte(message.getMessage(false) == null ? 2 : message
			// .getMessage(false).getBytes().length + 2);
			// data.writeShort(message.getFileId());
			// if (message.getMessage(false) != null)
			// data.writeBytes(message.getMessage(false).getBytes());
		} else {
			byte[] chatStr = new byte[256];
			chatStr[0] = (byte) p.getNextPublicChatMessage().getMessage(false).length();
			int offset = 1 + Huffman.encryptMessage(1, p.getNextPublicChatMessage().getMessage(false).length(), chatStr, 0, p.getNextPublicChatMessage().getMessage(false).getBytes());
			data.writeByte128(offset);
			for (int index = 0; index < offset; index++)
				data.writeByte(chatStr[index]);
		}
	}

	private void applySecondaryBarMask(Player p, OutputStream data) {
		SecondaryBar bar = p.getNextSecondaryBar();
		boolean permanant = bar.isPermenant();
		int unknownV = bar.getTotalUnits();
		data.writeShort128((permanant ? 8000 : 0) | (unknownV & 0x7fff));
		data.writeByte(bar.getBeginningOffset());
		data.writeByte128(bar.getIncrementalUnits());

	}

	private void applyTemporaryMoveTypeMask(Player p, OutputStream data) {
		data.writeByteC(p.getTemporaryMoveType());
	}

	private void applyMoveTypeMask(Player p, OutputStream data) {
		data.writeByteC(p.getRun() ? 2 : 1);
	}

	private void applyForceTalkMask(Player p, OutputStream data) {
		data.writeString(p.getNextForceTalk().getText());
	}

	private void applyHitsMask(Player p, OutputStream data, boolean secondary) {
		if (secondary) {
			data.writeSmart(p.getNextHit2().getDamage());
			data.write128Byte(p.getNextHit2().getMark());
		} else {
			data.writeSmart(p.getNextHit1().getDamage());
			data.writeByte128(p.getNextHit1().getMark());
			int amtHP = p.getHitpoints();
			int maxHP = p.getMaxHitpoints();
			if (amtHP > maxHP)
				amtHP = maxHP;
			data.writeByte128(amtHP * 255 / maxHP);
		}

	}

	private void applyFaceEntityMask(Player p, OutputStream data) {
		data.writeShortLE128(p.getNextFaceEntity() == -2 ? p.getLastFaceEntity() : p.getNextFaceEntity());
	}

	private void applyFaceDirectionMask(Player p, OutputStream data) {
		data.writeShort(p.getDirection()); // also works as face tile as
											// dir
		// calced on setnextfacetile
	}

	private void applyGraphicsMask1(Player p, OutputStream data) {
		data.writeShortLE(p.getNextGraphics1().getId());
		data.writeInt(p.getNextGraphics1().getSettingsHash());
		data.write128Byte(p.getNextGraphics1().getSettings2Hash());
	}

	private void applyGraphicsMask2(Player p, OutputStream data) {
		data.writeShortLE(p.getNextGraphics2().getId());
		data.writeIntV2(p.getNextGraphics2().getSettingsHash());
		data.writeByteC(p.getNextGraphics2().getSettings2Hash());
	}

	private void applyAnimationMask(Player p, OutputStream data) {
		// for (int id : p.getNextAnimation().getIds())
		data.writeShort(p.getNextAnimation().getIds()[0]);
		data.writeByte(p.getNextAnimation().getSpeed());
	}

	private void applyAppearanceMask(Player p, OutputStream data) {
		byte[] renderData = p.getAppearence().getAppeareanceData();
		totalRenderDataSentLength += renderData.length;
		cachedAppearencesHashes[p.getIndex()] = p.getAppearence().getMD5AppeareanceDataHash();
		data.writeByte(renderData.length);
		data.writeBytes(renderData, 0, renderData.length);

	}

	@SuppressWarnings("unused")
	private void applyForceMovementMask(Player p, OutputStream data) {
		data.writeByteC(p.getNextForceMovement().getToFirstTile().getX() - p.getX());
		data.writeByte(p.getNextForceMovement().getToFirstTile().getY() - p.getY());
		data.writeByte(p.getNextForceMovement().getToSecondTile() == null ? 0 : p.getNextForceMovement().getToSecondTile().getX() - p.getX());
		data.writeByte128(p.getNextForceMovement().getToSecondTile() == null ? 0 : p.getNextForceMovement().getToSecondTile().getY() - p.getY());
		data.writeShort(p.getNextForceMovement().getFirstTileTicketDelay() * 30);
		data.writeShort(p.getNextForceMovement().getToSecondTile() == null ? 0 : p.getNextForceMovement().getSecondTileTicketDelay() * 30);
		data.writeShort(p.getNextForceMovement().getDirection());
	}

	public OutputStream createPacketAndProcess() {
		OutputStream stream = new OutputStream();
		OutputStream updateBlockData = new OutputStream();
		stream.writePacketVarShort(player, 97);
		processLocalPlayers(stream, updateBlockData, true);
		processLocalPlayers(stream, updateBlockData, false);
		processOutsidePlayers(stream, updateBlockData, true);
		processOutsidePlayers(stream, updateBlockData, false);
		stream.writeBytes(updateBlockData.getBuffer(), 0, updateBlockData.getOffset());
		stream.endPacketVarShort();
		totalRenderDataSentLength = 0;
		localPlayersIndexesCount = 0;
		outPlayersIndexesCount = 0;
		for (int playerIndex = 1; playerIndex < 2048; playerIndex++) {
			slotFlags[playerIndex] >>= 1;
			Player player = localPlayers[playerIndex];
			if (player == null)
				outPlayersIndexes[outPlayersIndexesCount++] = playerIndex;
			else
				localPlayersIndexes[localPlayersIndexesCount++] = playerIndex;
		}
		return stream;
	}

}