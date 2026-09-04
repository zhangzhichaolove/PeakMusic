package com.chao.peakmusic.base;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PrettyJsonFormatterTest {
    @Test
    public void indentsNestedJson() {
        assertEquals("{\n  \"result\": [\n    1,\n    2\n  ]\n}",
                PrettyJsonFormatter.format("{\"result\":[1,2]}"));
    }

    @Test
    public void preservesNonJsonResponse() {
        assertEquals("upstream error", PrettyJsonFormatter.format("upstream error"));
    }
}
