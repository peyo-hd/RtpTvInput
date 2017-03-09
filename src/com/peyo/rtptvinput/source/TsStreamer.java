package com.peyo.rtptvinput.source;

public interface TsStreamer {
    void startStream();
    void stopStream();
    TsDataSource createDataSource();
}
