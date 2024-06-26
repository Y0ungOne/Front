package com.example.missingapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import com.android.congestionobserver.ActivityContainer;
import com.example.missingapp.databinding.ActivityMainBinding;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private FragmentManager fragmentManager;
    private UserService userService;
    private String jwtToken;
    private int selectedTargetId = -1; // 선택된 보호대상 ID를 저장하는 변수

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 토큰 불러오기
        SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        jwtToken = sharedPreferences.getString("token", null);

        if (jwtToken == null) {
            Toast.makeText(this, "토큰이 유효하지 않습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        userService = RetrofitClient.getLocalClient(jwtToken).create(UserService.class);

        Button btnExplore = findViewById(R.id.btn_explore);
        ImageView imageView = findViewById(R.id.imageView);
        TextView statusText = findViewById(R.id.status_text);
        TextView infoText = findViewById(R.id.info_text);
        RadioGroup radioGroup = findViewById(R.id.radio_group);

        // 보호대상 목록 불러와서 라디오 버튼 추가
        loadProtectedTargets(radioGroup);

        btnExplore.setOnClickListener(v -> {
            // 선택된 보호대상 ID 확인
            int selectedRadioButtonId = radioGroup.getCheckedRadioButtonId();
            if (selectedRadioButtonId != -1) {
                RadioButton selectedRadioButton = findViewById(selectedRadioButtonId);
                selectedTargetId = Integer.parseInt(selectedRadioButton.getTag().toString());

                // 탐색중 텍스트 설정
                statusText.setText("탐색중");
                infoText.setText(""); // 탐색 중에는 정보 텍스트를 비웁니다.

                // 선택된 보호대상 ID를 사용하여 API 호출
                searchProtectedTargetImage(selectedTargetId, statusText, imageView, infoText);
            } else {
                Toast.makeText(this, "보호대상을 선택해주세요.", Toast.LENGTH_SHORT).show();
            }
        });

        fragmentManager = getSupportFragmentManager();

        binding.bottomNavigation.setOnNavigationItemSelectedListener(item -> {
            Intent intent = null;
            int itemId = item.getItemId();
            if (itemId == R.id.navigation_home) {
                intent = new Intent(getApplicationContext(), MainActivity.class);
            } else if (itemId == R.id.navigation_people) {
                intent = new Intent(getApplicationContext(), ActivityContainer.class);
            } else if (itemId == R.id.navigation_mypage) {
                intent = new Intent(getApplicationContext(), Mypage.class);
            }
            if (intent != null) {
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
            }
            return true;
        });

        binding.bottomNavigation.setSelectedItemId(R.id.navigation_home);

        showDecisionPopup();
    }

    private void loadProtectedTargets(RadioGroup radioGroup) {
        userService.getPhotos().enqueue(new Callback<ProtectedTargetResponse>() {
            @Override
            public void onResponse(Call<ProtectedTargetResponse> call, Response<ProtectedTargetResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<ProtectedTargetReadDto> targets = response.body().getProtectedTargetReadDtos();
                    for (ProtectedTargetReadDto target : targets) {
                        RadioButton radioButton = new RadioButton(MainActivity.this);
                        radioButton.setText(target.getName());
                        radioButton.setTag(target.getId());
                        radioGroup.addView(radioButton);
                    }
                } else {
                    Log.e("MainActivity", "보호대상 목록 불러오기 실패: " + response.message());
                }
            }

            @Override
            public void onFailure(Call<ProtectedTargetResponse> call, Throwable t) {
                Log.e("MainActivity", "보호대상 목록 API 호출 실패", t);
            }
        });
    }

    private void searchProtectedTargetImage(int id, TextView statusText, ImageView imageView, TextView infoText) {
        userService.getProtectedTargetImage(id).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    new Thread(() -> {
                        boolean isSaved = saveResponseBodyToDisk(response.body());
                        runOnUiThread(() -> {
                            if (isSaved) {
                                statusText.setText("탐색 완료!");
                                File imgFile = new File(getExternalFilesDir(null) + File.separator + "result.jpg");
                                if (imgFile.exists()) {
                                    Bitmap bitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
                                    imageView.setImageBitmap(bitmap);
                                    // 이미지 로드 후 추가 정보를 가져와서 표시합니다.
                                    getDetectInfo(infoText);
                                }
                            } else {
                                statusText.setText("이미지 저장 실패");
                                Log.e("MainActivity", "이미지 저장 실패");
                            }
                        });
                    }).start();
                } else {
                    statusText.setText("탐색 실패");
                    Log.e("MainActivity", "탐색 실패: " + response.message());
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                statusText.setText("탐색 실패");
                Log.e("MainActivity", "API 호출 실패", t);
            }
        });
    }

    private void getDetectInfo(TextView infoText) {
        userService.getDetectInfo().enqueue(new Callback<String[]>() {
            @Override
            public void onResponse(Call<String[]> call, Response<String[]> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String[] info = response.body();
                    infoText.setText("Section: " + info[1] + ", Floor: " + info[0]);
                } else {
                    infoText.setText("정보를 불러오지 못했습니다.");
                    Log.e("MainActivity", "정보 불러오기 실패: " + response.message());
                }
            }

            @Override
            public void onFailure(Call<String[]> call, Throwable t) {
                infoText.setText("정보 불러오기 실패");
                Log.e("MainActivity", "API 호출 실패", t);
            }
        });
    }

    private boolean saveResponseBodyToDisk(ResponseBody body) {
        try {
            File futureStudioIconFile = new File(getExternalFilesDir(null) + File.separator + "result.jpg");
            InputStream inputStream = null;
            FileOutputStream outputStream = null;

            try {
                byte[] fileReader = new byte[4096];
                long fileSize = body.contentLength();
                long fileSizeDownloaded = 0;

                inputStream = body.byteStream();
                outputStream = new FileOutputStream(futureStudioIconFile);

                while (true) {
                    int read = inputStream.read(fileReader);
                    if (read == -1) {
                        break;
                    }

                    outputStream.write(fileReader, 0, read);
                    fileSizeDownloaded += read;

                    Log.d("MainActivity", "file download: " + fileSizeDownloaded + " of " + fileSize);
                }

                outputStream.flush();
                return true;
            } catch (IOException e) {
                return false;
            } finally {
                if (inputStream != null) {
                    inputStream.close();
                }
                if (outputStream != null) {
                    outputStream.close();
                }
            }
        } catch (IOException e) {
            return false;
        }
    }

    private void showDecisionPopup() {
        // AlertDialog 빌더 생성
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // LayoutInflater를 사용하여 커스텀 레이아웃을 인플레이트
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.custom_alert_dialog, null);
        builder.setView(dialogView);

        // 커스텀 타이틀과 메시지 설정
        TextView customTitle = dialogView.findViewById(R.id.customTitle);
        TextView customMessage = dialogView.findViewById(R.id.customMessage);

        // 버튼 설정
        builder.setNegativeButton("아니오", (dialog, which) -> dialog.dismiss());
        builder.setPositiveButton("예", (dialog, which) -> {
            Intent intent = new Intent(getApplicationContext(), Mypage.class);
            startActivity(intent);
        });

        // AlertDialog 생성 및 표시
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
