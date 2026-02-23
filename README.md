# La Torrentola 

La Torrentola is an example Android app made to test modern Android ecosystem functionalities and architectures.

**Purpose:** This app is a specialized discovery tool intended to be a companion for **[Transdrone](https://play.google.com/store/apps/details?id=com.nascent.transdrone)**. While La Torrentola handles movie discovery, data visualization, and accessibility, it intentionally transfers the responsibility of torrent management and downloading to Transdrone.

### Why use La Torrentola with Transdrone?

By delegating the actual download task to **Transdrone**, users gain several advantages:

1.  **Remote Management:** You can find a movie on your phone with La Torrentola and send the magnet link to Transdrone, which then manages the download on a remote server (like a home NAS or seedbox) rather than consuming your phone's storage and battery.
2.  **Protocol Support:** Transdrone supports a vast array of remote clients (uTorrent, Transmission, rTorrent, Synology, etc.), making La Torrentola compatible with almost any home setup.
3.  **Efficiency:** La Torrentola remains a lightweight discovery app, avoiding the heavy battery drain and data usage associated with running a full BitTorrent client on a mobile device.
4.  **Security & Privacy:** Downloads happen on your designated home server, allowing you to centralize your media and maintain better control over your network traffic.

## Architecture Overview

The app follows a **Model-View-Controller (MVC)** pattern, transitioning towards **MVVM**.

```mermaid
graph TD
    subgraph UI_Layer [UI Layer]
        A[MainActivity]
        B[PeliActivity]
        C[SearchableActivity]
        D[UrlHandlerActivity]
        E[XML Layouts]
    end

    subgraph Logic_Layer [Logic/Controller Layer]
        A -->|Coordinates| F[Data Fetching]
        B -->|Coordinates| F
        C -->|Coordinates| F
        D -->|Coordinates| F
    end

    subgraph Data_Layer [Data Layer]
        F --> G[Remote: Retrofit]
        F --> H[Local: Room DB]
        F --> I[AI: Google ML Kit]
        
        G -->|YTS API| J[YTS Movie Data]
        H -->|AppDatabase| L[Personal Movie List]
        I -->|Translator| M[Spanish Summary]
    end

    subgraph Cross_Cutting [Cross-Cutting]
        N[RxJava 2: Reactive Streams]
        O[TTS: Text-to-Speech]
        P[Firebase: Analytics/Crashlytics]
    end

    UI_Layer -.-> Cross_Cutting
    Cross_Cutting -.-> Data_Layer
    UI_Layer -.->|Magnet Intent| Q[Transdrone/External Client]
```

- **View:** XML layouts and Activities (`MainActivity`, `PeliActivity`, `SearchableActivity`, etc.).
- **Controller/Logic:** Activities handle user interaction and coordinate data fetching.
- **Model:** Data classes representing YTS API responses and Room entities.
- **Data Layer:** 
    - **Remote:** Retrofit interface for the YTS API.
    - **Local:** Room database (`AppDatabase`) for storing a personal movie list.
    - **AI:** Google ML Kit for on-device translation.
- **Async/Reactive:** RxJava 2 for handling network calls and database operations.

## Key Features & Technologies

1.  **Modern Build System:** Updated to **Android Gradle Plugin 8.7.0** and **Gradle 8.9**.
2.  **Android 15 Ready:** Target SDK 35 with **Edge-to-Edge** support (using `fitsSystemWindows`) to ensure the UI respects system bars.
3.  **On-Device Translation:** Replaced Yandex API with **Google ML Kit Translate** (English to Spanish) for movie summaries.
4.  **Accessibility:** Integrated **Text-to-Speech (TTS)** for reading movie details and translations.
5.  **Reactive Programming:** Extensive use of **RxJava 2** for asynchronous operations.
6.  **Networking:** **Retrofit 2** with reactive adapters.
7.  **Persistence:** **Room Persistence Library** for local storage.
8.  **Media Integration:** **YouTube Player** integration for trailers.
9.  **Firebase:** Integration with Analytics, Messaging, Crashlytics, and Sessions.

## Sequence Diagrams

### 1. Main Movie Discovery Flow
```mermaid
sequenceDiagram
    participant User
    participant MainActivity
    participant YTS_API as YTS Remote API
    participant RecyclerView

    User->>MainActivity: Open App
    MainActivity->>YTS_API: loadJSON(page 1)
    YTS_API-->>MainActivity: List<Movie>
    MainActivity->>RecyclerView: Update Adapter
    RecyclerView-->>User: Display Movies
    User->>RecyclerView: Scroll to bottom
    MainActivity->>YTS_API: loadJSON(next page)
    YTS_API-->>MainActivity: More Movies
    MainActivity->>RecyclerView: Append Movies
```

### 2. Search & Filter Flow
```mermaid
sequenceDiagram
    participant User
    participant SearchableActivity
    participant YTS_API as YTS Remote API
    participant RoomDB as Local Room DB
    participant TTS as Text-to-Speech

    User->>SearchableActivity: Search Query / Select Genre
    SearchableActivity->>TTS: Speak "Buscando..."
    alt Search / Genre
        SearchableActivity->>YTS_API: Request Data
        YTS_API-->>SearchableActivity: Movie Results
    else Personal List
        SearchableActivity->>RoomDB: Query getAll()
        RoomDB-->>SearchableActivity: List<Movie>
    end
    SearchableActivity->>TTS: Speak Results Summary
    SearchableActivity-->>User: Display Results
```

### 3. Movie Details & Translation Flow
```mermaid
sequenceDiagram
    participant User
    participant PeliActivity
    participant MLKit as Google ML Kit
    participant TTS as Text-to-Speech
    participant OS as Android Intent System
    participant Transdrone

    User->>PeliActivity: Click Movie
    PeliActivity->>TTS: Speak Movie Title
    alt Voice Translation Enabled
        PeliActivity->>MLKit: translate(Summary)
        MLKit-->>PeliActivity: Spanish Text
        PeliActivity->>TTS: Speak Spanish Summary
    else Voice Summary Enabled
        PeliActivity->>TTS: Speak English Summary
    end
    PeliActivity-->>User: Display Details & YTS Torrents
    User->>PeliActivity: Click Magnet Link
    PeliActivity->>OS: startActivity(Magnet Intent)
    OS->>Transdrone: Handle Torrent Download
```

### 4. Deep Link / URL Handling Flow
```mermaid
sequenceDiagram
    participant OS as Android OS
    participant UrlHandlerActivity
    participant YTS_API as YTS Remote API
    participant TTS as Text-to-Speech

    OS->>UrlHandlerActivity: Open IMDb Link / Share Text
    UrlHandlerActivity->>TTS: Speak "Chequeando disponibilidad"
    UrlHandlerActivity->>YTS_API: getMovieSearch(IMDb ID)
    YTS_API-->>UrlHandlerActivity: Movie Result
    alt Found
        UrlHandlerActivity->>TTS: Speak "Disponible"
    else Not Found
        UrlHandlerActivity->>TTS: Speak "No disponible"
    end
    UrlHandlerActivity-->>User: Display Result
```

## Requirements

To compile the project, you need a `Constants.java` file in your `constants` package.

```java
public class Constants {
    public static final String YTS_BASE_URL = "https://yts.ag/api/v2/";
    public static final int PAGE_SIZE = 50;
}
```

## Next Steps

- Decouple views with **ViewModel**, moving from plain Android MVC to **MVVM**.
- Move to a pluggable architecture of torrent vendors to reduce coupling.
- Offer different switchable posters view with normal and staggered grids.
- Enhance notification handling.
