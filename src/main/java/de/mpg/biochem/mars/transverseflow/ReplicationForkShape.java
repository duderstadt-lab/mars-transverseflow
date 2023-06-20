/*-
 * #%L
 * Mars command and definitions for transverse flow molecule types.
 * %%
 * Copyright (C) 2023 Karl Duderstadt
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

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.mars.molecule.AbstractJsonConvertibleRecord;
import net.imagej.ImgPlus;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.roi.IterableRegion;
import net.imglib2.roi.Masks;
import net.imglib2.roi.Regions;
import net.imglib2.roi.geom.GeomMasks;
import net.imglib2.roi.geom.real.WritablePolygon2D;
import net.imglib2.type.logic.BoolType;
import net.imglib2.view.Views;

/**
 * Stores shape information for replication fork shapes. Extends
 * AbstractJsonConvertibleRecord to allow for saving to file as JSON. Provides
 * utility methods to calculate replication fork shape features.
 *
 * @author Karl Duderstadt
 */

public class ReplicationForkShape extends AbstractJsonConvertibleRecord {

    public double[] x, y;

    public ReplicationForkShape(final double[] x, final double[] y) {
        this.x = x;
        this.y = y;
    }

    public ReplicationForkShape(JsonParser jParser) throws IOException {
        super();
        fromJSON(jParser);
    }

    public double length() {

        //TODO update me
        /*
        final int nPoints = x.length;
        if (nPoints < 2) return 0;

        double length = 0;
        for (int i = 0; i < nPoints - 1; i++) {
            final double dx = x[i + 1] - x[i];
            final double dy = y[i + 1] - y[i];
            length += Math.sqrt(dx * dx + dy * dy);
        }

        final double dx0 = x[0] - x[nPoints - 1];
        final double dy0 = y[0] - y[nPoints - 1];
        length += Math.sqrt(dx0 * dx0 + dy0 * dy0);

        return length;
         */
    }

    @Override
    protected void createIOMaps() {
        //TODO update me...
        /*
        setJsonField("vertices", jGenerator -> jGenerator.writeNumberField(
                "vertices", x.length), jParser -> {
            final int vertices = jParser.getIntValue();
            x = new double[vertices];
            y = new double[vertices];
        });
        setJsonField("x", jGenerator -> {
            jGenerator.writeFieldName("x");
            jGenerator.writeArray(x, 0, x.length);
        }, jParser -> {
            int xIndex = 0;
            while (jParser.nextToken() != JsonToken.END_ARRAY) {
                x[xIndex] = jParser.getDoubleValue();
                xIndex++;
            }
        });
        setJsonField("y", jGenerator -> {
            jGenerator.writeFieldName("y");
            jGenerator.writeArray(y, 0, y.length);
        }, jParser -> {
            int yIndex = 0;
            while (jParser.nextToken() != JsonToken.END_ARRAY) {
                y[yIndex] = jParser.getDoubleValue();
                yIndex++;
            }
        });
         */
    }
}