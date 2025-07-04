package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private EditText etUsername, etPassword, etConfirmPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);
        Button btnRegister = findViewById(R.id.btn_register);
        btnRegister.setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            String confirmPassword = etConfirmPassword.getText().toString().trim();

            if( username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "用户名或密码不能为空", Toast.LENGTH_SHORT).show();
                Log.d("登录与注册-注册", "请填写完整信息");
                return;
            }

            if (!password.equals(confirmPassword)) {
                Toast.makeText(this, "两次密码不一致", Toast.LENGTH_SHORT).show();
                Log.d("登录与注册-注册", "用户名已存在");
                return;
            }

            Map<String, String> params = new HashMap<>();
            params.put("username", username);
            params.put("password", password);

            HttpUtil.post("register", params, new HttpUtil.HttpCallback() {
                @Override
                public void onSuccess(String response) {
                    runOnUiThread(() -> {
                        switch (response) {
                            case "SUCCESS":
                                Log.d("登录与注册-注册", "注册成功");
                                startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                                finish();
                                break;
                            case "EXIST":
                                Toast.makeText(RegisterActivity.this, "用户名已存在", Toast.LENGTH_SHORT).show();
                                break;
                            default:
                                Toast.makeText(RegisterActivity.this, "注册失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                @Override
                public void onFailure(String error) {
                    runOnUiThread(() -> {
                        Toast.makeText(RegisterActivity.this, "网络错误：" + error, Toast.LENGTH_SHORT).show();
                        Log.e("RegisterActivity", "请求失败：" + error); // 添加日志
                    });
                }
            });
        });
    }
}