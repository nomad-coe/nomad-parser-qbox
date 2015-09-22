#!/bin/bash
# Gets and installs hdf5 (with java components) and netcdf (and JNA)
# all stuff gets installed in linux-native/lib, and is copyed over to sbt lib
# directory (../lib)
#
# developed on ununtu 14.04, but should be useful as starting point also for
# other environments

myDir="$(dirname $0)"
if [[ "${myDir:0:1}" != "/" ]]; then
    if [[ "$myDir" == "." ]]; then
        # cosmetically more pleasing :)
        baseDir="$(pwd)"
    else
        baseDir="$(pwd)/$myDir"
    fi
else
    baseDir="$myDir"
fi

cd "$baseDir"

if ! [[ -e "hdf-java-2.11.0.tar.gz" || -e "hdf-java-2.11.0.tar" ]] ; then
    wget http://www.hdfgroup.org/ftp/HDF5/hdf-java/current/src/hdf-java-2.11.0.tar.gz
fi
if [[ -e "hdf-java-2.11.0.tar.gz" && ! -e "hdf-java-2.11.0.tar" ]] ; then
    gunzip hdf-java-2.11.0.tar.gz
fi
if ! [[ -e SZip.tar.gz ]] ; then
    wget http://www.hdfgroup.org/ftp/HDF5/hdf-java/current/cmake/SZip.tar.gz
fi
if ! [[ -e ZLib.tar.gz ]] ; then
    wget http://www.hdfgroup.org/ftp/HDF5/hdf-java/current/cmake/ZLib.tar.gz
fi
if ! [[ -e JPEG8b.tar.gz ]] ; then
    wget http://www.hdfgroup.org/ftp/HDF5/hdf-java/current/cmake/JPEG8b.tar.gz
fi
if ! [[ -e HDF5_orig.tar.gz ]] ; then
    wget http://www.hdfgroup.org/ftp/HDF5/hdf-java/current/cmake/HDF5.tar.gz -O HDF5_orig.tar.gz
fi
if ! [[ -e HDF5.tar.gz ]] ; then
    # this is pretty stupid, but somehow adding a build option in HDFJAVALinuxCMake.cmake
    # or -DHDF5_BUILD_HL_LIB:BOOL=ON do not seem to work, so we patch CMakeLists.txt
    # in the tar, and re-tar it
    tar xzf HDF5_orig.tar.gz
    patch -p1 < hlBuild.patch
    tar czf HDF5.tar.gz HDF5
    rm -rf HDF5
fi
if ! [[ -e HDF4.tar.gz ]] ; then
    wget http://www.hdfgroup.org/ftp/HDF5/hdf-java/current/cmake/HDF4.tar.gz
fi
if ! [[ -e HDFJAVALinuxCMake.cmake ]] ; then
    wget http://www.hdfgroup.org/ftp/HDF5/hdf-java/current/cmake/HDFJAVALinuxCMake.cmake
    patch -p1 < HDFJAVALinuxCMake.cmake.patch
fi
if ! [[ -e CTestScript.cmake ]] ; then
    wget http://www.hdfgroup.org/ftp/HDF5/hdf-java/current/cmake/CTestScript.cmake
fi

if ! which ctest >& /dev/null ; then
    echo "trying to install cmake, as ctest is missing"
    sudo apt-get install cmake
fi

if [[ -z "$JAVA_HOME" ]] ; then
    export JAVA_HOME="$(update-java-alternatives -l | head -1 | tr -s ' ' | cut -d ' ' -f 3)"
fi

ctest -S HDFJAVALinuxCMake.cmake -C Release -V -O hdf-java.log

cp -rp hdf-java/build/_CPack_Packages/Linux/STGZ/HDFView-2.11.0-Linux/HDF_Group/HDFView/2.11.0 linux-native

if ! [[ -e netcdf-4.3.3.1.tar.gz ]] ; then
    wget ftp://ftp.unidata.ucar.edu/pub/netcdf/netcdf-4.3.3.1.tar.gz
