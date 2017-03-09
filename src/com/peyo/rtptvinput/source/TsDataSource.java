package com.peyo.rtptvinput.source;

import com.google.android.exoplayer2.upstream.DataSource;

public interface TsDataSource extends DataSource {
    long getBufferedPosition();
    long getLastReadPosition();
    void shiftStartPosition(long offset);
}
