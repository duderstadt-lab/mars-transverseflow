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

    public double[] parentalX, parentalY, leadingX, leadingY, laggingX, laggingY;

    public ReplicationForkShape(final double[] parentalX, final double[] parentalY, final double[] leadingX, final double[] leadingY, final double[] laggingX, final double[] laggingY) {
        this.parentalX = parentalX;
        this.parentalY = parentalY;
        this.leadingX = leadingX;
        this.leadingY = leadingY;
        this.laggingX = laggingX;
        this.laggingY = laggingY;
    }

    public ReplicationForkShape(JsonParser jParser) throws IOException {
        super();
        fromJSON(jParser);
    }

    public double parentalLength() {
        return length(parentalX, parentalY);
    }

    public double[] parentCenter() {
        return centroid(parentalX, parentalY);
    }

    public double[] leadingCenter() {
        return centroid(leadingX, leadingY);
    }

    public double[] laggingCenter() {
        return centroid(laggingX, laggingY);
    }

    public double leadingLength() {
        return length(leadingX, leadingY);
    }

    public double laggingLength() {
        return length(laggingX, laggingY);
    }

    private double length(final double[] x, final double[] y) {
        final int nPoints = x.length;
        if (nPoints < 2) return 0;

        double length = 0;
        for (int i = 0; i < nPoints - 1; i++) {
            final double dx = x[i + 1] - x[i];
            final double dy = y[i + 1] - y[i];
            length += Math.sqrt(dx * dx + dy * dy);
        }
        return length;
    }

    private static double[] centroid(final double[] x, final double[] y) {
        final double area = signedArea(x, y);
        double ax = 0.0;
        double ay = 0.0;
        final int n = x.length;
        for (int i = 0; i < n - 1; i++) {
            final double w = x[i] * y[i + 1] - x[i + 1] * y[i];
            ax += (x[i] + x[i + 1]) * w;
            ay += (y[i] + y[i + 1]) * w;
        }

        final double w0 = x[n - 1] * y[0] - x[0] * y[n - 1];
        ax += (x[n - 1] + x[0]) * w0;
        ay += (y[n - 1] + y[0]) * w0;
        return new double[] { ax / 6. / area, ay / 6. / area };
    }

    private static double signedArea(final double[] x, final double[] y) {
        final int n = x.length;
        double a = 0.0;
        for (int i = 0; i < n - 1; i++)
            a += x[i] * y[i + 1] - x[i + 1] * y[i];

        return (a + x[n - 1] * y[0] - x[0] * y[n - 1]) / 2.0;
    }

    @Override
    protected void createIOMaps() {

        //Parental
        setJsonField("parentalPoints", jGenerator -> jGenerator.writeNumberField(
                "parentalPoints", parentalX.length), jParser -> {
            final int parentalPoints = jParser.getIntValue();
            parentalX = new double[parentalPoints];
            parentalY = new double[parentalPoints];
        });
        setJsonField("parentalX", jGenerator -> {
            jGenerator.writeFieldName("parentalX");
            jGenerator.writeArray(parentalX, 0, parentalX.length);
        }, jParser -> {
            int xIndex = 0;
            while (jParser.nextToken() != JsonToken.END_ARRAY) {
                parentalX[xIndex] = jParser.getDoubleValue();
                xIndex++;
            }
        });
        setJsonField("parentalY", jGenerator -> {
            jGenerator.writeFieldName("parentalY");
            jGenerator.writeArray(parentalY, 0, parentalY.length);
        }, jParser -> {
            int yIndex = 0;
            while (jParser.nextToken() != JsonToken.END_ARRAY) {
                parentalY[yIndex] = jParser.getDoubleValue();
                yIndex++;
            }
        });

        //Leading
        setJsonField("leadingPoints", jGenerator -> jGenerator.writeNumberField(
                "leadingPoints", leadingX.length), jParser -> {
            final int leadingPoints = jParser.getIntValue();
            leadingX = new double[leadingPoints];
            leadingY = new double[leadingPoints];
        });
        setJsonField("leadingX", jGenerator -> {
            jGenerator.writeFieldName("leadingX");
            jGenerator.writeArray(leadingX, 0, leadingX.length);
        }, jParser -> {
            int xIndex = 0;
            while (jParser.nextToken() != JsonToken.END_ARRAY) {
                leadingX[xIndex] = jParser.getDoubleValue();
                xIndex++;
            }
        });
        setJsonField("leadingY", jGenerator -> {
            jGenerator.writeFieldName("leadingY");
            jGenerator.writeArray(leadingY, 0, leadingY.length);
        }, jParser -> {
            int yIndex = 0;
            while (jParser.nextToken() != JsonToken.END_ARRAY) {
                leadingY[yIndex] = jParser.getDoubleValue();
                yIndex++;
            }
        });

        //Lagging
        setJsonField("laggingPoints", jGenerator -> jGenerator.writeNumberField(
                "laggingPoints", laggingX.length), jParser -> {
            final int laggingPoints = jParser.getIntValue();
            laggingX = new double[laggingPoints];
            laggingY = new double[laggingPoints];
        });
        setJsonField("laggingX", jGenerator -> {
            jGenerator.writeFieldName("laggingX");
            jGenerator.writeArray(laggingX, 0, laggingX.length);
        }, jParser -> {
            int xIndex = 0;
            while (jParser.nextToken() != JsonToken.END_ARRAY) {
                laggingX[xIndex] = jParser.getDoubleValue();
                xIndex++;
            }
        });
        setJsonField("laggingY", jGenerator -> {
            jGenerator.writeFieldName("laggingY");
            jGenerator.writeArray(laggingY, 0, laggingY.length);
        }, jParser -> {
            int yIndex = 0;
            while (jParser.nextToken() != JsonToken.END_ARRAY) {
                laggingY[yIndex] = jParser.getDoubleValue();
                yIndex++;
            }
        });
    }
}