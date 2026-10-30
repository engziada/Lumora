package com.firestick.aura.launcher.model;

/** Offline fixtures for the opt-in preview experiment, never a provider recommendation feed. */
public final class AppPreviewSamples {
    public enum Provider { YOUTUBE, PRIME, NETFLIX }

    public static final class Title {
        public final String name;
        public final String category;
        public final String description;

        private Title(String name, String category, String description) {
            this.name = name;
            this.category = category;
            this.description = description;
        }
    }

    private static final Title[] NETFLIX = {
            new Title("Stranger Things", "Series · Mystery", "A small town, an extraordinary discovery, and a journey into the unknown."),
            new Title("Wednesday", "Series · Fantasy", "An unusual student investigates the mysteries surrounding her new school."),
            new Title("Our Planet", "Series · Nature", "Discover remarkable wildlife and the landscapes they call home.")
    };
    private static final Title[] PRIME = {
            new Title("Fallout", "Series · Science fiction", "A sheltered survivor ventures into a world transformed beyond recognition."),
            new Title("The Boys", "Series · Action", "A group of outsiders challenges the powerful heroes celebrated by the world."),
            new Title("The Rings of Power", "Series · Fantasy", "A journey through Middle-earth as an ancient threat begins to return.")
    };
    private static final Title[] YOUTUBE = {
            new Title("Big Buck Bunny", "Short film · Animation", "A peaceful woodland morning turns into an unexpected adventure."),
            new Title("Sintel", "Short film · Fantasy", "A young traveler searches for a lost companion in a world of dragons."),
            new Title("Tears of Steel", "Short film · Science fiction", "A team faces the consequences of a collision between humanity and machines.")
    };

    private AppPreviewSamples() { }

    /** Explicit package matching avoids showing a service preview for unrelated apps with similar names. */
    public static Provider providerFor(String packageName) {
        if (packageName == null) return null;
        switch (packageName) {
            case "com.google.android.youtube.tv":
            case "com.amazon.firetv.youtube":
            case "com.google.android.youtube":
                return Provider.YOUTUBE;
            case "com.amazon.amazonvideo.livingroom":
            case "com.amazon.avod.thirdpartyclient":
            case "com.amazon.firebat":
            case "com.amazon.avod":
                return Provider.PRIME;
            case "com.netflix.ninja":
            case "com.netflix.mediaclient":
                return Provider.NETFLIX;
            default:
                return null;
        }
    }

    public static int count(Provider provider) {
        return titles(provider).length;
    }

    public static Title title(Provider provider, int index) {
        Title[] titles = titles(provider);
        return titles[Math.floorMod(index, titles.length)];
    }

    private static Title[] titles(Provider provider) {
        if (provider == null) throw new IllegalArgumentException("A preview provider is required");
        switch (provider) {
            case NETFLIX: return NETFLIX;
            case PRIME: return PRIME;
            default: return YOUTUBE;
        }
    }
}
