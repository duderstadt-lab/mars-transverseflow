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

package de.mpg.biochem.mars.transverseflow.commands;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import de.mpg.biochem.mars.metadata.MarsOMEMetadata;
import de.mpg.biochem.mars.metadata.MarsOMEUtils;
import de.mpg.biochem.mars.molecule.MoleculeArchiveService;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.table.MarsTableService;
import de.mpg.biochem.mars.transverseflow.ReplicationForkShape;
import de.mpg.biochem.mars.transverseflow.TransverseFlowArchive;
import de.mpg.biochem.mars.transverseflow.TransverseFlowMolecule;
import de.mpg.biochem.mars.util.DefaultJsonConverter;
import de.mpg.biochem.mars.util.ForceCalculator;
import de.mpg.biochem.mars.util.LogBuilder;
import de.mpg.biochem.mars.util.MarsMath;
import ij.ImagePlus;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.process.FloatPolygon;
import io.scif.Metadata;
import io.scif.img.SCIFIOImgPlus;
import io.scif.ome.OMEMetadata;
import io.scif.ome.services.OMEXMLService;
import io.scif.services.FormatService;
import io.scif.services.TranslatorService;
import loci.common.services.ServiceException;
import net.imagej.Dataset;
import net.imagej.ImgPlus;
import net.imagej.display.ImageDisplay;
import net.imagej.ops.OpService;
import ome.xml.meta.OMEXMLMetadata;
import org.scijava.Initializable;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.convert.ConvertService;
import org.scijava.event.EventService;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.ChoiceWidget;

import java.io.*;
import java.util.*;

@Plugin(type = Command.class, label = "Transverse Flow Archive Builder", menu = { @Menu(
	label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
	mnemonic = MenuConstants.PLUGINS_MNEMONIC), @Menu(label = "Mars",
		weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = 'm'), @Menu(
			label = "Molecule", weight = 1, mnemonic = 'm'), @Menu(
				label = "Build Transverse Flow Archive", weight = 4, mnemonic = 't') })
