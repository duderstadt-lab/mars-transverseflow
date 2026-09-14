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

import bdv.util.BdvOverlay;
import de.mpg.biochem.mars.image.DNASegment;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.type.numeric.ARGBType;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class ArchBranchIntegratedOverlay extends BdvOverlay {

    private int thickness = 5;
    private int radius = 5;
    private int handleThickness = 1;
    private int currentTimePoint = 0;
    private List<Point2D.Double> points;
    private List<Point2D.Double> handles;
    private Map<Integer, DNASegment> segments;

    public ArchBranchIntegratedOverlay()
    {
        points = new ArrayList<>();
        handles = new ArrayList<>();
        segments = new HashMap<>();
    }

    @Override
    protected void draw(Graphics2D g) {
        AffineTransform2D transform = new AffineTransform2D();
        getCurrentTransform2D(transform);

        if (points.isEmpty() || handles.isEmpty()) return;
        else
        {
            List<Point2D.Double> transformedPoints = transformCoordinates(transform, points);
            List<Point2D.Double> transformedHandles = transformCoordinates(transform, handles);
            drawCurve(g, transformedPoints, transformedHandles);
            //drawHandle(g, transformedPoints, transformedHandles);
            drawAnchors(g, transformedPoints);
            if (!segments.isEmpty()) drawBranch(g, transform);
        }
    }

    private List<Point2D.Double> transformCoordinates(AffineTransform2D transform, List<Point2D.Double> coordinates)
    {
        List<Point2D.Double> retval = new ArrayList<>();
        for (Point2D.Double coordinate : coordinates)
        {
            final double[] globalCoords = new double[]{coordinate.x, coordinate.y};
            final double[] viewerCoords = new double[2];
            transform.apply(globalCoords, viewerCoords);
            Point2D.Double transformedPoint = new Point2D.Double(viewerCoords[0], viewerCoords[1]);
            retval.add(transformedPoint);
        }
        return retval;
    }

    private void drawCurve(Graphics2D g, List<Point2D.Double> points, List<Point2D.Double> handles)
    {
        // draw Bezier segment curves
        Path2D.Double path = new Path2D.Double();
        path.moveTo(points.get(0).x, points.get(0).y);
        // curve from 1st to 2nd anchors
        path.curveTo(
                handles.get(0).x, handles.get(0).y,
                handles.get(1).x, handles.get(1).y,
                points.get(1).x, points.get(1).y
        );
        g.setColor(getColor());
        g.setStroke(new BasicStroke(thickness));
        g.draw(path);
    }

    private void drawHandle(Graphics2D g, List<Point2D.Double> points, List<Point2D.Double> handles)
    {
        // draw Bezier handle to control a curve shape
        Stroke oldstroke = g.getStroke();
        g.setStroke(new BasicStroke(handleThickness));
        g.setColor(Color.DARK_GRAY);
        drawLine(g, points.get(0), handles.get(0));
        drawLine(g, points.get(1), handles.get(1));
        g.setStroke(oldstroke);
        drawPoint(g, handles.get(0), Color.GRAY);
        drawPoint(g, handles.get(1), Color.GRAY);
    }

    private void drawAnchors(Graphics2D g, List<Point2D.Double> points)
    {
        // draw Bezier anchors
        drawPoint(g, points.get(0), Color.lightGray);
        drawPoint(g, points.get(1), Color.lightGray);
    }

    private void drawLine(Graphics2D g, Point2D.Double source, Point2D.Double target)
    {
        g.drawLine((int) source.x, (int) source.y, (int) target.x, (int) target.y);
    }

    private void drawPoint(Graphics2D g, Point2D.Double point, Color color)
    {
        int x = (int) point.x - radius;
        int y = (int) point.y - radius;
        int d = radius * 2;
        g.setColor(color);
        g.fillOval(x, y, d, d);
    }

    private void drawBranch(Graphics2D g, AffineTransform2D transform) {

        this.currentTimePoint = info.getTimePointIndex();
        if ((!segments.isEmpty()) && segments.containsKey(this.currentTimePoint)) {
            DNASegment segment = segments.get(this.currentTimePoint);
            if (Double.isNaN(segment.getX1()) || Double.isNaN(segment.getY1()) || Double.isNaN(segment.getX2()) || Double
                    .isNaN(segment.getY2())) return;

            final double[] globalCoords = new double[]{segment.getX1(), segment.getY1()};
            final double[] viewerCoords = new double[2];
            transform.apply(globalCoords, viewerCoords);

            int xSource = (int) Math.round(viewerCoords[0]);
            int ySource = (int) Math.round(viewerCoords[1]);

            final double[] globalCoords2 = new double[]{segment.getX2(), segment.getY2()};
            final double[] viewerCoords2 = new double[2];
            transform.apply(globalCoords2, viewerCoords2);

            int xTarget = (int) Math.round(viewerCoords2[0]);
            int yTarget = (int) Math.round(viewerCoords2[1]);

            g.setColor(getColor());
            g.setStroke(new BasicStroke(thickness));
            g.drawLine(xSource, ySource, xTarget, yTarget);

        }
    }

    public void setThickness(int thickness) {
        this.thickness = thickness;
    }

    public void setRadius(int radius) {
        this.radius = radius;
    }

    public void setCurveParameters(List<Point2D.Double> points, List<Point2D.Double> handles, Map<Integer, DNASegment> segments)
    {
        this.points = points;
        this.handles = handles;
        this.segments = segments;
    }

    public List<Point2D.Double> getAnchorPoints() { return points; }
    public List<Point2D.Double> getHandles() { return handles; }
    public Map<Integer, DNASegment> getBranches() { return segments; }

    private Color getColor() {
        int alpha = (int) info.getDisplayRangeMax();

        if (alpha > 255 || alpha < 0) alpha = 255;

        final int r = ARGBType.red(info.getColor().get());
        final int g = ARGBType.green(info.getColor().get());
        final int b = ARGBType.blue(info.getColor().get());
        return new Color(r, g, b, alpha);
    }
}
