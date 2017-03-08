package com.peyo.rtptvinput;

import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputService;
import android.net.Uri;
import android.util.Log;
import android.view.Surface;

public class RtpTvInputService extends TvInputService {
    private static final String TAG = "RtpTvInputService";

	@Override
	public TvInputService.Session onCreateSession(String inputId) {
		Log.i(TAG, "OnCreateSession() inputId : " + inputId);
		return new Session(this);
	}

	private class Session extends TvInputService.Session {
		private final TsPlayer mPlayer;
		private int mChannel;

		public Session(Context context) {
			super(context);
			notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);
			mChannel = 0;
			mPlayer = new TsPlayer(context);
		}

		@Override
		public boolean onSetSurface(Surface surface) {
			mPlayer.setSurface(surface);
			return true;
		}

		@Override
		public boolean onTune(Uri uri) {
			Log.i(TAG, "onTune() uri : " + uri);
			boolean changed = changeChannel(uri);
			if (changed) {
				notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);
				mPlayer.stop();
				mPlayer.setListener(new TsPlayer.Listener() {
					@Override
					public void onPlayStarted() {
						notifyVideoAvailable();
					}
				});

				String addr = "udp://233.15.200.";
				addr += String.valueOf(mChannel) + ":5000";
				Log.i(TAG, "startRtp() " + addr);
				mPlayer.startRtp(Uri.parse(addr));
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
			}
			return oldChannel != mChannel;
		}

		@Override
		public void onRelease() {
			mPlayer.stop();
			mPlayer.setListener(null);
		}

		@Override
		public void onSetCaptionEnabled(boolean enabled) {}

		@Override
		public void onSetStreamVolume(float volume) {}
	}
}
