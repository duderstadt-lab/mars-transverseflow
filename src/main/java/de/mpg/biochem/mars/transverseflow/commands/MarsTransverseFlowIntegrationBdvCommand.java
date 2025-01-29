/*-
 * #%L
 * JavaFX GUI for processing single-molecule TIRF and FMT data in the Structure and Dynamics of Molecular Machines research group.
 * %%
 * Copyright (C) 2018 - 2025 Karl Duderstadt
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
package de.mpg.biochem.mars.transverseflow.commands;

import bdv.viewer.Source;
import de.mpg.biochem.mars.fx.bdv.MarsBdvFrame;
import de.mpg.biochem.mars.image.PeakShape;
import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.molecule.Molecule;
import de.mpg.biochem.mars.molecule.MoleculeArchive;
import de.mpg.biochem.mars.molecule.MoleculeArchiveIndex;
import de.mpg.biochem.mars.molecule.MoleculeArchiveProperties;
import de.mpg.biochem.mars.object.MartianObject;
import de.mpg.biochem.mars.transverseflow.ReplicationForkShape;
import de.mpg.biochem.mars.transverseflow.TransverseFlowMolecule;
import de.mpg.biochem.mars.util.LogBuilder;
import de.mpg.biochem.mars.util.MarsUtil;
import net.imagej.ops.OpService;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.roi.IterableRegion;
import net.imglib2.roi.Masks;
import net.imglib2.roi.Regions;
import net.imglib2.roi.geom.GeomMasks;
import net.imglib2.roi.geom.real.WritableBox;
import net.imglib2.roi.geom.real.WritablePolygon2D;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import org.scijava.Initializable;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.command.Previewable;
import org.scijava.convert.ConvertService;
import org.scijava.log.LogService;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.util.DoubleArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Plugin(type = Command.class, label = "Bdv Transverse Flow Integrator")
public class MarsTransverseFlowIntegrationBdvCommand extends DynamicCommand implements Command,
Initializable, Previewable
{

	/**
	 * SERVICES
	 */
	@Parameter
	private LogService logService;
	
	@Parameter
	private ConvertService convertService;
	
	@Parameter
	private OpService opService;
	
	@Parameter
	private UIService uiService;

	@Parameter
	private StatusService statusService;
	
	/**
	 * IMAGE
	 */
	private MarsBdvFrame marsBdvFrame;
	
	private MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties<Molecule, MarsMetadata>, MoleculeArchiveIndex<Molecule, MarsMetadata>> archive;

	private TransverseFlowMolecule molecule;

	/**
	 * INPUT SETTINGS
	 */
	
	@Parameter(label = "Source", choices = { "a", "b", "c" },
		style = "group:Input", persist = false)
	private String source = "";

	@Parameter(label = "Strand integration radius")
	private long strandRadius = 2;
	@Parameter(label = "Background integration radius")
	private long backgroundRadius = 10;

	@Parameter(label = "Threads", required = false, min = "1", max = "120")
	private int nThreads = Runtime.getRuntime().availableProcessors();

	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false)
	private String imgIntegrationMessage = "Image Integration Boundaries (in pixels)";

	@Parameter(label = " X0")
	private int X0 = 0;

	@Parameter(label = " Y0")
	private int Y0 = 0;

	@Parameter(label = " width")
	private int width = 1024;

	@Parameter(label = " height")
	private int height = 1024;

	private ConcurrentMap<Integer, Double> tToPixelMedianBackground, tToParentSum, tToLeadingSum, tToLaggingSum, tToParentIntensity, tToLeadingIntensity, tToLaggingIntensity;

	private AtomicInteger progressInteger = new AtomicInteger(0);

	Interval imgInterval;
	
	@Override
	public void initialize() {
		final MutableModuleItem<String> channelItems = getInfo().getMutableInput(
			"source", String.class);
		channelItems.setChoices(marsBdvFrame.getSourceNames());
	}
	
	@Override
	public void run() {
		archive.getWindow().lock();
		//save the current settings to the PrefService
		//so they are reloaded the next time the command is opened.
		saveInputs();
		archive.getWindow().updateLockMessage("Integrating molecule");
		imgInterval = Intervals.createMinMax(X0, Y0, X0 + width - 1,
				Y0 + height - 1);

		//background
		tToPixelMedianBackground = new ConcurrentHashMap<>();

		//Parent
		tToParentSum = new ConcurrentHashMap<>();
		tToParentIntensity = new ConcurrentHashMap<>();

		//Lead
		tToLeadingSum = new ConcurrentHashMap<>();
		tToLeadingIntensity = new ConcurrentHashMap<>();

		//Lag
		tToLaggingSum = new ConcurrentHashMap<>();
		tToLaggingIntensity = new ConcurrentHashMap<>();

		List<Runnable> tasks = new ArrayList<>();
		for (int t : molecule.getShapeKeys()) tasks.add(() -> integrateStrandInT(t));

		MarsUtil.threadPoolBuilder(statusService, logService, () -> {
			archive.getWindow().setProgress((double) progressInteger.get() / molecule.getShapeKeys().size());
		}, tasks, nThreads);

		//Add sum, median pixel background, and intensity (sum - medianPixelBG * numPixels...) to table
		molecule.getTable().rows().forEach(row -> {
			int theT = (int)row.getValue("T");
            row.setValue(source + "_Median_Background_Pixel", tToPixelMedianBackground.getOrDefault(theT, Double.NaN));
			row.setValue(source + "_Parent_Sum_Pixels", tToParentSum.getOrDefault(theT, Double.NaN));
			row.setValue(source + "_Parent_Intensity", tToParentIntensity.getOrDefault(theT, Double.NaN));
			row.setValue(source + "_Lead_Sum_Pixels", tToLeadingSum.getOrDefault(theT, Double.NaN));
			row.setValue(source + "_Lead_Intensity", tToLeadingIntensity.getOrDefault(theT, Double.NaN));
			row.setValue(source + "_Lag_Sum_Pixels", tToLaggingSum.getOrDefault(theT, Double.NaN));
			row.setValue(source + "_Lag_Intensity", tToLaggingIntensity.getOrDefault(theT, Double.NaN));
		});

		LogBuilder builder = new LogBuilder();
		String log = LogBuilder.buildTitleBlock(getInfo().getLabel());
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		log += "\n" + LogBuilder.endBlock();
		archive.getMetadata(marsBdvFrame.getMetadataUID()).logln(log);

		archive.getWindow().unlock();
	}

	@SuppressWarnings("unchecked")
	private <T extends RealType<T> & NativeType<T>> void integrateStrandInT(int t)
	{
		Source<T> bdvSource = marsBdvFrame.getSource(source);

		//Remove the Z dimension
		RandomAccessibleInterval<T> img = Views.hyperSlice(bdvSource.getSource(t, 0), 2, 0);

		//Integrate background
		//Find shape boundaries that define integration interval
		ReplicationForkShape shape = molecule.getShape(t);
		double xmin = Double.POSITIVE_INFINITY;
		double xmax = Double.NEGATIVE_INFINITY;
		for (double x: shape.parentalX) {
			if (x < xmin) xmin = x;
			if (x > xmax) xmax = x;
		}
		for (double x: shape.leadingX) {
			if (x < xmin) xmin = x;
			if (x > xmax) xmax = x;
		}
		for (double x: shape.laggingX) {
			if (x < xmin) xmin = x;
			if (x > xmax) xmax = x;
		}

		double ymin = Double.POSITIVE_INFINITY;
		double ymax = Double.NEGATIVE_INFINITY;
		for (double y: shape.parentalY) {
			if (y < ymin) ymin = y;
			if (y > ymax) ymax = y;
		}
		for (double y: shape.leadingY) {
			if (y < ymin) ymin = y;
			if (y > ymax) ymax = y;
		}
		for (double y: shape.laggingY) {
			if (y < ymin) ymin = y;
			if (y > ymax) ymax = y;
		}

		Interval integrationInterval = Intervals.createMinMax((long) xmin - backgroundRadius, (long) ymin - backgroundRadius, (long) xmax + backgroundRadius, (long) ymax + backgroundRadius);

		//remove regions outside imgInterval. For example, on other half of dual view.
		Interval finalInterval = Intervals.intersect(imgInterval, integrationInterval);
		img = Views.interval(img, finalInterval);

		//find median in pixel values in local background surrounding molecule
		final WritableBox innerBox = GeomMasks.closedBox(new double[] { xmin, ymin }, new double[] { xmax, ymax });
		final WritableBox outerBox = GeomMasks.closedBox(new double[] { xmin - backgroundRadius, ymin - backgroundRadius}, new double[] { xmax + backgroundRadius, ymax + backgroundRadius });
		final IterableRegion<BoolType> backgroundRegion = Masks.toIterableRegion(outerBox.minus(innerBox));
		IterableInterval<T> backgroundNeighborhood = Regions.sample(backgroundRegion, Views.extendMirrorDouble(img));
		final DoubleArray backgroundPixels = new DoubleArray();
		for ( final T pixel : backgroundNeighborhood )
		{
			final double val = pixel.getRealDouble();
			if ( Double.isNaN( val ) )
				continue;
			backgroundPixels.addValue( val );
		}
		Util.quicksort( backgroundPixels.getArray(), 0, backgroundPixels.size() - 1 );

		final double medianBackgroundPixelValue = Double.valueOf( backgroundPixels.getArray()[ backgroundPixels.size() / 2 ] );
		tToPixelMedianBackground.put(t, medianBackgroundPixelValue);


		//PARENT
		double[] parentShapeX = new double[shape.parentalX.length*2];
		double[] parentShapeY = new double[shape.parentalY.length*2];
		for (int i=0; i < shape.parentalX.length; i++) {
			parentShapeX[i] = shape.parentalX[i];
			parentShapeX[shape.parentalX.length*2 - i - 1] = shape.parentalX[i];

			parentShapeY[i] = shape.parentalY[i] - strandRadius;
			parentShapeY[shape.parentalY.length*2 - i - 1] = shape.parentalY[i] + strandRadius;
		}

		final WritablePolygon2D polygonParent = GeomMasks.closedPolygon2D(parentShapeX, parentShapeY);
		final IterableRegion<BoolType> regionParent = Masks.toIterableRegion(polygonParent);
		Cursor<T> neighborhoodParent = Regions.sample(regionParent, Views.extendMirrorDouble(img)).localizingCursor();

		final DoubleArray intensitiesParent = new DoubleArray();
		final Map<Integer, Double> parentKymograph = new HashMap<>();
		final Map<Integer, Integer> parentKymographPixelCount = new HashMap<>();
		while(neighborhoodParent.hasNext())
		{
			final double val = neighborhoodParent.next().getRealDouble();
			final int x = neighborhoodParent.getIntPosition(0);
			//final int y = neighborhoodParent.getIntPosition(1);
			if ( Double.isNaN( val ) )
				continue;
			intensitiesParent.addValue( val );
			if (parentKymograph.containsKey(x)) {
				parentKymograph.put(x, val + parentKymograph.get(x));
				parentKymographPixelCount.put(x, parentKymographPixelCount.get(x) + 1);
			} else {
				parentKymograph.put(x, val);
				parentKymographPixelCount.put(x, 1);
			}
		}
		parentKymograph.keySet().forEach(y -> parentKymograph.put(y, parentKymograph.get(y) - parentKymographPixelCount.get(y)*medianBackgroundPixelValue));
		molecule.getShape(t).putParentIntegrationMap(source, parentKymograph);

		double parentSum;
		if ( intensitiesParent.isEmpty() ) parentSum = Double.NaN;
		else
		{
			parentSum = 0;
			for ( int i = 0; i < intensitiesParent.size(); i++ )
				parentSum += intensitiesParent.getArray()[ i ];
		}
		tToParentSum.put(t, parentSum);
		tToParentIntensity.put(t, Double.valueOf( parentSum - medianBackgroundPixelValue * intensitiesParent.size() ));


		//LEADING
		double[] leadingShapeX = new double[shape.leadingX.length*2];
		double[] leadingShapeY = new double[shape.leadingY.length*2];
		for (int i=0; i < shape.leadingX.length; i++) {
			leadingShapeX[i] = shape.leadingX[i];
			leadingShapeX[shape.leadingX.length*2 - i - 1] = shape.leadingX[i];

			leadingShapeY[i] = shape.leadingY[i] - strandRadius;
			leadingShapeY[shape.leadingY.length*2 - i - 1] = shape.leadingY[i] + strandRadius;
		}

		final WritablePolygon2D polygonleading = GeomMasks.closedPolygon2D(leadingShapeX, leadingShapeY);
		final IterableRegion<BoolType> regionleading = Masks.toIterableRegion(polygonleading);
		Cursor<T> neighborhoodleading = Regions.sample(regionleading, Views.extendMirrorDouble(img)).localizingCursor();

		final DoubleArray intensitiesleading = new DoubleArray();
		final Map<Integer, Double> leadingKymograph = new HashMap<>();
		final Map<Integer, Integer> leadingKymographPixelCount = new HashMap<>();
		while(neighborhoodleading.hasNext())
		{
			final double val = neighborhoodleading.next().getRealDouble();
			final int x = neighborhoodleading.getIntPosition(0);
			//final int y = neighborhoodleading.getIntPosition(1);
			if ( Double.isNaN( val ) )
				continue;
			intensitiesleading.addValue( val );
			if (leadingKymograph.containsKey(x)) {
				leadingKymograph.put(x, val + leadingKymograph.get(x));
				leadingKymographPixelCount.put(x, leadingKymographPixelCount.get(x) + 1);
			} else {
				leadingKymograph.put(x, val);
				leadingKymographPixelCount.put(x, 1);
			}
		}
		leadingKymograph.keySet().forEach(y -> leadingKymograph.put(y, leadingKymograph.get(y) - leadingKymographPixelCount.get(y)*medianBackgroundPixelValue));
		molecule.getShape(t).putLeadingIntegrationMap(source, leadingKymograph);

		double leadingSum;
		if ( intensitiesleading.isEmpty() ) leadingSum = Double.NaN;
		else
		{
			leadingSum = 0;
			for ( int i = 0; i < intensitiesleading.size(); i++ )
				leadingSum += intensitiesleading.getArray()[ i ];
		}
		tToLeadingSum.put(t, leadingSum);
		tToLeadingIntensity.put(t, Double.valueOf( leadingSum - medianBackgroundPixelValue * intensitiesParent.size() ));


		//LAGGING
		double[] laggingShapeX = new double[shape.laggingX.length*2];
		double[] laggingShapeY = new double[shape.laggingY.length*2];
		for (int i=0; i < shape.laggingY.length; i++) {
			laggingShapeY[i] = shape.laggingY[i];
			laggingShapeY[shape.laggingY.length*2 - i - 1] = shape.laggingY[i];

			laggingShapeX[i] = shape.laggingX[i] - strandRadius;
			laggingShapeX[shape.laggingX.length*2 - i - 1] = shape.laggingX[i] + strandRadius;
		}

		final WritablePolygon2D polygonlagging = GeomMasks.closedPolygon2D(laggingShapeX, laggingShapeY);
		final IterableRegion<BoolType> regionlagging = Masks.toIterableRegion(polygonlagging);
		Cursor<T> neighborhoodlagging = Regions.sample(regionlagging, Views.extendMirrorDouble(img)).localizingCursor();

		final DoubleArray intensitieslagging = new DoubleArray();
		final Map<Integer, Double> laggingKymograph = new HashMap<>();
		final Map<Integer, Integer> laggingKymographPixelCount = new HashMap<>();
		while(neighborhoodlagging.hasNext())
		{
			final double val = neighborhoodlagging.next().getRealDouble();
			//final int x = neighborhoodlagging.getIntPosition(0);
			final int y = neighborhoodlagging.getIntPosition(1);
			if ( Double.isNaN( val ) )
				continue;
			intensitieslagging.addValue( val );
			if (laggingKymograph.containsKey(y)) {
				laggingKymograph.put(y, val + laggingKymograph.get(y));
				laggingKymographPixelCount.put(y, laggingKymographPixelCount.get(y) + 1);
			} else {
				laggingKymograph.put(y, val);
				laggingKymographPixelCount.put(y, 1);
			}
		}
		laggingKymograph.keySet().forEach(y -> laggingKymograph.put(y, laggingKymograph.get(y) - laggingKymographPixelCount.get(y)*medianBackgroundPixelValue));
		molecule.getShape(t).putLaggingIntegrationMap(source, laggingKymograph);

		double laggingSum;
		if ( intensitieslagging.isEmpty() ) laggingSum = Double.NaN;
		else
		{
			laggingSum = 0;
			for ( int i = 0; i < intensitieslagging.size(); i++ )
				laggingSum += intensitieslagging.getArray()[ i ];
		}
		tToLaggingSum.put(t, laggingSum);
		tToLaggingIntensity.put(t, Double.valueOf( laggingSum - medianBackgroundPixelValue * intensitiesParent.size() ));

		progressInteger.incrementAndGet();
	}

	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("Source", source);
	}
	
	public void setMarsBdvFrame(MarsBdvFrame marsBdvFrame) {
		this.marsBdvFrame = marsBdvFrame;
	}
	
	public MarsBdvFrame getMarsBdvFrame() {
		return marsBdvFrame;
	}
	
	public void setArchive(MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties<Molecule, MarsMetadata>, MoleculeArchiveIndex<Molecule, MarsMetadata>> archive) {
		this.archive = archive;
	}
	
	public MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties<Molecule, MarsMetadata>, MoleculeArchiveIndex<Molecule, MarsMetadata>> getArchive() {
		return archive;
	}

	public void setMolecule(TransverseFlowMolecule molecule) {
		this.molecule = molecule;
	}

	public TransverseFlowMolecule getMolecule() {
		return this.molecule;
	}
}

