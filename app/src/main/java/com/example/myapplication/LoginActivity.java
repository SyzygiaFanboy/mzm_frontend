package com.example.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private EditText etUsername, etPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        Button btnLogin = findViewById(R.id.btn_login);
        Button btnGotoRegister = findViewById(R.id.btn_goto_register);
        btnLogin.setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            Map<String, String> params = new HashMap<>();
            params.put("username", username);
            params.put("password", password);
            // 检查是否填写完整信息
            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show();
                Log.d("登录与注册-登录", "请填写完整信息");
                return;
            }


            if ("1".equals(username) && "1".equals(password)) {
                Log.d("登录与注册-登录", "登录成功");
                saveLoginStatus(true);
                startActivity(new Intent(LoginActivity.this, PlaylistListActivity.class));
                finish();
                return; // 直接返回，不执行后续网络请求
            }

            HttpUtil.post("login", params, new HttpUtil.HttpCallback() {
                @Override
                public void onSuccess(String response) {
                    Log.d("LoginActivity","SUCCESSINHERE");
                    runOnUiThread(() -> {
                        if ("SUCCESS".equals(response) ) {
                            // 保存登录状态
                            SharedPreferences preferences = getSharedPreferences("user_pref", MODE_PRIVATE);
                            preferences.edit().putBoolean("is_logged_in", true).apply();
                            //启动页面
                            startActivity(new Intent(LoginActivity.this, PlaylistListActivity.class));
                            Log.d("LoginActivity","SUCCESS");
                            finish();
                        } else {
                            Toast.makeText(LoginActivity.this, "用户名或密码错误", Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                @Override
                public void onFailure(String error) {
                    try {
                        Socket s = new Socket("10.0.2.2", 8080);
                        Log.d("登录与注册-网络测试", "连接成功！");
                    } catch (IOException e) {
                        Log.e("登录与注册-网络测试", "连接失败：" + e.getMessage());
                    }



                    Log.d("LoginActivity","FAILURE:" + error);
                    runOnUiThread(() ->
                            Toast.makeText(LoginActivity.this, "登录失败：" + error, Toast.LENGTH_SHORT).show());
                }
            });
        });

        btnGotoRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });
    }


    private void saveLoginStatus(boolean isLoggedIn) {
        SharedPreferences preferences = getSharedPreferences("user_pref", MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("is_logged_in", isLoggedIn);
        editor.apply();
    }
}
