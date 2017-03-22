package com.peyo.rtptvinput;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.PlaybackParams;
import android.media.tv.TvContract;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputService;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Surface;

import com.peyo.rtptvinput.source.TsDataSourceFactory;
import com.peyo.rtptvinput.source.TsRecorder;
import com.peyo.rtptvinput.source.TsStreamWriter;

import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class RtpTvInputService extends TvInputService {
    private static final String TAG = "RtpTvInputService";

	private static final int DVR_CLEANUP_JOB_ID = 100;
	@Override
	public void onCreate() {
		super.onCreate();
		JobScheduler jobScheduler =
				(JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
		JobInfo pendingJob = jobScheduler.getPendingJob(DVR_CLEANUP_JOB_ID);
		if (pendingJob != null) {
			// storage cleaning job is already scheduled.
		} else {
			JobInfo job = new JobInfo.Builder(DVR_CLEANUP_JOB_ID,
					new ComponentName(this, DvrCleanUpService.class))
					.setPersisted(true)
					.setPeriodic(TimeUnit.MINUTES.toMillis(30))
					.build();
			jobScheduler.schedule(job);
		}
	}

	@Override
	public TvInputService.Session onCreateSession(String inputId) {
		Log.i(TAG, "OnCreateSession() inputId : " + inputId);
		return new Session(this);
	}

	private class Session extends TvInputService.Session implements
            TsPlayer.Listener, Handler.Callback {
		private final TsPlayer mPlayer;
		private int mServiceId = 0;
        private PlaybackParams mPlaybackParams;
        private Handler mHandler;

		public Session(Context context) {
			super(context);
			mPlayer = new TsPlayer(context, this);
			mPlayer.setDataSourceFactory(TsDataSourceFactory.createSourceFactory());
            mHandler = new Handler(this);
            mPlaybackParams = new PlaybackParams();
            mPlaybackParams.setSpeed(1);
		}

		@Override
		public boolean onSetSurface(Surface surface) {
			mPlayer.setSurface(surface);
			return true;
		}

		private boolean changeChannel(Uri uri) {
			int oldId = mServiceId;
			String[] projection = { TvContract.Channels.COLUMN_SERVICE_ID };
			Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
			if (cursor.getCount() > 0) {
				cursor.moveToFirst();
				mServiceId = cursor.getInt(0);
			}
			return oldId != mServiceId;
		}

		@Override
		public boolean onTune(Uri uri) {
			Log.i(TAG, "onTune() uri : " + uri);
			boolean changed = changeChannel(uri);
			if (changed) {
				notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);
				notifyTimeShiftStatusChanged(TvInputManager.TIME_SHIFT_STATUS_UNAVAILABLE);
				mPlayer.stop();
				mPlayer.setDataSource(getRtpAddress(mServiceId));
                mPlayer.setAudioVolume(1);
				mPlayer.start();
			}
			return true;
		}

        private long mBufferStartTimeMs;
		private boolean mRecorded = false;
        private long mRecordedDurationMs;


		@Override
		public void onTimeShiftPlay(Uri uri) {
			Log.i(TAG, "onTimeShiftPlay() uri : " + uri);

			String[] projection = { TvContract.RecordedPrograms.COLUMN_RECORDING_DATA_URI,
                    TvContract.RecordedPrograms.COLUMN_RECORDING_DURATION_MILLIS };
			Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
			if (cursor.getCount() > 0) {
				cursor.moveToFirst();
				notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);
				notifyTimeShiftStatusChanged(TvInputManager.TIME_SHIFT_STATUS_UNAVAILABLE);
				mPlayer.stop();
				mPlayer.setDataSource(cursor.getString(0));
                mRecorded  = true;
                mRecordedDurationMs = cursor.getLong(1);
                mPlayer.setAudioVolume(1);
				mPlayer.start();
			}
		}

		@Override
		public void onPlayStarted() {
            if (mTrickPlayTargetMs != 0) return;
            Log.i(TAG, "onPlayStarted()");
			mBufferStartTimeMs = mRecorded ? 0 : System.currentTimeMillis();
			notifyVideoAvailable();
			notifyTimeShiftStatusChanged(TvInputManager.TIME_SHIFT_STATUS_AVAILABLE);
		}

		@Override
		public void onTimeShiftPause() {
			Log.i(TAG, "TimeShiftPause");
            mHandler.removeMessages(MSG_TRICKPLAY);
            mPlayer.pause();
		}

		@Override
		public void onTimeShiftResume() {
			Log.i(TAG, "TimeShiftResume");
            mHandler.removeMessages(MSG_TRICKPLAY);
			mPlayer.setPlaybackParams(new PlaybackParams().setSpeed(1));
            mPlayer.setAudioVolume(1);
			mPlayer.resume();
		}

		@Override
		public void onTimeShiftSeekTo(long timeMs) {
            mHandler.removeMessages(MSG_TRICKPLAY);
			mPlayer.seekTo(timeMs - mBufferStartTimeMs);
		}

		@Override
		public void onTimeShiftSetPlaybackParams(PlaybackParams params) {
            mPlaybackParams = params;
			float speed = params.getSpeed();
            Log.i(TAG, "SetPlaybackParams " + speed);
			if (1 <= speed && speed <= 2) {
				mPlayer.setPlaybackParams(params);
                mPlayer.setAudioVolume(1);
				mPlayer.resume();
			} else if (2 < speed) {
                if (!mHandler.hasMessages(MSG_TRICKPLAY)) {
                    mPlayer.setAudioVolume(0);
                    //mPlayer.pause();
                    mTrickPlayTargetMs = mPlayer.getCurrentPosition()
                            + (int) speed * getTrickPlaySeekIntervalMs() ;
                    mHandler.sendEmptyMessage(MSG_TRICKPLAY);
                }
            }
		}

        private volatile long mTrickPlayTargetMs = 0;

        private static final int EXPECTED_KEY_FRAME_INTERVAL_MS = 1000;
        private static final int MIN_TRICKPLAY_SEEK_INTERVAL_MS = 400;

        private int getTrickPlaySeekIntervalMs() {
            return Math.max(EXPECTED_KEY_FRAME_INTERVAL_MS / (int) Math.abs(mPlaybackParams.getSpeed()),
                    MIN_TRICKPLAY_SEEK_INTERVAL_MS);
        }

        private void doTrickPlayBySeek() {
            mHandler.removeMessages(MSG_TRICKPLAY);
            float speed = mPlaybackParams.getSpeed();
            if (speed == 1 || !mPlayer.isPrepared()) {
                return;
            }
            if ( mTrickPlayTargetMs > (mRecorded ? mRecordedDurationMs :
                    (System.currentTimeMillis() - mBufferStartTimeMs))) {
                mPlaybackParams.setSpeed(1);
                mPlayer.setAudioVolume(1);
                mTrickPlayTargetMs = 0;
                return;
            }
            long delayForNextSeek = getTrickPlaySeekIntervalMs();
                Log.i(TAG, "TrickPlay SeekTo " + mTrickPlayTargetMs);
                //mPlayer.pause();
                mPlayer.seekTo(mTrickPlayTargetMs);
                mPlayer.resume();
            mTrickPlayTargetMs += speed * delayForNextSeek;
            mHandler.sendEmptyMessageDelayed(MSG_TRICKPLAY, delayForNextSeek);
        }

        private static final int MSG_TRICKPLAY = 100;
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_TRICKPLAY:
                    doTrickPlayBySeek();
                    return true;
            }
            return false;
        }

		@Override
		public long onTimeShiftGetStartPosition() {
			return mBufferStartTimeMs;
		}

		@Override
		public long onTimeShiftGetCurrentPosition() {
            if (mTrickPlayTargetMs != 0) return mTrickPlayTargetMs;

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

	private String getRtpAddress(int serviceId) {
		return RtpTvInputSetupActivity.MULTICAST_ADDR
				+ String.valueOf((serviceId >> 8) & 255) + "."
				+ String.valueOf(serviceId & 255)
				+ RtpTvInputSetupActivity.MULTICAST_PORT;
	}

	@Override
	public TvInputService.RecordingSession onCreateRecordingSession(String inputId) {
		return new RecordingSession(this, inputId);
	}

	private class RecordingSession extends TvInputService.RecordingSession {
		private final TsStreamWriter mTsStreamWriter;
		private final TsRecorder mTsRecorder;

		private String mInputId;

		private int mServiceId = 0;
		private String mChannelName;
		private String mProgramTitle;
		private long mStartTimeMs;
		private long mStopTimeMs;


		public RecordingSession(Context context, String inputId) {
			super(context);
			mInputId = inputId;

			mTsStreamWriter = new TsStreamWriter();
			mTsRecorder = new TsRecorder(mTsStreamWriter);
		}

		@Override
		public void onTune(Uri uri) {
			Log.i(TAG, "RecordingSession.onTune()");
			getChannelServiceId(uri);
			mTsStreamWriter.setChannelName(mChannelName);
			notifyTuned(uri);
		}

		private void getChannelServiceId(Uri channelUri) {
			String[] projection = { TvContract.Channels.COLUMN_SERVICE_ID,
					TvContract.Channels.COLUMN_DISPLAY_NAME };
			Cursor cursor = getContentResolver().query(channelUri, projection, null, null, null);
			if (cursor.getCount() > 0) {
				cursor.moveToFirst();
				mServiceId = cursor.getInt(0);
				mChannelName = cursor.getString(1);
			}
		}

		private void getProgramTitle(@Nullable Uri programUri) {
			if (programUri == null) return;

			String[] projection = { TvContract.Programs.COLUMN_TITLE };
			Cursor cursor = getContentResolver().query(programUri, projection, null, null, null);
			if (cursor.getCount() > 0) {
				cursor.moveToFirst();
				mProgramTitle = cursor.getString(0);
			}
		}

		@Override
		public void onStartRecording(@Nullable Uri programUri) {
			getProgramTitle(programUri);

			mTsStreamWriter.setProgramTitle(mProgramTitle);
			mTsStreamWriter.openFile();

			mTsRecorder.startRecording(getRtpAddress(mServiceId));
			mStartTimeMs = System.currentTimeMillis();

			Log.i(TAG, "onStartRecording() " + DateFormat.getTimeInstance().format(new Date(mStartTimeMs)));
		}

		@Override
		public void onStopRecording() {
			mTsRecorder.stopRecording();
			mTsStreamWriter.closeFile();

			mStopTimeMs = System.currentTimeMillis();
			Log.i(TAG, "onStopRecording() " + DateFormat.getTimeInstance().format(new Date(mStopTimeMs)));

			notifyRecordingStopped(insertRecordedPorgram());
		}

		private Uri insertRecordedPorgram() {
			ContentValues values = new ContentValues();
			values.put(TvContract.RecordedPrograms.COLUMN_INPUT_ID, mInputId);
			values.put(TvContract.RecordedPrograms.COLUMN_TITLE, mProgramTitle != null ?
					mProgramTitle : mChannelName );
			values.put(TvContract.RecordedPrograms.COLUMN_START_TIME_UTC_MILLIS, mStartTimeMs);
			values.put(TvContract.RecordedPrograms.COLUMN_END_TIME_UTC_MILLIS, mStopTimeMs);
			values.put(TvContract.RecordedPrograms.COLUMN_RECORDING_DURATION_MILLIS,
					mStopTimeMs - mStartTimeMs);
			values.put(TvContract.RecordedPrograms.COLUMN_RECORDING_DATA_URI,
					mTsStreamWriter.getFileUri().toString());
			return getContentResolver().insert(TvContract.RecordedPrograms.CONTENT_URI, values);
		}

		@Override
		public void onRelease() {
        }

	}

}
