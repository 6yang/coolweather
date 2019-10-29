package com.example.coolweather.entity;

import com.google.gson.annotations.SerializedName;

public class Now {

    public String temperature;

    @SerializedName("cond")
    public More more;

    public class More {

        @SerializedName("txt")
        public String info;
    }
}
