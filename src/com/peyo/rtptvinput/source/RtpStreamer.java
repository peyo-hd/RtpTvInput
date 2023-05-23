package com.peyo.rtptvinput.source;

import android.net.Uri;
import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.upstream.UdpDataSource;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

public class RtpStreamer implements TsStreamer {
    private static final String TAG = "RtpStreamer";
    private final Uri mUri;

    public RtpStreamer(Uri uri) {
        mUri = uri;
    }

    @Override
    public TsDataSource createDataSource() {
        return new RtpDataSource(this);
    }

    public class RtpDataSource implements TsDataSource {
        private final RtpStreamer mStreamer;
        private final AtomicLong mLastReadPosition = new AtomicLong(0);
        private long mStartBufferedPosition;

        public RtpDataSource(RtpStreamer streamer) {
            mStreamer = streamer;
            mStartBufferedPosition = streamer.getBufferedPosition();
        }

        @Override
        public void addTransferListener(TransferListener transferListener) {

        }

        @Override
        public long open(DataSpec dataSpec) throws IOException {
            mLastReadPosition.set(0);
            return C.LENGTH_UNSET;
        }

        @Override
        public int read(byte[] buffer, int offset, int readLength) throws IOException {
            int ret = readAt(mStartBufferedPosition + mLastReadPosition.get(), buffer,
                    offset, readLength);
            if (ret > 0) {
                mLastReadPosition.addAndGet(ret);
            }
            return ret;
        }

        @Override
        public Uri getUri() {
            return mStreamer.mUri;
        }

        @Override
        public void close() throws IOException {
        }

        @Override
        public long getBufferedPosition() {
            return mStreamer.getBufferedPosition() - mStartBufferedPosition;
        }

        @Override
        public long getLastReadPosition() {
            return mLastReadPosition.get();
        }

        @Override
        public void shiftStartPosition(long offset) {
            mStartBufferedPosition += offset;
        }
    }

    public int readAt(long pos, byte[] buffer, int offset, int amount) throws IOException {
        while (true) {
            synchronized (mCircularBufferMonitor) {
                if (!mStreaming) {
                    return -1;
                }
                if (mBytesFetched - pos > CIRCULAR_BUFFER_SIZE) {
                    Log.e(TAG, "Demux is requesting the data which is already overwritten.");
                    return -1;
                }

                if (mBytesFetched < pos + amount) {
                    try {
                        mCircularBufferMonitor.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    continue;
                }

                int startPos = (int) (pos % CIRCULAR_BUFFER_SIZE);
                int endPos = (int) ((pos + amount) % CIRCULAR_BUFFER_SIZE);
                int firstLength = (startPos > endPos ? CIRCULAR_BUFFER_SIZE : endPos) - startPos;
                System.arraycopy(mCircularBuffer, startPos, buffer, offset, firstLength);
                if (firstLength < amount) {
                    System.arraycopy(mCircularBuffer, 0, buffer, offset + firstLength,
                            amount - firstLength);
                }
                mCircularBufferMonitor.notifyAll();
                return amount;
            }
        }
    }

    private static final int RTP_HEADER_SIZE = 12;
    private static final int MAX_PACKET_SIZE = 2048;
    private static final int CIRCULAR_BUFFER_SIZE = MAX_PACKET_SIZE * 512 * 32; // 32MB
    private final Object mCircularBufferMonitor = new Object();
    private final byte[] mCircularBuffer = new byte[CIRCULAR_BUFFER_SIZE];

    private DataSource mUdpSource;
    private long mBytesFetched;
    private boolean mStreaming;
    private StreamingThread mStreamingThread;

    @Override
    public void startStream() {
        mUdpSource = new UdpDataSource(MAX_PACKET_SIZE);
        try {
            mUdpSource.open(new DataSpec(mUri));
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        synchronized (mCircularBufferMonitor) {
            if (mStreaming) {
                Log.w(TAG, "Streaming should be stopped before start streaming");
                return;
            }
            mStreaming = true;
            mBytesFetched = 0;
        }
        mStreamingThread = new StreamingThread();
        mStreamingThread.start();
        Log.i(TAG, "Streaming started");
    }

    private class StreamingThread extends Thread {
        @Override
        public void run() {
            byte[] dataBuffer = new byte[UdpDataSource.DEFAULT_MAX_PACKET_SIZE];
            while (true) {
                synchronized (mCircularBufferMonitor) {
                    if (!mStreaming) {
                        break;
                    }
                }

                int bytesWritten;
                try {
                    bytesWritten = mUdpSource.read(dataBuffer, 0, dataBuffer.length);
                    if (bytesWritten <= RTP_HEADER_SIZE) continue;
                    bytesWritten -= RTP_HEADER_SIZE;
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }

                synchronized (mCircularBufferMonitor) {
                    int posInBuffer = (int) (mBytesFetched % CIRCULAR_BUFFER_SIZE);
                    int bytesToCopyInFirstPass = bytesWritten;
                    if (posInBuffer + bytesToCopyInFirstPass > mCircularBuffer.length) {
                        bytesToCopyInFirstPass = mCircularBuffer.length - posInBuffer;
                    }
                    System.arraycopy(dataBuffer, RTP_HEADER_SIZE, mCircularBuffer, posInBuffer,
                            bytesToCopyInFirstPass);
                    if (bytesToCopyInFirstPass < bytesWritten) {
                        System.arraycopy(dataBuffer, RTP_HEADER_SIZE + bytesToCopyInFirstPass, mCircularBuffer, 0,
                                bytesWritten - bytesToCopyInFirstPass);
                    }
                    mBytesFetched += bytesWritten;
                    mCircularBufferMonitor.notifyAll();
                }
            }
            Log.i(TAG, "Streaming stopped");
        }
    }

    public long getBufferedPosition() {
        synchronized (mCircularBufferMonitor) {
            return mBytesFetched;
        }
    }

    @Override
    public void stopStream() {
        synchronized (mCircularBufferMonitor) {
            mStreaming = false;
            mCircularBufferMonitor.notifyAll();
        }
        try {
            if (mStreamingThread != null) {
                mStreamingThread.join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            mUdpSource.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
