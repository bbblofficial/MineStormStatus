package com.minestorm.status.util;

import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Translates & codes and &#RRGGBB hex colors (works on 1.8.8+). */
public final class Colors {

    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final boolean HEX_SUPPORTED = detectHex();
    private static final char[] CODES = "0123456789abcdef".toCharArray();
    private static final int[] RGB = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    private Colors() {}

    private static boolean detectHex() {
        try {
            Class.forName("net.md_5.bungee.api.ChatColor").getMethod("of", String.class);
            return true;
        } catch (Throwable ignored) { return false; }
    }

    public static String translate(String text) {
        if (text == null) return "";
        Matcher matcher = HEX.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(hex(matcher.group(1))));
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    private static String hex(String rrggbb) {
        char c = ChatColor.COLOR_CHAR;
        if (HEX_SUPPORTED) {
            StringBuilder sb = new StringBuilder().append(c).append('x');
            for (char ch : rrggbb.toCharArray()) sb.append(c).append(ch);
            return sb.toString();
        }
        int r = Integer.parseInt(rrggbb.substring(0, 2), 16);
        int g = Integer.parseInt(rrggbb.substring(2, 4), 16);
        int b = Integer.parseInt(rrggbb.substring(4, 6), 16);
        int best = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < RGB.length; i++) {
            int dr = r - ((RGB[i] >> 16) & 0xFF);
            int dg = g - ((RGB[i] >> 8) & 0xFF);
            int db = b - (RGB[i] & 0xFF);
            long distance = (long) dr * dr + (long) dg * dg + (long) db * db;
            if (distance < bestDistance) { bestDistance = distance; best = i; }
        }
        return String.valueOf(c) + CODES[best];
    }
}
