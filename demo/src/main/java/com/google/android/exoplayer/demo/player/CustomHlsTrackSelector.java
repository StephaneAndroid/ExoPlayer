package com.google.android.exoplayer.demo.player;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;

import com.google.android.exoplayer.chunk.VideoFormatSelectorUtil;
import com.google.android.exoplayer.hls.HlsMasterPlaylist;
import com.google.android.exoplayer.hls.HlsTrackSelector;
import com.google.android.exoplayer.hls.Variant;

import java.io.IOException;
import java.util.ArrayList;

public class CustomHlsTrackSelector implements HlsTrackSelector {

    private static final int WIFI_MIN_INITIAL_BITRATE = 1000000;

    private final Context context;

    /**
     * Creates a {@link CustomHlsTrackSelector} that selects the streams defined in the playlist.
     *
     * @param context A context.
     * @return The selector instance.
     */
    public static CustomHlsTrackSelector newCustomHlsTrackSelector(Context context) {
        return new CustomHlsTrackSelector(context);
    }

    private CustomHlsTrackSelector(Context context) {
        this.context = context;
    }

    @Override
    public void selectTracks(HlsMasterPlaylist playlist, Output output) throws IOException {

        ArrayList<Variant> enabledVariantList = new ArrayList<>();
        int[] variantIndices = VideoFormatSelectorUtil.selectVideoFormatsForDefaultDisplay(
                context, playlist.variants, null, false);
        for (int i = 0; i < variantIndices.length; i++) {
            enabledVariantList.add(playlist.variants.get(variantIndices[i]));
        }

        ArrayList<Variant> definiteVideoVariants = new ArrayList<>();
        ArrayList<Variant> definiteAudioOnlyVariants = new ArrayList<>();
        for (int i = 0; i < enabledVariantList.size(); i++) {
            Variant variant = enabledVariantList.get(i);
            if (variant.format.height > 0 || variantHasExplicitCodecWithPrefix(variant, "avc")) {
                definiteVideoVariants.add(variant);
            } else if (variantHasExplicitCodecWithPrefix(variant, "mp4a")) {
                definiteAudioOnlyVariants.add(variant);
            }
        }

        if (!definiteVideoVariants.isEmpty()) {
            // We've identified some variants as definitely containing video. Assume variants within the
            // master playlist are marked consistently, and hence that we have the full set. Filter out
            // any other variants, which are likely to be audio only.
            enabledVariantList = definiteVideoVariants;
        } else if (definiteAudioOnlyVariants.size() < enabledVariantList.size()) {
            // We've identified some variants, but not all, as being audio only. Filter them out to leave
            // the remaining variants, which are likely to contain video.
            enabledVariantList.removeAll(definiteAudioOnlyVariants);
        } else {
            // Leave the enabled variants unchanged. They're likely either all video or all audio.
        }

        if (enabledVariantList.size() > 1) {
            Variant[] enabledVariants = new Variant[enabledVariantList.size()];
            enabledVariantList.toArray(enabledVariants);
            output.adaptiveTrack(playlist, enabledVariants, getDefaultVariantsIndex(enabledVariants));
        }
        for (int i = 0; i < enabledVariantList.size(); i++) {
            output.fixedTrack(playlist, enabledVariantList.get(i));
        }
    }

    public int getDefaultVariantsIndex(Variant[] variants) {
        final int minInitialBitrate = getMinInitialBitrate();
        for (int i = 0; i < variants.length; i++) {
            final Variant variant = variants[i];
            if (variant.format.bitrate > minInitialBitrate) {
                return i;
            }
        }
        return 0;
    }

    private static boolean variantHasExplicitCodecWithPrefix(Variant variant, String prefix) {
        String codecs = variant.format.codecs;
        if (TextUtils.isEmpty(codecs)) {
            return false;
        }
        String[] codecArray = codecs.split("(\\s*,\\s*)|(\\s*$)");
        for (int i = 0; i < codecArray.length; i++) {
            if (codecArray[i].startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private int getMinInitialBitrate() {
        return isWifiConnected() ? WIFI_MIN_INITIAL_BITRATE : 0;
    }

    private boolean isWifiConnected() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifiNetwork = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return wifiNetwork != null && wifiNetwork.isConnected();
    }

}
