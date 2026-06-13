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

import java.util.ArrayList;
import java.util.List;

/**
 * Pure (Android-free, JVM-testable) logic that, for a given location, splits the barriers into:
 * <ul>
 *   <li>{@code inZone} — every barrier the car is physically inside the radius of, regardless of
 *       direction, sorted nearest-first. These are all reachable from the popup's "also in range"
 *       list so the driver can open, say, the entrance barrier even while pointed at the exit.</li>
 *   <li>{@code freshMatches} — barriers that match by direction <i>and</i> aren't already triggered.
 *       These get suppressed (marked triggered) so the popup doesn't stack.</li>
 *   <li>{@code primary} — the closest fresh directional match; the one the popup suggests. Null when
 *       there is nothing new to offer.</li>
 * </ul>
 */
public final class NearbyResolver {

    public interface TriggeredPredicate {
        boolean isTriggered(long barrierId);
    }

    public static final class Resolution {
        public final Barrier primary;
        public final List<Barrier> freshMatches;
        public final List<Barrier> inZone;

        Resolution(Barrier primary, List<Barrier> freshMatches, List<Barrier> inZone) {
            this.primary = primary;
            this.freshMatches = freshMatches;
            this.inZone = inZone;
        }
    }

    private NearbyResolver() {}

    public static Resolution resolve(List<Barrier> barriers, BarrierDetector.Fix fix,
                                     TriggeredPredicate triggered) {
        List<Barrier> inZone = new ArrayList<>();
        List<Barrier> freshMatches = new ArrayList<>();

        for (Barrier barrier : barriers) {
            BarrierDetector.Result r = BarrierDetector.evaluate(barrier, fix);
            if (r == BarrierDetector.Result.OUT_OF_RANGE) {
                continue;
            }
            inZone.add(barrier);
            if (r == BarrierDetector.Result.IN_RANGE && !triggered.isTriggered(barrier.getId())) {
                freshMatches.add(barrier);
            }
        }

        inZone.sort((a, b) -> Double.compare(distance(fix, a), distance(fix, b)));

        Barrier primary = null;
        double best = Double.MAX_VALUE;
        for (Barrier barrier : freshMatches) {
            double d = distance(fix, barrier);
            if (d < best) {
                best = d;
                primary = barrier;
            }
        }

        return new Resolution(primary, freshMatches, inZone);
    }

    private static double distance(BarrierDetector.Fix fix, Barrier barrier) {
        return GeoMath.distanceMeters(
                fix.latitude, fix.longitude,
                barrier.getLatitude(), barrier.getLongitude());
    }
}
