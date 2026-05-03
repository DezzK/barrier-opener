/*
 * Copyright © 2025-2026 Dezz (https://github.com/DezzK)
 */
package com.barrieropener.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BarrierDetectorTest {

    private static final double LAT = 55.751244;
    private static final double LON = 37.618423;

    private static Barrier bidirectional(double radius) {
        Barrier b = new Barrier();
        b.setId(1);
        b.setLatitude(LAT);
        b.setLongitude(LON);
        b.setHeading(0);
        b.setDetectionRadius(radius);
        b.setBarrierType(Barrier.BarrierType.BIDIRECTIONAL);
        return b;
    }

    private static Barrier oneWay(double mapHeadingDeg, double radius) {
        Barrier b = new Barrier();
        b.setId(2);
        b.setLatitude(LAT);
        b.setLongitude(LON);
        b.setHeading(mapHeadingDeg);
        b.setDetectionRadius(radius);
        b.setBarrierType(Barrier.BarrierType.ONE_WAY);
        return b;
    }

    /** Returns a fix at distanceMeters away from the barrier, on the given compass bearing. */
    private static BarrierDetector.Fix fixAt(double distanceMeters, double bearingFromBarrierDeg,
                                              boolean hasBearing, double deviceBearingDeg,
                                              double speedMs) {
        double bearingRad = Math.toRadians(bearingFromBarrierDeg);
        double earthRadius = 6_371_008.8;
        double angularDistance = distanceMeters / earthRadius;
        double phi1 = Math.toRadians(LAT);
        double lambda1 = Math.toRadians(LON);
        double phi2 = Math.asin(Math.sin(phi1) * Math.cos(angularDistance)
                + Math.cos(phi1) * Math.sin(angularDistance) * Math.cos(bearingRad));
        double lambda2 = lambda1 + Math.atan2(
                Math.sin(bearingRad) * Math.sin(angularDistance) * Math.cos(phi1),
                Math.cos(angularDistance) - Math.sin(phi1) * Math.sin(phi2));
        return new BarrierDetector.Fix(
                Math.toDegrees(phi2),
                Math.toDegrees(lambda2),
                hasBearing,
                deviceBearingDeg,
                true,
                speedMs);
    }

    @Test
    public void outOfRange() {
        Barrier b = bidirectional(50);
        // 100 m north of barrier, no bearing
        BarrierDetector.Fix fix = fixAt(100, 0, false, 0, 0);
        assertEquals(BarrierDetector.Result.OUT_OF_RANGE, BarrierDetector.evaluate(b, fix));
    }

    @Test
    public void bidirectionalNoBearingTriggersWhenInRange() {
        Barrier b = bidirectional(50);
        BarrierDetector.Fix fix = fixAt(20, 0, false, 0, 0);
        assertEquals(BarrierDetector.Result.IN_RANGE, BarrierDetector.evaluate(b, fix));
    }

    @Test
    public void bidirectionalApproachingTriggers() {
        Barrier b = bidirectional(50);
        // We're 20 m north of barrier (i.e. barrier is south of us, bearing 180 from us),
        // moving south at 10 m/s.
        BarrierDetector.Fix fix = fixAt(20, 0, true, 180, 10);
        assertEquals(BarrierDetector.Result.IN_RANGE, BarrierDetector.evaluate(b, fix));
    }

    @Test
    public void bidirectionalWrongDirectionDoesNotTrigger() {
        Barrier b = bidirectional(50);
        // North of barrier, but heading north (away).
        BarrierDetector.Fix fix = fixAt(20, 0, true, 0, 10);
        assertEquals(BarrierDetector.Result.WRONG_DIRECTION, BarrierDetector.evaluate(b, fix));
    }

    @Test
    public void oneWayNoBearingDoesNotTrigger() {
        // Map heading 90° = north (after +270 mod 360 → 0 = north). Approaching from south.
        Barrier b = oneWay(90, 50);
        BarrierDetector.Fix fix = fixAt(20, 180, false, 0, 0);
        assertEquals(BarrierDetector.Result.WRONG_DIRECTION, BarrierDetector.evaluate(b, fix));
    }

    @Test
    public void oneWayMatchingApproachTriggers() {
        // Map heading 90° → compass 0° (north). Driver heading north, located south of barrier.
        Barrier b = oneWay(90, 50);
        BarrierDetector.Fix fix = fixAt(20, 180, true, 0, 10);
        assertEquals(BarrierDetector.Result.IN_RANGE, BarrierDetector.evaluate(b, fix));
    }

    @Test
    public void oneWayWrongApproachDoesNotTrigger() {
        // Same barrier (compass 0 = north as approach direction). Driver heading south.
        Barrier b = oneWay(90, 50);
        BarrierDetector.Fix fix = fixAt(20, 0, true, 180, 10);
        assertEquals(BarrierDetector.Result.WRONG_DIRECTION, BarrierDetector.evaluate(b, fix));
    }

    @Test
    public void mapHeadingToCompassZeroEquivalent() {
        // 90° on map (east) → compass 360 ≡ 0 (north).
        assertEquals(0.0, BarrierDetector.mapHeadingToCompassBearing(90.0), 1e-6);
    }

    @Test
    public void angleDifferenceWraps() {
        assertEquals(10.0, GeoMath.angleDifference(355, 5), 1e-9);
        assertEquals(170.0, GeoMath.angleDifference(0, 190), 1e-9);
    }

    @Test
    public void distanceMatchesKnownPair() {
        // Moscow Red Square to Manezh (~250 m).
        double d = GeoMath.distanceMeters(55.753215, 37.622504, 55.755826, 37.612617);
        assertTrue("got " + d, d > 600 && d < 800);
    }
}
