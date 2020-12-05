/* *
 * Copyright (C) 2018 The spreadtrum.com
 */

package com.android.server.display;


public abstract class SunLightManager {

    public static final int SUNLIGHT_TABLE_ID_NONE = 0;
    public static final int SUNLIGHT_TABLE_ID_NORMAL = 1<<0; // is 1
    public static final int SUNLIGHT_TABLE_ID_LOWPOWER = 1<<1; //is 2
    public static final int SUNLIGHT_TABLE_ID_UI = 1<<2; //is 4
    public static final int SUNLIGHT_TABLE_ID_GAME = 1<<3; //is 8
    public static final int SUNLIGHT_TABLE_ID_VIDEO = 1<<4;
    public static final int SUNLIGHT_TABLE_ID_IMAGE= 1<<5;
    public static final int SUNLIGHT_TABLE_ID_CAMERA= 1<<6;
    public static final int SUNLIGHT_TABLE_ID_VIDEO_FULL = (1<<4 | 1<<7);

    public abstract void setLight(int light);
    public abstract void setTable(int index);
}
