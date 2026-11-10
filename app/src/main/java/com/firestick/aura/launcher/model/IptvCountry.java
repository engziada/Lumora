package com.firestick.aura.launcher.model;

import java.util.List;

public class IptvCountry {
    public final String code;
    public final String name;
    public final String flag;
    public final List<IptvChannel> channels;

    public IptvCountry(String code, String name, List<IptvChannel> channels) {
        this(code, name, "", channels);
    }

    public IptvCountry(String code, String name, String flag, List<IptvChannel> channels) {
        this.code = code;
        this.name = name;
        this.flag = flag;
        this.channels = channels;
    }
}
