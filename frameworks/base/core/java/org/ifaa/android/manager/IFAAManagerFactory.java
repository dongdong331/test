/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2015 All Rights Reserved.
 */
package org.ifaa.android.manager;

import android.content.Context;

public class IFAAManagerFactory {

    public static final int AUTHTYPE_FINGERPRINT = 1;
    public static final int AUTHTYPE_IRIS = 2;

    public static IFAAManager getIFAAManager(Context context, int authType) {
        String className = "";

        switch (authType) {
            case AUTHTYPE_FINGERPRINT:
                return new IFAAFPManagerV2();

            // unsupported now.
            case AUTHTYPE_IRIS:
                break;

            default:
                break;
        }

        return null;
    }
}
