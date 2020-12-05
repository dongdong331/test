#!/bin/bash

export ANDROID_BUILD_TOP=`cd ../../../; pwd`
#export ANDROID_EABI_TOOLCHAIN=$ANDROID_BUILD_TOP/prebuilt/linux-x86/toolchain/arm-eabi-4.2.1/bin/
export ANDORID_ICU_LIB_PATH=$ANDROID_BUILD_TOP/external/icu/icu4c/source/icuBuild/lib
#export LD_LIBRARY_PATH=$ANDORID_ICU_LIB_PATH:$LD_LIBRARY_PATH
#export ANDROID_EABI_TOOLCHAIN=$ANDROID_BUILD_TOP/prebuilt/linux-x86/toolchain/arm-eabi-4.4.3/bin/

ICU4C_DIR=`pwd`
BUILD_DIR=icuBuild

mkdir $BUILD_DIR
cd $BUILD_DIR

../runConfigureICU Linux
make clean
make INCLUDE_UNI_CORE_DATA=1 all

cp data/out/tmp/icudt60l.dat ../stubdata/icudt60l.dat

cd $ICU4C_DIR

rm -rf icuBuild/
