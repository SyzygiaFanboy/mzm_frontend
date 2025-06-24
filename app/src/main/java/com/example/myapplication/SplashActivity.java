package com.example.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
//启动页行为，可添加退出按钮
public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler().postDelayed(() -> {
            SharedPreferences preferences = getSharedPreferences("user_pref", MODE_PRIVATE);
            boolean isLoggedIn = preferences.getBoolean("is_logged_in", false);

            if (isLoggedIn) {
                //startActivity(new Intent(this, MainActivity.class));
                Log.d("登录与注册-启动页", "is_logged_in==true，直接启动歌单页");
                startActivity(new Intent(this, PlaylistListActivity.class));
            } else {
                Log.d("登录与注册-启动页", "is_logged_in==false，启动登录页面");
                startActivity(new Intent(this, LoginActivity.class));
            }
            finish();
        }, 1000);
    }
}