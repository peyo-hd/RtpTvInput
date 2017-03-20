package com.peyo.rtptvinput.source;

import android.media.tv.TvInputService;
import android.net.Uri;
import android.util.Log;

import com.google.android.exoplayer2.upstream.DataSpec;

import java.io.IOException;

public class TsRecorder {
    private static final String TAG = "TsRecorder";

    private final TsStreamWriter mTsStreamWriter;
    private boolean mRecording;
    private TsDataSource mTsSource;
    private RecordingThread mRecordingThread;

    public TsRecorder(TsStreamWriter writer) {
        mTsStreamWriter = writer;
        mTsSource = (TsDataSource) TsDataSourceFactory.createSourceFactory().createDataSource();
    }

    public void startRecording(String addr) {
        try {
            mTsSource.open(new DataSpec(Uri.parse(addr)));
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        mTsSource.shiftStartPosition(mTsSource.getBufferedPosition());

        mRecording = true;
        mRecordingThread = new RecordingThread();
        mRecordingThread.start();
        Log.i(TAG, "Recording started");
    }

    private class RecordingThread extends Thread {
        @Override
        public void run() {
            byte[] dataBuffer = new byte[188 * 7];
            while (true) {
                if (!mRecording) {
                    break;
                }

                int bytesWritten;
                try {
                    bytesWritten = mTsSource.read(dataBuffer, 0, dataBuffer.length);
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
                //Log.i(TAG, "RecoridngThread got " + bytesWritten);
                if (mTsStreamWriter != null) {
                    mTsStreamWriter.writeToFile(dataBuffer, bytesWritten);
                }
            }
            Log.i(TAG, "Recording stopped");
        }
    }

    public void stopRecording() {
        mRecording = false;
        try {
            if (mRecordingThread != null) {
                mRecordingThread.join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            mTsSource.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
