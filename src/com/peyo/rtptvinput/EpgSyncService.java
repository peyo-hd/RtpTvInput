package com.peyo.rtptvinput;

import android.net.Uri;

import com.google.android.media.tv.companionlibrary.EpgSyncJobService;
import com.google.android.media.tv.companionlibrary.model.Channel;
import com.google.android.media.tv.companionlibrary.model.Program;

import com.peyo.rtptvinput.XmlTvParser.TvListing;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.List;

public class EpgSyncService extends EpgSyncJobService {

    @Override
    public List<Channel> getChannels() {
        return getTvListing().getChannels();
    }

    @Override
    public List<Program> getProgramsForChannel(Uri channelUri, Channel channel, long startMs, long endMs) {
        return getTvListing().getPrograms(channel);
    }

    private TvListing getTvListing() {
        TvListing listing = null;
        try {
            URL epgXml = new URL(RtpTvInputSetupActivity.EPG_URL);
            listing = XmlTvParser.parse(new BufferedInputStream(epgXml.openStream()));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (XmlTvParser.XmlTvParseException e) {
            e.printStackTrace();
        }
        return listing;
    }
}
