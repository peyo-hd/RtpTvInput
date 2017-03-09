package com.peyo.rtptvinput.source;

import android.net.Uri;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;

import java.io.IOException;

public class TsDataSourceFactory implements DataSource.Factory {

    private final TsStreamerManager mStreamManager = TsStreamerManager.getInstance();

    public static TsDataSourceFactory createSourceFactory() {
        return new TsDataSourceFactory();
    }

    @Override
    public DataSource createDataSource() {
        return new DataSource();
    }

    private class DataSource implements TsDataSource {

        private final TsDataSource rtpDataSource;

        private TsDataSource dataSource;

        public DataSource() {
            rtpDataSource =  mStreamManager.createDataSource();
        }

        @Override
        public long open(DataSpec dataSpec) throws IOException {
            dataSource = rtpDataSource;
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
            if (dataSource != null) {
                try {
                    dataSource.close();
                } finally {
                    dataSource = null;
                }
            }
        }

        @Override
        public long getBufferedPosition() {
            if (dataSource != null) {
                return dataSource.getBufferedPosition();
            } else {
                return 0;
            }
        }

        @Override
        public long getLastReadPosition() {
            if (dataSource != null) {
                return dataSource.getLastReadPosition();
            } else {
                return 0;
            }
        }

        @Override
        public void shiftStartPosition(long offset) {
            if (dataSource != null) {
                dataSource.shiftStartPosition(offset);
            }
        }

    }
}
