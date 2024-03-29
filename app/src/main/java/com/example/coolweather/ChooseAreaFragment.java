package com.example.coolweather;

import android.animation.TypeEvaluator;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.coolweather.db.City;
import com.example.coolweather.db.County;
import com.example.coolweather.db.Province;
import com.example.coolweather.util.HttpUtil;
import com.example.coolweather.util.Utility;

import org.jetbrains.annotations.NotNull;
import org.litepal.LitePal;

import java.io.IOException;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class ChooseAreaFragment extends Fragment {

    public static final int LEVEL_PROVINCE =0;

    public static final int LEVEL_CITY =1;

    public static final int LEVEL_COUNTRY =2;

    private ProgressDialog progressDialog;

    private Button backButton;

    private TextView titleText;

    private ListView listView;

    private ArrayAdapter<String> adapter;

    private List<String> dataList = new ArrayList<>();

    /*
    * 省列表
    * */
    private List<Province> provinceList = new ArrayList<>();

    /*
    * 市列表
    * */
    private List<City> cityList = new ArrayList<>();

    /*
    * 县列表
    * */
    private List<County> countyList = new ArrayList<>();

    /*
    * 选中的省份
    * */
    private Province selectedProvince ;

    /*
    * 选中的城市
    * */
    private City selectedCity;

    /*
    * 当前选中的级别
    * */
    private  int currentLevel;

    private static final String TAG = "ChooseAreaFragment";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.choose_area, container, false);
        titleText = view.findViewById(R.id.title_text);
        backButton = view.findViewById(R.id.back_button);
        listView  = view.findViewById(R.id.list_area);
        adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1,dataList);
        listView.setAdapter(adapter);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if(currentLevel == LEVEL_PROVINCE){
                    selectedProvince = provinceList.get(position);
                    queryCities();
                }else if(currentLevel == LEVEL_CITY){
                    selectedCity = cityList.get(position);
                    queryCounties();
                }else if(currentLevel == LEVEL_COUNTRY){
                    String weatherId = countyList.get(position).getWeatherId();
                    if(getActivity() instanceof  MainActivity){
                        Intent intent = new Intent(getActivity(), WeatherActivity.class);
                        intent.putExtra("weather_id",weatherId);
                        startActivity(intent);
                        getActivity().finish();
                    }else if(getActivity() instanceof WeatherActivity){
                        WeatherActivity weatherActivity  = (WeatherActivity) getActivity();
                        weatherActivity.drawerLayout.closeDrawers();
                        weatherActivity.swipeRefreshLayout.setRefreshing(true);
                        weatherActivity.requestWeather(weatherId);
                    }


                }
            }
        });

        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(currentLevel == LEVEL_COUNTRY){
                    queryCities();
                }else if(currentLevel == LEVEL_CITY){
                    queryProvince();
                }
            }
        });
        queryProvince();
    }

    /*
    * 查询所有的省，先从数据库里面查询，如果没有在去服务器上面查询
    * */
    private void queryProvince(){
        titleText.setText("中国");
        backButton.setVisibility(View.GONE);
        provinceList = LitePal.findAll(Province.class);
        if(provinceList.size()>0){
            dataList.clear();
            for (Province province : provinceList) {
                dataList.add(province.getProvinceName());
            }
            //通知数据更新
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_PROVINCE;
        }else {
            String address = "http://guolin.tech/api/china";
            queryFromServer(address,LEVEL_PROVINCE);
        }
    }

    /*
    * 查询所有的市，先从数据库里面查询，如果没有在去服务器上面查询
    * */
    private void queryCities(){
        titleText.setText(selectedProvince.getProvinceName());
        backButton.setVisibility(View.VISIBLE);
        cityList = LitePal.where("provinceid = ?", String.valueOf(selectedProvince.getId())).find(City.class);
        if(cityList.size()>0){
            dataList.clear();
            for (City city : cityList) {
                dataList.add(city.getCityName());
            }
            //通知数据更新
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_CITY;
        }else{
            int provinceCode = selectedProvince.getProvinceCode();
            String address =  "http://guolin.tech/api/china/"+provinceCode;
            queryFromServer(address,LEVEL_CITY);
        }
    }

    /*
     * 查询所有的县，先从数据库里面查询，如果没有在去服务器上面查询
     * */
    private void queryCounties(){
        titleText.setText(selectedCity.getCityName());
        backButton.setVisibility(View.VISIBLE);
        countyList = LitePal.where("cityid = ?", String.valueOf(selectedCity.getId())).find(County.class);
        if(countyList.size()>0){
            dataList.clear();
            for (County county : countyList) {
                dataList.add(county.getCountyName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_COUNTRY;
        }else{
            int provinceCode = selectedProvince.getProvinceCode();
            int cityCode = selectedCity.getCityCode();
            String address =  "http://guolin.tech/api/china/"+provinceCode + "/"+ cityCode;
            queryFromServer(address,LEVEL_COUNTRY);
        }
    }

    /*
    * 根据传入的地址从服务器上查询数据
    * */
    private void queryFromServer(String address, final int  type) {
        showProgressDialog();
        HttpUtil.sendOkHttpRequest(address, new Callback() {
            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                String responseText = response.body().string();
                boolean result = false;
                if(LEVEL_PROVINCE == type){
                    result = Utility.handleProvinceResponse(responseText);
                }else if(LEVEL_CITY == type){
                    result = Utility.handleCityResponse(responseText,selectedProvince.getId());
                }else if(LEVEL_COUNTRY== type){
//                    Log.d(TAG, responseText);
                    result = Utility.handleCountyResponse(responseText,selectedCity.getId());
                }
                /*
                * 因为网络查询这是一个很耗时的工作，所以网络请求这个这个函数应该跑在子线程上
                * 然后当把数据查出来的时候，切换到主线程，在主线程上更新UI
                * */
                if(result){
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            closeProgressDialog();
                            if(LEVEL_PROVINCE == type){
                                queryProvince();
                            }else if(LEVEL_CITY == type){
                                queryCities();
                            }else if(LEVEL_COUNTRY== type){
                                queryCounties();
                            }
                        }
                    });
                }
            }
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                //切换到主线程处理逻辑
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        closeProgressDialog();
                        Toast.makeText(getContext(), "加载失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }

        });
    }


    /*
    * 显示对话框
    * */
    private void showProgressDialog(){
        if(progressDialog ==null){
            progressDialog = new ProgressDialog(getActivity());
            progressDialog.setMessage("正在加载");
            progressDialog.setCanceledOnTouchOutside(false);
        }
        progressDialog.show();
    }
    /*
    * 关闭对话框
    * */
    private void closeProgressDialog(){
        if(progressDialog!=null){
            progressDialog.dismiss();
        }
    }

}
