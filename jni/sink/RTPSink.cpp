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

#define LOG_NDEBUG 0
#define LOG_TAG "RTPSink"
#include <utils/Log.h>

#include "RTPSink.h"
#include "TunnelRenderer.h"

#include <media/stagefright/foundation/ABuffer.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AMessage.h>
#include <media/stagefright/foundation/ANetworkSession.h>
#include <media/stagefright/foundation/hexdump.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/Utils.h>

namespace android {

struct RTPSink::Source : public RefBase {
    Source(uint16_t seq, const sp<ABuffer> &buffer,
           const sp<AMessage> queueBufferMsg);

    bool updateSeq(uint16_t seq, const sp<ABuffer> &buffer);

    void addReportBlock(uint32_t ssrc, const sp<ABuffer> &buf);

protected:
    virtual ~Source();

private:
    static const uint32_t kMinSequential = 2;
    static const uint32_t kMaxDropout = 3000;
    static const uint32_t kMaxMisorder = 100;
    static const uint32_t kRTPSeqMod = 1u << 16;

    sp<AMessage> mQueueBufferMsg;

    uint16_t mMaxSeq;
    uint32_t mCycles;
    uint32_t mBaseSeq;
    uint32_t mBadSeq;
    uint32_t mProbation;
    uint32_t mReceived;
    uint32_t mExpectedPrior;
    uint32_t mReceivedPrior;

    void initSeq(uint16_t seq);
    void queuePacket(const sp<ABuffer> &buffer);

