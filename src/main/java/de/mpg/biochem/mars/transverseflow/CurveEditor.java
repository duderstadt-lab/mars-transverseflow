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

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.List;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOverlaySource;
import de.mpg.biochem.mars.fx.bdv.DnaMolecule.LineOverlay;
import de.mpg.biochem.mars.fx.bdv.MarsBdvFrame;
import net.imglib2.RealPoint;
import net.imglib2.type.numeric.ARGBType;
import org.scijava.ui.behaviour.Behaviour;
import org.scijava.ui.behaviour.BehaviourMap;
import org.scijava.ui.behaviour.InputTrigger;

import javax.swing.*;

public class CurveEditor
{
    private static final String BLOCKING_MAP = "line-blocking";
    private static final String[] BOUNDING_LINE_TOGGLE_EDITOR_KEYS = new String[] { "button1" };
    private LineOverlay lineOverlay;
    private BdvOverlaySource<?> overlaySource;
    private MarsBdvFrame marsBdvFrame;
    private final MouseListener ml;
    private CurveOverlay curveOverlay;
    private final BehaviourMap blockMap;
    private List<Point2D.Double> points = new ArrayList<>();
    private List<Point2D.Double> handles = new ArrayList<>();
    private Mode mode = Mode.SET_START;
    private Integer selectedIndex = null;
    private Part selectedPart = null;
    private Point2D.Double selectedPoint = null;
    private double initialHandleFactor = 0.25;
    private static final int HIT_RADIUS = 5;
    private double dragOffsetX = 0.0;
    private double dragOffsetY = 0.0;

    public CurveEditor(MarsBdvFrame marsBdvFrame)
    {
        this.marsBdvFrame = marsBdvFrame;
        final BdvHandle bdvHandle = marsBdvFrame.getBdvHandle();
        ml = new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e)
            {
                if (selectedPoint == null || mode != Mode.EDIT) { return; }
                final RealPoint realPoint = new RealPoint(3);
                bdvHandle.getViewerPanel().getGlobalMouseCoordinates(realPoint);
                Point2D.Double newPoint = new Point2D.Double(
                    realPoint.getDoublePosition(0) + dragOffsetX,
                    realPoint.getDoublePosition(1) + dragOffsetY
                );
                if (selectedPart == Part.ANCHOR) points.set(selectedIndex, newPoint);
                else if (selectedPart == Part.HANDLE) handles.set(selectedIndex, newPoint);
                bdvHandle.getViewerPanel().repaint();
                return;
            }

            @Override
            public void mousePressed(MouseEvent e)
            {
                final RealPoint realPoint = new RealPoint(3);
                bdvHandle.getViewerPanel().getGlobalMouseCoordinates(realPoint);
                if (mode == Mode.SET_START)
                {
                    points = new ArrayList<>(){{
                        add(new Point2D.Double(realPoint.getDoublePosition(0), realPoint.getDoublePosition(1)));
                        add(new Point2D.Double(realPoint.getDoublePosition(0), realPoint.getDoublePosition(1)));
                    }};
                    handles = new ArrayList<>(){{
                        add(new Point2D.Double(realPoint.getDoublePosition(0), realPoint.getDoublePosition(1)));
                        add(new Point2D.Double(realPoint.getDoublePosition(0), realPoint.getDoublePosition(1)));
                    }};
                    mode = Mode.SET_END;
                    curveOverlay.setCurveParameters(points, handles);
                    return;
                }
                else if (mode == Mode.SET_END)
                {
                    Point2D.Double newPoint = new Point2D.Double(realPoint.getDoublePosition(0), realPoint.getDoublePosition(1));
                    points.set(1, newPoint);
                    initializeCurve();
                    mode = Mode.EDIT;
                    curveOverlay.setCurveParameters(points, handles);
                    return;
                }
                else if (mode == Mode.EDIT)
                {
                    Point2D.Double mousePos = new Point2D.Double(realPoint.getDoublePosition(0), realPoint.getDoublePosition(1));
                    searchNearestPoint(mousePos);
                    dragOffsetX = (selectedPoint == null) ? 0.0 : selectedPoint.x - mousePos.x;
                    dragOffsetY = (selectedPoint == null) ? 0.0 : selectedPoint.y - mousePos.y;
                    return;
                }
            }
            public void mouseReleased(MouseEvent e) { selectedPoint = null; }

