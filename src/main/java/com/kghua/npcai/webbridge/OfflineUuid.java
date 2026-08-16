package com.kghua.npcai.webbridge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/** 离线模式 UUID = MD5("OfflinePlayer:" + name)，与 Minecraft 原版规则一致 */
public final class OfflineUuid {
    private OfflineUuid() {}

    public static UUID of(String playerName) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                .digest(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8));
            byte[] uuidBytes = new byte[16];
            System.arraycopy(digest, 0, uuidBytes, 0, 16);
            uuidBytes[6] &= 0x0f; // clear version
            uuidBytes[6] |= 0x30; // version 3
            uuidBytes[8] &= 0x3f; // clear variant
            uuidBytes[8] |= 0x80; // variant IETF
            long msb = 0, lsb = 0;
            for (int i = 0; i < 8; i++) msb = (msb << 8) | (uuidBytes[i] & 0xff);
            for (int i = 8; i < 16; i++) lsb = (lsb << 8) | (uuidBytes[i] & 0xff);
            return new UUID(msb, lsb);
        } catch (Exception e) {
            return UUID.nameUUIDFromBytes(playerName.getBytes(StandardCharsets.UTF_8));
        }
    }
}
