package com.softmyc.tpv.presentation;
import com.softmyc.tpv.R;
import com.softmyc.tpv.domain.*;
import com.softmyc.tpv.data.*;


import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private ImageView imgLogo;
    private TextView txtTitle, txtSubtitle;
    private final String FULL_TEXT = "Soluciones_Tecnológicas";
    private int charIndex = 0;
    // S8: Constructor deprecated reemplazado por la versión con Looper explícito
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Hide ActionBar
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        // 1. Start Background Animation
        android.view.View container = findViewById(R.id.splashContainer);
        if (container != null) {
            AnimationDrawable anim = (AnimationDrawable) container.getBackground();
            anim.setEnterFadeDuration(2000);
            anim.setExitFadeDuration(4000);
            anim.start();
        }

        imgLogo = findViewById(R.id.imgLogo);
        txtTitle = findViewById(R.id.txtTitle);
        txtSubtitle = findViewById(R.id.txtSubtitle);

        // 2. Animate Logo & Title (Entrance)
        imgLogo.animate()
                .alpha(1f)
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(1200)
                .setInterpolator(new OvershootInterpolator())
                .start();

        txtTitle.animate()
                .alpha(1f)
                .setDuration(1000)
                .setStartDelay(300)
                .start();

        // 3. Start Typewriter Effect after logo settles
        handler.postDelayed(typeWriterRunnable, 800);

        // 4. Navigate to Login after total time (approx 3.5s)
        handler.postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, LoginActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, 3500);
    }

    private final Runnable typeWriterRunnable = new Runnable() {
        @Override
        public void run() {
            if (charIndex <= FULL_TEXT.length()) {
                txtSubtitle.setText(FULL_TEXT.substring(0, charIndex));
                charIndex++;
                handler.postDelayed(this, 80);
            }
        }
    };

    // S9: Cancelar todos los Runnables pendientes al destruir la Activity
    // Evita que la navegación a LoginActivity ocurra si el usuario retrocede
    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}