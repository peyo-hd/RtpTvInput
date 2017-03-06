package com.peyo.rtptvinput;

import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputService;
import android.net.Uri;
import android.util.Log;
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

public class RtpTvInputService extends TvInputService {
    private static final String TAG = "RtpTvInputService";

	@Override
	public Session onCreateSession(String inputId) {
		Log.i(TAG, "OnCreateSession() inputId : " + inputId);
		return new SessionImpl(this);
	}

	private class SessionImpl extends TvInputService.Session {
		private SimpleExoPlayer mExoPlayer;
		private int mChannel;
		private Surface mSurface;
		private boolean mStartPlay;

		public SessionImpl(Context context) {
			super(context);
			notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);
			mChannel = 0;
			mStartPlay = false;
		}

		@Override
		public void onRelease() {
	    	stopVideo();
		}

		private void stopVideo() {
			if (mExoPlayer != null) {
				mExoPlayer.release();
				mExoPlayer = null;
			}
		}

		@Override
		public void onSetCaptionEnabled(boolean enabled) {
			Log.d(TAG, "onSetCaptionEnabled(" + enabled + ")");
		}

		@Override
		public void onSetStreamVolume(float volume) {
			Log.i(TAG, "onSetStreamVolume(" + volume + ")");
		}

		@Override
		public boolean onSetSurface(Surface surface) {
			Log.i(TAG, "onSetSurface(" + surface + ")");
			mSurface = surface;
			if (mSurface != null && mStartPlay) {
				playVideo();
			} else {
				if (mExoPlayer != null) mStartPlay = true;
				stopVideo();
			}
			return true;
		}

		@Override
		public boolean onTune(Uri uri) {
			Log.i(TAG, "onTune() uri : " + uri);
			boolean changed = changeChannel(uri);
			if (changed) {
				notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);
				stopVideo();
			}
			if (mSurface != null) { 
				playVideo();
			} else {
				mStartPlay = true;
			}
			return true;
		}

		private boolean changeChannel(Uri uri) {
			int oldChannel = mChannel;
			String[] projection = { TvContract.Channels.COLUMN_SERVICE_ID};
			Cursor cursor = getContentResolver().query(uri, projection,	null, null, null);
			if (cursor.getCount() > 0) {
				cursor.moveToFirst();
				mChannel = cursor.getInt(0);
				Log.i(TAG, "changeChannel() to " + mChannel);
			}
			return oldChannel != mChannel;
		}

		private void playVideo() {
			String addr = "udp://233.15.200.";
			addr += String.valueOf(mChannel) + ":5000";
			Log.i(TAG, "playVideo() : " + addr);

			mExoPlayer = ExoPlayerFactory.newSimpleInstance(getBaseContext(),
					new DefaultTrackSelector(null),
					new DefaultLoadControl(
							new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
							DEFAULT_MIN_BUFFER_MS/5, DEFAULT_MAX_BUFFER_MS/5,
							DEFAULT_BUFFER_FOR_PLAYBACK_MS/5,
							DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS/5), null);

			mExoPlayer.setVideoSurface(mSurface);
			MediaSource source = new ExtractorMediaSource(Uri.parse(addr),
					new DataSource.Factory() {
						public DataSource createDataSource() {
							return new RtpDataSource(); } },
					new DefaultExtractorsFactory(), null, null);
			mExoPlayer.prepare(source);
			mExoPlayer.setPlayWhenReady(true);

			mStartPlay = false;
			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						Thread.sleep(1300);
					} catch (InterruptedException e) {
					}
					notifyVideoAvailable();
				}
			}).run();
		}
	}
}
