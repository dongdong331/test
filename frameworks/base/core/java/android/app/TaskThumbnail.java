package android.app;


import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.UriPermission;
import android.content.pm.ApplicationInfo;
import android.content.pm.ConfigurationInfo;
import android.content.pm.IPackageDataObserver;
import android.content.pm.PackageManager;
import android.content.pm.ParceledListSlice;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.DisplayMetrics;
import android.util.Singleton;
import android.util.Size;

import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import org.xmlpull.v1.XmlSerializer;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
/** @hide */
 public class TaskThumbnail implements Parcelable {

    public Bitmap mainThumbnail;
    public ParcelFileDescriptor thumbnailFileDescriptor;
    public TaskThumbnailInfo thumbnailInfo;
    
    public TaskThumbnail() {
    }
    
    private TaskThumbnail(Parcel source) {
        readFromParcel(source);
    }
    
    public int describeContents() {
        if (thumbnailFileDescriptor != null) {
            return thumbnailFileDescriptor.describeContents();
        }
        return 0;
    }
    
    public void writeToParcel(Parcel dest, int flags) {
        if (mainThumbnail != null) {
            dest.writeInt(1);
            mainThumbnail.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
        if (thumbnailFileDescriptor != null) {
            dest.writeInt(1);
            thumbnailFileDescriptor.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
        if (thumbnailInfo != null) {
            dest.writeInt(1);
            thumbnailInfo.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
    }
    
    public void readFromParcel(Parcel source) {
        if (source.readInt() != 0) {
            mainThumbnail = Bitmap.CREATOR.createFromParcel(source);
        } else {
            mainThumbnail = null;
        }
        if (source.readInt() != 0) {
            thumbnailFileDescriptor = ParcelFileDescriptor.CREATOR.createFromParcel(source);
        } else {
            thumbnailFileDescriptor = null;
        }
        if (source.readInt() != 0) {
            thumbnailInfo = TaskThumbnailInfo.CREATOR.createFromParcel(source);
        } else {
            thumbnailInfo = null;
        }
    }
    
    public static final Creator<TaskThumbnail> CREATOR = new Creator<TaskThumbnail>() {
        public TaskThumbnail createFromParcel(Parcel source) {
            return new TaskThumbnail(source);
        }
        public TaskThumbnail[] newArray(int size) {
            return new TaskThumbnail[size];
        }
    };
    public static class TaskThumbnailInfo implements Parcelable {
        /** @hide */
        public static final String ATTR_TASK_THUMBNAILINFO_PREFIX = "task_thumbnailinfo_";
        private static final String ATTR_TASK_WIDTH =
                ATTR_TASK_THUMBNAILINFO_PREFIX + "task_width";
        private static final String ATTR_TASK_HEIGHT =
                ATTR_TASK_THUMBNAILINFO_PREFIX + "task_height";
        private static final String ATTR_SCREEN_ORIENTATION =
                ATTR_TASK_THUMBNAILINFO_PREFIX + "screen_orientation";

        public int taskWidth;
        public int taskHeight;
        public int screenOrientation = Configuration.ORIENTATION_UNDEFINED;

        public TaskThumbnailInfo() {
            // Do nothing
        }

        private TaskThumbnailInfo(Parcel source) {
            readFromParcel(source);
        }

        /**
         * Resets this info state to the initial state.
         * @hide
         */
        public void reset() {
            taskWidth = 0;
            taskHeight = 0;
            screenOrientation = Configuration.ORIENTATION_UNDEFINED;
        }

        /**
         * Copies from another ThumbnailInfo.
         */
        public void copyFrom(TaskThumbnailInfo o) {
            taskWidth = o.taskWidth;
            taskHeight = o.taskHeight;
            screenOrientation = o.screenOrientation;
        }

        /** @hide */
        public void saveToXml(XmlSerializer out) throws IOException {
            out.attribute(null, ATTR_TASK_WIDTH, Integer.toString(taskWidth));
            out.attribute(null, ATTR_TASK_HEIGHT, Integer.toString(taskHeight));
            out.attribute(null, ATTR_SCREEN_ORIENTATION, Integer.toString(screenOrientation));
        }

        /** @hide */
        public void restoreFromXml(String attrName, String attrValue) {
            if (ATTR_TASK_WIDTH.equals(attrName)) {
                taskWidth = Integer.parseInt(attrValue);
            } else if (ATTR_TASK_HEIGHT.equals(attrName)) {
                taskHeight = Integer.parseInt(attrValue);
            } else if (ATTR_SCREEN_ORIENTATION.equals(attrName)) {
                screenOrientation = Integer.parseInt(attrValue);
            }
        }

        public int describeContents() {
            return 0;
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(taskWidth);
            dest.writeInt(taskHeight);
            dest.writeInt(screenOrientation);
        }

        public void readFromParcel(Parcel source) {
            taskWidth = source.readInt();
            taskHeight = source.readInt();
            screenOrientation = source.readInt();
        }

        public static final Creator<TaskThumbnailInfo> CREATOR = new Creator<TaskThumbnailInfo>() {
            public TaskThumbnailInfo createFromParcel(Parcel source) {
                return new TaskThumbnailInfo(source);
            }
            public TaskThumbnailInfo[] newArray(int size) {
                return new TaskThumbnailInfo[size];
            }
        };
    }
}