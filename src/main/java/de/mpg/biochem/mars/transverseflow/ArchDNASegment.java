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

import net.imglib2.RealLocalizable;
import java.awt.geom.Point2D;

public class ArchDNASegment implements RealLocalizable {

    private double x1, y1, hx1, hy1, x2, y2, hx2, hy2;
    private double xMiddle, yMiddle;
    private int medianIntensity;
    private double variance;
    public static final String X1 = "X1";
    public static final String Y1 = "Y1";
    public static final String X2 = "X2";
    public static final String Y2 = "Y2";
    public static final String MEDIAN_INTENSITY = "Median_intensity";
    public static final String INTENSITY_VARIANCE = "Intensity_variance";
    public static final String LENGTH = "Length";

    public ArchDNASegment(double x1, double y1, double hx1, double hy1, double x2, double y2, double hx2, double hy2) {
        this.x1 = x1;
        this.y1 = y1;
        this.hx1 = hx1;
        this.hy1 = hy1;
        this.x2 = x2;
        this.y2 = y2;
        this.hx2 = hx2;
        this.hy2 = hy2;
        Point2D.Double p0 = new Point2D.Double(x1, y1);
        Point2D.Double h0 = new Point2D.Double(hx1, hy1);
        Point2D.Double p1 = new Point2D.Double(x2, y2);
        Point2D.Double h1 = new Point2D.Double(hx2, hy2);
        Point2D.Double middle = BezierLength.evaluate(p0, h0, h1, p1, 0.5);
        this.xMiddle = middle.getX();
        this.yMiddle = middle.getY();
    }

    public ArchDNASegment(Point2D.Double top, Point2D.Double bottom, Point2D.Double topHandle, Point2D.Double bottomHandle) {
        this.x1 = top.getX();
        this.y1 = top.getY();
        this.hx1 = topHandle.getX();
        this.hy1 = topHandle.getY();
        this.x2 = bottom.getX();
        this.y2 = bottom.getY();
        this.hx2 = bottomHandle.getX();
        this.hy2 = bottomHandle.getY();
        Point2D.Double middle = BezierLength.evaluate(top, topHandle, bottomHandle, bottom, 0.5);
        this.xMiddle = middle.getX();
        this.yMiddle = middle.getY();
    }

    public double getX1() {
        return x1;
    }

    public double getY1() {
        return y1;
    }

    public double getHX1() {
        return hx1;
    }

    public double getHY1() {
        return hy1;
    }

    public double getX2() {
        return x2;
    }

    public double getY2() {
        return y2;
    }

    public double getHX2() {
        return hx2;
    }

    public double getHY2() {
        return hy2;
    }

    public double getXMiddle() { return xMiddle; }

    public double getYMiddle() { return yMiddle; }

    public double getXCenter() { return (xMiddle + x1 + x2) / 3; }

    public double getYCenter() { return (yMiddle + y1 + y2) / 3; }

    public void setX1(double x1) {
        this.x1 = x1;
    }

    public void setY1(double y1) {
        this.y1 = y1;
    }

    public void setHX1(double hx1) {
        this.hx1 = hx1;
    }

    public void setHY1(double hy1) {
        this.hy1 = hy1;
    }

    public void setX2(double x2) {
        this.x2 = x2;
    }

    public void setY2(double y2) {
        this.y2 = y2;
    }

    public void setHX2(double hx2) {
        this.hx2 = hx2;
    }

    public void setHY2(double hy2) {
        this.hy2 = hy2;
    }

    public double getLength() {
        Point2D.Double p0 = new Point2D.Double(x1, y1);
        Point2D.Double h0 = new Point2D.Double(hx1, hy1);
        Point2D.Double p1 = new Point2D.Double(x2, y2);
        Point2D.Double h1 = new Point2D.Double(hx2, hy2);
        return BezierLength.length(p0, h0, h1, p1);
    }

    public double getPositionOnDNA(double x, double y, double DNALength) {
        Point2D.Double p0 = new Point2D.Double(x1, y1);
        Point2D.Double h0 = new Point2D.Double(hx1, hy1);
        Point2D.Double p1 = new Point2D.Double(x2, y2);
        Point2D.Double h1 = new Point2D.Double(hx2, hy2);
        Point2D.Double pos = new Point2D.Double(x, y);
        BezierNearestBernstein.BNBResult bnbResult = BezierNearestBernstein.findNearestPoint(pos, p0, p1, h0, h1);
        return bnbResult.t * DNALength;
    }

    public void setMedianIntensity(int medianIntensity) {
        this.medianIntensity = medianIntensity;
    }

    public int getMedianIntensity() {
        return medianIntensity;
    }

    public void setVariance(double variance) {
        this.variance = variance;
    }

    public double getVariance() {
        return variance;
    }

    //Override from RealLocalizable interface so peaks can be passed to
    // KDTree and other ImgLib2 functions.
    @Override
    public int numDimensions() {
        // We make no effort to think beyond 2 dimensions !
        return 2;
    }

    @Override
    public double getDoublePosition(int arg0) {
        if (arg0 == 0) {
            return getXCenter();
        }
        else if (arg0 == 1) {
            return getYCenter();
        }
        else {
            return -1;
        }
    }

    @Override
    public float getFloatPosition(int arg0) {
        if (arg0 == 0) {
            return (float) getXCenter();
        }
        else if (arg0 == 1) {
            return (float) getYCenter();
        }
        else {
            return -1;
        }
    }

    @Override
    public void localize(float[] arg0) {
        arg0[0] = (float) getXCenter();
        arg0[1] = (float) getYCenter();
    }

    @Override
    public void localize(double[] arg0) {
        arg0[0] = getXCenter();
        arg0[1] = getYCenter();
    }
}
