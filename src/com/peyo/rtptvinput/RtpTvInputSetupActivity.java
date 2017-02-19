package com.peyo.rtptvinput;

import android.app.Activity;
import android.content.ComponentName;
import android.media.tv.TvInputInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.google.android.media.tv.companionlibrary.ChannelSetupFragment;
import com.google.android.media.tv.companionlibrary.EpgSyncJobService;

public class RtpTvInputSetupActivity extends Activity {
	private static final String TAG = "RtpTvInputSetupActivity";
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_setup);
	}

	public static class SetupFragment extends ChannelSetupFragment {
		private String mInputId;
		private Button mButton;

		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			mInputId = getActivity().getIntent().getStringExtra(TvInputInfo.EXTRA_INPUT_ID);
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
			View view = super.onCreateView(inflater, container, savedInstanceState);
			setChannelListVisibility(true);
			mButton = (Button) view.findViewById(com.google.android.media.tv.companionlibrary.R.id.tune_cancel);
			mButton.setVisibility(View.GONE);
			return view;
		}

		private static final int EPG_DURATION_MILLIS = 1000 * 60 * 60 * 24 * 7; // 1 Week
		@Override
		public void onScanStarted() {
			if (mInputId == null) {
				getActivity().finish();
			} else {
				Log.i(TAG, "onScanStarted");
				EpgSyncJobService.cancelAllSyncRequests(getActivity());
				EpgSyncJobService.requestImmediateSync(getActivity(), mInputId, EPG_DURATION_MILLIS,
						new ComponentName(getActivity(), EpgSyncService.class));
			}
		}

		@Override
		public String getInputId() {
			return mInputId;
		}

		@Override
		public void onScanFinished() {
			Log.i(TAG, "onScanStopped");
			EpgSyncJobService.cancelAllSyncRequests(getActivity());
			getActivity().setResult(Activity.RESULT_OK);
			finishAfter(3);
		}

		private void finishAfter(final int sec) {
			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						Thread.sleep(sec * 1000);
					} catch (InterruptedException e) {
					}
					getActivity().finish();
				}
			}).run();
		}
	}
}
