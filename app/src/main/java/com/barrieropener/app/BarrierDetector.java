/*
 * Copyright © 2025-2026 Dezz (https://github.com/DezzK)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.barrieropener.app;

import android.location.Location;

/**
 * Pure detection logic — Android-free. Bridges to {@link Location} via {@link #evaluate(Barrier,
 * Location)} but the actual decision lives in {@link #evaluate(Barrier, Fix)} which is unit
 * testable on the JVM.
 */
public final class BarrierDetector {

    public static final double APPROACH_ANGLE_TOLERANCE_DEG = 45.0;
    /** Below this speed (m/s) we don't trust device bearing for one-way matching. Kept low so
     *  triggering isn't delayed when the car is creeping toward a barrier. */
    public static final double MIN_SPEED_FOR_BEARING_MS = 0.3; // ~1 km/h

    private BarrierDetector() {}

    public enum Result {
        OUT_OF_RANGE,
        WRONG_DIRECTION,
        IN_RANGE
    }

    /** Pure-data representation of a location fix used by the detector. */
    public static final class Fix {
        public final double latitude;
        public final double longitude;
        public final boolean hasBearing;
        public final double bearingDeg;
        public final boolean hasSpeed;
        public final double speedMs;

        public Fix(double latitude, double longitude,
                   boolean hasBearing, double bearingDeg,
                   boolean hasSpeed, double speedMs) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.hasBearing = hasBearing;
            this.bearingDeg = bearingDeg;
            this.hasSpeed = hasSpeed;
            this.speedMs = speedMs;
        }

        public static Fix from(Location loc) {
            return new Fix(
                    loc.getLatitude(), loc.getLongitude(),
                    loc.hasBearing(), loc.getBearing(),
                    loc.hasSpeed(), loc.getSpeed());
        }
    }

    public static Result evaluate(Barrier barrier, Location location) {
        return evaluate(barrier, Fix.from(location));
    }

    public static Result evaluate(Barrier barrier, Fix fix) {
        double distance = GeoMath.distanceMeters(
                fix.latitude, fix.longitude,
                barrier.getLatitude(), barrier.getLongitude());
        if (distance > barrier.getDetectionRadius()) {
            return Result.OUT_OF_RANGE;
        }

        double bearingToBarrier = GeoMath.bearingDegrees(
                fix.latitude, fix.longitude,
                barrier.getLatitude(), barrier.getLongitude());

        boolean trustBearing = fix.hasBearing
                && (!fix.hasSpeed || fix.speedMs >= MIN_SPEED_FOR_BEARING_MS);

        if (trustBearing) {
            double angleToBarrier = GeoMath.angleDifference(bearingToBarrier, fix.bearingDeg);
            if (angleToBarrier > APPROACH_ANGLE_TOLERANCE_DEG) {
                return Result.WRONG_DIRECTION;
            }
        }

        if (barrier.getBarrierType() == Barrier.BarrierType.BIDIRECTIONAL) {
            return Result.IN_RANGE;
        }

        // ONE_WAY: refuse to trigger when we can't tell direction — protects against false
        // positives when the car is stationary right by the barrier.
        if (!trustBearing) {
            return Result.WRONG_DIRECTION;
        }

        double compassFromMapHeading = mapHeadingToCompassBearing(barrier.getHeading());
        double angleToHeading = GeoMath.angleDifference(fix.bearingDeg, compassFromMapHeading);
        return angleToHeading <= APPROACH_ANGLE_TOLERANCE_DEG
                ? Result.IN_RANGE
                : Result.WRONG_DIRECTION;
    }

    /**
     * Yandex MapKit's {@code placemark.setDirection} interprets 0° as east; rotating by +270°
     * (mod 360) brings it to a compass bearing where 0° is north.
     */
    public static double mapHeadingToCompassBearing(double mapHeadingDeg) {
        return GeoMath.normalize(mapHeadingDeg + 270.0);
    }
}
