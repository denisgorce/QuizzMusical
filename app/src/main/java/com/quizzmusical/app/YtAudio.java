package com.quizzmusical.app;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

/** Recherche d'une chanson et extraction du flux audio direct (NewPipeExtractor), sans interface ni pub YouTube. */
public final class YtAudio {

    public interface ListCb { void ok(List<String> videoUrls); void err(String msg); }
    public interface UrlCb { void ok(String audioUrl); void err(String msg); }

    private static final ExecutorService EX = Executors.newSingleThreadExecutor();
    private static volatile boolean init;

    private YtAudio() {}

    private static synchronized void ensureInit() {
        if (init) return;
        NewPipe.init(new OkDownloader(new OkHttpClient()), new Localization("fr", "FR"), new ContentCountry("FR"));
        init = true;
    }

    private static SearchQueryHandler query(StreamingService yt, String q, boolean music) throws Exception {
        if (music) {
            try {
                return yt.getSearchQHFactory().fromQuery(q, Collections.singletonList(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS), "");
            } catch (Exception ignored) { /* repli recherche standard */ }
        }
        return yt.getSearchQHFactory().fromQuery(q);
    }

    /** Liste d'URL de vidéos candidates (chansons YouTube Music d'abord, puis recherche standard). */
    public static void search(String q, ListCb cb) {
        EX.execute(() -> {
            try {
                ensureInit();
                StreamingService yt = ServiceList.YouTube;
                List<String> out = new ArrayList<>();
                for (boolean music : new boolean[]{true, false}) {
                    try {
                        SearchInfo info = SearchInfo.getInfo(yt, query(yt, q, music));
                        for (InfoItem it : info.getRelatedItems()) {
                            if (it instanceof StreamInfoItem && !out.contains(it.getUrl())) out.add(it.getUrl());
                            if (out.size() >= 6) break;
                        }
                    } catch (Exception ignored) { /* on tente l'autre mode */ }
                    if (out.size() >= 3) break;
                }
                if (out.isEmpty()) cb.err("Aucun résultat");
                else cb.ok(out);
            } catch (Exception e) {
                cb.err(e.getMessage() != null ? e.getMessage() : "Erreur de recherche");
            }
        });
    }

    /** Meilleur flux audio seul en téléchargement progressif. */
    public static void resolve(String videoUrl, UrlCb cb) {
        EX.execute(() -> {
            try {
                ensureInit();
                StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl);
                AudioStream best = null;
                for (AudioStream s : info.getAudioStreams()) {
                    if (!s.isUrl() || s.getDeliveryMethod() != DeliveryMethod.PROGRESSIVE_HTTP) continue;
                    if (best == null || s.getAverageBitrate() > best.getAverageBitrate()) best = s;
                }
                if (best == null) cb.err("Pas de flux audio");
                else cb.ok(best.getContent());
            } catch (Exception e) {
                cb.err(e.getMessage() != null ? e.getMessage() : "Erreur de lecture");
            }
        });
    }

    /** Pont réseau requis par NewPipeExtractor. */
    static final class OkDownloader extends Downloader {
        private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0";
        private final OkHttpClient client;

        OkDownloader(OkHttpClient c) { client = c; }

        @Override
        public Response execute(Request request) throws IOException, ReCaptchaException {
            RequestBody body = null;
            byte[] data = request.dataToSend();
            if (data != null) body = RequestBody.create(data, MediaType.parse("application/octet-stream"));
            okhttp3.Request.Builder b = new okhttp3.Request.Builder()
                    .method(request.httpMethod(), body)
                    .url(request.url())
                    .header("User-Agent", UA);
            for (Map.Entry<String, List<String>> h : request.headers().entrySet()) {
                b.removeHeader(h.getKey());
                for (String v : h.getValue()) b.addHeader(h.getKey(), v);
            }
            try (okhttp3.Response r = client.newCall(b.build()).execute()) {
                if (r.code() == 429) throw new ReCaptchaException("reCAPTCHA", request.url());
                ResponseBody rb = r.body();
                return new Response(r.code(), r.message(), r.headers().toMultimap(),
                        rb != null ? rb.string() : "", r.request().url().toString());
            }
        }
    }
}
