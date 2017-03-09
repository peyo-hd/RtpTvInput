package com.peyo.rtptvinput;

import android.content.Context;
import android.database.Cursor;
import android.media.PlaybackParams;
import android.media.tv.TvContract;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputService;
import android.net.Uri;
import android.util.Log;
import android.view.Surface;


import com.peyo.rtptvinput.source.TsDataSourceFactory;

import java.text.DateFormat;
import java.util.Date;

public class RtpTvInputService extends TvInputService {
    private static final String TAG = "RtpTvInputService";

	@Override
	public TvInputService.Session onCreateSession(String inputId) {
		Log.i(TAG, "OnCreateSession() inputId : " + inputId);
		return new Session(this);
	}

	private class Session extends TvInputService.Session implements TsPlayer.Listener {
		private final TsPlayer mPlayer;
		private int mChannel = 0;

		public Session(Context context) {
			super(context);
			mPlayer = new TsPlayer(context, this);
			mPlayer.setDataSourceFactory(TsDataSourceFactory.createSourceFactory());
		}

		@Override
		public boolean onSetSurface(Surface surface) {
			mPlayer.setSurface(surface);
			return true;
		}

		private boolean changeChannel(Uri uri) {
			int oldChannel = mChannel;
			String[] projection = { TvContract.Channels.COLUMN_SERVICE_ID};
			Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
			if (cursor.getCount() > 0) {
				cursor.moveToFirst();
				mChannel = cursor.getInt(0);
			}
			return oldChannel != mChannel;
		}

		@Override
		public boolean onTune(Uri uri) {
			Log.i(TAG, "onTune() uri : " + uri);
			boolean changed = changeChannel(uri);
			if (changed) {
				notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);
				notifyTimeShiftStatusChanged(TvInputManager.TIME_SHIFT_STATUS_UNAVAILABLE);
				mPlayer.stop();

				String addr = EpgSyncService.MULTICAST_ADDR
						+ String.valueOf(mChannel)
						+ EpgSyncService.MULTICAST_PORT;
				mPlayer.setRtpSource(addr);

				Log.i(TAG, "startRtp() " + addr);
				mPlayer.start();
			}
			return true;
		}

		@Override
		public void onPlayStarted() {
			mBufferStartTimeMs = System.currentTimeMillis();
			Log.i(TAG, "onPlayStarted() " +
					DateFormat.getTimeInstance().format(new Date(mBufferStartTimeMs)));
			notifyVideoAvailable();
			notifyTimeShiftStatusChanged(TvInputManager.TIME_SHIFT_STATUS_AVAILABLE);
		}

		private volatile long mBufferStartTimeMs;

		@Override
		public void onTimeShiftPause() {
			if (mPlayer != null) {
				Log.i(TAG, "TimeShiftPause");
				mPlayer.pause();
			}
		}

		@Override
		public void onTimeShiftResume() {
			if (mPlayer != null) {
				Log.i(TAG, "TimeShiftResume");
				mPlayer.setPlaybackParams(new PlaybackParams().setSpeed(1));
				mPlayer.resume();
			}
		}

		@Override
		public void onTimeShiftSeekTo(long timeMs) {
			Log.i(TAG, "SeekTo " +
					DateFormat.getTimeInstance().format(new Date(timeMs)));
			mPlayer.seekTo(timeMs - mBufferStartTimeMs);
		}

		@Override
		public void onTimeShiftSetPlaybackParams(PlaybackParams params) {
			float speed = params.getSpeed();
			if (mPlayer != null && (speed == 1.0f || speed == 2.0f) ) {
				Log.i(TAG, "SetPlaybackParams " + params.getSpeed());
				mPlayer.setPlaybackParams(params);
				mPlayer.resume();
			}
		}

		@Override
		public long onTimeShiftGetStartPosition() {
			return mBufferStartTimeMs;
		}

		@Override
		public long onTimeShiftGetCurrentPosition() {
			long currentTimeMs = mBufferStartTimeMs;

			if (mPlayer != null) {
				currentTimeMs += mPlayer.getCurrentPosition();
			}
			//Log.i(TAG, "GetCurrentPosition " + DateFormat.getTimeInstance().format(new Date(currentTimeMs)));
			return currentTimeMs;
		}

		@Override
		public void onRelease() {
			mPlayer.stop();
		}

		@Override
		public void onSetCaptionEnabled(boolean enabled) {}

		@Override
		public void onSetStreamVolume(float volume) {}

	}

}
