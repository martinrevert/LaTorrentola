#La Torrentola 

La Torrentola is an example Android app made to test modern Android ecosystem funcionalities and architectures.

It makes use of:

1. Android SDK
2. Some Java8 lambdas & streams
3. Rxjava2
4. Retrofit2 with reactive adapter
5. ConstraintLayout
6. Room: Google new, ORM reactively typed.
7. ????????

Next steps:

- Decouple views with ViewModel, move plain Android MVC to MVVM.
- Move to a pluggable architecture of torrent vendors (more interfaced, because now is basically ugly spaguetti)
- Offer different switchable posters view with normal and staggered grids (basically change the Layout Manager of the RecyclerView).
- Maybe notifications.
- ???????


Want to compile by yourself?

Check build.gradle for the needs.

Also you will need a Constants.java config like this in your "constants" package:

```
public class Constants {
    public static final String ARGENTEAM_BASE_URL = "http://www.argenteam.net/api/v1/";
    public static final String YTS_BASE_URL = "https://yts.ag/api/v2/";
    public static final String YANDEX_BASE_URL = "https://translate.yandex.net/api/v1.5/tr.json/";
    public static final String YANDEX_API_KEY = "your_key";
    public static final String YOUTUBE_API_KEY = "your_key";
}
```