            public void mouseMoved(MouseEvent e) {
                if (mode == Mode.SET_END) {
                    final RealPoint realPoint = new RealPoint(3);
                    bdvHandle.getViewerPanel().getGlobalMouseCoordinates(realPoint);
                    Point2D.Double newPoint = new Point2D.Double(
                            realPoint.getDoublePosition(0),
                            realPoint.getDoublePosition(1)
                    );
                    points.set(1, newPoint);
                    handles = new ArrayList<>(){{
                        add(new Point2D.Double());
                        add(new Point2D.Double());
                    }};
                    initializeCurve();
                    return;
                }
            }
        };
        blockMap = new BehaviourMap();
    }

    public void install()
    {
        if (curveOverlay == null) curveOverlay = new CurveOverlay();
        overlaySource = BdvFunctions.showOverlay(curveOverlay, "Arch-Preview", Bdv
                .options().addTo(marsBdvFrame.getBdvHandle()));
        overlaySource.setColor(new ARGBType(-13312));
        marsBdvFrame.getBdvHandle().getViewerPanel().getDisplay().addHandler(ml);

        refreshBlockMap();
        block();
    }

    public void uninstall()
    {
        points = new ArrayList<>();
        handles = new ArrayList<>();
        curveOverlay.setCurveParameters(new ArrayList<>(), new ArrayList<>());
        unblock();
        mode = Mode.SET_START;
        marsBdvFrame.getBdvHandle().getViewerPanel().getDisplay().removeHandler(ml);

        if (overlaySource != null) overlaySource.removeFromBdv();
        if (curveOverlay != null) marsBdvFrame.getBdvHandle().getViewerPanel().getDisplay().overlays().remove( curveOverlay );
    }

    private void block()
    {
        marsBdvFrame.getBdvHandle().getTriggerbindings().addBehaviourMap( BLOCKING_MAP, blockMap );
    }

    private void unblock()
    {
        marsBdvFrame.getBdvHandle().getTriggerbindings().removeBehaviourMap( BLOCKING_MAP );
    }

    private void refreshBlockMap()
    {
        marsBdvFrame.getBdvHandle().getTriggerbindings().removeBehaviourMap( BLOCKING_MAP );

        final Set< InputTrigger > moveCornerTriggers = new HashSet<>();
        for ( final String s : BOUNDING_LINE_TOGGLE_EDITOR_KEYS )
            moveCornerTriggers.add(InputTrigger.getFromString(s));

        final Map< InputTrigger, Set< String > > bindings = marsBdvFrame.getBdvHandle().getTriggerbindings().getConcatenatedInputTriggerMap().getAllBindings();
        final Set< String > behavioursToBlock = new HashSet<>();
        for ( final InputTrigger t : moveCornerTriggers )
            behavioursToBlock.addAll( bindings.getOrDefault( t, Collections.emptySet() ) );

        blockMap.clear();
        final Behaviour block = new Behaviour() {};
        for ( final String key : behavioursToBlock )
            blockMap.put( key, block );
    }

    public List<Point2D.Double> getAnchorPoints()
    {
        return points;
    }

    public List<Point2D.Double> getHandles()
    {
        return handles;
    }

    public void setMarsBdvFrame(MarsBdvFrame marsBdvFrame) {
        this.marsBdvFrame = marsBdvFrame;
    }

    public MarsBdvFrame getMarsBdvFrame() {
        return marsBdvFrame;
    }

    private void initializeCurve()
    {
        double dx = points.get(1).x - points.get(0).x;
        double dy = points.get(1).y - points.get(0).y;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length == 0) { return; }

        double nx = -dy / length;
        double ny =  dx / length;

        double handleLength = initialHandleFactor * length;
        Point2D.Double h0out = new Point2D.Double(
            points.get(0).x + dx * initialHandleFactor + nx * handleLength,
            points.get(0).y + dy * initialHandleFactor + ny * handleLength
        );
        Point2D.Double h1in = new Point2D.Double(
            points.get(1).x - dx * initialHandleFactor + nx * handleLength,
            points.get(1).y - dy * initialHandleFactor + ny * handleLength
        );
        handles.set(0, h0out);
        handles.set(1, h1in);
    }

    private void searchNearestPoint(Point2D.Double mousePos)
    {
        double minDist = HIT_RADIUS;
        selectedPoint = null;
        selectedPart = null;
        selectedIndex = null;
        int minIdx = 0;
        for (Point2D.Double p : points)
        {
            double dist = p.distance(mousePos);
            if (dist < minDist)
            {
                minDist = dist;
                selectedPoint = p;
                selectedIndex = minIdx;
                selectedPart = Part.ANCHOR;
            }
            ++minIdx;
        }
        minIdx = 0;
        for (Point2D.Double p : handles)
        {
            double dist = p.distance(mousePos);
            if (dist < minDist)
            {
                minDist = dist;
                selectedPoint = p;
                selectedIndex = minIdx;
                selectedPart = Part.HANDLE;
            }
            ++minIdx;
        }
    }

    private enum Mode
    {
        SET_START,
        SET_END,
        EDIT
    }

    private enum Part
    {
        ANCHOR,
        HANDLE,
    }
}
