/**************************************************************************
 ** File Name:  ifaa.h                                                    *
 ** Author:     liyun.wang                                                *
 ** Date:       2018-02-01                                                *
 ** Copyright:  2014 Spreadtrum Communications Inc. All rights reserved.  *
 ** Description: header file of ifaa for hidl                             *
 *************************************************************************/

#ifndef ANDROID_HARDWARE_IFAA_H
#define ANDROID_HARDWARE_IFAA_H

#include <sys/cdefs.h>
#include <sys/types.h>
#include <hardware/hardware.h>

__BEGIN_DECLS

#define IFAA_HARDWARE_MODULE_ID "ifaa"

#define IFAA_MODULE_API_VERSION_0_1 HARDWARE_MODULE_API_VERSION(0, 1)

#define HARDWARE_IFAA "ifaa"

struct ifaa_module {
    /**
     * Comon methods of the gatekeeper module. This *must* be the first member of
     * gatekeeper_module as users of this structure will cast a hw_module_t to
     * a gatekeeper_module pointer in the appropriate context.
     */
    hw_module_t common;
};

struct ifaa_device {
    /**
     * Common methods of the gatekeeper device. As above, this must be the first
     * member of keymaster_device.
     */
    hw_device_t common;

    /*
     * Process cmd.
     *
     * Parameters
     * - param: request buffer
     * - paramLen: length of request buffer
     * - out: response buffer
     * - outSize: size of response buffer
     *
     * Returns:
     * - 0 on success
     * - An error code < 0 on failure
     */
    int (*process_cmd_v2)(const struct ifaa_device *dev, const uint8_t *param, ssize_t paramLen, uint8_t *out, ssize_t *outSize);
};

typedef struct ifaa_device ifaa_device_t;

static inline int ifaa_open(const struct hw_module_t *module,
        ifaa_device_t **device) {
    return module->methods->open(module, HARDWARE_IFAA,
            (struct hw_device_t **) device);
}

static inline int ifaa_close(ifaa_device_t *device) {
    return device->common.close(&device->common);
}

__END_DECLS

#endif // ANDROID_HARDWARE_IFAA_H
