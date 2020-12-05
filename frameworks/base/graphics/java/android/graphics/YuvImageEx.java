 /*
 * Copyright (C) 2010 The Android Open Source Project
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
package android.graphics;

import java.io.OutputStream;
import android.graphics.YuvImage;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import android.graphics.Rect;
import android.graphics.ImageFormat;

/**
 * YuvImageEx class provide the following function:
 * 1.encode the yuv data to jpeg
 *    using as follows:
 *        YuvImageEx.EncoderParameter encoderPara =
 *                               new YuvImageEx.EncoderParameter(yuvByte, YuvImageEx.YUV_FORMAT_NV21, width, height)
 *                                    .setQuality(90)
 *                                    .setRotation(0)
 *                                    .setRectangle(new Rect(0, 0, width, height))
 *                                    .setStrides(null);
 *        YuvImageEx.encodeYuvToJpeg(encoderPara, outputStream);
 *
 * 2.decode the jpeg file to yuv
 *     using as follows:
 *        YuvImageEx yuvImageEx = YuvImageEx.decodeJpegToYuv(filePath, format);
 *     or using another static method:
 *        YuvImageEx yuvImageEx = YuvImageEx.decodeJpegToYuv(jpegData, format);
 *
 *         byte[] yuvData = yuvImageEx.getYuvData();
 *         int format = yuvImageEx.getYuvFormat();
 *         int width = yuvImageEx.getWidth();
 *         int height = yuvImageEx.getHeight();
 *
 * Currently hardware encoder only support NV21 and NV12, YUY2 can be encoded by software encoder.
 * @hide
 */
public class YuvImageEx {

    private static final String TAG = "YuvImageEx";

    public static final int YUV_FORMAT_NV21 = ImageFormat.NV21;    /*v/u interleaved, value is 0x11*/
    public static final int YUV_FORMAT_YUY2  = ImageFormat.YUY2;   /*0x14*/
    public static final int YUV_FORMAT_NV12 = 0x12;          /*u/v interleaved, the value is defined privately*/
    private static boolean sLoadLibrarySuccess = true;

    /*The raw YUV format*/
    private int mFormat;

    /*The raw YUV data*/
    private byte[] mData;

    /*The width of the image*/
    private int mWidth;

    /*The height of the the image*/
    private int mHeight;

    /*currently not used*/
    private int[] mStrides;

    static{
        try{
            System.loadLibrary("yuv_jpeg_converter_jni");
        }catch(UnsatisfiedLinkError e) {
            sLoadLibrarySuccess = false;
            Log.e("YuvImageEx","load libyuv_jpeg_converter_jni.so fail!");
            e.printStackTrace();
        }
    }

    /**
     * App should call this interface to confirm whether jpeg hardware codec is available
     *
     * @return true if jpeg hardware codec is available, otherwise return false.
     */
    static public boolean isJpegHwCodecAvailable() {
        return sLoadLibrarySuccess;
    }

    /**
     * Constructor of YuvImageEx.
     *
     * @param yuv      The YUV data.
     * @param format  The YUV data format.
     * @param width   The width of the yuv image.
     * @param height  The height of the yuv image.
     * @param strides (Optional) Row bytes of each image plane. If yuv contains padding, the stride
     *                 of each image must be provided. If strides is null, the method assumes no padding
     *                 and derives the row bytes by format and width itself, only used for software encoder.
     * @throws IllegalArgumentException if format is not support; width or height <= 0; or yuv is null.
     */
    public YuvImageEx(byte[] yuv, int format, int width, int height, int[] strides) {
        if (yuv == null) {
            throw new IllegalArgumentException("YuvImageEx yuv cann't be null");
        }

        if (format != YUV_FORMAT_NV21 && format != YUV_FORMAT_NV12 && format != YUV_FORMAT_YUY2) {
            throw new IllegalArgumentException("YuvImageEx format parameter must be NV12, NV21 or YUY2!");
        }

        if (width <= 0  || height <= 0) {
            throw new IllegalArgumentException("YuvImageEx width and height must be larger than 0");
        }
        mData = yuv;
        mWidth = width;
        mHeight = height;
        mFormat = format;
        mStrides = strides;
    }

   /**
     * @return the YUV data.
     */
    public byte[] getYuvData() {
        return mData;
    }

    /**
     * @return the YUV format
     */
    public int getYuvFormat() {
        return mFormat;
    }

