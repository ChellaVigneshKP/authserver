package com.chellavignesh.authserver.adminportal.util;

import org.bouncycastle.util.encoders.Hex;

import java.nio.ByteBuffer;
import java.util.UUID;

public class UUIDUtils {
    private UUIDUtils() {
        /*
         * Utility class
         */
    }

    public static UUID asUuid(byte[] bytes) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        long firstLong = bb.getLong();
        long secondLong = bb.getLong();
        return new UUID(firstLong, secondLong);
    }

    public static byte[] asBytes(UUID uuid) {
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }

    public static UUID oracleGuidToSqlUUID(String oracleGuid) {
        return asUuid(Hex.decode(oracleGuid));
    }
}
