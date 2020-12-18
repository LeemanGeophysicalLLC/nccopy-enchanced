package edu.ucar.experimental;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterDescription;
import com.beust.jcommander.ParameterException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Formatter;
import java.util.List;
import java.util.Optional;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.dataset.NetcdfDatasets;
import ucar.nc2.ft2.coverage.CoverageCollection;
import ucar.nc2.ft2.coverage.CoverageDatasetFactory;
import ucar.nc2.ft2.coverage.FeatureDatasetCoverage;
import ucar.nc2.ft2.coverage.SubsetParams;
import ucar.nc2.ft2.coverage.adapter.DtCoverageAdapter;
import ucar.nc2.ft2.coverage.adapter.DtCoverageDataset;
import ucar.nc2.ft2.coverage.writer.CFGridCoverageWriter;
import ucar.nc2.util.CancelTask;
import ucar.nc2.util.DiskCache;
import ucar.nc2.write.Nc4Chunking;
import ucar.nc2.write.Nc4ChunkingStrategy;
import ucar.nc2.write.NetcdfFileFormat;
import thredds.server.ncss.params.NcssGridParamsBean;
import ucar.nc2.write.NetcdfFormatWriter;

public class NccopyEnhanced {

  /**
   * duplicated from thredds.core.DatasetManager
   */
  private static boolean isLocationObjectStore(String location) {
    return location != null && (location.startsWith("cdms3:") || location.startsWith("s3:"));
  }

  /**
   * duplicated from ucar.nc2.write.Nccopy
   */
  private static class CommandLine {

    @Parameter(names = {"-i", "--input"}, description = "Input dataset.", required = true)
    String inputFile;

    @Parameter(names = {"-o", "--output"}, description = "Output file.", required = true)
    File outputFile;

    @Parameter(names = {"-f", "--format"}, description = "Output file format (DEPRECATED use --outformat). "
        + "Allowed values = [netcdf3, netcdf4, netcdf4_classic, netcdf3c, netcdf3c64, ncstream]")
    String formatLegacy = null;

    @Parameter(names = {"-outf", "--outformat"}, description = "Output file format. Allowed values = "
        + "[netcdf3, netcdf4, netcdf4_classic, netcdf3_64bit_offset,  ncstream] (See NetcdfFileFormat enum values)")
    NetcdfFileFormat format = NetcdfFileFormat.NETCDF3;

    @Parameter(names = {"-st", "--strategy"},
        description = "Chunking strategy. Only used in NetCDF 4. Allowed values = [standard, grib, none]")
    Nc4Chunking.Strategy strategy = Nc4Chunking.Strategy.standard;

    @Parameter(names = {"-isLargeFile", "--isLargeFile"},
        description = "Write to large file offset format. Only used in NetCDF 3.")
    boolean isLargeFile;

    @Parameter(names = {"-useJna", "--useJna"}, description = "Use JNA/netCDF C library for writing.")
    boolean useJna;

    @Parameter(names = {"-d", "--deflateLevel"}, description = "Compression level. Only used in NetCDF 4. "
        + "Allowed values = 0 (no compression, fast) to 9 (max compression, slow)")
    int deflateLevel = 5;

    @Parameter(names = {"-sh", "--shuffle"}, description = "Enable the shuffle filter, which may improve compression. "
        + "Only used in NetCDF 4. This option is ignored unless a non-zero deflate level is specified.")
    boolean shuffle = true;

    @Parameter(names = "--diskCacheRoot",
        description = "Set the DiskCache root. "
            + "This parameter controls where temporary files will be stored, if necessary "
            + "(e.g. intermediate uncompressed NEXRAD files created when reading compressed files). "
            + "Must be a valid filesystem path. "
            + "Note: this directory is not automatically cleaned, so be sure to clean-up as needed.")
    File diskCacheRoot;

    // todo - add flag to autoclean diskCacheRoot

    @Parameter(names = {"-h", "--help"}, description = "Display this help and exit", help = true)
    boolean help;

    private static class ParameterDescriptionComparator implements Comparator<ParameterDescription> {

      // Display parameters in this order in the usage information.
      private final List<String> orderedParamNames = Arrays.asList("--input", "--output", "--ncformat", "--isLargeFile",
          "--strategy", "--deflateLevel", "--shuffle", "--diskCacheRoot", "--useJna", "--help");

      @Override
      public int compare(ParameterDescription p0, ParameterDescription p1) {
        int index0 = orderedParamNames.indexOf(p0.getLongestName());
        int index1 = orderedParamNames.indexOf(p1.getLongestName());
        assert index0 >= 0 : "Unexpected parameter name: " + p0.getLongestName();
        assert index1 >= 0 : "Unexpected parameter name: " + p1.getLongestName();

        return Integer.compare(index0, index1);
      }
    }

    private final JCommander jc;

    CommandLine(String progName, String[] args) throws ParameterException {
      this.jc = new JCommander(this); // Parses args and uses them to initialize *this*.
      this.jc.parse(args);
      jc.setProgramName(progName); // Displayed in the usage information.

      // Set the ordering of parameters in the usage information.
      jc.setParameterDescriptionComparator(new NccopyEnhanced.CommandLine.ParameterDescriptionComparator());
    }

    void printUsage() {
      jc.usage();
    }

