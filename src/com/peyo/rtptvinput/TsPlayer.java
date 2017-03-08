package com.peyo.rtptvinput;

import android.content.Context;
import android.net.Uri;
import android.view.Surface;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultAllocator;

import static com.google.android.exoplayer2.DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS;
import static com.google.android.exoplayer2.DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS;
import static com.google.android.exoplayer2.DefaultLoadControl.DEFAULT_MAX_BUFFER_MS;
import static com.google.android.exoplayer2.DefaultLoadControl.DEFAULT_MIN_BUFFER_MS;

public class TsPlayer {
    private final Context mContext;
    private SimpleExoPlayer mExoPlayer;
    private Surface mSurface;
    private boolean mWaitForSurface;
    private Uri mUri;
    private Listener mListener;

    public interface Listener {
        void onPlayStarted();
    }

    public TsPlayer(Context context) {
        mContext = context;
        mWaitForSurface = false;
        mSurface = null;
        mExoPlayer = null;
        mListener = null;
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void setSurface(Surface surface) {
        mSurface = surface;
        if (mSurface != null && mWaitForSurface) {
            mWaitForSurface = false;
            playRtp();
        } else {
            stop();
        }
    }

    public void startRtp(Uri uri) {
        mUri = uri;
        if (mSurface == null) {
            mWaitForSurface = true;
        } else {
            mWaitForSurface = false;
            playRtp();
        }
    }

    public void stop() {
        if (mExoPlayer != null) {
            mExoPlayer.release();
            mExoPlayer = null;
        }
        mWaitForSurface = false;
    }

    private void playRtp() {
        mExoPlayer = ExoPlayerFactory.newSimpleInstance(mContext,
                new DefaultTrackSelector(null),
                new DefaultLoadControl(
                        new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
                        DEFAULT_MIN_BUFFER_MS / 5, DEFAULT_MAX_BUFFER_MS / 5,
                        DEFAULT_BUFFER_FOR_PLAYBACK_MS / 5,
                        DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS / 5), null);

        mExoPlayer.setVideoListener(new SimpleExoPlayer.VideoListener() {
            @Override
            public void onRenderedFirstFrame() {
                if (mListener != null) {
                    mListener.onPlayStarted();
                }
            }
            @Override
            public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {}
        });

        mExoPlayer.setVideoSurface(mSurface);
        MediaSource source = new ExtractorMediaSource(mUri,
                new DataSource.Factory() {
                    public DataSource createDataSource() {
                        return new RtpDataSource();
                    }
                },
                new DefaultExtractorsFactory(), null, null);
        mExoPlayer.prepare(source);
        mExoPlayer.setPlayWhenReady(true);
    }

}
