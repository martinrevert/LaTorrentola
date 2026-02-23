package com.martinrevert.latorrentola;

import android.app.TaskStackBuilder;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import androidx.annotation.NonNull;
import androidx.core.app.NavUtils;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.martinrevert.latorrentola.database.AppDatabase;
import com.martinrevert.latorrentola.model.YTS.Movie;
import com.martinrevert.latorrentola.model.YTS.Torrent;
import com.martinrevert.latorrentola.constants.Constants;

import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.util.List;
import java.util.Locale;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class PeliActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private String youtube_video_trailer;
    private CompositeDisposable mCompositeDisposable;
    private YouTubePlayerView youTubePlayerView;
    private Movie movie;
    private boolean ispresent;

    private TextView summary;
    private TextView year;
    private TextView language;
    private TextView rating;

    private TextToSpeech tts;
    private String speak;
    private AppDatabase db;
    private boolean voice_translation;
    private boolean voice_system;
    private boolean voice_summary;
    private Translator englishSpanishTranslator;
    private boolean isTtsInitialized = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_peli);
        youTubePlayerView = findViewById(R.id.youtube_player_view);
        getLifecycle().addObserver(youTubePlayerView);

        mCompositeDisposable = new CompositeDisposable();
        db = AppDatabase.getAppDatabase(this);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        voice_system = sharedPreferences.getBoolean("voice_system", true);
        voice_summary = sharedPreferences.getBoolean("voice_summary",true);
        voice_translation = sharedPreferences.getBoolean("voice_translation", false);


        summary = findViewById(R.id.summary);
        year = findViewById(R.id.year);
        language = findViewById(R.id.language);
        rating = findViewById(R.id.rating);

        tts = new TextToSpeech(this, this);
        tts.setLanguage(Locale.getDefault());

        Bundle bundle = getIntent().getExtras();
        String peliStr = null;
        if (bundle != null) {
            peliStr = bundle.getString("PELI" + "");
            Gson gson = new Gson();
            Type type = new TypeToken<Movie>() {
            }.getType();

            movie = gson.fromJson(peliStr, type);

            Toolbar toolbar = findViewById(R.id.toolbar);
            toolbar.setTitle(movie.getTitle());
            setSupportActionBar(toolbar);

            youtube_video_trailer = movie.getYtTrailerCode();

            Log.v("YTS", movie.getTitleLong());

            if (youtube_video_trailer != null && !youtube_video_trailer.isEmpty()) {
                youTubePlayerView.addYouTubePlayerListener(new AbstractYouTubePlayerListener() {
                    @Override
                    public void onReady(@NonNull YouTubePlayer initializedYouTubePlayer) {
                        initializedYouTubePlayer.cueVideo(youtube_video_trailer, 0);
                    }
                });
            }


            String summ = "Summary: " + movie.getSummary();
            summary.setText(summ);
            String yr = "Year: " + movie.getYear();
            year.setText(yr);
            String lg = "Language: " + movie.getLanguage();
            language.setText(lg);
            String rt = "Rating: " + movie.getRating();
            rating.setText(rt);

            String texto = movie.getSummary();

            if(voice_summary && !voice_translation){
                speak = texto;
            }

            Integer id = movie.getId();

            mCompositeDisposable.add(db.movieDao().getMovie(id)
                    .subscribeOn(Schedulers.io())
                    .subscribe(peli -> {
                                ispresent = true;
                                Log.v("DB", peli.getTitle());
                            },
                            throwable -> {
                                ispresent = false;
                                Log.v("DB", "NO DB" + throwable.getLocalizedMessage());
                            }

                    ));


            List<Torrent> torrentsyts = movie.getTorrents();

            for (Torrent torroyts : torrentsyts) {

                String text = "YTS" + " " + torroyts.getQuality() + " " + torroyts.getSize() + " " + torroyts.getType();

                LinearLayout linearyts = findViewById(R.id.linearyts);

                Button btntorrentyts = new Button(PeliActivity.this);
                btntorrentyts.setText(text);
                btntorrentyts.setOnClickListener(new View.OnClickListener() {

                    private void sendtorrent() {

                        String hash = torroyts.getHash();
                        String uriyts = null;
                        try {
                            uriyts = "magnet:?xt=urn:btih:" + hash + "&dn=" + URLEncoder.encode(movie.getTitle(), "UTF-8") + "&tr=udp://open.demonii.com:1337/announce&tr=udp://tracker.openbittorrent.com:80";
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                        Intent sharingIntent = new Intent(
                                android.content.Intent.ACTION_VIEW);
                        sharingIntent
                                .addCategory(android.content.Intent.CATEGORY_BROWSABLE);
                        sharingIntent.setData(Uri.parse(uriyts));
                        //ToDO Acá hay que implementar algo por si no hay apps que reciban magnet links
                        try {
                            startActivity(sharingIntent);
                        } catch (ActivityNotFoundException activityNotFound) {

                            new AlertDialog.Builder(PeliActivity.this, R.style.Theme_AppCompat_Dialog)
                                    .setTitle("Enviar torrent")
                                    .setMessage("No tienes ningun torrent player instalado o no tienes una app Android para enviar a descargar tu torrent a otro sitio.")
                                    .setPositiveButton("Ok", null)
                                    .show();

                        }

                    }

                    @Override
                    public void onClick(View view) {
                        if (ispresent) {
                            new AlertDialog.Builder(PeliActivity.this, R.style.Theme_AppCompat_Dialog)
                                    .setTitle("Enviar torrent")
                                    .setMessage("Esta peli esta en tu lista de deseos ¿Deseas quitarla de alli?")
                                    .setPositiveButton("Si", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {

                                            Runnable loadRunnable = new Runnable() {
                                                @Override
                                                public void run() {
                                                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                                                    db.movieDao().delete(movie);

                                                }
                                            };
                                            Thread insertThread = new Thread(loadRunnable);
                                            insertThread.start();

                                            sendtorrent();
                                        }
                                    })
                                    .setNegativeButton("No", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            sendtorrent();
                                        }
                                    }).show();
                        } else {
                            sendtorrent();
                        }


                    }
                });
                btntorrentyts.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                linearyts.addView(btntorrentyts);

            }
            if (voice_translation) {
                translateAndSpeak(texto);
            }
        }

    }


    private void translateAndSpeak(String text) {
        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.SPANISH)
                .build();
        englishSpanishTranslator = Translation.getClient(options);

        DownloadConditions conditions = new DownloadConditions.Builder()
                .requireWifi()
                .build();

        englishSpanishTranslator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(unused -> {
                    englishSpanishTranslator.translate(text)
                            .addOnSuccessListener(translatedText -> {
                                if (isTtsInitialized) {
                                    tts.speak(translatedText, TextToSpeech.QUEUE_ADD, null, null);
                                } else {
                                    speak = translatedText;
                                }
                            })
                            .addOnFailureListener(e -> Log.e("MLKIT", "Translation failed: " + e.getLocalizedMessage()));
                })
                .addOnFailureListener(e -> {
                    Log.e("MLKIT", "Model download failed: " + e.getLocalizedMessage());
                    Toast.makeText(this, "Error al descargar modelo de traducción", Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                Intent upIntent = NavUtils.getParentActivityIntent(this);
                if (NavUtils.shouldUpRecreateTask(this, upIntent) || isTaskRoot()) {
                    // This activity is NOT part of this app's task, so create a new task
                    // when navigating up, with a synthesized back stack.
                    TaskStackBuilder.create(this)
                            // Add all of this activity's parents to the back stack
                            .addNextIntentWithParentStack(upIntent)
                            // Navigate up to the closest parent
                            .startActivities();
                } else {
                    // This activity is part of this app's task, so simply
                    // navigate up to the logical parent activity.
                    NavUtils.navigateUpTo(this, upIntent);
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mCompositeDisposable.dispose();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (englishSpanishTranslator != null) {
            englishSpanishTranslator.close();
        }
        AppDatabase.destroyInstance();

    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true;
            if (voice_system && movie != null) {
                tts.speak(movie.getTitleLong(), TextToSpeech.QUEUE_ADD, null, null);
            }
            if (speak != null) {
                tts.speak(speak, TextToSpeech.QUEUE_ADD, null, null);
            }
        }
    }
}
