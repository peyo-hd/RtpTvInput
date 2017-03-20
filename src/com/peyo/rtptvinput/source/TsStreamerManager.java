package com.peyo.rtptvinput.source;

import android.net.Uri;
import android.util.Log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TsStreamerManager {
    private static final String TAG = "TsStreamManager";

    private static TsStreamerManager sInstance;
    private final Object mCancelLock = new Object();
    private final StreamerFinder mStreamerFinder = new StreamerFinder();
    private final Map<TsDataSource, RtpStreamer> mSourceToStreamerMap = new HashMap<>();

    static synchronized TsStreamerManager getInstance() {
        if (sInstance == null) {
            sInstance = new TsStreamerManager();
        }
        return sInstance;
    }

    synchronized TsDataSource createDataSource(Uri uri, int sessionId) {
        Log.i(TAG, "createDataSource() " + uri + " " + sessionId);
        synchronized (mCancelLock) {
            if (mStreamerFinder.containsLocked(uri)) {
                mStreamerFinder.appendSessionLocked(uri, sessionId);
                RtpStreamer streamer = (RtpStreamer) mStreamerFinder.getStreamerLocked(uri);
                TsDataSource source = streamer.createDataSource();
                mSourceToStreamerMap.put(source, streamer);
                return source;
            }
        }
        RtpStreamer streamer = new RtpStreamer(uri);
        streamer.startStream();
        synchronized (mCancelLock) {
            mStreamerFinder.putLocked(uri, sessionId, streamer);
            TsDataSource source = streamer.createDataSource();
            mSourceToStreamerMap.put(source, streamer);
            return source;
        }
    }

    synchronized void releaseDataSource(TsDataSource source, int sessionId) {
        Uri uri = source.getUri();
        Log.i(TAG, "releaseDataSource() " + uri + " " + sessionId);
        synchronized (mCancelLock) {
            TsStreamer streamer = mSourceToStreamerMap.get(source);
            mSourceToStreamerMap.remove(source);
            if (streamer == null) {
                return;
            }
            mStreamerFinder.removeSessionLocked(uri, sessionId);
            if (mStreamerFinder.containsLocked(uri)) {
                return;
            }
            streamer.stopStream();
        }
    }

    private class StreamerFinder {
        private final Map<Uri, Set<Integer>> mSessions = new HashMap<>();
        private final Map<Uri, TsStreamer> mStreamers = new HashMap<>();

        // @GuardedBy("mCancelLock")
        private void putLocked(Uri uri, int sessionId, TsStreamer streamer) {
            Set<Integer> sessions = new HashSet<>();
            sessions.add(sessionId);
            mSessions.put(uri, sessions);
            mStreamers.put(uri, streamer);
        }

        // @GuardedBy("mCancelLock")
        private void appendSessionLocked(Uri uri, int sessionId) {
            if (mSessions.containsKey(uri)) {
                mSessions.get(uri).add(sessionId);
            }
        }

        // @GuardedBy("mCancelLock")
        private void removeSessionLocked(Uri uri, int sessionId) {
            Set<Integer> sessions = mSessions.get(uri);
            sessions.remove(sessionId);
            if (sessions.size() == 0) {
                mSessions.remove(uri);
                mStreamers.remove(uri);
            }
        }

        // @GuardedBy("mCancelLock")
        private boolean containsLocked(Uri uri) {
            return mSessions.containsKey(uri);
        }

        // @GuardedBy("mCancelLock")
        private TsStreamer getStreamerLocked(Uri uri) {
            return mStreamers.containsKey(uri) ? mStreamers.get(uri) : null;
        }
    }
}
