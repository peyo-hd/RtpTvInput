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
	static {
		System.loadLibrary("rtp_sink");
	}

    private static final String TAG = "RtpTvInputService";
	private int mChannel = 0;
	private String mChannelName = null;
	
	@Override
	public Session onCreateSession(String inputId) {
		Log.i(TAG, "OnCreateSession() inputId : " + inputId);
		return new SessionImpl(this);
	}
	
	private native final void startSink(String address, int port, Surface surface);
	private native final void stopSink();

	private class SessionImpl extends TvInputService.Session {

		private String mAddress;
		private Surface mSurface;
		private boolean mStartPlay;
		Thread mThread;

		public SessionImpl(Context context) {
			super(context);
			notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);
			mStartPlay = false;
		}

		@Override
		public void onRelease() {
	    	stopVideo();
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
		    	stopSink();
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
			String[] projection = { TvContract.Channels.COLUMN_DISPLAY_NUMBER, TvContract.Channels.COLUMN_DISPLAY_NAME };
			Cursor cursor = getContentResolver().query(uri, projection,	null, null, null);
			if (cursor.getCount() > 0) {
				cursor.moveToFirst();
				mChannel = cursor.getInt(0);
				mChannelName = cursor.getString(1);
				Log.i(TAG, "changeChannel() to " + mChannel + " " + mChannelName);
			}
			return oldChannel != mChannel;
		}


		private void playVideo() {
			mAddress = "233.15.200.";
			mAddress += String.valueOf(mChannel);
			Log.i(TAG, "playVideo() : " + mAddress);
			stopVideo();
			mThread = new Thread(new Runnable(){
				@Override
				public void run() {
					startSink(mAddress, 5000, mSurface);
					Log.i(TAG, "startSink() thread stopped");
				}
			});
			mThread.start();
	        try {
				Thread.sleep(1200);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
        	notifyVideoAvailable();
			mStartPlay = false;
		}
		
		private void stopVideo() {
			if (mThread != null) {
				stopSink();
				try {
					mThread.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				Log.i(TAG, "stopVideo() thread joined");	
				mThread = null;
		        try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
}
