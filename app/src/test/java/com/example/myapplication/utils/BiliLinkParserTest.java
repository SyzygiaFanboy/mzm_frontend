package com.example.myapplication.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BiliLinkParserTest {
    @Test
    public void extractToken_videoUrlInText() {
        String raw = "【【官方中文】Innocent Grey《壳之少女》宣传动画】 https://www.bilibili.com/video/BV1vo4y1K7sv/?share_source=copy_web&vd_source=xxx";
        String token = BiliLinkParser.extractToken(raw);
        assertTrue(token.startsWith("https://www.bilibili.com/video/BV1vo4y1K7sv/"));
        assertEquals("BV1vo4y1K7sv", BiliLinkParser.extractBvid(token));
    }

    @Test
    public void extractToken_bangumiEpUrlInText() {
        String raw = "【颂乐人偶：#13插曲】 https://www.bilibili.com/bangumi/play/ep1555167/?share_source=copy_web";
        String token = BiliLinkParser.extractToken(raw);
        assertTrue(token.startsWith("https://www.bilibili.com/bangumi/play/ep1555167/"));
        assertNull(BiliLinkParser.extractBvid(token));
        assertNull(BiliLinkParser.extractAid(token));
    }

    @Test
    public void extractToken_b23Url() {
        String raw = "https://b23.tv/G9TYaxx";
        String token = BiliLinkParser.extractToken(raw);
        assertEquals("https://b23.tv/G9TYaxx", token);
    }

    @Test
    public void extractToken_bvOnly() {
        String raw = "BV1vo4y1K7sv";
        String token = BiliLinkParser.extractToken(raw);
        assertEquals("BV1vo4y1K7sv", token);
        assertEquals("BV1vo4y1K7sv", BiliLinkParser.extractBvid(token));
    }

    @Test
    public void extractToken_avOnly() {
        String raw = "av170001";
        String token = BiliLinkParser.extractToken(raw);
        assertEquals("av170001", token);
        assertEquals("170001", BiliLinkParser.extractAid(token));
    }
}

