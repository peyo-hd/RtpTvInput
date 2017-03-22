package com.peyo.rtptvinput;

import android.content.Context;
import android.media.PlaybackParams;
import android.net.Uri;
import android.view.Surface;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.peyo.rtptvinput.source.TsDataSourceFactory;

import static com.google.android.exoplayer2.DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS;
import static com.google.android.exoplayer2.DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS;
import static com.google.android.exoplayer2.DefaultLoadControl.DEFAULT_MAX_BUFFER_MS;
import static com.google.android.exoplayer2.DefaultLoadControl.DEFAULT_MIN_BUFFER_MS;

public class TsPlayer {
    private final Context mContext;
    private SimpleExoPlayer mExoPlayer;
    private Surface mSurface;
    private boolean mWaitForSurface;
    private MediaSource mSource;
    private Listener mListener;
    private TsDataSourceFactory mSourceFactory;

    public void setPlaybackParams(PlaybackParams params) {
         if (mExoPlayer != null) {
            mExoPlayer.setPlaybackParams(params);
        }
    }

    public void seekTo(long position) {
        if (mExoPlayer != null) {
            mExoPlayer.seekTo(position);
        }
    }

    public interface Listener {
        void onPlayStarted();
    }

    public TsPlayer(Context context, Listener listener) {
        mContext = context;
        mWaitForSurface = false;
        mSurface = null;
        mExoPlayer = null;
        mListener = listener;
    }

    public void setDataSourceFactory(TsDataSourceFactory dataSourceManager) {
        mSourceFactory = dataSourceManager;
    }

    public void setSurface(Surface surface) {
        mSurface = surface;
        if (mSurface != null && mWaitForSurface) {
            mWaitForSurface = false;
            play();
        } else {
            stop();
        }
    }

    public void start() {
        if (mSurface == null) {
            mWaitForSurface = true;
        } else {
            mWaitForSurface = false;
            play();
        }
    }

    public void pause() {
        if (mExoPlayer != null) {
            mExoPlayer.setPlayWhenReady(false);
        }
    }

    public void resume() {
        if (mExoPlayer != null) {
            mExoPlayer.setPlayWhenReady(true);
        }
    }

    public void stop() {
        if (mExoPlayer != null) {
            mExoPlayer.release();
            mExoPlayer = null;
        }
        mWaitForSurface = false;
    }

    public void setDataSource(String uri) {
        mSource = new ExtractorMediaSource(Uri.parse(uri),
                mSourceFactory,
                new DefaultExtractorsFactory(), null, null);
    }

    private void play() {
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
        mExoPlayer.prepare(mSource);
        mExoPlayer.setPlayWhenReady(true);
    }

    public long getCurrentPosition() {
        if (mExoPlayer != null) {
            return mExoPlayer.getCurrentPosition();
        } else {
            return 0;
        }
    }

    public void setAudioVolume(float volume) {
        if (mExoPlayer != null) {
            mExoPlayer.setVolume(volume);
        }
    }

    public boolean isPrepared() {
        if (mExoPlayer != null) {
            int state = mExoPlayer.getPlaybackState();
            switch (state) {
                case ExoPlayer.STATE_READY:
                case ExoPlayer.STATE_BUFFERING:
                    return true;
            }
        }
        return false;
    }

    public boolean isBuffering() {
        if (mExoPlayer != null) {
            return mExoPlayer.getPlaybackState() == ExoPlayer.STATE_BUFFERING;
        }
        return false;
    }

}
