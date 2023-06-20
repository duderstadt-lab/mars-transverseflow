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

import de.mpg.biochem.mars.image.MarsImageUtils;
import de.mpg.biochem.mars.image.Peak;
import de.mpg.biochem.mars.image.PeakShape;
import de.mpg.biochem.mars.image.PeakTracker;
import de.mpg.biochem.mars.metadata.MarsOMEMetadata;
import de.mpg.biochem.mars.metadata.MarsOMEUtils;
import de.mpg.biochem.mars.molecule.MoleculeArchiveService;
import de.mpg.biochem.mars.object.ObjectArchive;
import de.mpg.biochem.mars.table.MarsTableService;
import de.mpg.biochem.mars.transverseflow.TransverseFlowArchive;
import de.mpg.biochem.mars.util.LogBuilder;
import de.mpg.biochem.mars.util.MarsMath;
import de.mpg.biochem.mars.util.MarsUtil;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
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
import net.imagej.axis.Axes;
import net.imagej.display.ImageDisplay;
import net.imagej.ops.OpService;
import net.imglib2.Interval;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.algorithm.labeling.ConnectedComponents.StructuringElement;
import net.imglib2.algorithm.neighborhood.HyperSphereShape;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.outofbounds.OutOfBoundsMirrorFactory;
import net.imglib2.outofbounds.OutOfBoundsMirrorFactory.Boundary;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.Scale;
import net.imglib2.roi.geom.real.Polygon2D;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.roi.labeling.LabelRegion;
import net.imglib2.roi.labeling.LabelRegions;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import ome.units.quantity.Length;
import ome.xml.meta.OMEXMLMetadata;
import ome.xml.model.enums.EnumerationException;
import ome.xml.model.enums.UnitsLength;
import ome.xml.model.enums.handlers.UnitsLengthEnumHandler;
import ome.xml.model.primitives.PositiveInteger;
import org.decimal4j.util.DoubleRounder;
import org.scijava.Initializable;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.command.Previewable;
import org.scijava.convert.ConvertService;
import org.scijava.event.EventService;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt.MessageType;
import org.scijava.ui.DialogPrompt.OptionType;
import org.scijava.ui.UIService;
import org.scijava.widget.ChoiceWidget;
import org.scijava.widget.NumberWidget;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.io.File;

@Plugin(type = Command.class, label = "Transverse Flow Archive Builder", menu = { @Menu(
	label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
	mnemonic = MenuConstants.PLUGINS_MNEMONIC), @Menu(label = "Mars",
		weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = 'm'), @Menu(
			label = "Molecule", weight = 1, mnemonic = 'm'), @Menu(
				label = "Transverse Flow Archive Builder", weight = 4, mnemonic = 't') })
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

	@Parameter(label = "Microscope", style = "group:Output", required = false)
 	private String microscope = "unknown";

	@Parameter(label = "Metadata UID",
		style = ChoiceWidget.RADIO_BUTTON_VERTICAL_STYLE + ", group:Output",
		choices = { "unique from dataset", "random" })
	private String metadataUIDSource = "random";

	@Parameter(label = "Transverse Flow Archive", type = ItemIO.OUTPUT)
	private TransverseFlowArchive archive;

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

		//TODO Here we need to use the input directory with the label files to build the TranverseFlowMolecule records
		//and add them to the archive.

		// Make sure the output archive has the correct name
		getInfo().getMutableOutput("archive", TransverseFlowArchive.class).setLabel(archive
			.getName());

		logService.info(LogBuilder.endBlock(true));
		log += "\n" + LogBuilder.endBlock(true);
		archive.logln(log);
		archive.logln("   ");
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
		builder.addParameter("Microscope", microscope);
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

	public void setMetadataUIDSource(String metadataUIDSource) {
		this.metadataUIDSource = metadataUIDSource;
	}

	public String getMetadataUIDSource() {
		return this.metadataUIDSource;
	}
}