    /**
     * @return the width of the image.
     */
    public int getWidth() {
        return mWidth;
    }

    /**
     * @return the height of the image.
     */
    public int getHeight() {
        return mHeight;
    }

    /**
     * The static inner class EncoderParameter provided encapsulation of encoder parameters, some member
     *  variable wonn't be used by sprd jpeg encoder for now. When create EncoderParameter object or call
     *  set interface, it may throws IllegalArgumentException in some situations: yuv is null; format is not support;
     *  width or height <= 0; quality is not within [0, 100]; rotation is not 0, 90, 180 or 270.
     */
    public static class EncoderParameter {
        //The yuv data to be encoded
        private byte[] mYuvData;

         //The YUV data format, hardware encoder support NV21 and NV12
        private int mFormat;

        //The width of the yuv image
        private int mWidth;

        //The height of the yuv image
        private int mHeight;

        //Hint to the encoder, 0-100. 0 meaning encode for small size, 100 meaning encode for max quality
        private int mQuality;

        //The angle of rotation, can be set as 0, 90, 180 or 270
        private int mRotation;

        //(Optional) Row bytes of each image plane, currently only used for software encoder
        private int[] mStrides;

        //The rectangle region to be encoded, Sprd hardware encoder won't use it for now, only used for software encoder
        private Rect mRectangle;

        public EncoderParameter(byte[] yuv, int format, int width, int height) {
            if (yuv == null) {
                throw new IllegalArgumentException("EncoderParameter yuv cannot be null");
            }

            if (format != YUV_FORMAT_NV21 && format != YUV_FORMAT_NV12 && format != YUV_FORMAT_YUY2) {
                throw new IllegalArgumentException("EncoderParameter format must be NV12, NV21 or YUY2!");
            }

            if (width <= 0  || height <= 0) {
                throw new IllegalArgumentException("EncoderParameter width and height must be larger than 0");
            }
            mYuvData = yuv;
            mFormat = format;
            mWidth = width;
            mHeight = height;
            mRotation = 0;
            mQuality = 100;
        }

        public EncoderParameter setQuality(int quality) {
            if (quality < 0 || quality > 100) {
                throw new IllegalArgumentException("setQuality quality must be 0..100");
            }
            mQuality = quality;
            return this;
        }

        public EncoderParameter setRotation(int rotation) {
            if (rotation != 0 && rotation != 90 && rotation != 180 && rotation != 270) {
                throw new IllegalArgumentException("setRotation rotation parameter invalid!");
            }
            mRotation = rotation;
            return this;
        }

        public EncoderParameter setStrides(int[] strides) {
            mStrides = strides;
            return this;
        }

        public EncoderParameter setRectangle(Rect rectangle) {
            mRectangle = rectangle;
            return this;
        }

        public String toString () {
            return new String("EncoderParameter:"
                    + " format=" + mFormat
                    + " width=" + mWidth
                    + " height=" + mHeight
                    + " rotation=" + mRotation
                    + " quality=" + mQuality);
        }
    }

    /**
     * Static method used to encode the yuv data to a jpeg. sprd hardware encoder only support NV21 and
     * NV12 for now, and YUY2 will be encoded by software encoder.
     *
     * @param encoderParam  contains some encoder parameters, not all member variables are used for now
     * @param stream   OutputStream to write the encoded jpeg data.
     * @return      true if encode successfully.
     * @throws IllegalArgumentException if stream is null.
     */
    static public boolean encodeYuvToJpeg(EncoderParameter encoderParam, OutputStream stream) {
        if (!sLoadLibrarySuccess) {
            throw new IllegalStateException("encodeYuvToJpeg load libyuv_jpeg_converter_jni.so fail");
        }
        if (stream == null) {
            throw new IllegalArgumentException("encodeYuvToJpeg stream cann't be null");
        }
        int format = encoderParam.mFormat;
        int width = encoderParam.mWidth;
        int height = encoderParam.mHeight;
        int quality = encoderParam.mQuality;
        int rotation = encoderParam.mRotation;
        byte[] yuv = encoderParam.mYuvData;

        //use hardware encoder
        boolean encRet = false;
        if (format == YUV_FORMAT_NV21 || format == YUV_FORMAT_NV12) {
            encRet = nativeEncodeYuvToJpeg(yuv, format, width, height, quality, rotation, stream);
            if (!encRet) {
                Log.d(TAG, "sprd jpeg hardware encoder failed: " + encoderParam.toString());
            }
        }

        //try to use software encoder for NV21 and YUY2
        if (!encRet && (format == YUV_FORMAT_NV21 || format == YUV_FORMAT_YUY2)) {
            Log.d(TAG, "try to use software encoder of YuvImage, format: " + format);
            if (rotation != 0) {
                Log.e(TAG, "software encoder YuvImage donn't support rotate!");
                return false;
            }
            int[] strides = encoderParam.mStrides;
            Rect rectangle = encoderParam.mRectangle;
            YuvImage yuvImage = new YuvImage(yuv, format, width, height, strides);
            if (yuvImage != null) {
                encRet = yuvImage.compressToJpeg(rectangle, quality, stream);
            }
        }
        return encRet;
    }

