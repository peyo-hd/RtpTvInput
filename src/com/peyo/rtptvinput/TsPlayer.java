package com.peyo.rtptvinput;

import android.content.Context;
import android.net.Uri;
import android.view.Surface;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.peyo.rtptvinput.source.TsDataSourceFactory;

public class TsPlayer {
    private final Context mContext;
    public ExoPlayer mExoPlayer;
    private Surface mSurface;
    private boolean mWaitForSurface;
    private MediaSource mSource;
    private Listener mListener;
    private TsDataSourceFactory mSourceFactory;
    public void setPlaybackParams(PlaybackParameters params) {
         if (mExoPlayer != null) {
            mExoPlayer.setPlaybackParameters(params);
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
        mSource = new ProgressiveMediaSource
                .Factory(mSourceFactory)
                .createMediaSource(MediaItem.fromUri(Uri.parse(uri)));
    }

    class TsLoadControl extends DefaultLoadControl{
        public TsLoadControl() {
            super(new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
                    DEFAULT_MIN_BUFFER_MS/5,
                    DEFAULT_MAX_BUFFER_MS,
                    DEFAULT_BUFFER_FOR_PLAYBACK_MS/2,
                    DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS/5,
                    DEFAULT_TARGET_BUFFER_BYTES,
                    DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS,
                    DEFAULT_BACK_BUFFER_DURATION_MS,
                    DEFAULT_RETAIN_BACK_BUFFER_FROM_KEYFRAME);
        }
    }
    private void play() {
        mExoPlayer = new ExoPlayer.Builder(mContext)
                .setTrackSelector(new DefaultTrackSelector(mContext))
                .setLoadControl(new TsLoadControl()).build();

        mExoPlayer.addListener(new Player.Listener() {
                                   @Override
                                   public void onRenderedFirstFrame() {
                                       if (mListener != null) {
                                           mListener.onPlayStarted();
                                       }
                                   }
                               }
        );

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


}
