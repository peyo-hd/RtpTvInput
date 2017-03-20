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
 * limitations under the License.
 */

package com.peyo.rtptvinput.source;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import com.peyo.rtptvinput.EpgSyncService;
import com.peyo.rtptvinput.RtpTvInputSetupActivity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Stores TS files to the disk
 */
public class TsStreamWriter {
    private static final String TAG = "TsStreamWriter";
    private static final boolean DEBUG = true;

    private static final int NO_INSTANCE_ID = 0;
    private static final int MAX_GET_ID_RETRY_COUNT = 5;
    private static final int MAX_INSTANCE_ID = 1000;
    private static final String SEPARATOR = "_";

    private FileOutputStream mFileOutputStream;
    private long mFileStartTimeMs;
    private String mFileName = null;
    private final String mDirectoryPath;
    private final File mDirectory;
    private Uri mFileUri;
    private final int mInstanceId;

    private String mChannel;
    private String mProgramTitle;

    public TsStreamWriter() {
        File externalFilesDir = Environment.getExternalStorageDirectory();
        if (externalFilesDir == null || !externalFilesDir.isDirectory()) {
            mDirectoryPath = null;
            mDirectory = null;
            mInstanceId = NO_INSTANCE_ID;
            if (DEBUG) {
                Log.w(TAG, "Fail to get external files dir!");
            }
        } else {
            mDirectoryPath = externalFilesDir.getPath() + RtpTvInputSetupActivity.DVR_DIR;
            mDirectory = new File(mDirectoryPath);
            if (!mDirectory.exists()) {
                boolean madeDir = mDirectory.mkdir();
                if (!madeDir) {
                    Log.w(TAG, "Error. Fail to create folder!");
                }
            }
            mInstanceId = generateInstanceId();
        }
    }

    /**
     * Sets the current channel.
     *
     * @param channel curren channel of the stream
     */
    public void setChannelName(String channel) {
        mChannel = channel;
    }

    /**
     * Opens a file to store TS data.
     */
    public void openFile() {
        if (mChannel == null || mDirectoryPath == null) {
            return;
        }
        mFileStartTimeMs = System.currentTimeMillis();
        String date = new SimpleDateFormat("yyyy_MMdd_HHmm").format(new Date(mFileStartTimeMs));
        mFileName = mChannel + getTitleSeparator() + date + SEPARATOR + mInstanceId + ".ts";
        String filePath = mDirectoryPath + "/" + mFileName;
        try {
            mFileOutputStream = new FileOutputStream(filePath, false);
        } catch (FileNotFoundException e) {
            Log.w(TAG, "Cannot open file: " + filePath, e);
        }
        mFileUri = Uri.parse("file://" + filePath);
    }

    private String getTitleSeparator() {
        if (mProgramTitle == null) {
            return SEPARATOR;
        } else {
            return SEPARATOR + mProgramTitle + SEPARATOR;
        }
    }

    public Uri getFileUri() {
        return mFileUri;
    }

    /**
     * Closes the file and stops storing TS data.
     */
    public void closeFile() {
        if (mFileOutputStream == null) {
            return;
        }
        try {
            mFileOutputStream.close();
            mFileName = null;
            mFileOutputStream = null;
        } catch (IOException e) {
            Log.w(TAG, "Error on closing file.", e);
        }
    }

    /**
     * Writes the data to the file.
     *
     * @param buffer the data to be written
     * @param bytesWritten number of bytes written
     */
    public void writeToFile(byte[] buffer, int bytesWritten) {
        if (mFileOutputStream == null) {
            return;
        }
        try {
            mFileOutputStream.write(buffer, 0, bytesWritten);
        } catch (IOException e) {
            Log.w(TAG, "Error on writing TS stream.", e);
        }
    }

    /**
     * Generates a unique instance ID.
     *
     * @return a unique instance ID
     */
    private int generateInstanceId() {
        if (mDirectory == null) {
            return NO_INSTANCE_ID;
        }
        Set<Integer> idSet = getExistingIds();
        if (idSet == null) {
            return  NO_INSTANCE_ID;
        }
        for (int i = 0; i < MAX_GET_ID_RETRY_COUNT; i++) {
            // Range [1, MAX_INSTANCE_ID]
            int id = (int)Math.floor(Math.random() * MAX_INSTANCE_ID) + 1;
            if (!idSet.contains(id)) {
                return id;
            }
        }
        return NO_INSTANCE_ID;
    }

    /**
     * Gets all existing instance IDs.
     *
     * @return a set of all existing instance IDs
     */
    private Set<Integer> getExistingIds() {
        if (mDirectory == null || !mDirectory.isDirectory()) {
            return null;
        }

        Set<Integer> idSet = new HashSet<>();
        for (File file : mDirectory.listFiles()) {
            int id = getFileId(file);
            if(id != NO_INSTANCE_ID) {
                idSet.add(id);
            }
        }
        return idSet;
    }

    /**
     * Gets the instance ID of a given file.
     *
     * @param file the file whose TsStreamWriter ID is returned
     * @return the TsStreamWriter ID of the file or NO_INSTANCE_ID if not available
     */
    private static int getFileId(File file) {
        if (file == null || !file.isFile()) {
            return NO_INSTANCE_ID;
        }
        String fileName = file.getName();
        int lastSeparator = fileName.lastIndexOf(SEPARATOR);
        if (!fileName.endsWith(".ts") || lastSeparator == -1) {
            return NO_INSTANCE_ID;
        }
        try {
            return Integer.parseInt(fileName.substring(lastSeparator + 1, fileName.length() - 3));
        } catch (NumberFormatException e) {
            if (DEBUG) {
                Log.e(TAG, fileName + " is not a valid file name.");
            }
        }
        return NO_INSTANCE_ID;
    }

    public void setProgramTitle(String programTitle) {
        mProgramTitle = programTitle;
    }
}
