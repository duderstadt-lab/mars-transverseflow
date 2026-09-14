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

public class BezierLength {

    /**
     * Calculate arc length of a cubic Bezier curve
     *
     * P0 -> P1 -> P2 -> P3
     *
     * P1 and P2 are control points.
     */
    public static double length(
            Point2D.Double p0,
            Point2D.Double p1,
            Point2D.Double p2,
            Point2D.Double p3) {

        // 10-point Gauss-Legendre quadrature
        final double[] nodes = {
                0.1488743389816312,
                0.4333953941292472,
                0.6794095682990244,
                0.8656312023878318,
                0.9739065285171717
        };

        final double[] weights = {
                0.2955242247147529,
                0.2692667193099963,
                0.2190863625159820,
                0.1494513491505806,
                0.0666713443086881
        };

        double sum = 0.0;

        // Transform [-1,1] -> [0,1]
        double center = 0.5;
        double half = 0.5;

        for (int i = 0; i < nodes.length; i++) {

            double x1 =
                    center - half * nodes[i];

            double x2 =
                    center + half * nodes[i];

            double f1 =
                    speed(
                            p0, p1, p2, p3,
                            x1
                    );

            double f2 =
                    speed(
                            p0, p1, p2, p3,
                            x2
                    );

            sum +=
                    weights[i] * (f1 + f2);
        }

        return half * sum;
    }

    public static Point2D.Double evaluate(
            Point2D.Double p0,
            Point2D.Double h0,
            Point2D.Double h1,
            Point2D.Double p3,
            double t) {

        double u = 1.0 - t;

        double x =
                u * u * u * p0.x
                        + 3 * u * u * t * h0.x
                        + 3 * u * t * t * h1.x
                        + t * t * t * p3.x;

        double y =
                u * u * u * p0.y
                        + 3 * u * u * t * h0.y
                        + 3 * u * t * t * h1.y
                        + t * t * t * p3.y;

        return new Point2D.Double(x, y);
    }


    /**
     * |B'(t)|
     */
    private static double speed(
            Point2D.Double p0,
            Point2D.Double p1,
            Point2D.Double p2,
            Point2D.Double p3,
            double t) {

        double u = 1.0 - t;

        double dx =
                3.0 * (
                        u * u * (p1.x - p0.x)
                                + 2.0 * u * t * (p2.x - p1.x)
                                + t * t * (p3.x - p2.x)
                );

        double dy =
                3.0 * (
                        u * u * (p1.y - p0.y)
                                + 2.0 * u * t * (p2.y - p1.y)
                                + t * t * (p3.y - p2.y)
                );

        return Math.sqrt(
                dx * dx + dy * dy
        );
    }
}
