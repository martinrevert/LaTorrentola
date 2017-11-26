package com.martinrevert.latorrentola.model.argenteam;

/**
 * Created by martin on 21/11/17.
 */

import java.util.List;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Release {

    @SerializedName("source")
    @Expose
    private String source;
    @SerializedName("codec")
    @Expose
    private String codec;
    @SerializedName("team")
    @Expose
    private String team;
    @SerializedName("tags")
    @Expose
    private String tags;
    @SerializedName("size")
    @Expose
    private String size;
    @SerializedName("torrents")
    @Expose
    private List<Torrent> torrents = null;
    @SerializedName("elinks")
    @Expose
    private List<Object> elinks = null;
    @SerializedName("subtitles")
    @Expose
    private List<Object> subtitles = null;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getCodec() {
        return codec;
    }

    public void setCodec(String codec) {
        this.codec = codec;
    }

    public String getTeam() {
        return team;
    }

    public void setTeam(String team) {
        this.team = team;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public List<Torrent> getTorrents() {
        return torrents;
    }

    public void setTorrents(List<Torrent> torrents) {
        this.torrents = torrents;
    }

    public List<Object> getElinks() {
        return elinks;
    }

    public void setElinks(List<Object> elinks) {
        this.elinks = elinks;
    }

    public List<Object> getSubtitles() {
        return subtitles;
    }

    public void setSubtitles(List<Object> subtitles) {
        this.subtitles = subtitles;
    }

}
