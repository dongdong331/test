/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2015 All Rights Reserved.
 */
package org.ifaa.android.manager;

import android.content.Context;

public abstract class IFAAManagerV2 extends IFAAManager {
    /**
     * IFAAManager新接口，使用此接口需要将getVersion返回值写成2
     * 通过ifaateeclient的so文件实现REE到TA的通道
     * @param context
     * @param param 用于传输到IFAA TA的数据buffer
     * @return IFAA TA返回给REE数据buffer
     */
    public abstract byte[] processCmdV2(Context context, byte[] param);
}
