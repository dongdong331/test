package android.media;

import android.mtp.MtpConstants;

/**
 * @hide
 */
public class MediaFileExUtils {

    // More audio file types
    public static final int FILE_TYPE_OPUS = 400;//SPRD:Add for opus
    public static final int FILE_TYPE_AUDIO_BP3 = 401; //SPRD: add for drm
    public static final int FILE_TYPE_AUDIO_MP4 = 402; //SPRD: add for mp4
    public static final int FILE_TYPE_AUDIO_M4R = 403; //SPRD: add for m4r
    public static final int FILE_TYPE_AUDIO_M4B = 404; //SPRD: add for m4b
    public static final int FILE_TYPE_AUDIO_3GP = 405; //SPRD: add for 3gp
    public static final int FILE_TYPE_AUDIO_3G2 = 406; //SPRD: add for 3g2
    public static final int FILE_TYPE_AUDIO_OGA = 407; //SPRD: add for oga
    public static final int FILE_TYPE_AUDIO_MP2 = 408; //UNISOC : add for mp2
    public static final int FILE_TYPE_AUDIO_DM = 409; //UNISOC: add for dm
    protected static final int SPRD_FIRST_AUDIO_FILE_TYPE2 = FILE_TYPE_OPUS;
    protected static final int SPRD_LAST_AUDIO_FILE_TYPE2 = FILE_TYPE_AUDIO_DM;

    // More MIDI file types
    public static final int FILE_TYPE_MMID = 14;
    public static final int FILE_TYPE_XMID = 15;
    protected static final int SPRD_FIRST_MIDI_FILE_TYPE = FILE_TYPE_MMID;
    protected static final int SPRD_LAST_MIDI_FILE_TYPE = FILE_TYPE_XMID;

    // More video file types
    public static final int FILE_TYPE_FLV = 202;//SPRD:Add for flv
    public static final int FILE_TYPE_F4V = 203; // SPRD: add for f4v
    public static final int FILE_TYPE_DIVX = 204; // SPRD: add for divx
    public static final int FILE_TYPE_K3G = 205; //SPRD: add for drm
    public static final int FILE_TYPE_AMC = 206; //SPRD: add for drm
    public static final int FILE_TYPE_VOB = 207;
    protected static final int SPRD_FIRST_VIDEO_FILE_TYPE2 = FILE_TYPE_FLV;
    protected static final int SPRD_LAST_VIDEO_FILE_TYPE2 = FILE_TYPE_VOB;

    // More other popular file types
    public static final int FILE_TYPE_APK = 108;

    protected static void addFileType() {
        MediaFile.addFileType("MP3", MediaFile.FILE_TYPE_MP3, "audio/x-mp3");
        MediaFile.addFileType("MP3", MediaFile.FILE_TYPE_MP3, "audio/mpeg3");
        MediaFile.addFileType("MP3", MediaFile.FILE_TYPE_MP3, "audio/mp3");//SPRD:add for bug 519475
        MediaFile.addFileType("MP3", MediaFile.FILE_TYPE_MP3, "audio/mpg3");//SPRD:add for bug 519475
        //MediaFile.addFileType("MP4", FILE_TYPE_AUDIO_MP4, "audio/mp4");//SPRD:Add for mp4
        MediaFile.addFileType("M4R", FILE_TYPE_AUDIO_M4R, "audio/mp4");//SPRD:Add for m4r
        MediaFile.addFileType("M4B", FILE_TYPE_AUDIO_M4B, "audio/mp4");//SPRD:Add for m4b
        MediaFile.addFileType("3GP", FILE_TYPE_AUDIO_3GP, "audio/3gpp");//SPRD:Add for 3gp
        MediaFile.addFileType("3G2", FILE_TYPE_AUDIO_3G2, "audio/3gpp2");//SPRD:Add for 3g2
        MediaFile.addFileType("OPUS", FILE_TYPE_OPUS, "audio/opus");//SPRD:Add for opus
        MediaFile.addFileType("OGA", FILE_TYPE_AUDIO_OGA, "audio/ogg");//SPRD:Add for oga
        MediaFile.addFileType("MP2", FILE_TYPE_AUDIO_MP2, "audio/mpeg");//SPRD:Add for mp2
        MediaFile.addFileType("DM", FILE_TYPE_AUDIO_DM, "audio/mpeg4");//SPRD: Add for dm


        MediaFile.addFileType("MMID", FILE_TYPE_MMID, "audio/mid");
        MediaFile.addFileType("XMID", FILE_TYPE_XMID, "audio/x-midi");
        MediaFile.addFileType("IMY", MediaFile.FILE_TYPE_IMY, "audio/imy"); // SPRD:add for drm
        MediaFile.addFileType("BP3", FILE_TYPE_AUDIO_BP3, "audio/bp3"); //SPRD: add for drm
        MediaFile.addFileType("3G2", MediaFile.FILE_TYPE_3GPP2, "video/3g2"); // SPRD: add for drm

        MediaFile.addFileType("FLV", FILE_TYPE_FLV, "video/flv");//SPRD:Add for flv
        MediaFile.addFileType("F4V", FILE_TYPE_F4V, "video/mp4"); // SPRD: add for f4v
        MediaFile.addFileType("DIVX", FILE_TYPE_DIVX, "video/avi"); // SPRD: add for divx
        MediaFile.addFileType("AMC", FILE_TYPE_AMC, "video/amc"); // SPRD: add for drm
        MediaFile.addFileType("K3G", FILE_TYPE_K3G, "video/k3g"); // SPRD: add for drm
        MediaFile.addFileType("VOB", FILE_TYPE_VOB, "video/mp2p");


        MediaFile.addFileType("JPE", MediaFile.FILE_TYPE_JPEG, "image/jpeg", MtpConstants.FORMAT_EXIF_JPEG, false);
        MediaFile.addFileType("DRMBMP", MediaFile.FILE_TYPE_BMP, "image/bmp");// SPRD: add for another bmp


        MediaFile.addFileType("WAV", MediaFile.FILE_TYPE_WAV, "audio/wav", MtpConstants.FORMAT_WAV, false);
        MediaFile.addFileType("AAC", MediaFile.FILE_TYPE_AAC, "audio/x-aac", MtpConstants.FORMAT_AAC, false);
        MediaFile.addFileType("AVI", MediaFile.FILE_TYPE_AVI, "video/x-msvideo");

        MediaFile.addFileType("APK", FILE_TYPE_APK, "application/application");
        MediaFile.addFileType("APK", FILE_TYPE_APK, "application/vnd.android.package-archive");
    }
}
