package com.example.myapplication;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
//发送请求
public class HttpUtil {
    private static final String BASE_URL = "http://192.168.209.196:8080/";

    public static void post(String endpoint, Map<String, String> params,
                            final HttpCallback callback) {
        OkHttpClient client = new OkHttpClient();
        FormBody.Builder builder = new FormBody.Builder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            builder.add(entry.getKey(), entry.getValue());
        }

        Request request = new Request.Builder()
                .url(BASE_URL + endpoint)
                .post(builder.build())
                .build();

        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body().string());
                }
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onFailure(e.getMessage());
            }
        });
    }

    public interface HttpCallback {
        void onSuccess(String response);
        void onFailure(String error);
    }
}
