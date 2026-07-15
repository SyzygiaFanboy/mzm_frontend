package com.example.myapplication.utils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BiliLinkParser {
    private static final Pattern URL_PATTERN = Pattern.compile("(https?://[^\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WWW_BILI_PATTERN = Pattern.compile("\\b((?:www|m)\\.bilibili\\.com/[^\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern B23_PATTERN = Pattern.compile("\\b(b23\\.tv/[^\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BV_PATTERN = Pattern.compile("\\b(BV[0-9A-Za-z]{10})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern AV_PATTERN = Pattern.compile("\\b(av\\d+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EP_PATTERN = Pattern.compile("\\b(ep\\d+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SS_PATTERN = Pattern.compile("\\b(ss\\d+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BVID_QUERY_PATTERN = Pattern.compile("(?i)\\b(bvid)=(BV[0-9A-Za-z]{10})\\b");
    private static final Pattern AID_QUERY_PATTERN = Pattern.compile("(?i)\\b(?:aid|avid)=(\\d+)\\b");

    private BiliLinkParser() {
    }

    public static String extractToken(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return "";
        }

        Matcher m = URL_PATTERN.matcher(s);
        if (m.find()) {
            return sanitizeToken(m.group(1));
        }

        m = WWW_BILI_PATTERN.matcher(s);
        if (m.find()) {
            return sanitizeToken(m.group(1));
        }

        m = B23_PATTERN.matcher(s);
        if (m.find()) {
            return sanitizeToken(m.group(1));
        }

        m = BV_PATTERN.matcher(s);
        if (m.find()) {
            return normalizeBvid(m.group(1));
        }

        m = AV_PATTERN.matcher(s);
        if (m.find()) {
            return m.group(1).toLowerCase(Locale.ROOT);
        }

        m = EP_PATTERN.matcher(s);
        if (m.find()) {
            return m.group(1).toLowerCase(Locale.ROOT);
        }

        m = SS_PATTERN.matcher(s);
        if (m.find()) {
            return m.group(1).toLowerCase(Locale.ROOT);
        }

        m = BVID_QUERY_PATTERN.matcher(s);
        if (m.find()) {
            return normalizeBvid(m.group(2));
        }

        m = AID_QUERY_PATTERN.matcher(s);
        if (m.find()) {
            return ("av" + m.group(1)).toLowerCase(Locale.ROOT);
        }

        return "";
    }

    public static String sanitizeToken(String token) {
        if (token == null) {
            return "";
        }
        String s = token.trim();
        if (s.startsWith("//")) {
            s = "https:" + s;
        }
        String stripChars = ")]}>,，。；;！!？?】》」\"'“”‘’、…";
        while (!s.isEmpty()) {
            char c = s.charAt(s.length() - 1);
            if (stripChars.indexOf(c) >= 0) {
                s = s.substring(0, s.length() - 1);
                continue;
            }
            break;
        }
        if ((s.startsWith("(") && s.endsWith(")")) || (s.startsWith("（") && s.endsWith("）"))) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    public static String extractBvid(String token) {
        if (token == null) {
            return null;
        }
        Matcher m = Pattern.compile("(BV[0-9A-Za-z]{10})", Pattern.CASE_INSENSITIVE).matcher(token);
        if (m.find()) {
            return normalizeBvid(m.group(1));
        }
        return null;
    }

    public static String extractAid(String token) {
        if (token == null) {
            return null;
        }
        Matcher m = Pattern.compile("(?i)\\bav(\\d+)\\b").matcher(token);
        if (m.find()) {
            return m.group(1);
        }
        m = AID_QUERY_PATTERN.matcher(token);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static String normalizeBvid(String bv) {
        if (bv == null || bv.isEmpty()) {
            return bv;
        }
        if (bv.length() >= 2) {
            return "BV" + bv.substring(2);
        }
        return bv;
    }
}

