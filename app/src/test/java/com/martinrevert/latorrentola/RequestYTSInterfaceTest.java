package com.martinrevert.latorrentola;

import static com.google.common.truth.Truth.assertThat;

import com.martinrevert.latorrentola.model.YTS.MovieDetails;
import com.martinrevert.latorrentola.network.RequestYTSInterface;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;

import io.reactivex.observers.TestObserver;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class RequestYTSInterfaceTest {

    private MockWebServer mockWebServer;
    private RequestYTSInterface api;

    @Before
    public void setup() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        api = new Retrofit.Builder()
                .baseUrl(mockWebServer.url("/"))
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build()
                .create(RequestYTSInterface.class);
    }

    @After
    public void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    public void getMovieDetails_parsesCorrectly() {
        String json = "{\"status\":\"ok\",\"data\":{\"movies\":[{\"id\":123,\"title\":\"Test Movie\"}]}}";
        mockWebServer.enqueue(new MockResponse().setBody(json));

        TestObserver<MovieDetails> observer = api.getMovieDetails(1, "5", 1, "true").test();

        observer.awaitTerminalEvent();
        observer.assertNoErrors();
        MovieDetails details = observer.values().get(0);
        assertThat(details.getStatus()).isEqualTo("ok");
        assertThat(details.getData().getMovies().get(0).getTitle()).isEqualTo("Test Movie");
    }
}
