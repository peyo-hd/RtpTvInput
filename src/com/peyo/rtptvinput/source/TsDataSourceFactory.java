package com.peyo.rtptvinput.source;

import android.net.Uri;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.FileDataSource;

import java.io.IOException;

public class TsDataSourceFactory implements DataSource.Factory {

    private static final Object sLock = new Object();
    private static int sSequenceId;
    private final int mId;

    private final TsStreamerManager mStreamManager = TsStreamerManager.getInstance();

    public static TsDataSourceFactory createSourceFactory() {
        int id;
        synchronized (sLock) {
            id = ++sSequenceId;
        }
        return new TsDataSourceFactory(id);
    }

    private TsDataSourceFactory(int id) {
        mId = id;
    }

    @Override
    public DataSource createDataSource() {
        return new DataSourceImpl();
    }

    private class DataSourceImpl implements TsDataSource {
        private DataSource dataSource;

        @Override
        public long open(DataSpec dataSpec) throws IOException {
            String uri = dataSpec.uri.toString();
            if (uri.startsWith("file://")) {
                dataSource = new FileDataSource(null);
            } else if (uri.startsWith("udp://")) {
                dataSource = mStreamManager.createDataSource(dataSpec.uri, mId);
            } else {
                return C.LENGTH_UNSET;
            }
            return dataSource.open(dataSpec);
        }

        @Override
        public int read(byte[] buffer, int offset, int readLength) throws IOException {
            return dataSource.read(buffer, offset, readLength);
        }

        @Override
        public Uri getUri() {
            return dataSource == null ? null : dataSource.getUri();
        }

        @Override
        public void close() throws IOException {
            if (dataSource instanceof FileDataSource) {
                    dataSource.close();
            } else if (dataSource instanceof RtpStreamer.RtpDataSource) {
                mStreamManager.releaseDataSource((TsDataSource)dataSource, mId);
                try {
                    dataSource.close();
                } finally {
                    dataSource = null;
                }
            }
        }

        @Override
        public long getBufferedPosition() {
            if (dataSource instanceof RtpStreamer.RtpDataSource) {
                return ((TsDataSource)dataSource).getBufferedPosition();
            } else {
                return 0;
            }
        }

        @Override
        public long getLastReadPosition() {
            if (dataSource instanceof RtpStreamer.RtpDataSource) {
                return ((TsDataSource)dataSource).getLastReadPosition();
            } else {
                return 0;
            }
        }

        @Override
        public void shiftStartPosition(long offset) {
            if (dataSource instanceof RtpStreamer.RtpDataSource) {
                ((TsDataSource)dataSource).shiftStartPosition(offset);
            }
        }

    }
}
