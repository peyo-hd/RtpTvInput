package com.peyo.rtptvinput;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

public class RtpTvInputSetupActivity extends Activity {
    private static final String TAG = "RtpTvInputSetupActivity";
	private String mInputId;
	private ContentResolver mResolver;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mInputId = getIntent().getStringExtra(TvInputInfo.EXTRA_INPUT_ID);
		if (mInputId != null) {
			Log.i(TAG, "onCreate() inputId : " + mInputId);
			mResolver = getContentResolver();
			clearChannels();
			insertChannels();
		}
		finish();
	}

    private void clearChannels() {
        Uri uri = TvContract.buildChannelsUriForInput(mInputId);
        int n = mResolver.delete(uri, null, null);
        Log.i(TAG, "clearChannels() " + n + " channels");
    }
    
    private void insertChannel(int num, String name) {
    	ContentValues values = new ContentValues();
    	values.put(TvContract.Channels.COLUMN_INPUT_ID, mInputId);
        values.put(TvContract.Channels.COLUMN_SERVICE_ID, num);
        values.put(TvContract.Channels.COLUMN_DISPLAY_NUMBER, num);
        values.put(TvContract.Channels.COLUMN_DISPLAY_NAME, name);
    	Uri uri = mResolver.insert(TvContract.Channels.CONTENT_URI, values);
    	Log.i(TAG, "insertChannel() " + uri);
    }
    
    private void insertChannels() {
        insertChannel(146, "ChannelA");
        insertChannel(147, "JTBC");
        insertChannel(149, "MBN");
        insertChannel(150, "YTV");
    }
}
