/*-
 * #%L
 * Mars command and definitions for transverse flow molecule types.
 * %%
 * Copyright (C) 2023 - 2026 Karl Duderstadt
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package de.mpg.biochem.mars.transverseflow;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BezierNearestBernstein {

    // ============================================================
    // BNBResult
    // ============================================================

    public static class BNBResult {

        public final Point2D.Double point;
        public final double t;
        public final double distance;

        public BNBResult(
                Point2D.Double point,
                double t,
                double distance) {

            this.point = point;
            this.t = t;
            this.distance = distance;
        }
    }


    // ============================================================
    // Find nearest point on two cubic Bezier segments
    // ============================================================

    public static BNBResult findNearestPoint(
            Point2D.Double startPoint,

            Point2D.Double p0,
            Point2D.Double p1,
            Point2D.Double h0out,
            Point2D.Double h1in) {

        // --------------------------------------------------------
        // Segment: P0 -> P1
        // P0, H0out, H1in, P1
        // --------------------------------------------------------

        BNBResult retval = findNearestOnSegment(
                startPoint,
                p0,
                h0out,
                h1in,
                p1
        );
        return retval;
    }


    // ============================================================
    // Find nearest point on ONE cubic Bezier segment
    // ============================================================

    private static BNBResult findNearestOnSegment(
            Point2D.Double s,
            Point2D.Double p0,
            Point2D.Double p1,
            Point2D.Double p2,
            Point2D.Double p3) {

        /*
         * f(t) = (B(t)-S) dot B'(t)
         *
         * f(t) is a quintic polynomial.
         *
         * First construct it in power basis:
         *
         * f(t) = c0 + c1*t + ... + c5*t^5
         */

        double[] power =
                makeDistanceDerivativePolynomial(
                        p0, p1, p2, p3, s
                );


        /*
         * Convert power basis to Bernstein basis.
         *
         * f(t) =
         * sum_{i=0}^5 b[i] * C(5,i)
         *            * t^i * (1-t)^(5-i)
         */

        double[] bernstein =
                powerToBernstein(power);


        /*
         * Find all roots in [0,1].
         */

        List<Interval> intervals =
                new ArrayList<>();

        isolateRoots(
                bernstein,
                0.0,
                1.0,
                0,
                intervals
        );


        /*
         * Candidate t values.
         *
         * End points must always be included because
         * the minimum can occur at t=0 or t=1.
         */

        List<Double> candidates =
                new ArrayList<>();

        candidates.add(0.0);
        candidates.add(1.0);

        for (Interval interval : intervals) {

            double t =
                    refineRoot(
                            power,
                            interval.left,
                            interval.right
                    );

            candidates.add(t);
        }


        /*
         * Remove duplicated candidates.
         */

        candidates.sort(Comparator.naturalOrder());

        List<Double> unique =
                new ArrayList<>();

        for (double t : candidates) {

            if (unique.isEmpty()
                    || Math.abs(
                    t - unique.get(
                            unique.size() - 1
                    )
            ) > ROOT_TOLERANCE) {

                unique.add(t);
            }
        }


        /*
         * Find the candidate with minimum distance.
         */

        double bestDistance2 =
                Double.POSITIVE_INFINITY;

        double bestT = 0.0;

        for (double t : unique) {

            Point2D.Double q =
                    evaluateBezier(
                            p0, p1, p2, p3, t
                    );

            double dx = q.x - s.x;
            double dy = q.y - s.y;

            double d2 =
                    dx * dx + dy * dy;

            if (d2 < bestDistance2) {

                bestDistance2 = d2;
                bestT = t;
            }
        }


        Point2D.Double bestPoint =
                evaluateBezier(
                        p0, p1, p2, p3, bestT
                );


        return new BNBResult(
                bestPoint,
                bestT,
                Math.sqrt(bestDistance2)
        );
    }


    // ============================================================
    // Construct
    //
    // f(t) = (B(t)-S) dot B'(t)
    //
    // in power basis.
    // ============================================================

    private static double[] makeDistanceDerivativePolynomial(
            Point2D.Double p0,
            Point2D.Double p1,
            Point2D.Double p2,
            Point2D.Double p3,
            Point2D.Double s) {

        /*
         * B(t)
         *
         * = a0
         * + a1 t
         * + a2 t^2
         * + a3 t^3
         */

        double[] bx = new double[4];
        double[] by = new double[4];

        bx[0] = p0.x;
        bx[1] = 3.0 * (p1.x - p0.x);
        bx[2] = 3.0 * (p0.x
                - 2.0 * p1.x
                + p2.x);
        bx[3] = -p0.x
                + 3.0 * p1.x
                - 3.0 * p2.x
                + p3.x;

        by[0] = p0.y;
        by[1] = 3.0 * (p1.y - p0.y);
        by[2] = 3.0 * (p0.y
                - 2.0 * p1.y
                + p2.y);
        by[3] = -p0.y
                + 3.0 * p1.y
                - 3.0 * p2.y
                + p3.y;


        /*
         * B(t) - S
         */

        bx[0] -= s.x;
        by[0] -= s.y;


        /*
         * B'(t)
         */

        double[] dx = {
                bx[1],
                2.0 * bx[2],
                3.0 * bx[3]
        };

        double[] dy = {
                by[1],
                2.0 * by[2],
                3.0 * by[3]
        };


        /*
         * Multiply:
         *
         * (Bx-Sx) * Bx'
         * +
         * (By-Sy) * By'
         */

        double[] result = new double[6];

        for (int i = 0; i <= 3; i++) {

            for (int j = 0; j <= 2; j++) {

                result[i + j] +=
                        bx[i] * dx[j]
                                + by[i] * dy[j];
            }
        }

        return result;
    }


    // ============================================================
    // Convert power basis to Bernstein basis
    //
    // power:
    //
    // p(t) = a0 + a1 t + ... + an t^n
    //
    // Bernstein:
    //
    // p(t) =
    // sum b_i * C(n,i)
    //        * t^i * (1-t)^(n-i)
    //
    // Relationship:
    //
    // b_i =
    // sum_{j=0}^i
    // a_j * C(i,j) / C(n,j)
    // ============================================================

    private static double[] powerToBernstein(
            double[] power) {

        int n = power.length - 1;

        double[] b =
                new double[n + 1];

        for (int i = 0; i <= n; i++) {

            double sum = 0.0;

            for (int j = 0; j <= i; j++) {

                sum += power[j]
                        * binomial(i, j)
                        / binomial(n, j);
            }

            b[i] = sum;
        }

        return b;
    }


    // ============================================================
    // Recursive Bernstein root isolation
    // ============================================================

    private static void isolateRoots(
            double[] coefficients,
            double left,
            double right,
            int depth,
            List<Interval> result) {

        /*
         * Safety limit.
         */

        if (depth > MAX_DEPTH) {

            result.add(
                    new Interval(left, right)
            );

            return;
        }


        /*
         * Determine number of sign variations.
         */

        int variations =
                signVariations(coefficients);


        /*
         * No sign variation:
         *
         * By the Bernstein convex hull property,
         * there is no sign-changing root.
         *
         * We also check coefficients close to zero
         * because multiple roots can touch zero.
         */

        if (variations == 0) {

            if (!containsPossibleZero(coefficients)) {
                return;
            }

            /*
             * Possible multiple root.
             *
             * Continue subdivision.
             */
        }


        /*
         * One variation:
         *
         * There is at most one root in this interval.
         */

        if (variations == 1) {

            if (right - left
                    < PARAMETER_TOLERANCE) {

                result.add(
                        new Interval(left, right)
                );

                return;
            }
        }


        /*
         * If interval is sufficiently small,
         * accept it as a root candidate.
         */

        if (right - left
                < PARAMETER_TOLERANCE) {

            result.add(
                    new Interval(left, right)
            );

            return;
        }


        /*
         * de Casteljau subdivision at u = 0.5.
         */

        SplitResult split =
                splitBernstein(
                        coefficients
                );

        double middle =
                0.5 * (left + right);


        /*
         * Recurse into left half.
         */

        isolateRoots(
                split.left,
                left,
                middle,
                depth + 1,
                result
        );


        /*
         * Recurse into right half.
         */

        isolateRoots(
                split.right,
                middle,
                right,
                depth + 1,
                result
        );
    }


    // ============================================================
    // Sign variation
    // ============================================================

    private static int signVariations(
            double[] c) {

        int variations = 0;

        int previousSign = 0;

        for (double value : c) {

            int sign;

            if (Math.abs(value)
                    < COEFFICIENT_TOLERANCE) {

                continue;
            }

            sign = value > 0 ? 1 : -1;

            if (previousSign != 0
                    && sign != previousSign) {

                variations++;
            }

            previousSign = sign;
        }

        return variations;
    }


    // ============================================================
    // Detect coefficients close to zero
    //
    // This is needed to avoid losing a multiple root.
    // ============================================================

    private static boolean containsPossibleZero(
            double[] c) {

        for (double value : c) {

            if (Math.abs(value)
                    < COEFFICIENT_TOLERANCE) {

                return true;
            }
        }

        return false;
    }


    // ============================================================
    // de Casteljau subdivision
    //
    // Given Bernstein coefficients
    //
    // b0 b1 b2 ... bn
    //
    // return left and right Bernstein coefficients
    // after splitting at u=0.5.
    // ============================================================

    private static SplitResult splitBernstein(
            double[] b) {

        int n = b.length - 1;

        double[][] triangle =
                new double[n + 1][n + 1];

        for (int i = 0; i <= n; i++) {
            triangle[0][i] = b[i];
        }


        /*
         * de Casteljau triangle
         */

        for (int level = 1; level <= n; level++) {

            for (int i = 0;
                 i <= n - level;
                 i++) {

                triangle[level][i] =
                        0.5
                                * (
                                triangle[level - 1][i]
                                        +
                                        triangle[level - 1][i + 1]
                        );
            }
        }


        /*
         * Left Bernstein coefficients
         */

        double[] left =
                new double[n + 1];

        for (int i = 0; i <= n; i++) {
            left[i] = triangle[i][0];
        }


        /*
         * Right Bernstein coefficients
         */

        double[] right =
                new double[n + 1];

        for (int i = 0; i <= n; i++) {
            right[i] =
                    triangle[n - i][i];
        }


        return new SplitResult(
                left,
                right
        );
    }


    // ============================================================
    // Root refinement
    //
    // The interval has already been isolated by Bernstein
    // subdivision. We refine it using bisection.
    // ============================================================

    private static double refineRoot(
            double[] polynomial,
            double left,
            double right) {

        double fLeft =
                evaluatePolynomial(
                        polynomial,
                        left
                );

        double fRight =
                evaluatePolynomial(
                        polynomial,
                        right
                );


        /*
         * If there is a sign change, ordinary bisection.
         */

        if (fLeft * fRight < 0.0) {

            for (int i = 0; i < 100; i++) {

                double mid =
                        0.5 * (left + right);

                double fMid =
                        evaluatePolynomial(
                                polynomial,
                                mid
                        );

                if (Math.abs(fMid)
                        < FUNCTION_TOLERANCE) {

                    return mid;
                }

                if (fLeft * fMid <= 0.0) {

                    right = mid;
                    fRight = fMid;

                } else {

                    left = mid;
                    fLeft = fMid;
                }

                if (right - left
                        < PARAMETER_TOLERANCE) {

                    break;
                }
            }

            return 0.5 * (left + right);
        }


        /*
         * Multiple root / no sign change.
         *
         * In this case use the point with the
         * smallest |f(t)| in the isolated interval.
         *
         * A small local search is sufficient because
         * Bernstein subdivision has already isolated
         * the root.
         */

        double bestT = left;

        double bestValue =
                Math.abs(fLeft);

        for (int i = 1; i <= 20; i++) {

            double t =
                    left
                            + (right - left)
                            * i / 20.0;

            double value =
                    Math.abs(
                            evaluatePolynomial(
                                    polynomial,
                                    t
                            )
                    );

            if (value < bestValue) {

                bestValue = value;
                bestT = t;
            }
        }

        return bestT;
    }


    // ============================================================
    // Polynomial evaluation
    // ============================================================

    private static double evaluatePolynomial(
            double[] c,
            double t) {

        double value =
                c[c.length - 1];

        for (int i = c.length - 2;
             i >= 0;
             i--) {

            value =
                    value * t + c[i];
        }

        return value;
    }


    // ============================================================
    // Cubic Bezier evaluation
    // ============================================================

    public static Point2D.Double evaluateBezier(
            Point2D.Double p0,
            Point2D.Double p1,
            Point2D.Double p2,
            Point2D.Double p3,
            double t) {

        double u = 1.0 - t;

        double x =
                u * u * u * p0.x
                        + 3.0 * u * u * t * p1.x
                        + 3.0 * u * t * t * p2.x
                        + t * t * t * p3.x;

        double y =
                u * u * u * p0.y
                        + 3.0 * u * u * t * p1.y
                        + 3.0 * u * t * t * p2.y
                        + t * t * t * p3.y;

        return new Point2D.Double(x, y);
    }


    // ============================================================
    // Binomial coefficient
    // ============================================================

    private static double binomial(
            int n,
            int k) {

        if (k < 0 || k > n) {
            return 0.0;
        }

        if (k == 0 || k == n) {
            return 1.0;
        }

        k = Math.min(k, n - k);

        double result = 1.0;

        for (int i = 1; i <= k; i++) {

            result *=
                    (double) (n - k + i) / i;
        }

        return result;
    }


    // ============================================================
    // Internal classes
    // ============================================================

    private static class Interval {

        final double left;
        final double right;

        Interval(
                double left,
                double right) {

            this.left = left;
            this.right = right;
        }
    }


    private static class SplitResult {

        final double[] left;
        final double[] right;

        SplitResult(
                double[] left,
                double[] right) {

            this.left = left;
            this.right = right;
        }
    }


    // ============================================================
    // Numerical parameters
    // ============================================================

    private static final int MAX_DEPTH = 60;

    private static final double
            PARAMETER_TOLERANCE = 1e-12;

    private static final double
            ROOT_TOLERANCE = 1e-10;

    private static final double
            FUNCTION_TOLERANCE = 1e-10;

    private static final double
            COEFFICIENT_TOLERANCE = 1e-10;
}
