package com.peyo.rtptvinput;

import android.net.Uri;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.UdpDataSource;

import java.io.IOException;

class RtpDataSource implements DataSource {
    private DataSource source;
    private static final int RtpHeaderSize = 12;
    private static final int packetMaxSize = UdpDataSource.DEFAULT_MAX_PACKET_SIZE;
    private final byte[] packetBuffer;
    private int packetSize = 0;
    private int remaining = 0;

    public RtpDataSource() {
        packetBuffer = new byte[packetMaxSize];
        source = new UdpDataSource(null);
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        return source.open(dataSpec);
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        if (remaining == 0) {
            packetSize = source.read(packetBuffer, 0, packetMaxSize);
            if (packetSize <= RtpHeaderSize) return 0;
            remaining = packetSize - RtpHeaderSize;
        }

        int packetOffset = packetSize - remaining;
        int bytesToRead = Math.min(remaining, readLength);
        System.arraycopy(packetBuffer, packetOffset, buffer, offset, bytesToRead);
        remaining -= bytesToRead;
        return bytesToRead;
    }

    @Override
    public Uri getUri() {
        return source.getUri();
    }

    @Override
    public void close() throws IOException {
        source.close();
    }
}