    DISALLOW_EVIL_CONSTRUCTORS(Source);
};

////////////////////////////////////////////////////////////////////////////////

RTPSink::Source::Source(
        uint16_t seq, const sp<ABuffer> &buffer,
        const sp<AMessage> queueBufferMsg)
    : mQueueBufferMsg(queueBufferMsg),
      mProbation(kMinSequential) {
    initSeq(seq);
    mMaxSeq = seq - 1;

    buffer->setInt32Data(mCycles | seq);
    queuePacket(buffer);
}

RTPSink::Source::~Source() {
}

void RTPSink::Source::initSeq(uint16_t seq) {
    mMaxSeq = seq;
    mCycles = 0;
    mBaseSeq = seq;
    mBadSeq = kRTPSeqMod + 1;
    mReceived = 0;
    mExpectedPrior = 0;
    mReceivedPrior = 0;
}

bool RTPSink::Source::updateSeq(uint16_t seq, const sp<ABuffer> &buffer) {
    uint16_t udelta = seq - mMaxSeq;

    if (mProbation) {
        // Startup phase

        if (seq == mMaxSeq + 1) {
            buffer->setInt32Data(mCycles | seq);
            queuePacket(buffer);

            --mProbation;
            mMaxSeq = seq;
            if (mProbation == 0) {
                initSeq(seq);
                ++mReceived;

                return true;
            }
        } else {
            // Packet out of sequence, restart startup phase

            mProbation = kMinSequential - 1;
            mMaxSeq = seq;

            buffer->setInt32Data(mCycles | seq);
            queuePacket(buffer);
        }

        return false;
    }

    if (udelta < kMaxDropout) {
        // In order, with permissible gap.

        if (seq < mMaxSeq) {
            // Sequence number wrapped - count another 64K cycle
            mCycles += kRTPSeqMod;
        }

        mMaxSeq = seq;
    } else if (udelta <= kRTPSeqMod - kMaxMisorder) {
        // The sequence number made a very large jump

        if (seq == mBadSeq) {
            // Two sequential packets -- assume that the other side
            // restarted without telling us so just re-sync
            // (i.e. pretend this was the first packet)

            initSeq(seq);
        } else {
            mBadSeq = (seq + 1) & (kRTPSeqMod - 1);

            return false;
        }
    } else {
        // Duplicate or reordered packet.
    }

    ++mReceived;

    buffer->setInt32Data(mCycles | seq);
    queuePacket(buffer);

    return true;
}

void RTPSink::Source::queuePacket(const sp<ABuffer> &buffer) {
    sp<AMessage> msg = mQueueBufferMsg->dup();
    msg->setBuffer("buffer", buffer);
    msg->post();
}

void RTPSink::Source::addReportBlock(
        uint32_t ssrc, const sp<ABuffer> &buf) {
    uint32_t extMaxSeq = mMaxSeq | mCycles;
    uint32_t expected = extMaxSeq - mBaseSeq + 1;

    int64_t lost = (int64_t)expected - (int64_t)mReceived;
    if (lost > 0x7fffff) {
        lost = 0x7fffff;
    } else if (lost < -0x800000) {
        lost = -0x800000;
    }

    uint32_t expectedInterval = expected - mExpectedPrior;
    mExpectedPrior = expected;

    uint32_t receivedInterval = mReceived - mReceivedPrior;
    mReceivedPrior = mReceived;

    int64_t lostInterval = expectedInterval - receivedInterval;

    uint8_t fractionLost;
    if (expectedInterval == 0 || lostInterval <=0) {
        fractionLost = 0;
    } else {
        fractionLost = (lostInterval << 8) / expectedInterval;
    }

    uint8_t *ptr = buf->data() + buf->size();

    ptr[0] = ssrc >> 24;
    ptr[1] = (ssrc >> 16) & 0xff;
    ptr[2] = (ssrc >> 8) & 0xff;
    ptr[3] = ssrc & 0xff;

    ptr[4] = fractionLost;

    ptr[5] = (lost >> 16) & 0xff;
    ptr[6] = (lost >> 8) & 0xff;
    ptr[7] = lost & 0xff;

    ptr[8] = extMaxSeq >> 24;
    ptr[9] = (extMaxSeq >> 16) & 0xff;
    ptr[10] = (extMaxSeq >> 8) & 0xff;
    ptr[11] = extMaxSeq & 0xff;

    // XXX TODO:

    ptr[12] = 0x00;  // interarrival jitter
    ptr[13] = 0x00;
    ptr[14] = 0x00;
    ptr[15] = 0x00;

    ptr[16] = 0x00;  // last SR
    ptr[17] = 0x00;
    ptr[18] = 0x00;
    ptr[19] = 0x00;

    ptr[20] = 0x00;  // delay since last SR
    ptr[21] = 0x00;
    ptr[22] = 0x00;
    ptr[23] = 0x00;
}

////////////////////////////////////////////////////////////////////////////////

RTPSink::RTPSink(
        const sp<ANetworkSession> &netSession,
        const sp<IGraphicBufferProducer> &bufferProducer)
    : mNetSession(netSession),
      mSurfaceTex(bufferProducer),
      mRTPPort(0),
      mRTPSessionID(0),
      mFirstArrivalTimeUs(-1ll),
      mNumPacketsReceived(0ll),
      mRegression(1000),
      mMaxDelayMs(-1ll) {
}

RTPSink::~RTPSink() {
    if (mRTPSessionID != 0) {
        mNetSession->destroySession(mRTPSessionID);
    }
}

status_t RTPSink::listen() {
    sp<AMessage> rtpNotify = new AMessage(kWhatRTPNotify, this);
    status_t err = mNetSession->createUDPSession(
            mRTPPort, rtpNotify, &mRTPSessionID);
    if (err != OK) {
        ALOGE("createUDPSession error %d", err);
        return err;
    }
    ALOGI("created RTPSession %d port %d",  mRTPSessionID, mRTPPort);

    err = mNetSession->connectUDPSession(mRTPSessionID, mRTPHost.c_str(), (unsigned)INADDR_ANY);

    if (err != OK) {
        ALOGE("An error occurred while connecting %s", mRTPHost.c_str());
        mNetSession->destroySession(mRTPSessionID);
        mRTPSessionID = 0;
    }

    ALOGI("connected to RTPsockets to %s:%d", mRTPHost.c_str(), mRTPPort);

    return err;
}

void RTPSink::start(const char *sourceHost, int32_t sourcePort) {
    sp<AMessage> msg = new AMessage(kWhatStart, this);
    msg->setString("sourceHost", sourceHost);
    msg->setInt32("sourcePort", sourcePort);
    msg->post();
}

void RTPSink::onMessageReceived(const sp<AMessage> &msg) {
    switch (msg->what()) {
    	case kWhatStart:
    	{
            CHECK(msg->findString("sourceHost", &mRTPHost));
            CHECK(msg->findInt32("sourcePort", &mRTPPort));
            status_t err = listen();
            if (err != OK) {
                looper()->stop();
            }
    		break;
    	}
        case kWhatRTPNotify:
        {
            int32_t reason;
            CHECK(msg->findInt32("reason", &reason));

            switch (reason) {
                case ANetworkSession::kWhatError:
                {
                    int32_t sessionID;
                    CHECK(msg->findInt32("sessionID", &sessionID));

                    int32_t err;
                    CHECK(msg->findInt32("err", &err));

                    AString detail;
                    CHECK(msg->findString("detail", &detail));

                    ALOGE("An error occurred in session %d (%d, '%s/%s').",
                          sessionID,
                          err,
                          detail.c_str(),
                          strerror(-err));

                    mNetSession->destroySession(sessionID);
                    if (sessionID == mRTPSessionID) {
                        mRTPSessionID = 0;
                    }

                    looper()->stop();
                    break;
                }

                case ANetworkSession::kWhatDatagram:
                {
                    int32_t sessionID;
                    CHECK(msg->findInt32("sessionID", &sessionID));

                    sp<ABuffer> data;
                    CHECK(msg->findBuffer("data", &data));

                    status_t err;
                    if (msg->what() == kWhatRTPNotify) {
                        err = parseRTP(data);
                    }
                    break;
                }

                default:
                    TRESPASS();
            }
            break;
        }

        default:
            TRESPASS();
    }
}

status_t RTPSink::parseRTP(const sp<ABuffer> &buffer) {
    size_t size = buffer->size();
    if (size < 12) {
        // Too short to be a valid RTP header.
        return ERROR_MALFORMED;
    }

    const uint8_t *data = buffer->data();

    if ((data[0] >> 6) != 2) {
        // Unsupported version.
        return ERROR_UNSUPPORTED;
    }

    if (data[0] & 0x20) {
        // Padding present.

        size_t paddingLength = data[size - 1];

        if (paddingLength + 12 > size) {
            // If we removed this much padding we'd end up with something
            // that's too short to be a valid RTP header.
            return ERROR_MALFORMED;
        }

        size -= paddingLength;
    }

    int numCSRCs = data[0] & 0x0f;

    size_t payloadOffset = 12 + 4 * numCSRCs;

    if (size < payloadOffset) {
        // Not enough data to fit the basic header and all the CSRC entries.
        return ERROR_MALFORMED;
    }

    if (data[0] & 0x10) {
        // Header eXtension present.

        if (size < payloadOffset + 4) {
            // Not enough data to fit the basic header, all CSRC entries
            // and the first 4 bytes of the extension header.

            return ERROR_MALFORMED;
        }

        const uint8_t *extensionData = &data[payloadOffset];

        size_t extensionLength =
            4 * (extensionData[2] << 8 | extensionData[3]);

        if (size < payloadOffset + 4 + extensionLength) {
            return ERROR_MALFORMED;
        }

        payloadOffset += 4 + extensionLength;
    }

    uint32_t srcId = U32_AT(&data[8]);
    uint32_t rtpTime = U32_AT(&data[4]);
    uint16_t seqNo = U16_AT(&data[2]);

    sp<AMessage> meta = buffer->meta();
    meta->setInt32("ssrc", srcId);
    meta->setInt32("rtp-time", rtpTime);
    meta->setInt32("PT", data[1] & 0x7f);
    meta->setInt32("M", data[1] >> 7);

    buffer->setRange(payloadOffset, size - payloadOffset);

    ssize_t index = mSources.indexOfKey(srcId);
    if (index < 0) {
        if (mRenderer == NULL) {
            mRenderer = new TunnelRenderer(mSurfaceTex);
            looper()->registerHandler(mRenderer);
        }

        sp<AMessage> queueBufferMsg =
            new AMessage(TunnelRenderer::kWhatQueueBuffer, mRenderer);

        sp<Source> source = new Source(seqNo, buffer, queueBufferMsg);
        mSources.add(srcId, source);
    } else {
        mSources.valueAt(index)->updateSeq(seqNo, buffer);
    }

    return OK;
}


}  // namespace android

