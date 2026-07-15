package com.example.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;

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

            HttpUtil.post("login", params, new HttpUtil.HttpCallback() {
                @Override
                public void onSuccess(String response) {
                    Log.d("LoginActivity","SUCCESSINHERE");
                    runOnUiThread(() -> {
                        boolean ok = false;
                        boolean isAdmin = false;

                        try {
                            String trimmed = response != null ? response.trim() : "";
                            if (trimmed.startsWith("{")) {
                                JSONObject obj = new JSONObject(trimmed);
                                ok = "SUCCESS".equalsIgnoreCase(obj.optString("status"));
                                isAdmin = "admin".equalsIgnoreCase(obj.optString("role"));
                            } else {
                                ok = "SUCCESS".equalsIgnoreCase(trimmed);
                            }
                        } catch (Exception e) {
                            ok = false;
                        }

                        if (!ok) {
                            Toast.makeText(LoginActivity.this, "用户名或密码错误", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if (!isAdmin && "admin".equalsIgnoreCase(username)) {
                            isAdmin = true;
                        }

                        SharedPreferences preferences = getSharedPreferences("user_pref", MODE_PRIVATE);
                        preferences.edit()
                                .putBoolean("is_logged_in", true)
                                .putString("username", username)
                                .putBoolean("is_admin", isAdmin)
                                .apply();

                        startActivity(new Intent(LoginActivity.this, PlaylistListActivity.class));
                        Log.d("LoginActivity","SUCCESS");
                        finish();
                    });
                }

                @Override
                public void onFailure(String error) {
                    try {
                        Socket s = new Socket(com.example.myapplication.network.ServerConfig.baseUrl()
                                .replace("http://", "")
                                .replace("/", "")
                                .split(":")[0], 8080);
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
