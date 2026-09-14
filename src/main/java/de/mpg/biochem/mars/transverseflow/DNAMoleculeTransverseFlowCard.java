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

import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.geom.Point2D;

import javax.swing.*;

import de.mpg.biochem.mars.fx.bdv.MarsBdvCard;
import de.mpg.biochem.mars.fx.bdv.MarsBdvFrame;
import de.mpg.biochem.mars.fx.dialogs.RoverConfirmationDialog;
import de.mpg.biochem.mars.fx.dialogs.RoverErrorDialog;
import de.mpg.biochem.mars.fx.molecule.AbstractMoleculeArchiveFxFrame;
import de.mpg.biochem.mars.image.DNASegment;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.LogBuilder;
import de.mpg.biochem.mars.util.MarsMath;
import de.mpg.biochem.mars.metadata.MarsOMEUtils;
import javafx.application.Platform;
import net.imagej.ops.Initializable;

import org.scijava.Context;
import org.scijava.log.LogService;
import org.scijava.module.ModuleService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

import bdv.util.BdvOverlay;
import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.molecule.AbstractJsonConvertibleRecord;
import de.mpg.biochem.mars.molecule.Molecule;
import de.mpg.biochem.mars.molecule.MoleculeArchive;
import de.mpg.biochem.mars.molecule.MoleculeArchiveIndex;
import de.mpg.biochem.mars.molecule.MoleculeArchiveProperties;
import de.mpg.biochem.mars.transverseflow.commands.MarsTransverseDNAPeakTrackerBdvCommand;

import java.util.*;
import java.awt.event.ItemEvent;
import java.awt.event.ActionListener;
import java.awt.Window;
import java.util.concurrent.*;

