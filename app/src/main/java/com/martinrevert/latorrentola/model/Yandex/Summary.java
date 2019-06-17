
package com.martinrevert.latorrentola.model.Yandex;

import java.util.List;

import com.google.gson.annotations.SerializedName;


public class Summary {

    @SerializedName("code")
    private Long Code;
    @SerializedName("lang")
    private String Lang;
    @SerializedName("text")
    private List<String> Text;

    public Long getCode() {
        return Code;
    }

    public void setCode(Long code) {
        Code = code;
    }

    public String getLang() {
        return Lang;
    }

    public void setLang(String lang) {
        Lang = lang;
    }

    public List<String> getText() {
        return Text;
    }

    public void setText(List<String> text) {
        Text = text;
    }

}
