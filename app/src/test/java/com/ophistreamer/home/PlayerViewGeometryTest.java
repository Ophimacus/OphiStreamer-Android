package com.ophistreamer.home;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlayerViewGeometryTest {
    @Test public void phoneFitsOnlyItsHalfWithoutStretching() {
        int[] first = PlayerViewGeometry.fit(1610, 720, 16.0 / 9, PlayerViewGeometry.region("p1", "side"));
        int[] second = PlayerViewGeometry.fit(1610, 720, 16.0 / 9, PlayerViewGeometry.region("p2", "side"));
        assertArrayEquals(new int[]{640, 720, 1280, 720, 0, 0}, first);
        assertArrayEquals(new int[]{640, 720, 1280, 720, -640, 0}, second);
        // Both viewports meet exactly at the divider; neither samples the other player's half.
        assertEquals(first[0], -second[4]);
        assertEquals(first[2], second[2]);
    }

    @Test public void horizontalSplitPortraitRotationAndOddSizesPreserveGeometry() {
        int[] bottom = PlayerViewGeometry.fit(1610, 720, 16.0 / 9, PlayerViewGeometry.region("p2", "stacked"));
        assertArrayEquals(new int[]{1610, 453, 1610, 906, 0, -453}, bottom);
        int[] portrait = PlayerViewGeometry.fit(720, 1610, 16.0 / 9, PlayerViewGeometry.region("p2", "side"));
        assertArrayEquals(new int[]{720, 810, 1440, 810, -720, 0}, portrait);
        int[] odd = PlayerViewGeometry.fit(853, 479, 16.0 / 9, PlayerViewGeometry.region("p2", "side"));
        assertTrue(odd[0] <= 853 && odd[1] <= 479);
        assertEquals(odd[0], -odd[4]);
        assertEquals(odd[0] * 2, odd[2]);
    }

    @Test public void fullPictureAndInvalidSizesFailSafely() {
        assertArrayEquals(new float[]{0, 0, 1, 1}, PlayerViewGeometry.region("unknown", "side"), 0);
        assertArrayEquals(new int[]{1280, 720, 1280, 720, 0, 0},
                PlayerViewGeometry.fit(1280, 720, 16.0 / 9, PlayerViewGeometry.region("full", "side")));
        assertArrayEquals(new int[6], PlayerViewGeometry.fit(0, 720, 16.0 / 9, PlayerViewGeometry.region("p1", "side")));
        assertArrayEquals(new int[6], PlayerViewGeometry.fit(1280, 720, Double.NaN, PlayerViewGeometry.region("p1", "side")));
    }
}
