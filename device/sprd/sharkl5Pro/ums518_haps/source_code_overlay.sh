echo "=====haps source code overlay start====="
# souce files overlay
cp -vr device/sprd/sharkl5Pro/ums518_haps/source_code_overlay/* ./

# increase anr timeout for haps
sed -i 's/\(static final int SERVICE_TIMEOUT = .*1000\);/\1\*100;/;s/\(static final int SERVICE_BACKGROUND_TIMEOUT = SERVICE_TIMEOUT.*10\);/\1\*100;/;s/\(static final int SERVICE_START_FOREGROUND_TIMEOUT.*1000\);/\1\*100;/' frameworks/base/services/core/java/com/android/server/am/ActiveServices.java

# disable systemui & vibrator for haps
sed -i 's/[^\/]\(startSystemUi(context, windowManagerF);\)/\/\/\1/;s/[^\/]\(vibrator = new VibratorService(context);\)/\/\/\1/;s/[^\/]\(ServiceManager.addService("vibrator", vibrator);\)/\/\/\1/;s/[^\/]\(vibrator.systemReady();\)/\/\/\1/' frameworks/base/services/java/com/android/server/SystemServer.java

# increase watchdog interval for haps
sed -i 's/GOODBYE/HAPS/;s/\(static final long CHECK_INTERVAL = DEFAULT_TIMEOUT \/ 2\);/\1\*50;/;s/[^\/]\(Process.killProcess(Process.myPid());\)/\/\/\1/;s/[^\/]\(System.exit(10);\)/\/\/\1/' frameworks/base/services/core/java/com/android/server/Watchdog.java

# enforce LOCAL_DEX_PREOPT to true
sed -i 's/LOCAL_DEX_PREOPT := $(strip $(LOCAL_DEX_PREOPT))/LOCAL_DEX_PREOPT := true/' build/make/core/dex_preopt_odex_install.mk

# catch Exception in AlarmManagerService
file=frameworks/base/services/core/java/com/android/server/AlarmManagerService.java;linenum=`grep -nir "PendingIntent.CanceledException e" $file | sed '1 d;3 d;s/:.*//'`;if [ $linenum -lt 4000 -a $linenum -gt 3000 ];then sed -i "$linenum s/} catch (PendingIntent.CanceledException e) {/} catch (Exception e) {/" $file;fi

# disable kernel fs check
sed -i 's/CONFIG_F2FS_CHECK_FS=y/# CONFIG_F2FS_CHECK_FS is not set/' kernel4.14/arch/arm64/configs/sprd_all_defconfig kernel4.14/arch/arm64/configs/sprd_sharkl5Pro_defconfig

# disable logd exit from time changed
sed -i 's/[^\/]\(exit(0);\)/\/\/\1/' system/core/logd/LogListener.cpp

# disable gpsd
file=device/sprd/sharkl5Pro/common/rootdir/root/init.common.rc;start=`cat -n $file | grep "[^#]service gpsd" | cut -f 1`;end=$((start + 3));sed -i "$start,$end{s/^/#/}" "$file"

# remove packages for haps
sed -i 's/product_MODULES := $(PRODUCTS.$(INTERNAL_PRODUCT).PRODUCT_PACKAGES)/product_MODULES := $(filter-out $(PACKAGES_TO_REMOVE), $(PRODUCTS.$(INTERNAL_PRODUCT).PRODUCT_PACKAGES))/' build/core/main.mk

# increase coldboot wait timeout
sed -i 's/COLDBOOT_DONE, 60s/COLDBOOT_DONE, 600s/' system/core/init/init.cpp

# workaround for systemserver deadlock
cd frameworks/base;git apply frameworks_base.diff;rm frameworks_base.diff;cd -
echo "=====haps source code overlay end====="
