/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.hardware.face;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Container for face metadata.
 * @hide
 */
public final class Face implements Parcelable {
    private CharSequence mName;
    private int mGroupId;
    private int mFaceId;
    private long mDeviceId; // physical device this is associated with

    public Face(CharSequence name, int groupId, int faceId, long deviceId) {
        mName = name;
        mGroupId = groupId;
        mFaceId = faceId;
        mDeviceId = deviceId;
    }

    private Face(Parcel in) {
        mName = in.readString();
        mGroupId = in.readInt();
        mFaceId = in.readInt();
        mDeviceId = in.readLong();
    }

    /**
     * Gets the human-readable name for the given face.
     * @return name given to face
     */
    public CharSequence getName() { return mName; }

    /**
     * Gets the device-specific face id.  Used by Settings to map a name to a specific
     * face template.
     * @return device-specific id for this face
     * @hide
     */
    public int getFaceId() { return mFaceId; }

    /**
     * Gets the group id specified when the face was enrolled.
     * @return group id for the set of faces this one belongs to.
     * @hide
     */
    public int getGroupId() { return mGroupId; }

    /**
     * Device this face belongs to.
     * @hide
     */
    public long getDeviceId() { return mDeviceId; }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mName.toString());
        out.writeInt(mGroupId);
        out.writeInt(mFaceId);
        out.writeLong(mDeviceId);
    }

    public static final Parcelable.Creator<Face> CREATOR
            = new Parcelable.Creator<Face>() {
        public Face createFromParcel(Parcel in) {
            return new Face(in);
        }

        public Face[] newArray(int size) {
            return new Face[size];
        }
    };
};