/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.peyo.rtptvinput;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Creates {@link JobService} to clean up recorded program files which are not referenced
 * from database.
 */
public class DvrCleanUpService extends JobService {
    private CleanUpStorageTask mTask;

    @Override
    public void onCreate() {
        super.onCreate();
        mTask = new CleanUpStorageTask(this, this);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        mTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

    /**
     * Cleans up recorded program files which are not referenced from database.
     * Cleaning up will be done periodically.
     */
    public static class CleanUpStorageTask extends AsyncTask<JobParameters, Void, JobParameters[]> {
        private final static long ELAPSED_MILLIS_TO_DELETE = TimeUnit.MINUTES.toMillis(10);

        private final Context mContext;
        private final JobService mJobService;
        private final ContentResolver mContentResolver;

        /**
         * Creates a recurring storage cleaning task.
         *
         * @param context {@link Context}
         * @param jobService {@link JobService}
         */
        public CleanUpStorageTask(Context context, JobService jobService) {
            mContext = context;
            mJobService = jobService;
            mContentResolver = mContext.getContentResolver();
        }

        private Set<String> getRecordedPrograms() {
            String[] projection = { TvContract.RecordedPrograms.COLUMN_RECORDING_DATA_URI };
            try (Cursor c = mContentResolver.query(
                    TvContract.RecordedPrograms.CONTENT_URI, projection, null, null, null)) {
                if (c == null) {
                    return null;
                }
                Set<String> recordedPrograms = new HashSet<>();
                while (c.moveToNext()) {
                    String dataUriString = c.getString(0);
                    if (dataUriString == null) {
                        continue;
                    }
                    Uri dataUri = Uri.parse(dataUriString);
                    File recordedProgram = new File(dataUri.getPath());
                    try {
                        recordedPrograms.add(recordedProgram.getCanonicalPath());
                    } catch (IOException | SecurityException e) {
                    }
                }
                return recordedPrograms;
            }
        }

        private String getRecordingRootDataDirectory() {
            File externalFilesDir = Environment.getExternalStorageDirectory();
            if (externalFilesDir == null || !externalFilesDir.isDirectory()) {
                return null;
            } else {
                return externalFilesDir.getPath() + RtpTvInputSetupActivity.DVR_DIR;
            }
        }

        @Override
        protected JobParameters[] doInBackground(JobParameters... params) {
            File dvrRecordingDir = new File(getRecordingRootDataDirectory());
            if (dvrRecordingDir == null || !dvrRecordingDir.isDirectory()) {
                return params;
            }
            Set<String> recordedPrograms = getRecordedPrograms();
            if (recordedPrograms == null) {
                return params;
            }
            File[] files = dvrRecordingDir.listFiles();
            if (files == null || files.length == 0) {
                return params;
            }
            for (File recording : files) {
                try {
                    if (!recordedPrograms.contains(recording.getCanonicalPath())) {
                        long lastModified = recording.lastModified();
                        long now = System.currentTimeMillis();
                        if (lastModified != 0
                                && lastModified < now - ELAPSED_MILLIS_TO_DELETE) {
                            // To prevent current recordings from being deleted,
                            // deletes recordings which was not modified for long enough time.
                            recording.delete();
                        }
                    }
                } catch (IOException | SecurityException e) {
                    // would not happen
                }
            }
            return params;
        }

        @Override
        protected void onPostExecute(JobParameters[] params) {
            for (JobParameters param : params) {
                mJobService.jobFinished(param, false);
            }
        }
    }
}
