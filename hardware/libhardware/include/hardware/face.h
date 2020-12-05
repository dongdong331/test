/*
 * Copyright (C) 2017 Spreadtrum Project
 *
 *
 *
 */

#ifndef ANDROID_INCLUDE_HARDWARE_FACE_H
#define ANDROID_INCLUDE_HARDWARE_FACE_H

#include <hardware/hw_auth_token.h>

#define FACE_MODULE_API_VERSION_1_0 HARDWARE_MODULE_API_VERSION(1, 0)
#define FACE_HARDWARE_MODULE_ID "face"

typedef enum face_msg_type {
    FACE_ERROR = -1,
    FACE_HELP = 1,
    FACE_TEMPLATE_ENROLLED = 2,
    FACE_TEMPLATE_REMOVED = 3,
    FACE_AUTHENTICATED = 4, // authenticated done
    FACE_ENROLL_PROCESSED = 5, // one frame data for enrolling processed done
    FACE_AUTHENTICATE_PROCESSED = 6, // one frame data for authenticating processed done
} face_msg_type_t;

/*
 * Face errors are meant to tell the framework to terminate the current operation and ask
 * for the user to correct the situation. These will almost always result in messaging and user
 * interaction to correct the problem.
 */
typedef enum face_error {
    FACE_ERROR_HW_UNAVAILABLE = 1, /* The hardware has an error that can't be resolved. */
    FACE_ERROR_UNABLE_TO_PROCESS = 2, /* Bad data; operation can't continue */
    FACE_ERROR_TIMEOUT = 3, /* The operation has timed out waiting for user input. */
    FACE_ERROR_NO_SPACE = 4, /* No space available to store a template */
    FACE_ERROR_CANCELED = 5, /* The current operation can't proceed. See above. */
    FACE_ERROR_UNABLE_TO_REMOVE = 6, /* face with given id can't be removed */
    FACE_ERROR_VENDOR_BASE = 1000, /* vendor-specific error messages start here */
    FACE_ERROR_AUTH_LIVENESSFAIL,
    FACE_ERROR_AUTH_NOFACE,
    FACE_ERROR_AUTH_FAIL
} face_error_t;

/*
 * Face help info is meant as feedback for the current operation.
 */
typedef enum face_help {
	FACE_HELP_MOVE_UNKOWN = 0,
	FACE_HELP_MOVE_NOFACE,
	FACE_HELP_MULTI_FACE,
	FACE_HELP_NOLIVENESS,
	FACE_HELP_EYE_CLOSED,
	FACE_HELP_OCCLUSION,
	FACE_HELP_BLUR,
	FACE_HELP_OUTOFIMAGE,
	FACE_HELP_MOVINGFAST,
	FACE_HELP_AE_NOT_CONVERGED,
	FACE_HELP_OFFSETPOS,
	FACE_HELP_FAR,
	FACE_HELP_NEAR,
	FACE_HELP_NEED_UPANGLE,
	FACE_HELP_NEED_DOWNANGLE,
	FACE_HELP_NEED_RIGHTANGLE,
	FACE_HELP_NEED_LEFTANGLE,
	FACE_HELP_NEED_DIFFPOSE,
	FACE_HELP_ABNORMAL,
	FACE_HELP_SAVE_ERROR,
	FACE_HELP_MAX,
} face_help_t;

typedef struct face_feature_id {
    uint32_t gid;
    uint32_t fid;
} face_feature_id_t;

typedef struct face_enroll {
    face_feature_id_t face;
} face_enroll_t;

typedef struct face_removed {
    face_feature_id_t face;
} face_removed_t;

typedef struct face_authenticated {
    face_feature_id_t face;
    hw_auth_token_t hat;
} face_authenticated_t;

typedef struct face_enroll_processed {
    int64_t addr;        // the address of the processed frame buffer
    uint32_t progress;   // progress: 0 - 100
} face_enroll_processed_t;

typedef struct face_authenticate_processed {
    int64_t main;        // the address of the processed main frame buffer
    int64_t sub;        // the address of the processed sub frame buffer
} face_authenticate_processed_t;

typedef struct face_msg {
    face_msg_type_t type;
    union {
        face_error_t error;
        face_help_t help;
        face_enroll_t enroll;
        face_removed_t removed;
        face_authenticated_t authenticated;
        face_enroll_processed_t enroll_processed;
        face_authenticate_processed_t authenticate_processed;
    } data;
} face_msg_t;

/* Callback function type */
typedef void (*face_notify_t)(const face_msg_t *msg);

