package com.chao.peakmusic.activity;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.chao.peakmusic.MainActivity;

/** Retains the existing launcher component without imposing an artificial splash delay. */
public class GuideActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
