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

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.*;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOverlaySource;
import de.mpg.biochem.mars.fx.bdv.MarsBdvFrame;
import de.mpg.biochem.mars.image.DNASegment;
import net.imglib2.RealPoint;
import net.imglib2.type.numeric.ARGBType;
import org.scijava.ui.behaviour.Behaviour;
import org.scijava.ui.behaviour.BehaviourMap;
import org.scijava.ui.behaviour.InputTrigger;
import java.util.Map;
import java.util.HashMap;

/**
 * Installs an interactive 2D line selection tool on a BDV.
 * <p>
 * The feature consists of an overlay added to the BDV and editing behaviours
 * where the user can draw the line.
 * <p>
 * Inspired by the TransformedBoxEditor in bigdataviewer-core.
 *
 * @author Karl Duderstadt
 */

public class DynamicLineEditor
{
    private static final String BLOCKING_MAP = "line-blocking";

    private static final String[] BOUNDING_LINE_TOGGLE_EDITOR_KEYS = new String[] { "button1" };

    private DynamicLineOverlay dynamicLineOverlay;

    private BdvOverlaySource<?> overlaySource;

    private MarsBdvFrame marsBdvFrame;

    private final MouseListener ml;

    private final BehaviourMap blockMap;

    private boolean lineSelectionActive;

    private Map<Integer, DNASegment> segments = new HashMap<>();
    private int currentTimePoint = 0;


    public DynamicLineEditor(MarsBdvFrame marsBdvFrame)
    {
        this.marsBdvFrame = marsBdvFrame;
        final BdvHandle bdvHandle = marsBdvFrame.getBdvHandle();
        ml = new MouseAdapter()
        {
            @Override
            public void mousePressed( final MouseEvent e )
            {
                if (e.getButton() != MouseEvent.BUTTON1) return;
                if (!lineSelectionActive) {
                    final RealPoint realPoint = new RealPoint(3);
                    bdvHandle.getViewerPanel().getGlobalMouseCoordinates(realPoint);
                    currentTimePoint = bdvHandle.getViewerPanel().state().getCurrentTimepoint();
                    segments.put(
                        currentTimePoint,
                        new DNASegment(
                            realPoint.getDoublePosition(0),
                            realPoint.getDoublePosition(1),
                            realPoint.getDoublePosition(0),
                            realPoint.getDoublePosition(1)
                        )
                    );
                    dynamicLineOverlay.setSegments(segments);
                    //dynamicLineOverlay.setCurrentTimePoint(currentTimePoint);
                    lineSelectionActive = true;
                } else {
                    final RealPoint realPoint = new RealPoint(3);
                    bdvHandle.getViewerPanel().getGlobalMouseCoordinates(realPoint);
                    segments.get(currentTimePoint).setX2(realPoint.getDoublePosition(0));
                    segments.get(currentTimePoint).setY2(realPoint.getDoublePosition(1));
                    lineSelectionActive = false;
                }
            }

            @Override
            public void mouseMoved( final MouseEvent e )
            {
                if (lineSelectionActive) {
                    final RealPoint realPoint = new RealPoint(3);
                    bdvHandle.getViewerPanel().getGlobalMouseCoordinates(realPoint);
                    segments.get(currentTimePoint).setX2(realPoint.getDoublePosition(0));
                    segments.get(currentTimePoint).setY2(realPoint.getDoublePosition(1));
                }
            }
        };

        /*
         * Create BehaviourMap to block behaviours interfering with
         * DragBoxCornerBehaviour. The block map is only active while a corner
         * is highlighted.
         */
        blockMap = new BehaviourMap();
    }

    public void install()
    {
        if (dynamicLineOverlay == null) dynamicLineOverlay = new DynamicLineOverlay();
        overlaySource = BdvFunctions.showOverlay(dynamicLineOverlay, "Branch-Preview", Bdv
                .options().addTo(marsBdvFrame.getBdvHandle()));
        overlaySource.setColor(new ARGBType(-13312));
        marsBdvFrame.getBdvHandle().getViewerPanel().getDisplay().addHandler(ml);

        refreshBlockMap();
        block();
    }

    public void uninstall()
    {
        segments = new HashMap<>();
        dynamicLineOverlay.setSegments(new HashMap<>());
        lineSelectionActive = false;
        unblock();

        marsBdvFrame.getBdvHandle().getViewerPanel().getDisplay().removeHandler(ml);

        if (overlaySource != null) overlaySource.removeFromBdv();
        if (dynamicLineOverlay != null) marsBdvFrame.getBdvHandle().getViewerPanel().getDisplay().overlays().remove(dynamicLineOverlay);
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

    public Map<Integer, DNASegment> getSegments() {
        if (lineSelectionActive)  {
            segments.remove(currentTimePoint);
            lineSelectionActive = false;
        }
        return segments;
    }

    public void setMarsBdvFrame(MarsBdvFrame marsBdvFrame) {
        this.marsBdvFrame = marsBdvFrame;
    }

    public MarsBdvFrame getMarsBdvFrame() {
        return marsBdvFrame;
    }

    //public void setTimePointOfOverlay(int timePoint) { this.dynamicLineOverlay.setCurrentTimePoint(timePoint); }
}
