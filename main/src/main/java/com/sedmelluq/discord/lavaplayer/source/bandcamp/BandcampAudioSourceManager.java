package com.sedmelluq.discord.lavaplayer.source.bandcamp;

import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.FAULT;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

/**
 * Audio source manager that implements finding Bandcamp tracks based on URL.
 */
public class BandcampAudioSourceManager implements AudioSourceManager {
  private static final String TRACK_URL_REGEX = "^https?://(?:[^.]+\\.|)bandcamp\\.com/track/([a-zA-Z0-9-_]+)/?(?:\\?.*|)$";
  private static final String ALBUM_URL_REGEX = "^https?://(?:[^.]+\\.|)bandcamp\\.com/album/([a-zA-Z0-9-_]+)/?(?:\\?.*|)$";

  private static final Pattern trackUrlPattern = Pattern.compile(TRACK_URL_REGEX);
  private static final Pattern albumUrlPattern = Pattern.compile(ALBUM_URL_REGEX);

  private final HttpClientBuilder httpClientBuilder;

  /**
   * Create an instance.
   */
  public BandcampAudioSourceManager() {
    httpClientBuilder = HttpClientTools.createSharedCookiesHttpBuilder();
  }

  @Override
  public String getSourceName() {
    return "bandcamp";
  }

  @Override
  public AudioItem loadItem(DefaultAudioPlayerManager manager, AudioReference reference) {
    if (trackUrlPattern.matcher(reference.identifier).matches()) {
      return loadTrack(reference.identifier);
    } else if (albumUrlPattern.matcher(reference.identifier).matches()) {
      return loadAlbum(reference.identifier);
    }
    return null;
  }

  private AudioItem loadTrack(String trackUrl) {
    return extractFromPage(trackUrl, (httpClient, text) -> {
      String bandUrl = readBandUrl(text);
      JsonBrowser trackListInfo = readTrackListInformation(text);
      String artist = trackListInfo.get("artist").text();

      return extractTrack(trackListInfo.get("trackinfo").index(0), bandUrl, artist);
    });
  }

  private AudioItem loadAlbum(String albumUrl) {
    return extractFromPage(albumUrl, (httpClient, text) -> {
      String bandUrl = readBandUrl(text);
      JsonBrowser trackListInfo = readTrackListInformation(text);
      String artist = trackListInfo.get("artist").text();

      List<AudioTrack> tracks = new ArrayList<>();
      for (JsonBrowser trackInfo : trackListInfo.get("trackinfo").values()) {
        tracks.add(extractTrack(trackInfo, bandUrl, artist));
      }

      JsonBrowser albumInfo = readAlbumInformation(text);
      return new BasicAudioPlaylist(albumInfo.get("album_title").text(), tracks, null, false);
    });
  }

  private AudioTrack extractTrack(JsonBrowser trackInfo, String bandUrl, String artist) {
    return new BandcampAudioTrack(new AudioTrackInfo(
        trackInfo.get("title").text(),
        artist,
        (long) (trackInfo.get("duration").as(Double.class) * 1000.0),
        bandUrl + trackInfo.get("title_link").text(),
        false
    ), this);
  }

  private String readBandUrl(String text) {
    String bandUrl = DataFormatTools.extractBetween(text, "var band_url = \"", "\";");

    if (bandUrl == null) {
      throw new FriendlyException("Band information not found on the Bandcamp page.", SUSPICIOUS, null);
    }

    return bandUrl;
  }

  private JsonBrowser readAlbumInformation(String text) throws IOException {
    String albumInfoJson = DataFormatTools.extractBetween(text, "var EmbedData = ", "};");

    if (albumInfoJson == null) {
      throw new FriendlyException("Album information not found on the Bandcamp page.", SUSPICIOUS, null);
    }

    albumInfoJson = albumInfoJson.replace("\" + \"", "") + "};";
    return JsonBrowser.parse(albumInfoJson);
  }

  JsonBrowser readTrackListInformation(String text) throws IOException {
    String trackInfoJson = DataFormatTools.extractBetween(text, "var TralbumData = ", "};");

    if (trackInfoJson == null) {
      throw new FriendlyException("Track information not found on the Bandcamp page.", SUSPICIOUS, null);
    }

    trackInfoJson = trackInfoJson.replace("\" + \"", "") + "};";
    return JsonBrowser.parse(trackInfoJson + "};");
  }

  private AudioItem extractFromPage(String url, AudioItemExtractor extractor) {
    try (CloseableHttpClient httpClient = httpClientBuilder.build()) {
      return extractFromPageWithClient(httpClient, url, extractor);
    } catch (Exception e) {
      throw ExceptionTools.wrapUnfriendlyExceptions("Loading information for a Bandcamp track failed.", FAULT, e);
    }
  }

  private AudioItem extractFromPageWithClient(CloseableHttpClient httpClient, String url, AudioItemExtractor extractor) throws Exception {
    String responseText;

    try (CloseableHttpResponse response = httpClient.execute(new HttpGet(url))) {
      int statusCode = response.getStatusLine().getStatusCode();

      if (statusCode == 404) {
        return new AudioReference(null, null);
      } else if (statusCode != 200) {
        throw new IOException("Invalid status code " + statusCode + " for track page.");
      }

      responseText = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
    }

    return extractor.extract(httpClient, responseText);
  }

  @Override
  public boolean isTrackEncodable(AudioTrack track) {
    return true;
  }

  @Override
  public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
    // No special values to encode
  }

  @Override
  public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
    return new BandcampAudioTrack(trackInfo, this);
  }

  @Override
  public void shutdown() {
    // Nothing to do
  }

  /**
   * @return A new HttpClient instance. All instances returned from this method use the same cookie jar.
   */
  public CloseableHttpClient createHttpClient() {
    return httpClientBuilder.build();
  }

  private interface AudioItemExtractor {
    AudioItem extract(CloseableHttpClient httpClient, String text) throws Exception;
  }
}