    Nc4Chunking getNc4Chunking() {
      return Nc4ChunkingStrategy.factory(strategy, deflateLevel, shuffle);
    }
  }

  /**
   * duplicated from ucar.nc2.write.Nccopy
   */
  private static NetcdfFileFormat getFormat(NccopyEnhanced.CommandLine cmdLine) {
    NetcdfFileFormat result = cmdLine.format;
    if (cmdLine.formatLegacy != null) {
      switch (cmdLine.formatLegacy) {
        case "netcdf3":
          result = NetcdfFileFormat.NETCDF3;
          break;
        case "netcdf4":
          result = NetcdfFileFormat.NETCDF4;
          break;
        case "netcdf4_classic":
          result = NetcdfFileFormat.NETCDF4_CLASSIC;
          break;
        case "netcdf3c":
          result = NetcdfFileFormat.NETCDF3;
          cmdLine.useJna = true;
          break;
        case "netcdf3c64":
          result = NetcdfFileFormat.NETCDF3_64BIT_OFFSET;
          cmdLine.useJna = true;
          break;
        case "ncstream":
          result = NetcdfFileFormat.NCSTREAM;
          break;
      }
    }

    if (cmdLine.isLargeFile) {
      result = NetcdfFileFormat.NETCDF3_64BIT_OFFSET;
    }

    return result;
  }

  private static void makeCfNetcdf(CoverageCollection gcd, String outputFileName, NcssGridParamsBean params,
      NetcdfFileFormat version) throws InvalidRangeException, IOException {
    SubsetParams subset = params.makeSubset(gcd);

    // write the file
    // default chunking - let user control at some point
    NetcdfFormatWriter.Builder writerb = NetcdfFormatWriter.builder().setLocation(outputFileName).setFormat(version);
    CFGridCoverageWriter.Result result =
        CFGridCoverageWriter.write(gcd, params.getVar(), subset, params.isAddLatLon(), writerb, -1);
  }

  public static void main(String[] args) {
    String progName = NccopyEnhanced.class.getName();
    NccopyEnhanced.CommandLine cmdLine;
    try {
      cmdLine = new NccopyEnhanced.CommandLine(progName, args);
      if (cmdLine.help) {
        cmdLine.printUsage();
        return;
      }
    } catch (ParameterException e) {
      System.err.println(e.getMessage());
      System.err.printf("Try \"%s --help\" for more information.%n", progName);
      return;
    }

    String datasetIn = cmdLine.inputFile;
    String datasetOut = cmdLine.outputFile.getAbsolutePath();
    System.out.printf("NetcdfDatataset read from %s write %s to %s ", datasetIn, cmdLine.format, datasetOut);

    Optional<File> diskCacheDir = Optional.ofNullable(cmdLine.diskCacheRoot);
    if (diskCacheDir.isPresent()) {
      DiskCache.setRootDirectory(diskCacheDir.get().getAbsolutePath());
      // if user has set the diskCacheRootDir, then always use it over trying the "normal" locations first.
      // Was seeing an issue on cloud-mounted drives in which the I/O error generated by even trying to write data next
      // to the original file
      // caused the the JVM to close out (not the case on a local write protected directory).
      DiskCache.setCachePolicy(true);
    }

    CoverageCollection coverageCollection = null;
    try {
      ucar.nc2.util.Optional<FeatureDatasetCoverage> opt = CoverageDatasetFactory.openCoverageDataset(datasetIn);
      // hack - CoverageDatasetFactory bombs out on an object store location string during the grib check,
      // this is the code from CoverageDatasetFactory.openCoverageDataset that comes after the grib check.
      if (opt.isPresent()) {
        coverageCollection = opt.get().getSingleCoverageCollection();
      } else if (isLocationObjectStore(datasetIn)) {
        // hack 2 - DtCoverageDataset not ported, so need to open the NetcdfDataset object through NetcdfDatasets
        // and pass that to CoverageDataset
        DtCoverageDataset gds = new DtCoverageDataset(NetcdfDatasets.openDataset(datasetIn));
        if (!gds.getGrids().isEmpty()) {
          Formatter errlog = new Formatter();
          FeatureDatasetCoverage featureDatasetCoverage = DtCoverageAdapter.factory(gds, errlog);
          if (featureDatasetCoverage != null) {
            coverageCollection = opt.get().getSingleCoverageCollection();
          } else {
            opt = ucar.nc2.util.Optional.empty(errlog.toString());
          }
        }
      }

      if (!opt.isPresent() || coverageCollection == null) {
        throw new Exception("Not a Grid Dataset " + datasetIn + " err=" + opt.getErrorMessage());
      }

      List<String> coverageNames = new ArrayList<>();
      coverageCollection.getCoverages().forEach(coverage -> coverageNames.add(coverage.getFullName()));

      NcssGridParamsBean ncssGridParamsBean = new NcssGridParamsBean();
      ncssGridParamsBean.setAddLatLon(true);
      ncssGridParamsBean.setVar(coverageNames);
      ncssGridParamsBean.setTime("all");

      makeCfNetcdf(coverageCollection, datasetOut, ncssGridParamsBean, getFormat(cmdLine));
    } catch (Exception ex) {
      System.out.printf("%s = %s %n", ex.getClass().getName(), ex.getMessage());
    }
  }
}