    /**
     * Decode the jpeg file to yuv data.
     *
     * @param  jpegPathName  The jpeg file path.
     * @param  format  The decoded YUV data format, hardware decoder support NV21 and NV12.
     * @return  YuvImageEx object, return null if decode fail, so the caller need to judge if the object is null.
     *              the caller can obtain the yuv data and width, height like this:
     *              byte[] yuvData = yuvImageEx.getYuvData();
     *              int format = yuvimage.getYuvFormat();
     *              int width = yuvimage.getWidth();
     *              int height = yuvimage.getHeight();
     */
    static public YuvImageEx decodeJpegToYuv(String jpegPathName, int format) {
        long fileSize = 0;
        FileInputStream fis = null;
        YuvImageEx yuvImage = null;
        if (!sLoadLibrarySuccess) {
            throw new IllegalStateException("decodeJpegToYuv load libyuv_jpeg_converter_jni.so fail");
        }
        if (format != YUV_FORMAT_NV21 && format != YUV_FORMAT_NV12) {
            throw new IllegalArgumentException("decodeJpegToYuv format parameter must be NV12 or NV21!");
        }
        try {
            File file = new File(jpegPathName);
            if (file.exists()) {
                fileSize = file.length();
                if (fileSize == 0) {
                    Log.d(TAG, "file is empty, filePath: " + jpegPathName);
                    return null;
                }

                fis = new FileInputStream(file);
                yuvImage = nativeDecodeJpegToYuvEx(fis, (int)fileSize, format);
            } else {
                Log.d(TAG, "file is not exist: " + jpegPathName);
            }
        } catch (Exception e) {
            Log.e(TAG, "decodeJpegToYuv file error");
            e.printStackTrace();
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException e) {
                Log.d(TAG, "decodeJpegToYuv close inputstrem error");
                e.printStackTrace();
            }
        }
        return yuvImage;
    }

    /**
     * Decode the jpeg data to yuv image.
     *
     * @param  jpegData  The byte array of jpeg data.
     * @param  format  The decoded YUV data format, hardware decoder support NV21 and NV12.
     * @return  YuvImageEx object, return null if decode fail, so the caller need to judge if the object is null.
     *              the caller can obtain the yuv data and width, height like this:
     *              byte[] yuvData = yuvImageEx.getYuvData();
     *              int format = yuvImageEx.getYuvFormat();
     *              int width = yuvImageEx.getWidth();
     *              int height = yuvImageEx.getHeight();
     */
    static public YuvImageEx decodeJpegToYuv(byte[] jpegData, int format) {
        if (!sLoadLibrarySuccess) {
            throw new IllegalStateException("decodeJpegToYuv load libyuv_jpeg_converter_jni.so fail");
        }
        if (format != YUV_FORMAT_NV21 && format != YUV_FORMAT_NV12) {
            throw new IllegalArgumentException("decodeJpegToYuv format parameter must be NV12 or NV21!");
        }
        if (jpegData == null) {
            throw new IllegalArgumentException("decodeJpegToYuv jpegData is null!");
        }
        return nativeDecodeJpegToYuv(jpegData, jpegData.length, format);
    }

///////////////////////////////////////////////////////////////////////////////
    private static native boolean nativeEncodeYuvToJpeg(byte[] oriYuv,
            int format, int width, int height, int quality, int rotation, OutputStream stream);

    private static native YuvImageEx nativeDecodeJpegToYuv(byte[] oriJpeg, int size, int format);
    private static native YuvImageEx nativeDecodeJpegToYuvEx(InputStream stream, int size, int format);
}