@Plugin(type = MarsBdvCard.class, name = "DNA-Overlay")
public class DNAMoleculeTransverseFlowCard extends AbstractJsonConvertibleRecord implements
        MarsBdvCard, SciJavaPlugin, Initializable
{

    private JTextField dnaThickness;
    private JTextField handleRadius;
    private JTextField dnaChannel;
    //private JTextField pixelSize;
    private JTextField dnaBpLength;
    private JTextField branchArchCutoff;
    private int dnaChannelID = 0;
    //private double pixel2um = 0.167;
    private double dnaLength = 28047.0;
    private double cutoffLength = 5;

    private JPanel panel;

    private ArchBranchIntegratedOverlay dnaMoleculeOverlay;
    private Molecule molecule;

    private boolean active = false;

    @Parameter
    protected MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties<Molecule, MarsMetadata>, MoleculeArchiveIndex<Molecule, MarsMetadata>> archive;

    @Parameter
    protected MarsBdvFrame marsBdvFrame;

    protected CurveEditor curveEditor;
    protected DynamicLineEditor dynamicLineEditor;

    @Parameter
    protected ModuleService moduleService;

    @Parameter
    protected LogService logService;

    @Parameter
    protected Context context;

    @Override
    public void initialize() {
        panel = new JPanel();
        panel.setLayout(new GridLayout(0, 2));

        panel.add(new JLabel("Thickness"));
        dnaThickness = new JTextField(6);
        dnaThickness.setText("5");
        dnaThickness.addActionListener(e -> {
            int thickness = Integer.valueOf(dnaThickness.getText());
            if (dnaMoleculeOverlay != null && thickness > 0 && thickness < 100) dnaMoleculeOverlay.setThickness(thickness);
        });
        Dimension dimScaleField = new Dimension(100, 20);
        dnaThickness.setMinimumSize(dimScaleField);
        panel.add(dnaThickness);

        panel.add(new JLabel("Radius"));
        handleRadius = new JTextField(6);
        handleRadius.setText("5");
        handleRadius.addActionListener(e -> {
            int radius = Integer.valueOf(handleRadius.getText());
            if (dnaMoleculeOverlay != null && radius > 0 && radius < 100) dnaMoleculeOverlay.setRadius(radius);
        });
        handleRadius.setMinimumSize(dimScaleField);
        panel.add(handleRadius);

        panel.add(new JLabel("Channel"));
        dnaChannel = new JTextField(6);
        dnaChannel.setText("0");
        dnaChannel.addActionListener(e -> {
            dnaChannelID = Integer.valueOf(dnaChannel.getText());
        });
        dnaChannel.setMinimumSize(dimScaleField);
        panel.add(dnaChannel);

        panel.add(new JLabel("Cutoff"));
        branchArchCutoff = new JTextField(6);
        branchArchCutoff.setText("5");
        branchArchCutoff.addActionListener(e -> {
            cutoffLength = Integer.valueOf(branchArchCutoff.getText());
        });
        branchArchCutoff.setMinimumSize(dimScaleField);
        panel.add(branchArchCutoff);

        //panel.add(new JLabel("Pixel Size"));
        //pixelSize = new JTextField(6);
        //pixelSize.setText("0.167");
        //pixelSize.addActionListener(e ->{
        //    pixel2um = Double.valueOf(pixelSize.getText());
        //});
        //pixelSize.setMinimumSize(dimScaleField);
        //panel.add(pixelSize);

        panel.add(new JLabel("DNA Length (bp)"));
        dnaBpLength = new JTextField(6);
        dnaBpLength.setText("28047");
        dnaBpLength.addActionListener(e ->{
            dnaLength = Double.valueOf(dnaBpLength.getText());
        });
        dnaBpLength.setMinimumSize(dimScaleField);
        panel.add(dnaBpLength);

        panel.add(new JLabel("Molecule Tracking Tool"));
        panel.add(new JLabel(""));

        JButton peakTrackerButton = new JButton("Add Track");
        peakTrackerButton.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                ExecutorService backgroundThread = Executors.newSingleThreadExecutor();
                backgroundThread.submit(() -> {
                    MarsTransverseDNAPeakTrackerBdvCommand peakTrackerCommand = new MarsTransverseDNAPeakTrackerBdvCommand();
                    peakTrackerCommand.setContext(context);

                    for (Window window : Window.getWindows())
                        if (window instanceof JDialog && ((JDialog) window).getTitle()
                                .equals(peakTrackerCommand.getInfo().getLabel()) && ((JDialog) window).isVisible()) {
                            ((JDialog) window).toFront();
                            ((JDialog) window).repaint();
                            return;
                        }

                    //We set these directly to avoid pre and post processors from running
                    //we don't need that in this context
                    peakTrackerCommand.setMarsBdvFrame(marsBdvFrame);
                    peakTrackerCommand.setArchive(archive);
                    try {
                        moduleService.run(peakTrackerCommand, true).get();
                    }
                    catch (InterruptedException | ExecutionException exc) {
                        exc.printStackTrace();
                    }
                });
                backgroundThread.shutdown();
            }
        });
        panel.add(peakTrackerButton);

        panel.add(new JLabel(""));

        panel.add(new JLabel("Drawing tools"));

        JToggleButton archDrawButton = new JToggleButton("Draw Arch");
        archDrawButton.addItemListener((ItemEvent ev) -> {
            if(ev.getStateChange() == ItemEvent.SELECTED) {
                if (curveEditor == null) curveEditor = new CurveEditor(marsBdvFrame);
                curveEditor.install();
            } else if(ev.getStateChange() == ItemEvent.DESELECTED) {
                final List<Point2D.Double> points = curveEditor.getAnchorPoints();
                final List<Point2D.Double> handles = curveEditor.getHandles();
                curveEditor.uninstall();
                if (!(points.isEmpty() || handles.isEmpty()))
                {
                    Platform.runLater(() -> {
                        RoverConfirmationDialog addDNAMoleculesToArchive =
                                new RoverConfirmationDialog(((AbstractMoleculeArchiveFxFrame<?, ?>) archive.getWindow()).getNode().getScene().getWindow(),
                                        "Create DnaMolecule records from drawn DNAs?", "Yes", "No");
                        addDNAMoleculesToArchive.showAndWait().ifPresent(result -> {
                            if (result.getButtonData().isDefaultButton())
                                ((AbstractMoleculeArchiveFxFrame<?, ?>) archive.getWindow())
                                        .runTask(() -> createDNAmoleculeRecords(points, handles),
                                                "Creating DnaMolecule records...");
                        });
                    });
                }
            }
        });
        panel.add(archDrawButton);

        JToggleButton branchDrawButton = new JToggleButton("Draw Branch");
        branchDrawButton.addItemListener((ItemEvent ev) -> {
            if(ev.getStateChange() == ItemEvent.SELECTED) {
                if (dynamicLineEditor == null) dynamicLineEditor = new DynamicLineEditor(marsBdvFrame);
                dynamicLineEditor.install();
            } else if(ev.getStateChange() == ItemEvent.DESELECTED) {
                Map<Integer, DNASegment> segments = dynamicLineEditor.getSegments();
                dynamicLineEditor.uninstall();
                if (!segments.isEmpty()) {
                    Platform.runLater(() -> {
                        RoverConfirmationDialog addDNAMoleculesToArchive =
                                new RoverConfirmationDialog(((AbstractMoleculeArchiveFxFrame<?, ?>) archive.getWindow()).getNode().getScene().getWindow(),
                                        "Append selected branches to MarsTable?", "Yes", "No");
                        addDNAMoleculesToArchive.showAndWait().ifPresent(result -> {
                            if (result.getButtonData().isDefaultButton()) {
                                try {
                                    appendBranchDNARecord(segments);
                                } catch (Exception e) {
                                    RoverErrorDialog alert = new RoverErrorDialog(((AbstractMoleculeArchiveFxFrame<?, ?>) archive.getWindow()).getNode().getScene().getWindow(),
                                            e.getMessage());
                                    alert.show();
                                }
                            }
                        });
                    });
                }
            }
        });
        panel.add(branchDrawButton);

        JButton removeArchDNA = new JButton("Remove Arch");
        removeArchDNA.addActionListener((ActionEvent e) -> {
            if (curveEditor != null) {
                curveEditor.getAnchorPoints().clear();
                curveEditor.getHandles().clear();
                marsBdvFrame.getBdvHandle().getViewerPanel().getDisplay().repaint();
            }
        });
        panel.add(removeArchDNA);

        JButton removeBranchDNA = new JButton("Remove current Branch");
        removeBranchDNA.addActionListener((ActionEvent e) -> {
            if (dynamicLineEditor != null) {
                Map<Integer, DNASegment> segments = dynamicLineEditor.getSegments();
                int currentTimePoint = marsBdvFrame.getBdvHandle().getViewerPanel().state().getCurrentTimepoint();
                if (!segments.isEmpty() && segments.containsKey(currentTimePoint)) {
                    segments.remove(currentTimePoint);
                    marsBdvFrame.getBdvHandle().getViewerPanel().getDisplay().repaint();
                }
            }
        });
        panel.add(removeBranchDNA);

        JButton clearBranchDNAs = new JButton("Clear all Branch");
        clearBranchDNAs.addActionListener((ActionEvent e) -> {
            if (dynamicLineEditor != null) {
                dynamicLineEditor.getSegments().clear();
                marsBdvFrame.getBdvHandle().getViewerPanel().getDisplay().repaint();
            }
        });
        panel.add(clearBranchDNAs);
    }

    @Override
    public JPanel getPanel() {
        return panel;
    }

    @Override
    protected void createIOMaps() {
        setJsonField("thickness", jGenerator -> {
            if (dnaThickness != null) jGenerator.writeStringField("thickness",
                    dnaThickness.getText());
        }, jParser -> dnaThickness.setText(jParser.getText()));
    }

    @Override
    public void setMolecule(Molecule molecule) {
        this.molecule = molecule;
        if (molecule != null && dnaMoleculeOverlay != null) {
            List<Point2D.Double> points  = new ArrayList<>();
            List<Point2D.Double> handles = new ArrayList<>();
            Map<Integer, DNASegment> segments = new HashMap<>();
            points.add(new Point2D.Double(molecule.getParameter("Dna_Top_X1"), molecule.getParameter("Dna_Top_Y1")));
            points.add(new Point2D.Double(molecule.getParameter("Dna_Bottom_X2"), molecule.getParameter("Dna_Bottom_Y2")));
            handles.add(new Point2D.Double(molecule.getParameter("Arch_Handle_Top_X1"), molecule.getParameter("Arch_Handle_Top_Y1")));
            handles.add(new Point2D.Double(molecule.getParameter("Arch_Handle_Bottom_X1"), molecule.getParameter("Arch_Handle_Bottom_Y1")));

            MarsTable table = molecule.getTable();
            if (table.hasColumn("Branch_T") && table.hasColumn("Branch_X1") && table.hasColumn("Branch_X2") && table.hasColumn("Branch_Y1") && table.hasColumn("Branch_Y2")) {
                double[] frames  = table.getColumnAsDoublesNoNaNs("Branch_T");
                double[] startXs = table.getColumnAsDoublesNoNaNs("Branch_X1");
                double[] startYs = table.getColumnAsDoublesNoNaNs("Branch_Y1");
                double[] endXs   = table.getColumnAsDoublesNoNaNs("Branch_X2");
                double[] endYs   = table.getColumnAsDoublesNoNaNs("Branch_Y2");
                int nrow = frames.length;
                for (int irow = 0; irow < nrow; ++irow) {
                    segments.put((int) frames[irow], new DNASegment(startXs[irow], startYs[irow], endXs[irow], endYs[irow]));
                }
            }

            dnaMoleculeOverlay.setCurveParameters(points, handles, segments);
        }
    }

    protected void createDNAmoleculeRecords(List<Point2D.Double> points, List<Point2D.Double> handles) {
        if (archive != null) {
            archive.getWindow().lock();
            MarsTable table = new MarsTable("table");
            Molecule dnaMolecule = archive.createMolecule(MarsMath.getUUID58(), table);
            dnaMolecule.setMetadataUID(marsBdvFrame.getMetadataUID());
            dnaMolecule.setImage(archive.getMetadata(marsBdvFrame.getMetadataUID()).getImage(0).getImageID());
            double archLength = BezierLength.length(points.get(0), handles.get(0), handles.get(1), points.get(1));
            dnaMolecule.setParameter("Dna_Top_X1", points.get(0).x);
            dnaMolecule.setParameter("Dna_Top_Y1", points.get(0).y);
            dnaMolecule.setParameter("Dna_Bottom_X2", points.get(1).x);
            dnaMolecule.setParameter("Dna_Bottom_Y2", points.get(1).y);
            dnaMolecule.setParameter("Arch_Handle_Top_X1", handles.get(0).x);
            dnaMolecule.setParameter("Arch_Handle_Top_Y1", handles.get(0).y);
            dnaMolecule.setParameter("Arch_Handle_Bottom_X1", handles.get(1).x);
            dnaMolecule.setParameter("Arch_Handle_Bottom_Y1", handles.get(1).y);
            dnaMolecule.setParameter("Arch_Length", archLength);
            dnaMolecule.setParameter("Arch_BpLength", dnaLength);
            dnaMolecule.addTag("Bdv Draw DNA");
            dnaMolecule.setNotes("DnaMolecule created on " + new java.util.Date() + " by the Bdv Draw DNA");
            //add to archive
            archive.put(dnaMolecule);
            //should add something to the archive log ... logService.info("Added DnaMolecule record " + dnaMolecule.getUID());
            String uid = dnaMolecule.getUID();

            LogBuilder builder = new LogBuilder();
            String log = LogBuilder.buildTitleBlock("Bdv Drawn DNA");

            builder.addParameter("Created DnaMolecules", uid);
            builder.addParameter("Metadata UID", marsBdvFrame.getMetadataUID());
            log += builder.buildParameterList();
            log += "\n" + LogBuilder.endBlock();
            archive.getMetadata(marsBdvFrame.getMetadataUID()).logln(log);

            archive.getWindow().unlock();
            Platform.runLater(() -> ((AbstractMoleculeArchiveFxFrame) archive.getWindow()).getMoleculesTab().setSelectedMolecule(uid));
            marsBdvFrame.setMolecule(archive.get(uid));
        }
    }

    protected void appendBranchDNARecord(Map<Integer, DNASegment> segments) {

        Molecule dnaMolecule = marsBdvFrame.getSelectedMolecule();
        // If no DNA molecule is selected (e.g. when you open an empty archive), it will open an error dialog.
        if (dnaMolecule == null) throw new NullPointerException("Error: No DNA molecule was selected. Please draw and select an arch DNA before drawing branches.");
        MarsTable dnaMoleculeTable = dnaMolecule.getTable();
        // If no branches are drawn, just ignore it.
        if (segments.isEmpty()) return;
        MarsTable branchMoleculeTable = findBranchDNAOnTheArch(dnaMolecule, segments);
        // If no branches are located around the selected arch DNA, just ignore it.
        if (branchMoleculeTable.getRowCount() < 1) {
            logService.info("No branch was found in the vicinity of the selected arch DNA molecule.");
            return;
        }

        if (dnaMoleculeTable.getColumnCount() > 0) {
            List<String> oldTrackColumnNames = new ArrayList<>();
            dnaMoleculeTable.stream().filter(col -> col.getHeader().startsWith("Branch_")).forEach(col -> oldTrackColumnNames.add(col.getHeader()));
            for (String header : oldTrackColumnNames)
                dnaMoleculeTable.removeColumn(header);
        }

        int nrow = Math.max(dnaMoleculeTable.getRowCount(), branchMoleculeTable.getRowCount());
        if (nrow < 1) return;
        else if (nrow > dnaMoleculeTable.getRowCount()) {
            for (int irow = dnaMoleculeTable.getRowCount(); irow < nrow; ++irow) {
                dnaMoleculeTable.appendRow();
                for (int icol = 0; icol < dnaMoleculeTable.getColumnCount(); ++icol) {
                    dnaMoleculeTable.set(icol, irow, Double.NaN);
                }
            }
        }
        for (int icol = 0; icol < branchMoleculeTable.getColumnCount(); ++icol) {
            dnaMoleculeTable.add(branchMoleculeTable.get(icol));
        }
        //molecule.addTag("Bdv Draw Branch");
        dnaMolecule.addTag("Bdv Draw Branch");
        archive.getWindow().unlock();
    }

    private MarsTable findBranchDNAOnTheArch(Molecule dnaMolecule, Map<Integer, DNASegment> segments) {

        MarsTable table = new MarsTable(12, 0);
        table.setColumnHeader( 0, "Branch_T");
        table.setColumnHeader( 1, "Branch_Time_(s)");
        table.setColumnHeader( 2, "Branch_X1");
        table.setColumnHeader( 3, "Branch_Y1");
        table.setColumnHeader( 4, "Branch_X2");
        table.setColumnHeader( 5, "Branch_Y2");
        table.setColumnHeader( 6, "Branch_R");
        table.setColumnHeader( 7, "Branch_Length");
        table.setColumnHeader( 8, "Branch_Root_X");
        table.setColumnHeader( 9, "Branch_Root_Y");
        table.setColumnHeader(10, "Branch_Arch_Distance");
        table.setColumnHeader(11, "Branch_Position_on_Arch");

        Map<Integer, Map<Integer, Double>> channelToTtoDtMap = MarsOMEUtils.buildChannelToTtoDtMap(archive.getMetadata(marsBdvFrame.getMetadataUID()));
        SortedSet<Integer> segmentKeys = new TreeSet<>(segments.keySet());
        double archLength = dnaMolecule.getParameter("Arch_Length");
        List<Point2D.Double> points = new ArrayList<>(){{
            add(new Point2D.Double(dnaMolecule.getParameter("Dna_Top_X1"), dnaMolecule.getParameter("Dna_Top_Y1")));
            add(new Point2D.Double(dnaMolecule.getParameter("Dna_Bottom_X2"), dnaMolecule.getParameter("Dna_Bottom_Y2")));
        }};
        List<Point2D.Double> handles = new ArrayList<>(){{
            add(new Point2D.Double(dnaMolecule.getParameter("Arch_Handle_Top_X1"), dnaMolecule.getParameter("Arch_Handle_Top_Y1")));
            add(new Point2D.Double(dnaMolecule.getParameter("Arch_Handle_Bottom_X1"), dnaMolecule.getParameter("Arch_Handle_Bottom_Y1")));
        }};
        int irow = 0;
        for (Integer segmentKey : segmentKeys) {
            DNASegment segment = segments.get(segmentKey);
            Point2D.Double startPoint = new Point2D.Double(segment.getX1(), segment.getY1());
            Point2D.Double endPoint   = new Point2D.Double(segment.getX2(), segment.getY2());
            double branchR = startPoint.distance(endPoint);
            double branchLength = (branchR * dnaLength) / archLength;
            BezierNearestBernstein.BNBResult bnbResult = BezierNearestBernstein.findNearestPoint(
                    startPoint,
                    points.get(0), points.get(1),
                    handles.get(0), handles.get(1)
            );
            double branchT = (double) segmentKey;
            double branchTime = channelToTtoDtMap.get(dnaChannelID).get(segmentKey);
            Point2D.Double branchRoot = bnbResult.point;
            double branchArchDistance = bnbResult.distance;
            double branchPosition = bnbResult.t * dnaLength;
            if (branchArchDistance > cutoffLength) continue;
            table.appendRow();
            table.setValue("Branch_T", irow, branchT);
            table.setValue("Branch_Time_(s)", irow, branchTime);
            table.setValue("Branch_X1", irow, segment.getX1());
            table.setValue("Branch_Y1", irow, segment.getY1());
            table.setValue("Branch_X2", irow, segment.getX2());
            table.setValue("Branch_Y2", irow, segment.getY2());
            table.setValue("Branch_R", irow, branchR);
            table.setValue("Branch_Length", irow, branchLength);
            table.setValue("Branch_Root_X", irow, branchRoot.x);
            table.setValue("Branch_Root_Y", irow, branchRoot.y);
            table.setValue("Branch_Arch_Distance", irow, branchArchDistance);
            table.setValue("Branch_Position_on_Arch", irow, branchPosition);
            ++irow;
        }
        return table;
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
        return "DNA-Overlay";
    }

    @Override
    public BdvOverlay getBdvOverlay() {
        if (dnaMoleculeOverlay == null) {
            dnaMoleculeOverlay = new ArchBranchIntegratedOverlay();
            dnaMoleculeOverlay.setThickness(Integer.valueOf(dnaThickness.getText()));
            if (this.molecule != null) {
                List<Point2D.Double> points  = new ArrayList<>();
                List<Point2D.Double> handles = new ArrayList<>();
                Map<Integer, DNASegment> segments = new HashMap<>();

                points.add(new Point2D.Double(molecule.getParameter("Dna_Top_X1"), molecule.getParameter("Dna_Top_Y1")));
                points.add(new Point2D.Double(molecule.getParameter("Dna_Bottom_X2"), molecule.getParameter("Dna_Bottom_Y2")));
                handles.add(new Point2D.Double(molecule.getParameter("Arch_Handle_Top_X1"), molecule.getParameter("Arch_Handle_Top_Y1")));
                handles.add(new Point2D.Double(molecule.getParameter("Arch_Handle_Bottom_X1"), molecule.getParameter("Arch_Handle_Bottom_Y1")));

                MarsTable table = molecule.getTable();
                if (table.hasColumn("Branch_T") && table.hasColumn("Branch_X1") && table.hasColumn("Branch_X2") && table.hasColumn("Branch_Y1") && table.hasColumn("Branch_Y2")) {
                    double[] frames  = table.getColumnAsDoublesNoNaNs("Branch_T");
                    double[] startXs = table.getColumnAsDoublesNoNaNs("Branch_X1");
                    double[] startYs = table.getColumnAsDoublesNoNaNs("Branch_Y1");
                    double[] endXs   = table.getColumnAsDoublesNoNaNs("Branch_X2");
                    double[] endYs   = table.getColumnAsDoublesNoNaNs("Branch_Y2");
                    int nrow = frames.length;
                    for (int irow = 0; irow < nrow; ++irow) {
                        segments.put((int) frames[irow], new DNASegment(startXs[irow], startYs[irow], endXs[irow], endYs[irow]));
                    }
                }

                dnaMoleculeOverlay.setCurveParameters(points, handles, segments);
            }
        }

        return dnaMoleculeOverlay;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void setActive(boolean active) {
        this.active = active;
    }
}
