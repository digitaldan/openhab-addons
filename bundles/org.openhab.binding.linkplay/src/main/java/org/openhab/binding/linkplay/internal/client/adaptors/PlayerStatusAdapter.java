package org.openhab.binding.linkplay.internal.client.adaptors;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

import org.openhab.binding.linkplay.internal.client.dto.PlayerStatus;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

/**
 * Custom Gson deserializer for {@link PlayerStatus}. It converts the hex encoded
 * "Title", "Artist" and "Album" values into UTF-8 strings so consumers only deal with
 * decoded values.
 *
 * This avoids the need for additional get*Decoded() helper methods on the DTO.
 */
public class PlayerStatusAdapter implements JsonDeserializer<PlayerStatus> {

    private static final Gson GSON_INTERNAL = new Gson();

    @Override
    public PlayerStatus deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        PlayerStatus ps = GSON_INTERNAL.fromJson(json, PlayerStatus.class);

        if (ps == null) {
            return null;
        }

        ps.title = decodeHexSafe(ps.title);
        ps.artist = decodeHexSafe(ps.artist);
        ps.album = decodeHexSafe(ps.album);

        return ps;
    }

    private static String decodeHexSafe(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            return hex;
        }
        try {
            int len = hex.length();
            byte[] data = new byte[len / 2];
            for (int i = 0; i < len; i += 2) {
                data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                        + Character.digit(hex.charAt(i + 1), 16));
            }
            return new String(data, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return hex;
        }
    }
}