/* Synchronous operation */
typedef struct face_device {
    /**
     * Common methods of the face device. This *must* be the first member
     * of face_device as users of this structure will cast a hw_device_t
     * to face_device pointer in contexts where it's known
     * the hw_device_t references a face_device.
     */
    struct hw_device_t common;

    /**
     * the handle of face id algorithm
     */
    void* priv_handle;

    /*
     * Client provided callback function to receive notifications.
     * Do not set by hand, use the function below instead.
     */
    face_notify_t notify;

    /*
     * Set notification callback:
     * Registers a user function that would receive notifications from the HAL
     * The call will block if the HAL state machine is in busy state until HAL
     * leaves the busy state.
     *
     * Function return: 0 if callback function is successfuly registered
     *                  or a negative number in case of error, generally from the errno.h set.
     */
    int (*set_notify)(struct face_device *dev, face_notify_t notify);

    /*
     * Face pre-enroll enroll request:
     * Generates a unique token to upper layers to indicate the start of an enrollment transaction.
     * This token will be wrapped by security for verification and passed to enroll() for
     * verification before enrollment will be allowed. This is to ensure adding a new face
     * template was preceded by some kind of credential confirmation (e.g. device password).
     *
     * Function return: 0 if function failed
     *                  otherwise, a uint64_t of token
     */
    uint64_t (*pre_enroll)(struct face_device *dev);

    /*
     * Face enroll request:
     * Switches the HAL state machine to collect and store a new face
     * template. Switches back as soon as enroll is complete
     * or after timeout_sec seconds.
     * The face template will be assigned to the group gid. User has a choice
     * to supply the gid or set it to 0 in which case a unique group id will be generated.
     *
     * Function return: 0 if enrollment process can be successfully started
     *                  or a negative number in case of error, generally from the errno.h set.
     *                  A notify() function may be called indicating the error condition.
     */
    int (*enroll)(struct face_device *dev, const hw_auth_token_t *hat,
                  uint32_t gid, uint32_t timeout_sec, int32_t width, int32_t height);

    /*
     * send face data to enroll algorithm to process
     *
     * Function return: 0 : success (enrollment is successfully finished)
     *                  1 : enrollment is failed (and finished)
     *                  2 : request a new frame
     *                  or a negative number in case of error, generally from the errno.h set.
     */
    int (*do_enroll_process)(struct face_device *dev, int64_t addr, const int32_t *info, int32_t count, const int8_t *byteInfo, int32_t byteCount);

    /*
     * Finishes the enroll operation and invalidates the pre_enroll() generated challenge.
     * This will be called at the end the face enrollment session
     *
     * Function return: 0 if the request is accepted
     *                  or a negative number in case of error, generally from the errno.h set.
     */
    int (*post_enroll)(struct face_device *dev);

    /*
     * get_authenticator_id:
     * Returns a token associated with the current face set. This value will
     * change whenever a new face is enrolled, thus creating a new face
     * set.
     *
     * Function return: current authenticator id or 0 if function failed.
     */
    uint64_t (*get_authenticator_id)(struct face_device *dev);

    /*
     * Cancel pending enroll or authenticate, sending FACE_ERROR_CANCELED
     * to all running clients. Switches the HAL state machine back to the idle state.
     * Unlike enroll_done() doesn't invalidate the pre_enroll() challenge.
     *
     * Function return: 0 if cancel request is accepted
     *                  or a negative number in case of error, generally from the errno.h set.
     */
    int (*cancel)(struct face_device *dev);

    /*
     * Face remove request:
     * Deletes a face template.
     * Works only within a path set by set_active_group().
     * notify() will be called with details on the template deleted.
     *
     * Function return: 0 if face template(s) can be successfully deleted
     *                  or a negative number in case of error, generally from the errno.h set.
     */
    int (*remove)(struct face_device *dev, uint32_t gid, uint32_t fid);

    /*
     * Restricts the HAL operation to a set of face belonging to a
     * group provided.
     * The caller must provide a path to a storage location within the user's
     * data directory.
     *
     * Function return: 0 on success
     *                  or a negative number in case of error, generally from the errno.h set.
     */
    int (*set_active_group)(struct face_device *dev, uint32_t gid,
                            const char *store_path);

    /*
     * Face authenticate request:
     * Authenticates an operation identifed by operation_id
     *
     * Function return: 0 on success (the authenticate request is accepted)
     *                  or a negative number in case of error, generally from the errno.h set.
     */
    int (*authenticate)(struct face_device *dev, uint64_t operation_id, uint32_t gid, int32_t width, int32_t height);

    /*
     * send face data to authenticate algorithm to process
     *
     * Function return: 0 -> successfully authenticated
     *                  1 -> authentication failed
     *                  2 -> request a new frame
     *                  or a negative number in case of error, generally from the errno.h set.
     */
    int (*do_authenticate_process)(struct face_device *dev, int64_t main, int64_t sub, int64_t otp, const int32_t *info, int32_t count, const int8_t *byteInfo, int32_t byteCount);

    /* Reserved for backward binary compatibility */
    void *reserved[4];
} face_device_t;

typedef struct face_module {
    /**
     * Common methods of the face module. This *must* be the first member
     * of face_module as users of this structure will cast a hw_module_t
     * to face_module pointer in contexts where it's known
     * the hw_module_t references a face_module.
     */
    struct hw_module_t common;
} face_module_t;

#endif  /* ANDROID_INCLUDE_HARDWARE_FACE_H */
