/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

package com.act.lcms.plotter;

import com.act.utils.TSVWriter;
import com.act.lcms.Gnuplotter;
import com.act.lcms.MS1;
import com.act.lcms.XZ;
import com.act.lcms.db.analysis.PathwayProductAnalysis;
import com.act.lcms.db.analysis.ScanData;
import com.act.lcms.db.model.LCMSWell;
import com.act.lcms.db.model.MS1ScanForWellAndMassCharge;
import org.apache.commons.lang3.tuple.Pair;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WriteAndPlotMS1Results {

  public void plotTIC(List<XZ> tic, String outPrefix, String fmt) throws IOException {
    String outImg = outPrefix + "." + fmt;
    String outData = outPrefix + ".data";

    // Write data output to outfile
    try (PrintStream out = new PrintStream(new FileOutputStream(outData))) {
      // print each time point + intensity to outDATA
      for (XZ xz : tic) {
        out.format("%.4f\t%.4f\n", xz.getTime(), xz.getIntensity());
        out.flush();
      }
    }

    // render outDATA to outPDF using gnuplot
    new Gnuplotter().plot2D(outData, outImg, new String[] { "TIC" }, "time", null, "intensity",
        fmt);
  }

  public void plotScan(List<MS1.YZ> scan, String outPrefix, String fmt) throws IOException {
    String outPDF = outPrefix + "." + fmt;
    String outDATA = outPrefix + ".data";

    // Write data output to outfile
    PrintStream out = new PrintStream(new FileOutputStream(outDATA));

    // print out the spectra to outDATA
    for (MS1.YZ yz : scan) {
      out.format("%.4f\t%.4f\n", yz.getMZ(), yz.getIntensity());
      out.flush();
    }

    // close the .data
    out.close();

    // render outDATA to outPDF using gnuplot
    new Gnuplotter().plot2DImpulsesWithLabels(outDATA, outPDF, new String[] { "mz distribution at TIC max" },
        null, "mz", null, "intensity", fmt);
  }

  private List<String> writeFeedMS1Values(List<Pair<Double, List<XZ>>> ms1s, Double maxIntensity,
                                         OutputStream os) throws IOException {
    // Write data output to outfile
    PrintStream out = new PrintStream(os);

    List<String> plotID = new ArrayList<>(ms1s.size());
    for (Pair<Double, List<XZ>> ms1ForFeed : ms1s) {
      Double feedingConcentration = ms1ForFeed.getLeft();
      List<XZ> ms1 = ms1ForFeed.getRight();

      plotID.add(String.format("concentration: %5e", feedingConcentration));
      // print out the spectra to outDATA
      for (XZ xz : ms1) {
        out.format("%.4f\t%.4f\n", xz.getTime(), xz.getIntensity());
        out.flush();
      }
      // delimit this dataset from the rest
      out.print("\n\n");
    }

    return plotID;
  }

  private void writeFeedMS1Values(List<Pair<Double, Double>> concentrationIntensity, OutputStream os)
      throws IOException {
    PrintStream out = new PrintStream(os);
    for (Pair<Double, Double> ci : concentrationIntensity) {
      out.format("%f\t%f\n", ci.getLeft(), ci.getRight());
    }
    out.flush();
  }

  // input: list sorted on first field of pair of (concentration, ms1 spectra)
  //        the ion of relevance to compare across different spectra
  //        outPrefix for pdfs and data, and fmt (pdf or png) of output
  public void plotFeedings(List<Pair<Double, MS1ScanForWellAndMassCharge>> feedings, String ion, String outPrefix,
                                  String fmt, String gnuplotFile)
      throws IOException {
    String outSpectraImg = outPrefix + "." + fmt;
    String outSpectraData = outPrefix + ".data";
    String outFeedingImg = outPrefix + ".fed." + fmt;
    String outFeedingData = outPrefix + ".fed.data";
    String feedingGnuplotFile = gnuplotFile + ".fed";

    boolean useMaxPeak = true;

    // maps that hold the values for across different concentrations
    List<Pair<Double, List<XZ>>> concSpectra = new ArrayList<>();
    List<Pair<Double, Double>> concAreaUnderSpectra = new ArrayList<>();
    List<Pair<Double, Double>> concMaxPeak = new ArrayList<>();

    // we will compute a running max of the intensity in the plot, and integral
    Double maxIntensity = 0.0d, maxAreaUnder = 0.0d;

    // now compute the maps { conc -> spectra } and { conc -> area under spectra }
    for (Pair<Double, MS1ScanForWellAndMassCharge> feedExpr : feedings) {
      Double concentration = feedExpr.getLeft();
      MS1ScanForWellAndMassCharge scan = feedExpr.getRight();

      // get the ms1 spectra for the selected ion, and the max for it as well
      List<XZ> ms1 = scan.getIonsToSpectra().get(ion);
      Double maxInThisSpectra = scan.getMaxIntensityForIon(ion);
      Double areaUnderSpectra = scan.getIntegralForIon(ion);

      // update the max intensity over all different spectra
      maxIntensity = Math.max(maxIntensity, maxInThisSpectra);
      maxAreaUnder = Math.max(maxAreaUnder, areaUnderSpectra);

      // install this concentration and spectra in map, to be dumped to file later
      concSpectra.add(Pair.of(concentration, ms1));
      concAreaUnderSpectra.add(Pair.of(concentration, areaUnderSpectra));
      concMaxPeak.add(Pair.of(concentration, maxInThisSpectra));
    }

    // Write data output to outfiles
    List<String> plotID = null;
    try (FileOutputStream outSpectra = new FileOutputStream(outSpectraData)) {
      plotID = writeFeedMS1Values(concSpectra, maxIntensity, outSpectra);
    }

    try (FileOutputStream outFeeding = new FileOutputStream(outFeedingData)) {
      writeFeedMS1Values(useMaxPeak ? concMaxPeak : concAreaUnderSpectra, outFeeding);
    }

    // render outDATA to outPDF using gnuplot
    Gnuplotter gp = new Gnuplotter();
    String[] plotNames = plotID.toArray(new String[plotID.size()]);
    gp.plotOverlayed2D(outSpectraData, outSpectraImg, plotNames, "time", maxIntensity, "intensity", fmt, gnuplotFile);
    gp.plot2D(outFeedingData, outFeedingImg, new String[] { "feeding ramp" }, "concentration",
        useMaxPeak ? maxIntensity : maxAreaUnder, "integrated area under spectra", fmt, null, null, null,
        feedingGnuplotFile);
  }

  private List<Pair<String, String>> writeMS1Values(Map<String, List<XZ>> ms1s, Double maxIntensity,
                                                    Map<String, Double> metlinMzs, OutputStream os,
                                                    boolean heatmap) throws IOException {
    return writeMS1Values(ms1s, maxIntensity, metlinMzs, os, heatmap, true, null);
  }

  public List<Pair<String, String>> writeMS1Values(Map<String, List<XZ>> ms1s, Double maxIntensity,
                                                   Map<String, Double> metlinMzs, OutputStream os, boolean heatmap,
                                                   boolean applyThreshold, Set<String> ionsToWrite) throws IOException {
    // Write data output to outfile
    PrintStream out = new PrintStream(os);

    List<Pair<String, String>> plotID = new ArrayList<>(ms1s.size());
    for (Map.Entry<String, List<XZ>> ms1ForIon : ms1s.entrySet()) {
      String ion = ms1ForIon.getKey();
      // Skip ions not in the ionsToWrite set if that set is defined.
      if (ionsToWrite != null && !ionsToWrite.contains(ion)) {
        continue;
      }

      List<XZ> ms1 = ms1ForIon.getValue();
      String plotName = String.format("ion: %s, mz: %.5f", ion, metlinMzs.get(ion));
      plotID.add(Pair.of(ion, plotName));
      // print out the spectra to outDATA
      for (XZ xz : ms1) {
        if (heatmap) {
          /*
          * When we are building heatmaps, we use gnuplots pm3d package
          * along with `dgrid3d 2000,2` (which averages data into grids
          * that are 2000 on the time axis and 2 in the y axis), and
          * `view map` that flattens a 3D graphs into a 2D view.
          * We want time to be on the x-axis and intensity on the z-axis
          * (because that is the one that is mapped to heat colors)
          * but then we need an artificial y-axis. We create proxy y=1
          * and y=2 datapoints, and then dgrid3d averaging over 2 creates
          * a vertical "strip".
          */
          out.format("%.4f\t1\t%.4f\n", xz.getTime(), xz.getIntensity());
          out.format("%.4f\t2\t%.4f\n", xz.getTime(), xz.getIntensity());
        } else {
          out.format("%.4f\t%.4f\n", xz.getTime(), xz.getIntensity());
        }
        out.flush();
      }
      // delimit this dataset from the rest
      out.print("\n\n");
    }

    return plotID;
  }

  public void plotSpectra(Map<String, List<XZ>> ms1s, Double maxIntensity,
                           Map<String, Double> individualMaxIntensities, Map<String, Double> metlinMzs,
                           String outPrefix, String fmt, boolean makeHeatmap, boolean overlayPlots)
      throws IOException {

    String outImg = outPrefix + "." + fmt;
    String outData = outPrefix + ".data";

    // Write data output to outfile
    try (FileOutputStream out = new FileOutputStream(outData)) {
      List<Pair<String, String>> ionAndplotID = writeMS1Values(ms1s, maxIntensity, metlinMzs, out, makeHeatmap);

      // writeMS1Values picks an ordering of the plots.
      // create two new sets plotID and yMaxes that have the matching ordering
      // and contain plotNames, and yRanges respectively
      List<Double> yMaxesInSameOrderAsPlots = new ArrayList<>();
      List<String> plotID = new ArrayList<>();
      for (Pair<String, String> plot : ionAndplotID) {
        String ion = plot.getLeft();
        Double yMax = individualMaxIntensities.get(ion);
        yMaxesInSameOrderAsPlots.add(yMax);
        plotID.add(plot.getRight());
      }
      Double[] yMaxes = yMaxesInSameOrderAsPlots.toArray(new Double[yMaxesInSameOrderAsPlots.size()]);

      // render outDATA to outPDF using gnuplot
      Gnuplotter gp = new Gnuplotter();
      String[] plotNames = plotID.toArray(new String[plotID.size()]);

      if (makeHeatmap) {
        gp.plotHeatmap(outData, outImg, plotNames, maxIntensity, fmt);
      } else {
        if (!overlayPlots) {
          gp.plot2D(outData, outImg, plotNames, "time", maxIntensity, "intensity", fmt,
              null, null, yMaxes, outImg + ".gnuplot");
        } else {
          gp.plotOverlayed2D(outData, outImg, plotNames, "time", maxIntensity, "intensity", fmt, outImg + ".gnuplot");
        }
      }
    }
  }

  /**
   * This function writes the pathway product results to an analysis file
   * @param writer This is a handle to the tsv writer
   * @param chemicalName The name of the primary chemical of inspection
   * @param positiveAndNegativeWells This is a list of positive and negative control well samples
   * @param pathwayStepIon This is the metlin ion which usually is the best metlin ion based on standard ion analysis
   * @param wellsToBestPeaks This is a map of well to the best peak positions in the spectral charts
   * @throws IOException
   */
  public static void writePathwayProductOutput(TSVWriter<String, String> writer, String chemicalName,
                                               List<ScanData<LCMSWell>> positiveAndNegativeWells,
                                               String pathwayStepIon,
                                               Map<ScanData<LCMSWell>, XZ> wellsToBestPeaks) throws IOException {

    for (ScanData<LCMSWell> well : positiveAndNegativeWells) {

      String fedChemical = well.getWell().getChemical() == null ||
          well.getWell().getChemical().isEmpty() ? "nothing" : well.getWell().getChemical();

      String pelletOrSupernatant = well.getPlate().getDescription().contains("pellet") ? "pellet" : "supernatant";
      String detected;
      String intensity;
      String time;
      if (wellsToBestPeaks.get(well) != null) {
        detected = "YES";
        intensity = String.format("%.4f", wellsToBestPeaks.get(well).getIntensity());
        time = String.format("%.4f", wellsToBestPeaks.get(well).getTime());
      } else {
        detected = "NO";
        intensity = "-";
        time = "-";
      }

      Map<String, String> row = new HashMap<>();

      row.put(PathwayProductAnalysis.PATHWAY_PRODUCT_HEADER_FIELDS.TARGET_CHEMICAL.name(), chemicalName);
      row.put(PathwayProductAnalysis.PATHWAY_PRODUCT_HEADER_FIELDS.TYPE.name(), pelletOrSupernatant);
      row.put(PathwayProductAnalysis.PATHWAY_PRODUCT_HEADER_FIELDS.FED_CHEMICAL.name(), fedChemical);
      row.put(PathwayProductAnalysis.PATHWAY_PRODUCT_HEADER_FIELDS.DETECTED.name(), detected);
      row.put(PathwayProductAnalysis.PATHWAY_PRODUCT_HEADER_FIELDS.INTENSITY.name(), intensity);
      row.put(PathwayProductAnalysis.PATHWAY_PRODUCT_HEADER_FIELDS.TIME.name(), time);
      row.put(PathwayProductAnalysis.PATHWAY_PRODUCT_HEADER_FIELDS.PLATE_BARCODE.name(), well.getPlate().getBarcode());
      row.put(PathwayProductAnalysis.PATHWAY_PRODUCT_HEADER_FIELDS.MODE.name(), well.getScanFile().getMode().toString().toLowerCase());
      row.put(PathwayProductAnalysis.PATHWAY_PRODUCT_HEADER_FIELDS.WELL_COORDINATES.name(), well.getWell().getCoordinatesString());
      row.put(PathwayProductAnalysis.PATHWAY_PRODUCT_HEADER_FIELDS.MSID.name(), well.getWell().getMsid());
      row.put(PathwayProductAnalysis.PATHWAY_PRODUCT_HEADER_FIELDS.CONSTRUCT_ID.name(), well.getWell().getComposition());
      row.put(PathwayProductAnalysis.PATHWAY_PRODUCT_HEADER_FIELDS.METLIN_ION.name(), pathwayStepIon);

      writer.append(row);
      writer.flush();
    }
  }
}
