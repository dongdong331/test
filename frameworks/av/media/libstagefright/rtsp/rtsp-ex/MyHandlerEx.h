#ifndef MY_HANDLER_EX_H
#define MY_HANDLER_EX_H
    bool mSdpHasRange;
    float mNptStart;
    float mNptEnd;
    bool  mIsTeardownFinished;
    bool  mExited;
    sp<ALooper> mRTSPNetLooper;
    /** SPRD: support 3GPP Bitrate-Adaptation @{*/
    bool m3gppAdaptation;
    size_t mBitrate;
    /** SPRD:  @}*/
    bool mHaveVideo;
    bool mHaveAudio;

    bool skipMultiStreams(sp<APacketSource> source, size_t index) {
        // skip multiple audio/video streams
        bool skip = false;
        sp<MetaData> meta = source->getFormat();
        const char *mime = "";
        CHECK(meta->findCString(kKeyMIMEType, &mime));
        if (!strncasecmp(mime, "video/", 6)) {
            if (mHaveVideo) {
                ALOGI("Skip multiple video stream. Ignoring track #%d.", (int)index);
                skip = true;
            } else {
                mHaveVideo = true;
            }
        } else if (!strncasecmp(mime, "audio/", 6)) {
            if (mHaveAudio) {
                ALOGI("Skip multiple audio stream. Ignoring track #%d.", (int)index);
                skip = true;
            } else {
                mHaveAudio = true;
            }
        } else {
            ALOGE("Unsupported format %s. Ignoring track #%d.", mime, (int)index);
            skip = true;
        }

        if (skip) {
            ALOGI("setupTrack, skip this track #%d.", (int)index);
            sp<AMessage> reply = new AMessage('setu', this);
            reply->setSize("index", index);
            reply->setInt32("result", ERROR_UNSUPPORTED);
            reply->post();
            return true;
        }

        return false;
    }

    void init() {
        mSdpHasRange = false;
        mNptStart = 0.0;
        mNptEnd = 0.0;
        mIsTeardownFinished = false;
        mExited = false;
        mHaveVideo = false;
        mHaveAudio = false;
        mRTSPNetLooper = new ALooper;
        mRTSPNetLooper->setName("rtsp conn looper");
        mRTSPNetLooper->start();
    }

    void parseNTPRange() {
        AString value;
        if (!mSessionDesc->findAttribute(0, "a=range", &value)) {
            ALOGI("A: server has no npt range in sdp protocol");
        } else {
            if (strncmp(value.c_str(), "npt=", 4)) {
                ALOGI("B: server has no npt range in sdp protocol");
            } else {
                mSessionDesc->parseNTPRange(value.c_str() + 4, &mNptStart, &mNptEnd);
                ALOGI("NTPRange: mNptStart=%f, mNptEnd=%f", mNptStart, mNptEnd);
                mSdpHasRange = true;
            }
        }
    }

    ~MyHandler() {
        if (mRTSPNetLooper != NULL && mConn != NULL) {
            mRTSPNetLooper->unregisterHandler(mConn->id());
        }
    }

    void tearDownTimeout(const sp<AMessage>& msg) {
        if (mIsTeardownFinished) {
            ALOGW("tdto already finish teardown, break directly.");
            return;
        }

        ALOGI("TEARDOWN completed with timeout after 3s.");

        sp<AMessage> reply = new AMessage('disc', this);

        int32_t reconnect;
        if (msg->findInt32("reconnect", &reconnect) && reconnect) {
            if (!mExited) {
                reply->setInt32("reconnect", true);
            } else {
                ALOGI("tdto: no need to reconnect when stop playing");
            }
        }

        mConn->disconnect(reply);
        mIsTeardownFinished = true;
        mConn->setTearDownFlag(false);
    }

    void flushPackets(size_t trackIndex) {
        TrackInfo *trackInfo = &mTracks.editItemAt(trackIndex);

        if(!(trackInfo->mPackets.empty())) {
            sp<ABuffer> begin = *trackInfo->mPackets.begin();
            ALOGI("flush track %d,  dropping stale mPackets: begin %d size %d",(int)trackIndex,begin->int32Data(),(int)trackInfo->mPackets.size());
            while (!trackInfo->mPackets.empty()) {
                trackInfo->mPackets.erase(trackInfo->mPackets.begin());
           }
        }
    }

    void appendBitrateAdap(AString &request, size_t index, const AString &trackURL) {
        /** SPRD: for 3GPP Bitrate-Adaptation according to the design of opencore @{*/
        AString value;
        m3gppAdaptation = false;
        mBitrate = 0;
        if (mSessionDesc->findAttribute(index,"a=3GPP-Adaptation-Support", &value)) {
            if (strncmp(value.c_str(), "1", 1)) {
                m3gppAdaptation = false;
            } else {
                m3gppAdaptation = true;
                if (mSessionDesc->findAttribute(index,"a=MaxBitRate", &value)) {
                    mBitrate += atoi(strstr(value.c_str(),";") != NULL? strstr(value.c_str(),";") + 1 : 0);
                    ALOGI("3GPP Bitrate-Adaptation MaxBitRate=%d", (int)mBitrate);
                } else {
                    m3gppAdaptation = false;
                }
            }
        }
        if (m3gppAdaptation && mBitrate > 0) {
            request.append("3GPP-Adaptation: ");
            request.append("url=\"");
            request.append(trackURL);
            request.append("\";size=");
            size_t bufQueueSize = 0; //size in bytes
            size_t JitterBufferDurationInMilliSeconds = 4000; //according to the design of opencore
            size_t byteRate = mBitrate / 8;
            if (mSessionDesc->findAttribute(index,"a=Preroll", &value)) {
                        byteRate += atoi(strstr(value.c_str(),";") != NULL? strstr(value.c_str(),";") + 1 : 0);
            }
            ALOGI("byteRate(include Preroll)=%d", (int)byteRate);
            size_t overhead = (byteRate * 10) / 100; //floating byterate: 10%
            bufQueueSize = (byteRate + overhead) * (JitterBufferDurationInMilliSeconds / 1000);
            if (bufQueueSize < MIN_RTP_SOCKET_MEM_POOL_SIZE_IN_BYTES) {
                bufQueueSize = MIN_RTP_SOCKET_MEM_POOL_SIZE_IN_BYTES;
            }
            bufQueueSize += 2 * MAX_SOCKET_BUFFER_SIZE;
            ALOGI("bufQueueSize = %d", (int)bufQueueSize);
            request.append(bufQueueSize);
            request.append(";target-time=");
            request.append(JitterBufferDurationInMilliSeconds);
            request.append("\r\n");
            ALOGI("send request 3GPP-Adaptation:%s",request.c_str());
        }
        /** SPRD:  @}*/
    }
#endif