public class TransverseFlowArchiveBuilderCommand extends DynamicCommand implements Command,
	Initializable
{

	/**
	 * SERVICES
	 */
	@Parameter
	private LogService logService;

	@Parameter
	private TranslatorService translatorService;

	@Parameter
	private OMEXMLService omexmlService;

	@Parameter
	private FormatService formatService;

	@Parameter
	private MarsTableService resultsTableService;

	@Parameter
	private ConvertService convertService;

	@Parameter
	private OpService opService;

	@Parameter
	private EventService eventService;

	@Parameter
	private UIService uiService;

	@Parameter
	private MoleculeArchiveService moleculeArchiveService;

	/**
	 * IMAGE
	 */
	@Parameter(label = "Image providing metadata")
	private ImageDisplay imageDisplay;

	@Parameter(visibility = ItemVisibility.MESSAGE,
		style = "align:center", persist = false)
	private String imageName = "name";

	@Parameter(label = "Directory containing label files", style="directory")
	private File labelDirectory;

	/**
	 * OUTPUTS
	 */
	@Parameter(label = "Smooth labels")
	private boolean smooth = true;

	@Parameter(label = "Label rescaling factor (label coordinates / factor)")
	private float labelRescalingFactor = 1;

	@Parameter(label = "Label connectivity distance cutoff (pixel)")
	private double connectivityCutoff = 3.1;

	@Parameter(label = "Microscope")
 	private String microscope = "Winky";

	@Parameter(label = "Pixel size (um)")
	private double pixelSize = 0.156;

	@Parameter(label = "DNA length (bps)")
	private double lengthBps = 27000;

	@Parameter(label = "Metadata UID",
		style = ChoiceWidget.RADIO_BUTTON_VERTICAL_STYLE,
		choices = { "unique from dataset", "random" })
	private String metadataUIDSource = "random";

	@Parameter(label = "Transverse Flow Archive", type = ItemIO.OUTPUT)
	private TransverseFlowArchive archive;

	private static JsonFactory jfactory;
	private Dataset dataset;
	private ImagePlus image;

	@Override
	public void initialize() {
		if (imageDisplay != null) {
			dataset = (Dataset) imageDisplay.getActiveView().getData();
			image = convertService.convert(imageDisplay, ImagePlus.class);
		}
		else return;

		if (dataset != null) {
			final MutableModuleItem<String> imageNameItem = getInfo().getMutableInput(
				"imageName", String.class);
			imageNameItem.setValue(this, dataset.getName());
		}
	}

	@Override
	public void run() {
		if (dataset == null && image != null) dataset = convertService.convert(
			image, Dataset.class);

		if (dataset == null) return;

		// Build log
		LogBuilder builder = new LogBuilder();
		String log = LogBuilder.buildTitleBlock("Transverse Flow Archive Builder");
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		logService.info(log);

		archive = new TransverseFlowArchive("archive.yama");
		MarsOMEMetadata marsOMEMetadata = buildOMEMetadata();
		archive.putMetadata(marsOMEMetadata);

		if (jfactory == null) jfactory = new JsonFactory();

		String[] labelFileList = labelDirectory.list((dir, name) -> name.endsWith(".labeling"));

		if (labelFileList != null)
			for (String fileName : labelFileList) {
				TransverseFlowMolecule molecule = createTransverseFlowMolecule(new File(labelDirectory.getAbsolutePath() + "/" + fileName));
				molecule.setMetadataUID(marsOMEMetadata.getUID());
				archive.put(molecule);
			}

		// Make sure the output archive has the correct name
		getInfo().getMutableOutput("archive", TransverseFlowArchive.class).setLabel(archive
			.getName());

		logService.info(LogBuilder.endBlock(true));
		log += "\n" + LogBuilder.endBlock(true);
		archive.logln(log);
		archive.logln("   ");
	}

	private TransverseFlowMolecule createTransverseFlowMolecule(File labelingFile) {
		Map<Integer, double[]> parentalXMap = new HashMap<>();
		Map<Integer, double[]> parentalYMap = new HashMap<>();
		Map<Integer, double[]> leadingXMap = new HashMap<>();
		Map<Integer, double[]> leadingYMap = new HashMap<>();
		Map<Integer, double[]> laggingXMap = new HashMap<>();
		Map<Integer, double[]> laggingYMap = new HashMap<>();

		DefaultJsonConverter defaultParser = new DefaultJsonConverter();
		defaultParser.setShowWarnings(false);
		defaultParser.setJsonField("labels", null, jParser -> {
			while (jParser.nextToken() != JsonToken.END_OBJECT) {
				String fieldName = jParser.getCurrentName();
				if (fieldName.equals("parental")) parsePointsToMaps(jParser, parentalXMap, parentalYMap, fieldName);
				if (fieldName.equals("leading")) parsePointsToMaps(jParser, leadingXMap, leadingYMap, fieldName);
				if (fieldName.equals("lagging")) parsePointsToMaps(jParser, laggingXMap, laggingYMap, fieldName);
			}
		});

		try {
			InputStream inputStream = new BufferedInputStream(new FileInputStream(
					labelingFile));
			JsonParser jParser = jfactory.createParser(inputStream);
			defaultParser.fromJSON(jParser);
			jParser.close();
			inputStream.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		int sizeT = archive.getMetadata(0).getImage(0).getSizeT();

		MarsTable table = new MarsTable("TransverseFlowMolecule Table", "T",
				"Time_(s)", "Parental_(um)", "Parental_(kb)",
				"Leading_(um)", "Leading_(kb)",
				"Lagging_(um)", "Lagging_(kb)",
				"Parental_and_Leading_(um)", "Force_(pN)");
		table.appendRows(sizeT);

		ForceCalculator calculator = new ForceCalculator(50 * Math.pow(10, -9),
				lengthBps*0.34 * Math.pow(10, -9), 296.15);

		TransverseFlowMolecule molecule = new TransverseFlowMolecule();
		for (int t = 0; t < sizeT; t++) {
			ReplicationForkShape repliShape = new ReplicationForkShape((parentalXMap.containsKey(t)) ? parentalXMap.get(t) : new double[0],
					(parentalYMap.containsKey(t)) ? parentalYMap.get(t) : new double[0],
					(leadingXMap.containsKey(t)) ? leadingXMap.get(t) : new double[0],
					(leadingYMap.containsKey(t)) ? leadingYMap.get(t) : new double[0],
					(laggingXMap.containsKey(t)) ? laggingXMap.get(t) : new double[0],
					(laggingYMap.containsKey(t)) ? laggingYMap.get(t) : new double[0]
			);
			molecule.putShape(t, repliShape);
			double timeInSeconds = archive.getMetadata(0).getImage(0).getPlane(0, 0, t).getDeltaTinSeconds();
			//These lengths are in pixels..
			double parentalLength = repliShape.parentalLength();
			double leadingLength = repliShape.leadingLength();
			double laggingLength = repliShape.laggingLength();
			double parentalAndLeading = parentalLength + leadingLength;

			table.setValue("T", t, t);
			table.setValue("Time_(s)", t, timeInSeconds);
			table.setValue("Parental_(um)", t, parentalLength*pixelSize);
			table.setValue("Parental_(kb)", t, (lengthBps/1000)*parentalLength/parentalAndLeading);
			table.setValue("Leading_(um)", t, leadingLength*pixelSize);
			table.setValue("Leading_(kb)", t, (lengthBps/1000)*leadingLength/parentalAndLeading);
			table.setValue("Lagging_(um)", t, laggingLength*pixelSize);
			table.setValue("Lagging_(kb)", t, (lengthBps/1000)*laggingLength/parentalAndLeading);
			table.setValue("Parental_and_Leading_(um)", t, parentalAndLeading*pixelSize);
			table.setValue("Force_(pN)", t, calculator.getWLCForce(parentalAndLeading * pixelSize * Math.pow(10, -6)) * Math.pow(10, 12));
		}
		if (molecule.hasShape(0)) {
			double[] parentCenter = molecule.getShape(0).parentCenter();
			molecule.setParameter("Parent_Center_X", parentCenter[0]);
			molecule.setParameter("Parent_Center_Y", parentCenter[1]);
		}
		molecule.setTable(table);
		return molecule;
	}

	private void parsePointsToMaps(JsonParser jParser, Map<Integer, double[]> tToXMap, Map<Integer, double[]> tToYMap, String fieldName) throws IOException {
		jParser.nextToken();
		List<Point2D> points = new ArrayList<>();
		int curT = -1;
		while (jParser.nextToken() != JsonToken.END_ARRAY) {
			jParser.nextToken();
			float x = jParser.getIntValue() / labelRescalingFactor;

			jParser.nextToken();
			float y = jParser.getIntValue() / labelRescalingFactor;

			jParser.nextToken();
			int t = jParser.getIntValue();
			if (curT == -1) curT = t;

			if (t != curT) {
				points = (fieldName.equals("parental") || fieldName.equals("leading")) ? orderPoints(points, true) : orderPoints(points, false);
				calculatePolygons(points, curT, tToXMap, tToYMap);
				points.clear();
				curT = t;
			}
			points.add(new Point2D(x, y));

			//Move past the end of the point array
			jParser.nextToken();
		}

		points = (fieldName.equals("parental") || fieldName.equals("leading")) ? orderPoints(points, true) : orderPoints(points, false);
		calculatePolygons(points, curT, tToXMap, tToYMap);
	}

	private void calculatePolygons(List<Point2D> points, int curT, Map<Integer, double[]> tToXMap, Map<Integer, double[]> tToYMap) {
		//if (sortAxis == 0) points.sort(Comparator.comparing(Point2D::getX));
		//else if (sortAxis == 1) points.sort(Comparator.comparing(Point2D::getY));
		float[] xPoints = new float[points.size()];
		float[] yPoints = new float[points.size()];
		for (int i = 0; i < points.size(); i++) {
			xPoints[i] = points.get(i).getX();
			yPoints[i] = points.get(i).getY();
		}

		//Now let's do some smoothing
		PolygonRoi r = new PolygonRoi(xPoints, yPoints, Roi.POLYLINE);
		if (smooth) {
			r = new PolygonRoi(r.getInterpolatedPolygon(1, false), Roi.POLYLINE);
			r = smoothPolygonRoi(r);
			r = new PolygonRoi(r.getInterpolatedPolygon(Math.min(2, r
				.getNCoordinates() * 0.1), false), Roi.POLYLINE);
		}

		FloatPolygon fPoly = r.getFloatPolygon();

		double[] xDoublePoints = new double[fPoly.xpoints.length];
		double[] yDoublePoints = new double[fPoly.ypoints.length];
		for (int i = 0; i < xDoublePoints.length; i++) {
			xDoublePoints[i] = fPoly.xpoints[i];
			yDoublePoints[i] = fPoly.ypoints[i];
		}

		tToXMap.put(curT, xDoublePoints);
		tToYMap.put(curT, yDoublePoints);
	}

	private List<Point2D> orderPoints(List<Point2D> points, boolean fromLargestY) {
		List<Point2D> orderedPoints = new ArrayList<>();
		//Find the point in the list with the largest Y value
		orderedPoints.add(points.get(0));
		for (int i=1; i<points.size(); i++) {
			if (fromLargestY)
				if (points.get(i).getY() > orderedPoints.get(0).getY())
					orderedPoints.set(0, points.get(i));
			else
				if (points.get(i).getY() < orderedPoints.get(0).getY())
					orderedPoints.set(0, points.get(i));
		}

		orderedPoints.get(0).setConnected(true);
		for (int i=0; i < points.size() - 1; i++) {
			Point2D pos = orderedPoints.get(i);
			Point2D bestConnection = null;
			double bestDistance = Double.MAX_VALUE;
			for (int j = 0; j < points.size(); j++) {
				if (!points.get(j).connected()) {
					double dX = pos.getX() - points.get(j).getX();
					double dY = pos.getY() - points.get(j).getY();
					double distance = Math.sqrt(dX * dX + dY * dY);
					if (distance < bestDistance) {
						bestDistance = distance;
						bestConnection = points.get(j);
					}
				}
			}
			if (bestDistance < connectivityCutoff) {
				orderedPoints.add(bestConnection);
				bestConnection.setConnected(true);
			} else
				break;
		}

		return orderedPoints;
	}

	private PolygonRoi smoothPolygonRoi(PolygonRoi r) {
		FloatPolygon poly = r.getFloatPolygon();
		FloatPolygon poly2 = new FloatPolygon();
		int nPoints = poly.npoints;
		poly2.addPoint(poly.xpoints[0], poly.ypoints[0]);
		for (int i = 1; i < nPoints; i += 2) {
			int iMinus = (i + nPoints - 1) % nPoints;
			int iPlus = (i + 1) % nPoints;
			if (iPlus != 0)
				poly2.addPoint((poly.xpoints[iMinus] + poly.xpoints[iPlus] +
						poly.xpoints[i]) / 3, (poly.ypoints[iMinus] + poly.ypoints[iPlus] +
						poly.ypoints[i]) / 3);
		}
		poly2.addPoint(poly.xpoints[poly.xpoints.length - 1], poly.ypoints[poly.ypoints.length - 1]);
		return new PolygonRoi(poly2, Roi.POLYLINE);
	}

	private class Point2D {
		final float x, y;
		boolean connected;
		Point2D(float x, float y) {
			this.x = x;
			this.y = y;
		}

		float getX() {
			return x;
		}

		float getY() {
			return y;
		}

		boolean connected() {
			return connected;
		}

		void setConnected(boolean connected) {
			this.connected = connected;
		}
	}

	private MarsOMEMetadata buildOMEMetadata() {
		ImgPlus<?> imp = dataset.getImgPlus();

		OMEXMLMetadata omexmlMetadata = null;
		if (!(imp instanceof SCIFIOImgPlus)) {
			logService.info(
				"This image has not been opened with SCIFIO. Creating OME Metadata...");
			try {
				omexmlMetadata = MarsOMEUtils.createOMEXMLMetadata(omexmlService,
					dataset);
			}
			catch (ServiceException e) {
				e.printStackTrace();
			}
		}
		else {
			Metadata metadata = (Metadata) dataset.getProperties().get(
				"scifio.metadata.global");

			OMEMetadata omeMeta = new OMEMetadata(getContext());
			if (!translatorService.translate(metadata, omeMeta, true)) {
				logService.info("Unable to extract OME Metadata. Creating...");
				try {
					omexmlMetadata = MarsOMEUtils.createOMEXMLMetadata(omexmlService,
						dataset);
				}
				catch (ServiceException e) {
					e.printStackTrace();
				}
			}
			else {
				omexmlMetadata = omeMeta.getRoot();
			}

			assert omexmlMetadata != null;
			omexmlMetadata.setImageName(metadata.get(0).getName(), 0);
		}

		if (omexmlMetadata == null)
			return new MarsOMEMetadata(MarsMath.getUUID58().substring(0, 10), null);

		// Ensures that MarsMicromanagerFormat correctly sets the ImageID based on
		// the position.
		try {
			if (omexmlMetadata.getDoubleAnnotationCount() > 0 && omexmlMetadata
					.getDoubleAnnotationID(0).equals("ImageID"))
			{
				omexmlMetadata.setImageID("Image:" + omexmlMetadata
						.getDoubleAnnotationValue(0).intValue(), 0);
			}
		}
		catch (NullPointerException e) {
			/*
			 Do nothing. Many of the {@link ome.xml.meta.OMEXMLMetadata} methods give
			 NullPointerException if fields are not set.
			*/
		}

		String metaUID;
		if (metadataUIDSource.equals("unique from dataset")) {
			metaUID = MarsOMEUtils.generateMetadataUIDfromDataset(omexmlMetadata);

			if (metaUID == null) {
				logService.info(
					"Failed to generate unique metadata uid. Using random generated metadata uid.");
			}
			else metaUID = MarsMath.getUUID58().substring(0, 10);
		}
		else metaUID = MarsMath.getUUID58().substring(0, 10);

		return new MarsOMEMetadata(metaUID, omexmlMetadata);
	}

	private void addInputParameterLog(LogBuilder builder) {
		if (image != null) {
			builder.addParameter("Image Title", image.getTitle());
			if (image.getOriginalFileInfo() != null && image
				.getOriginalFileInfo().directory != null)
			{
				builder.addParameter("Image Directory", image
					.getOriginalFileInfo().directory);
			}
		}
		else builder.addParameter("Dataset Name", dataset.getName());
		builder.addParameter("Smooth labels", smooth);
		builder.addParameter("Label rescaling factor", labelRescalingFactor);
		builder.addParameter("Label connectivity distance cutoff (pixel)", connectivityCutoff);
		builder.addParameter("Microscope", microscope);
		builder.addParameter("Pixel size (um)", pixelSize);
		builder.addParameter("DNA length (bps)", lengthBps);
		builder.addParameter("Metadata UID source", metadataUIDSource);
	}

	// Getters and Setters
	public TransverseFlowArchive getArchive() {
		return archive;
	}

	public void setDataset(Dataset dataset) {
		this.dataset = dataset;
	}

	public Dataset getDataset() {
		return dataset;
	}

	public void setImagePlus(ImagePlus image) {
		this.image = image;
	}

	public ImagePlus getImagePlus() {
		return image;
	}

	public void setMicroscope(String microscope) {
		this.microscope = microscope;
	}

	public String getMicroscope() {
		return microscope;
	}

	public void setSmoothLabels(boolean smooth) {
		this.smooth = smooth;
	}

	public boolean getSmoothLabels() {
		return smooth;
	}

	public void setMetadataUIDSource(String metadataUIDSource) {
		this.metadataUIDSource = metadataUIDSource;
	}

	public String getMetadataUIDSource() {
		return this.metadataUIDSource;
	}
}
