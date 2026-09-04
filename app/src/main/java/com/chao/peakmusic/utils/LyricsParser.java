package com.chao.peakmusic.utils;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Decodes and parses LRC content without depending on Android UI classes. */
public final class LyricsParser {
    private static final Pattern TIME_PATTERN = Pattern.compile(
            "\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern OFFSET_PATTERN = Pattern.compile(
            "\\[offset:([+-]?\\d+)]", Pattern.CASE_INSENSITIVE);

    private LyricsParser() {
    }

    public static String decode(byte[] bytes) {
        if (bytes.length >= 2 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xfe) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xff) == 0xfe && (bytes[1] & 0xff) == 0xff) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ignored) {
            return new String(bytes, Charset.forName("GB18030"));
        }
    }

    public static List<LyricLine> parse(String source) {
        List<LyricLine> result = new ArrayList<>();
        List<String> untimedLines = new ArrayList<>();
        long offsetMs = 0;
        String content = source == null ? "" : source;
        String[] lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (String line : lines) {
            Matcher offsetMatcher = OFFSET_PATTERN.matcher(line.trim());
            if (offsetMatcher.matches()) {
                offsetMs = Long.parseLong(offsetMatcher.group(1));
                continue;
            }
            Matcher matcher = TIME_PATTERN.matcher(line);
            String lyricLine = matcher.replaceAll("").trim();
            if (lyricLine.matches("^\\[[a-zA-Z]+:.*]$") || lyricLine.isEmpty()) {
                continue;
            }
            matcher.reset();
            boolean foundTime = false;
            while (matcher.find()) {
                foundTime = true;
                result.add(new LyricLine(parseTime(matcher), lyricLine));
            }
            if (!foundTime) {
                untimedLines.add(lyricLine);
            }
        }
        if (!result.isEmpty()) {
            if (offsetMs != 0) {
                for (int i = 0; i < result.size(); i++) {
                    LyricLine line = result.get(i);
                    result.set(i, new LyricLine(Math.max(0, line.timeMs + offsetMs), line.text));
                }
            }
            Collections.sort(result, (first, second) -> Long.compare(first.timeMs, second.timeMs));
            return result;
        }
        for (String line : untimedLines) {
            result.add(new LyricLine(-1, line));
        }
        return result;
    }

    private static long parseTime(Matcher matcher) {
        long minutes = Long.parseLong(matcher.group(1));
        long seconds = Long.parseLong(matcher.group(2));
        String fraction = matcher.group(3);
        long milliseconds = 0;
        if (fraction != null) {
            if (fraction.length() == 1) {
                milliseconds = Long.parseLong(fraction) * 100;
            } else if (fraction.length() == 2) {
                milliseconds = Long.parseLong(fraction) * 10;
            } else {
                milliseconds = Long.parseLong(fraction.substring(0, 3));
            }
        }
        return (minutes * 60 + seconds) * 1000 + milliseconds;
    }

    /** Returns the last timed line at or before the playback position. */
    public static int findLineAt(List<LyricLine> lines, long positionMs) {
        int low = 0;
        int high = lines.size() - 1;
        int result = -1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (lines.get(middle).timeMs <= positionMs) {
                result = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return result;
    }

    public static final class LyricLine {
        public final long timeMs;
        public final String text;

        public LyricLine(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
    }
}
