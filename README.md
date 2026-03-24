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
        H -->|AppDatabase| LR[Last Visit Tracking]
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
    - **Local:** Room database (`AppDatabase`) for storing a personal movie list and session tracking.
    - **AI:** Google ML Kit for on-device translation.
- **Async/Reactive:** RxJava 2 for handling network calls and database operations.

## Key Features & Technologies

1.  **Modern Build System:** Updated to **Android Gradle Plugin 8.7.0** and **Gradle 8.9**.
2.  **Android 15 Ready:** Target SDK 35 with **Edge-to-Edge** support (using `fitsSystemWindows`) to ensure the UI respects system bars.
3.  **Adaptive Grid Layout:** Implemented a dynamic `GridLayoutManager` that supports:
    - **Portrait:** Minimum 2 columns.
    - **Landscape:** Minimum 4 columns.
    - **Foldables:** Automatic adjustment for open/closed states.
4.  **Fresh Content Highlighting:** A "New" badge (Yellow ribbon) automatically appears on movie posters uploaded since the user's last session, powered by Room-based session tracking.
5.  **On-Device Translation:** Replaced Yandex API with **Google ML Kit Translate** (English to Spanish) for movie summaries.
6.  **Accessibility:** Integrated **Text-to-Speech (TTS)** for reading movie details and translations.
7.  **Reactive Programming:** Extensive use of **RxJava 2** for asynchronous operations.
8.  **Networking:** **Retrofit 2** with reactive adapters.
9.  **Persistence:** **Room Persistence Library** for local storage.
10. **Media Integration:** **YouTube Player** integration for trailers.
11. **Firebase:** Integration with Analytics, Messaging, Crashlytics, and Sessions.

## Sequence Diagrams

### 1. Main Movie Discovery & Session Flow
```mermaid
sequenceDiagram
    participant User
    participant MainActivity
    participant RoomDB as Local Room DB
    participant YTS_API as YTS Remote API
    participant RecyclerView

    User->>MainActivity: Open App
    MainActivity->>RoomDB: fetchLastVisit()
    RoomDB-->>MainActivity: previousVisitDate
    MainActivity->>RoomDB: updateLastVisit(currentTime)
    MainActivity->>YTS_API: loadJSON(page 1)
    YTS_API-->>MainActivity: List<Movie>
    MainActivity->>RecyclerView: Compare date_uploaded vs previousVisitDate
    MainActivity->>RecyclerView: Update Adapter (Show "New" Badges)
    RecyclerView-->>User: Display Movies with Grid Layout
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
    SearchableActivity-->>User: Display Results in Adaptive Grid
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
- Enhance notification handling.
