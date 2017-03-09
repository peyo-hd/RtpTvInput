package com.peyo.rtptvinput.source;

public class TsStreamerManager {
    private static TsStreamerManager sInstance;

    static synchronized TsStreamerManager getInstance() {
        if (sInstance == null) {
            sInstance = new TsStreamerManager();
        }
        return sInstance;
    }

    synchronized TsDataSource createDataSource() {
        RtpStreamer streamer = new RtpStreamer();
        return streamer.createDataSource();
    }
}
