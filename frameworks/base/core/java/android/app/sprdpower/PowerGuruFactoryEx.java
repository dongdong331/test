/*
 * Copyright 2015 The Spreadtrum.com
 */

package android.app.sprdpower;

import android.content.Context;

/**
 * @hide
 */
public class PowerGuruFactoryEx extends PowerGuruFactory {

    public PowerGuruFactoryEx(){

    }

    public AbsPowerGuru createExtraPowerGuru(IPowerGuru service, Context context) {
        return new PowerGuru(service, context);
    }

}
