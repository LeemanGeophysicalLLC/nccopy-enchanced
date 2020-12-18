# nccopy-enchanced

An "enhanced" version of netCDF-Java's `Nccopy` command line utility.
For simple gridded datasets, this enhanced version of `Nccopy` will create 2D latitude and longitude variables for projected grids.

## Requirements

You will need:

1. a Java Development Kit (JDK), version 8 or greater.
   * Need a JDK?
     Try [Adoptium](https://adoptopenjdk.net/releases.html) (a.k.a. AdoptOpenJDK).

2. One of the following:
   * the `JAVA_HOME` environment variable set to the top-level directory of the JDK installation.

     \- or -

   * the `java` executable on your system path

## Building the jar

From the top-level of the repository, simply run:

~~~bash
./gradlew clean assemble
~~~

The jar file will be located in `${TOP_LEVEL_REPO_DIR}/build/libs/`, and is called `nccopyEnhanced-<VERSION>.jar`.

## Usage

~~~bash
java -jar nccopyEnhanced-0.0.1-SNAPSHOT.jar --help
Usage: edu.ucar.experimental.NccopyEnhanced [options]
  Options:
    -f, --format
      Output file format (DEPRECATED use --outformat). Allowed values =
      [netcdf3, netcdf4, netcdf4_classic, netcdf3c, netcdf3c64, ncstream]
    -outf, --outformat
      Output file format. Allowed values = [netcdf3, netcdf4, netcdf4_classic,
      netcdf3_64bit_offset,  ncstream] (See NetcdfFileFormat enum values)
      Default: NETCDF3
      Possible Values: [INVALID, NETCDF3, NETCDF3_64BIT_OFFSET, NETCDF4, NETCDF4_CLASSIC, NETCDF3_64BIT_DATA, NCSTREAM]
  * -i, --input
      Input dataset.
  * -o, --output
      Output file.
    -isLargeFile, --isLargeFile
      Write to large file offset format. Only used in NetCDF 3.
      Default: false
    -st, --strategy
      Chunking strategy. Only used in NetCDF 4. Allowed values = [standard,
      grib, none]
      Default: standard
      Possible Values: [standard, grib, none]
    -d, --deflateLevel
      Compression level. Only used in NetCDF 4. Allowed values = 0 (no
      compression, fast) to 9 (max compression, slow)
      Default: 5
    -sh, --shuffle
      Enable the shuffle filter, which may improve compression. Only used in
      NetCDF 4. This option is ignored unless a non-zero deflate level is
      specified.
~~~