fi
if ! [[ -d netcdf-4.3.3.1 ]] ; then
    tar xzf netcdf-4.3.3.1.tar.gz
fi

# curl dev
if ! dpkg -s libcurl4-openssl-dev >& /dev/null; then
    echo "installing libcurl4-openssl-dev"
    sudo apt-get install libcurl4-openssl-dev
fi

NC_CPPFLAGS="-I$baseDir/hdf-java/native/hdf5lib" \
    " -I$baseDir/hdf-java/build/native/HDF4-prefix/src/HDF4/mfhdf/libsrc"\
    " -I$baseDir/hdf-java/build/native/HDF4-prefix/src/HDF4/hdf/util"\
    " -I$baseDir/hdf-java/build/native/HDF4-prefix/src/HDF4/hdf/src"\
    " -I$baseDir/hdf-java/build/native/HDF4-prefix/src/HDF4-build"\
    " -I$baseDir/hdf-java/build/native/HDF4-prefix/src/HDF4-build/JPEG-prefix/src/JPEG-build"\
    " -I$baseDir/hdf-java/build/native/HDF4-prefix/src/HDF4-build/JPEG-prefix/src/JPEG/src"\
    " -I$baseDir/hdf-java/build/native/HDF4-prefix/src/HDF4-build"\
    " -I$baseDir/hdf-java/build/native/HDF5-prefix/src/HDF5-build/ZLIB-prefix/src/ZLIB-build"\
    " -I$baseDir/hdf-java/build/native/HDF5-prefix/src/HDF5-build/ZLIB-prefix/src/ZLIB"\
    " -I$baseDir/hdf-java/build/native/HDF5-prefix/src/HDF5-build"\
    " -I$baseDir/hdf-java/build/native/HDF5-prefix/src/HDF5-build/SZIP-prefix/src/SZIP/src"\
    " -I$baseDir/hdf-java/build/native/HDF5-prefix/src/HDF5-build/SZIP-prefix/src/SZIP/windows"\
    " -I$baseDir/hdf-java/build/native/HDF5-prefix/src/HDF5-build/SZIP-prefix/src/SZIP-build"\
    " -I$baseDir/hdf-java/build/native/HDF5-prefix/src/HDF5/src"\
    " -I$baseDir/hdf-java/build/native/HDF5-prefix/src/HDF5/c++/src"\
    " -I$baseDir/hdf-java/build/native/HDF5-prefix/src/HDF5/hl/src"\
    " -I$baseDir/hdf-java/build/native/HDF5-prefix/src/HDF5/hl/c++/src"\
    " -I$baseDir/hdf-java/build/bin"

NC_LIBS="$baseDir/linux-native/lib/libhdf5_hl.a"\
    " $baseDir/linux-native/lib/libhdf5.a"\
    " $baseDir/linux-native/lib/libhdf.a"\
    " $baseDir/linux-native/lib/libjpeg.a"\
    " $baseDir/linux-native/lib/libmfhdf.a"\
    " $baseDir/linux-native/lib/libszip.a"\
    " $baseDir/linux-native/lib/libz.a"\
    " -ldl -lm"

if ! [[ -e netcdf-4.3.3.1Build ]] ; then
    mkdir netcdf-4.3.3.1Build
    cd netcdf-4.3.3.1Build
    CPPFLAGS="$NC_CPPFLAGS" LIBS="$NC_LIBS" ../netcdf-4.3.3.1/configure --prefix=$baseDir/linux-native --enable-jna
fi
cd netcdf-4.3.3.1Build
make
make check
make install
cd ..

# copy to sbt lib dirctory
# we exclude the onld netcdf .jar shipped with hdf-java and the old logging jars
mkdir -p ../lib
cp -p linux-native/lib/fits.jar ../lib/
cp -p linux-native/lib/jar*.jar ../lib/
cp -rp linux-native/lib/lib* linux-native/lib/pkgconfig ../lib/
