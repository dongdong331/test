/*
 ** Copyright 2018 The Spreadtrum.com
 */

package android.os.sprdpower;


/**
 * PowerController local system service interface.
 *
 * @hide Only for use within the system server.
 */
public abstract class PowerControllerInternal {
    /**
     * App action: none.
     */
    public static final int APP_ACTION_NONE = 0;

    /**
     * App action: playing music. 
     */
    public static final int APP_ACTION_PLAYING_MUSIC = 1;

    /**
     * App action: doing download.
     */
    public static final int APP_ACTION_DOWNLOADING = 2;

    /**
     * App action: location.
     */
    public static final int APP_ACTION_LOCATION = 3;


    /**
     * App category: unknow.
     */
    public static final int APP_CATEGORY_TYPE_UNKNOWN = 0;

    /**
     * App category: message app.
     */
    public static final int APP_CATEGORY_TYPE_MESSAGE = 1;

}
