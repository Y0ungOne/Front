package com.android.congestionobserver;

import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.congestionobserver.databinding.FragmentHourBinding;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class HourFragment extends Fragment {
    FragmentHourBinding binding;
    private static final String BASE_URL = "http://223.130.152.183:8080/hourly-congestion?cctvId=1";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHourBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setEvent();

        getCongestionData();
    }


    List<BarEntry> entries = new ArrayList<>();

    private void initBarChart() {

        // 데이터셋 생성
        BarDataSet dataSet = new BarDataSet(entries, "시간별 인구 밀집도");
        setupDataSet(dataSet);

        // BarData 객체 생성 및 데이터 설정
        BarData barData = new BarData(dataSet);
        binding.chBar.setData(barData);

        // X축 설정
        XAxis xAxis = binding.chBar.getXAxis();
        setupXAxis(xAxis);

        // Y축 설정
        YAxis leftAxis = binding.chBar.getAxisLeft();
        YAxis rightAxis = binding.chBar.getAxisRight();
        setupYAxis(leftAxis, rightAxis);

        // 범례 설정
        Legend legend = binding.chBar.getLegend();
        setupLegend(legend);

        // 차트 설명 설정
        Description description = new Description();
        setupDescription(description);
        binding.chBar.setDescription(description);

        // 차트 스타일 설정
        setupChartStyle();
        binding.chBar.animateY(1000);
        binding.chBar.setScaleEnabled(false);
    }

    private void setupDataSet(BarDataSet dataSet) {
        dataSet.setColor(Color.parseColor("#1976D2")); // 막대 색상 설정
        dataSet.setValueTextColor(Color.RED); // 데이터 값 텍스트 색상 설정
    }

    private void setupXAxis(XAxis xAxis) {
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(Color.RED);
        xAxis.setDrawAxisLine(false);
        xAxis.setDrawGridLines(false);
    }

    private void setupYAxis(YAxis leftAxis, YAxis rightAxis) {
        leftAxis.setAxisMinimum(0f);
        leftAxis.setDrawAxisLine(false);
        leftAxis.setTextColor(Color.BLUE);
        rightAxis.setEnabled(false);
    }

    private void setupLegend(Legend legend) {
        legend.setForm(Legend.LegendForm.CIRCLE);
        legend.setTextSize(20f);
        legend.setTextColor(Color.BLACK);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
    }

    private void setupDescription(Description description) {
        description.setText("시간별 인구 밀집도");
    }

    private void setupChartStyle() {
        BarChart barChart = binding.chBar;
        barChart.setDrawGridBackground(false);
        barChart.setDrawBarShadow(false);
        barChart.setDrawBorders(false);
        Description description = barChart.getDescription();
        description.setEnabled(false);
    }

    private void setEvent() {

        binding.chBar.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                // 차트의 값이 선택되었을 때 호출됩니다.
                BarEntry entry = (BarEntry) e;
                String status = entry.getData().toString();

                binding.tvNotify.setText(+(int) entry.getX() + "시 인구 밀집도는 " + entry.getY() + "(" + status + ")" + " 입니다. ");

            }

            @Override
            public void onNothingSelected() {
                // 아무 값도 선택되지 않았을 때 호출됩니다.
            }
        });

    }

    private void getCongestionData() {
        // 서버에서 혼잡도 데이터를 가져오는 로직
        // 서버에서 가져온 데이터를 바탕으로 혼잡도를 계산하여 화면에 표시


        new FetchDataTask().execute(BASE_URL);
    }


    private class FetchDataTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... strings) {
            String urlString = strings[0];
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;
            StringBuilder stringBuilder = new StringBuilder();

            try {
                URL url = new URL(urlString);
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                int responseCode = urlConnection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stringBuilder.append(line);
                    }
                } else {
                    Log.e("##ERROR", "doInBackground: responseCode = " + responseCode);
                }
            } catch (IOException e) {
                Log.e("##ERROR", "doInBackground: error = " + e.getMessage());
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {

                        reader.close();
                    } catch (IOException e) {
                        Log.e("##ERROR", "doInBackground: error = " + e.getMessage());
                    }
                }
            }

            return stringBuilder.toString();
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                // 여기서 결과를 처리합니다. 예를 들어, UI 업데이트 등.
                Log.i("##INFO", "onPostExecute(): result = " + result);
                try {
                    JSONArray jsonArray = new JSONArray(result);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        int period = jsonArray.getJSONObject(i).getInt("period");
                        int average = jsonArray.getJSONObject(i).getInt("average");
                        String status = jsonArray.getJSONObject(i).getString("status");

                        Log.i("##INFO", "onPostExecute(): period = " + period + ", average = " + average + ", status = " + status);

                        entries.add(new BarEntry(i + 1, (float) average, status));

                    }

                    initBarChart();
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }


            } else {
                Log.e("##ERROR", "onPostExecute(): result is null");
            }
        }
    }
}