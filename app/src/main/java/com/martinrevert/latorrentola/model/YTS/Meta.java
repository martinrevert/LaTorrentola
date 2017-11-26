package com.martinrevert.latorrentola.model.YTS;

/**
 * Created by martin on 22/11/17.
 */
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Meta {

    @SerializedName("server_time")
    @Expose
    private Integer serverTime;
    @SerializedName("server_timezone")
    @Expose
    private String serverTimezone;
    @SerializedName("api_version")
    @Expose
    private Integer apiVersion;
    @SerializedName("execution_time")
    @Expose
    private String executionTime;

    public Integer getServerTime() {
        return serverTime;
    }

    public void setServerTime(Integer serverTime) {
        this.serverTime = serverTime;
    }

    public String getServerTimezone() {
        return serverTimezone;
    }

    public void setServerTimezone(String serverTimezone) {
        this.serverTimezone = serverTimezone;
    }

    public Integer getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(Integer apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(String executionTime) {
        this.executionTime = executionTime;
    }

}