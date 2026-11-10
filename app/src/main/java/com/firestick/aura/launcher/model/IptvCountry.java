package com.firestick.aura.launcher.model;

import java.util.List;

public class IptvCountry {
    public final String code;
    public final String name;
    public final List<IptvChannel> channels;

    public IptvCountry(String code, String name, List<IptvChannel> channels) {
        this.code = code;
        this.name = name;
        this.channels = channels;
    }
}
