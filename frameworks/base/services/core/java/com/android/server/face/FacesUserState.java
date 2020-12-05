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
 * limitations under the License
 */

package com.android.server.face;

import android.content.Context;
import android.hardware.face.Face;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.AtomicFile;
import android.util.Slog;
import android.util.Xml;

import com.android.internal.annotations.GuardedBy;

import libcore.io.IoUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Class managing the set of face per user across device reboots.
 */
class FacesUserState {

    private static final String TAG = "FaceState";
    private static final String FACE_FILE = "settings_face.xml";

    private static final String TAG_FACES = "faces";
    private static final String TAG_FACE = "face";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_GROUP_ID = "groupId";
    private static final String ATTR_FACE_ID = "faceId";
    private static final String ATTR_DEVICE_ID = "deviceId";

    private final File mFile;

    @GuardedBy("this")
    private final ArrayList<Face> mFaces = new ArrayList<Face>();
    private final Context mCtx;

    public FacesUserState(Context ctx, int userId) {
        mFile = getFileForUser(userId);
        mCtx = ctx;
        synchronized (this) {
            readStateSyncLocked();
        }
    }

    public void addFace(int faceId, int groupId) {
        synchronized (this) {
            mFaces.add(new Face(getUniqueName(), groupId, faceId, 0));
            scheduleWriteStateLocked();
        }
    }

    public void removeFace(int faceId) {
        synchronized (this) {
            for (int i = 0; i < mFaces.size(); i++) {
                if (mFaces.get(i).getFaceId() == faceId) {
                    mFaces.remove(i);
                    scheduleWriteStateLocked();
                    break;
                }
            }
        }
    }

    public void renameFace(int faceId, CharSequence name) {
        synchronized (this) {
            for (int i = 0; i < mFaces.size(); i++) {
                if (mFaces.get(i).getFaceId() == faceId) {
                    Face old = mFaces.get(i);
                    mFaces.set(i, new Face(name, old.getGroupId(), old.getFaceId(),
                            old.getDeviceId()));
                    scheduleWriteStateLocked();
                    break;
                }
            }
        }
    }

    public List<Face> getFaces() {
        synchronized (this) {
            return getCopy(mFaces);
        }
    }

    /**
     * Finds a unique name for the given face
     * @return unique name
     */
    private String getUniqueName() {
        int guess = 1;
        while (true) {
            // Not the most efficient algorithm in the world, but there shouldn't be more than 10
            String name = mCtx.getString(com.android.internal.R.string.face_name_template,
                    guess);
            if (isUnique(name)) {
                return name;
            }
            guess++;
        }
    }

    private boolean isUnique(String name) {
        for (Face face : mFaces) {
            if (face.getName().equals(name)) {
                return false;
            }
        }
        return true;
    }

    private static File getFileForUser(int userId) {
        return new File(Environment.getUserSystemDirectory(userId), FACE_FILE);
    }

    private final Runnable mWriteStateRunnable = new Runnable() {
        @Override
        public void run() {
            doWriteState();
        }
    };

    private void scheduleWriteStateLocked() {
        AsyncTask.execute(mWriteStateRunnable);
    }

    private ArrayList<Face> getCopy(ArrayList<Face> array) {
        ArrayList<Face> result = new ArrayList<Face>(array.size());
        for (int i = 0; i < array.size(); i++) {
            Face face = array.get(i);
            result.add(new Face(face.getName(), face.getGroupId(), face.getFaceId(),
                    face.getDeviceId()));
        }
        return result;
    }

    private void doWriteState() {
        AtomicFile destination = new AtomicFile(mFile);

        ArrayList<Face> faces;

        synchronized (this) {
            faces = getCopy(mFaces);
        }

        FileOutputStream out = null;
        try {
            out = destination.startWrite();

            XmlSerializer serializer = Xml.newSerializer();
            serializer.setOutput(out, "utf-8");
            serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
            serializer.startDocument(null, true);
            serializer.startTag(null, TAG_FACES);

            final int count = faces.size();
            for (int i = 0; i < count; i++) {
                Face face = faces.get(i);
                serializer.startTag(null, TAG_FACE);
                serializer.attribute(null, ATTR_FACE_ID, Integer.toString(face.getFaceId()));
                serializer.attribute(null, ATTR_NAME, face.getName().toString());
                serializer.attribute(null, ATTR_GROUP_ID, Integer.toString(face.getGroupId()));
                serializer.attribute(null, ATTR_DEVICE_ID, Long.toString(face.getDeviceId()));
                serializer.endTag(null, TAG_FACE);
            }

            serializer.endTag(null, TAG_FACES);
            serializer.endDocument();
            destination.finishWrite(out);

            // Any error while writing is fatal.
        } catch (Throwable t) {
            Slog.wtf(TAG, "Failed to write settings, restoring backup", t);
            destination.failWrite(out);
            throw new IllegalStateException("Failed to write faces", t);
        } finally {
            IoUtils.closeQuietly(out);
        }
    }

    private void readStateSyncLocked() {
        FileInputStream in;
        if (!mFile.exists()) {
            return;
        }
        try {
            in = new FileInputStream(mFile);
        } catch (FileNotFoundException fnfe) {
            Slog.i(TAG, "No face state");
            return;
        }
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(in, null);
            parseStateLocked(parser);

        } catch (XmlPullParserException | IOException e) {
            throw new IllegalStateException("Failed parsing settings file: "
                    + mFile , e);
        } finally {
            IoUtils.closeQuietly(in);
        }
    }

    private void parseStateLocked(XmlPullParser parser)
            throws IOException, XmlPullParserException {
        final int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }

            String tagName = parser.getName();
            if (tagName.equals(TAG_FACES)) {
                parseFacesLocked(parser);
            }
        }
    }

    private void parseFacesLocked(XmlPullParser parser)
            throws IOException, XmlPullParserException {

        final int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }

            String tagName = parser.getName();
            if (tagName.equals(TAG_FACE)) {
                String name = parser.getAttributeValue(null, ATTR_NAME);
                String groupId = parser.getAttributeValue(null, ATTR_GROUP_ID);
                String faceId = parser.getAttributeValue(null, ATTR_FACE_ID);
                String deviceId = parser.getAttributeValue(null, ATTR_DEVICE_ID);
                mFaces.add(new Face(name, Integer.parseInt(groupId),
                        Integer.parseInt(faceId), Integer.parseInt(deviceId)));
            }
        }
    }

}
