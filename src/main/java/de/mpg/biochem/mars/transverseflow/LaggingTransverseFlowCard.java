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
import de.mpg.biochem.mars.fx.bdv.MarsBdvCard;
import de.mpg.biochem.mars.fx.bdv.MarsBdvFrame;
import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.molecule.*;
import de.mpg.biochem.mars.object.MartianObject;
import de.mpg.biochem.mars.transverseflow.commands.MarsTransverseFlowIntegrationBdvCommand;
import net.imagej.ops.Initializable;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.type.numeric.ARGBType;
import org.scijava.Context;
import org.scijava.module.ModuleService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Plugin(type = MarsBdvCard.class, name = "Lagging")
public class LaggingTransverseFlowCard extends AbstractJsonConvertibleRecord implements
	MarsBdvCard, SciJavaPlugin, Initializable
{

	private JTextField lineThickness;

	private JPanel panel;

	private LaggingTransverseFlowOverlay laggingTransverseFlowOverlay;
	private Molecule molecule;

	private boolean active = false;

	@Parameter
	protected MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties<Molecule, MarsMetadata>, MoleculeArchiveIndex<Molecule, MarsMetadata>> archive;
	
	@Parameter
	protected MarsBdvFrame marsBdvFrame;

	@Parameter
	protected Context context;

	@Parameter
	protected ModuleService moduleService;

	@Override
	public void initialize() {
		panel = new JPanel();
		panel.setLayout(new GridLayout(0, 2));

		panel.add(new JLabel("Thickness"));

		lineThickness = new JTextField(6);
		lineThickness.setText("5");
		Dimension dimScaleField = new Dimension(100, 20);
		lineThickness.setMinimumSize(dimScaleField);

		panel.add(lineThickness);

		JButton transverseFlowIntegrationButton = new JButton("Integrate strands");
		transverseFlowIntegrationButton.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				ExecutorService backgroundThread = Executors.newSingleThreadExecutor();
				backgroundThread.submit(() -> {
					MarsTransverseFlowIntegrationBdvCommand transverseFlowIntegrationCommand = new MarsTransverseFlowIntegrationBdvCommand();
					transverseFlowIntegrationCommand.setContext(context);

					for (Window window : Window.getWindows())
						if (window instanceof JDialog && ((JDialog) window).getTitle()
								.equals(transverseFlowIntegrationCommand.getInfo().getLabel()) && ((JDialog) window).isVisible()) {
							((JDialog) window).toFront();
							((JDialog) window).repaint();
							return;
						}

					//We set these directly to avoid pre and post processors from running
					//we don't need that in this context
					transverseFlowIntegrationCommand.setMarsBdvFrame(marsBdvFrame);
					transverseFlowIntegrationCommand.setArchive(archive);
					transverseFlowIntegrationCommand.setMolecule((TransverseFlowMolecule) molecule);
					try {
						moduleService.run(transverseFlowIntegrationCommand, true).get();
					}
					catch (InterruptedException | ExecutionException exc) {
						exc.printStackTrace();
					}
				});
				backgroundThread.shutdown();
			}
		});
		panel.add(transverseFlowIntegrationButton);
	}

	@Override
	public void setMolecule(Molecule molecule) {
		this.molecule = molecule;
	}

	@Override
	public void setArchive(
		MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties<Molecule, MarsMetadata>, MoleculeArchiveIndex<Molecule, MarsMetadata>> archive)
	{
		this.archive = archive;
	}
	
	@Override
	public void setBdvFrame(MarsBdvFrame marsBdvFrame) {
		this.marsBdvFrame = marsBdvFrame;
	}

	@Override
	public String getName() {
		return "Lagging";
	}

	@Override
	public BdvOverlay getBdvOverlay() {
		if (laggingTransverseFlowOverlay == null) laggingTransverseFlowOverlay = new LaggingTransverseFlowOverlay();

		return laggingTransverseFlowOverlay;
	}

	@Override
	public boolean isActive() {
		return active;
	}

	@Override
	public void setActive(boolean active) {
		this.active = active;
	}

	public class LaggingTransverseFlowOverlay extends BdvOverlay {

		public LaggingTransverseFlowOverlay() {}

		@Override
		protected void draw(Graphics2D g) {
			if (((TransverseFlowMolecule) molecule).hasShape(info
				.getTimePointIndex()))
			{
				AffineTransform2D transform = new AffineTransform2D();
				getCurrentTransform2D(transform);

				g.setColor(getColor());
				g.setStroke(new BasicStroke(Integer.valueOf(lineThickness
					.getText())));

				ReplicationForkShape shape = ((TransverseFlowMolecule) molecule).getShape(info
					.getTimePointIndex());

				boolean sourceInitialized = false;
				int xSource = 0;
				int ySource = 0;
				for (int pIndex = 0; pIndex < shape.laggingX.length; pIndex++) {
					double x = shape.laggingX[pIndex];
					double y = shape.laggingY[pIndex];

					if (Double.isNaN(x) || Double.isNaN(y)) continue;

					final double[] globalCoords = new double[] { x, y };
					final double[] viewerCoords = new double[2];
					transform.apply(globalCoords, viewerCoords);

					int xTarget = (int) Math.round(viewerCoords[0]);
					int yTarget = (int) Math.round(viewerCoords[1]);

					if (sourceInitialized) g.drawLine(xSource, ySource, xTarget, yTarget);

					xSource = xTarget;
					ySource = yTarget;
					sourceInitialized = true;
				}
			}
		}

		private Color getColor() {
			int alpha = (int) info.getDisplayRangeMax();

			if (alpha > 255 || alpha < 0) alpha = 255;

			final int r = ARGBType.red(info.getColor().get());
			final int g = ARGBType.green(info.getColor().get());
			final int b = ARGBType.blue(info.getColor().get());
			return new Color(r, g, b, alpha);
		}
	}

	@Override
	public JPanel getPanel() {
		return panel;
	}

	@Override
	protected void createIOMaps() {
		setJsonField("thickness", jGenerator -> {
			if (lineThickness != null) jGenerator.writeStringField("thickness",
				lineThickness.getText());
		}, jParser -> lineThickness.setText(jParser.getText()));
	}
}
