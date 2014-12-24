/*
 * Copyright 2012, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef RTP_SINK_H_

#define RTP_SINK_H_

#include <media/stagefright/foundation/AHandler.h>

#include "LinearRegression.h"

#include <gui/Surface.h>

namespace android {

struct ABuffer;
struct ANetworkSession;

struct TunnelRenderer;

// Creates a pair of sockets for RTP/RTCP traffic, instantiates a renderer
// for incoming transport stream data and occasionally sends statistics over
// the RTCP channel.
struct RTPSink : public AHandler {
    RTPSink(const sp<ANetworkSession> &netSession,
            const sp<IGraphicBufferProducer> &bufferProducer);


    void start(const char *sourceHost, int32_t sourcePort);

    enum {
    	kWhatStart,
        kWhatRTPNotify
    };

protected:
    virtual void onMessageReceived(const sp<AMessage> &msg);
    virtual ~RTPSink();

private:

    struct Source;
    struct StreamSource;

    sp<ANetworkSession> mNetSession;
    sp<IGraphicBufferProducer> mSurfaceTex;
    sp<AMessage> mNotify;
    KeyedVector<uint32_t, sp<Source> > mSources;

    AString mRTPHost;
    int32_t mRTPPort;
    int32_t mRTPSessionID;

    int64_t mFirstArrivalTimeUs;
    int64_t mNumPacketsReceived;
    LinearRegression mRegression;
    int64_t mMaxDelayMs;

    sp<TunnelRenderer> mRenderer;

    status_t listen();
    status_t parseRTP(const sp<ABuffer> &buffer);

    DISALLOW_EVIL_CONSTRUCTORS(RTPSink);
};

}  // namespace android

#endif  // RTP_SINK_H_
