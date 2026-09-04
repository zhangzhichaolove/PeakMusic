package com.chao.peakmusic.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class LyricsParserTest {
    @Test
    public void parsesMultipleTimestampsSortsAndAppliesOffset() {
        List<LyricsParser.LyricLine> lines = LyricsParser.parse(
                "[ar:歌手]\n[offset:+250]\n[00:02.5]第二句\n[00:01.25][00:03.125]重复句");

        assertEquals(3, lines.size());
        assertEquals(1500, lines.get(0).timeMs);
        assertEquals("重复句", lines.get(0).text);
        assertEquals(2750, lines.get(1).timeMs);
        assertEquals(3375, lines.get(2).timeMs);
    }

    @Test
    public void keepsPlainLyricsWhenNoTimestampExists() {
        List<LyricsParser.LyricLine> lines = LyricsParser.parse("第一句\r\n第二句");

        assertEquals(2, lines.size());
        assertEquals(-1, lines.get(0).timeMs);
        assertEquals("第一句", lines.get(0).text);
    }

    @Test
    public void decodesUtf16BomWithoutLeavingBomCharacter() {
        byte[] text = "歌词".getBytes(StandardCharsets.UTF_16LE);
        byte[] bytes = new byte[text.length + 2];
        bytes[0] = (byte) 0xff;
        bytes[1] = (byte) 0xfe;
        System.arraycopy(text, 0, bytes, 2, text.length);

        assertEquals("歌词", LyricsParser.decode(bytes));
    }

    @Test
    public void fallsBackToGb18030() {
        assertEquals("中文歌词", LyricsParser.decode(
                "中文歌词".getBytes(Charset.forName("GB18030"))));
    }
}
