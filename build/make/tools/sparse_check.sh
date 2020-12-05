#!/bin/bash

INSTALLED_SYSTEMIMAGE=$1
PRODUCT_OUT=$2
SIMG2IMG=$3

echo INSTALLED_SYSTEMIMAGE=${INSTALLED_SYSTEMIMAGE}
echo PRODUCT_OUT=${PRODUCT_OUT}
echo SIMG2IMG=${SIMG2IMG}

dd if=${INSTALLED_SYSTEMIMAGE} of=${PRODUCT_OUT}/system_header.img bs=1 skip=0 count=4
echo -e -n "\x3a\xff\x26\xed" > ${PRODUCT_OUT}/sparse_magic.img
cmp -s ${PRODUCT_OUT}/system_header.img ${PRODUCT_OUT}/sparse_magic.img
if [ $? -eq 0 ] ; then
   echo "transfer sparse system img to unsparse img"
   ${SIMG2IMG} ${INSTALLED_SYSTEMIMAGE} ${PRODUCT_OUT}/unsparse_system.img
   rm ${INSTALLED_SYSTEMIMAGE}
   mv ${PRODUCT_OUT}/unsparse_system.img ${INSTALLED_SYSTEMIMAGE}
fi
rm ${PRODUCT_OUT}/sparse_magic.img
rm ${PRODUCT_OUT}/system_header.img
