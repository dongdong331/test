
# storage init files, move storage prop to /system, depends on feature_configs/base/config.mk
ifeq ($(STORAGE_ORIGINAL), true)
    PRODUCT_COPY_FILES += \
        $(ROOTCOMM)/root/init.storage_original.rc:root/init.storage.rc
else
PRODUCT_COPY_FILES += \
    $(ROOTCOMM)/root/init.storage_sprd.rc:root/init.storage.rc
endif
