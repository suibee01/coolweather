package com.coolweather.app.activity;

import java.util.ArrayList;
import java.util.List;



import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.coolweather.app.R;
import com.coolweather.app.db.CoolWeatherDB;
import com.coolweather.app.model.City;
import com.coolweather.app.model.County;
import com.coolweather.app.model.Province;
import com.coolweather.app.util.HttpCallbackListener;
import com.coolweather.app.util.HttpUtil;
import com.coolweather.app.util.Utility;
public class ChooseAreaActivity extends Activity {
	public static final int LEVEL_PROVINCE = 0;
	public static final int LEVEL_CITY = 1;
	public static final int LEVEL_COUNTY = 2;
	
	private ProgressDialog progressDialog;
	private TextView titleText;
	private ListView listView;
	private ArrayAdapter<String> adapter;
	private CoolWeatherDB coolWeatherDB;
	private List<String> dataList = new ArrayList<String>();

	/*
	 * 省列表
	 */
	private List<Province> provinceList;
	/*
	 * 市列表
	 */
	private List<City> cityList;
	/*
	 * 县列表
	 */
	private List<County> countyList;
	/*
	 * 选中的省份
	 */
	private Province selectedProvince;
	/*
	 * 选中的城市
	 */
	private City selectedCity;
	/*
	 * 当前选中的级别
	 */
	private int currentLevel;
	
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		if(prefs.getBoolean("city_selected", false)){
			Intent intent = new Intent(this,WeatherActivity.class);
			startActivity(intent);
			finish();
			return;
		}
		
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		//隐藏活动标题
		setContentView(R.layout.choose_area);
		//设置自己的布局
		listView = (ListView)findViewById(R.id.list_view);
		titleText = (TextView)findViewById(R.id.title_text);
		adapter = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,dataList);
		//创建适配器，适配列表的内容
		listView.setAdapter(adapter);
		//把内容布局和适配器管联起来
		coolWeatherDB = CoolWeatherDB.getInstance(this);
		//获取本地数据库实例
		listView.setOnItemClickListener(new OnItemClickListener(){
			public void onItemClick(AdapterView<?> arg0,View view,int index,long arg3){
				if(currentLevel == LEVEL_PROVINCE){
					selectedProvince = provinceList.get(index);
					//当前选择的是省级别
					queryCities();
					//查询城市
				}else if (currentLevel == LEVEL_CITY){
					selectedCity = cityList.get(index);
					queryCounties();
				}else if(currentLevel == LEVEL_COUNTY){
					String countyCode = countyList.get(index).getCountyCode();
					Intent intent = new Intent(ChooseAreaActivity.this,WeatherActivity.class);
					intent.putExtra("county_code", countyCode);
					startActivity(intent);
					finish();
				}
			}
		});
		queryProvinces();//加载省级数据，不用点击事件
	}
	
	/*
	 * 查询全国所有的省，优先从数据库查询，如果没有查询到再去服务器上查询。
	 */
	private void queryProvinces(){
		provinceList = coolWeatherDB.loadProvinces();
		if(provinceList.size()>0){
			dataList.clear();
			//每次都要清除，不然会越来越多
			for(Province province : provinceList){
				dataList.add(province.getProvinceName());
			}
			adapter.notifyDataSetChanged();
			listView.setSelection(0);
			titleText.setText("中国");
			currentLevel = LEVEL_PROVINCE;
		}else{
			queryFromServer(null,"province");
			//从服务器中查询
		}
	}
	/*
	 * 查询选中省内所有的市，优先从数据库查询，如果没有查询到再去服务器上查询。
	 */
	private void queryCities(){
		cityList = coolWeatherDB.loadCities(selectedProvince.getId());
		//从数据库里获取选中的省下的城市
		if(cityList.size()>0){
			dataList.clear();
			for(City city : cityList){
				dataList.add(city.getCityName());
			}
			adapter.notifyDataSetChanged();
			listView.setSelection(0);
			titleText.setText(selectedProvince.getProvinceName());
			//标题是选中的省名字
			currentLevel = LEVEL_CITY;
		}else{
			queryFromServer(selectedProvince.getProvinceCode(),"city");
		}
	}
	/*
	 * 查询选中市内所有的县，优先从数据库查询，如果没有查询到再去服务器上查询。
	 */
	private void queryCounties(){
		countyList = coolWeatherDB.loadCounties(selectedCity.getId());
		if(countyList.size()>0){
			dataList.clear();
			for(County county : countyList){
				dataList.add(county.getCountyName());
			}
			adapter.notifyDataSetChanged();
			listView.setSelection(0);
			titleText.setText(selectedCity.getCityName());
			currentLevel = LEVEL_COUNTY;
		}else{
			queryFromServer(selectedCity.getCityCode(),"county");
		}
	}

	/*
	 * 根据传入的代号和类型从服务器上查询省县数据
	 */
	private void queryFromServer(final String code,final String type) {
		// TODO Auto-generated method stub
		String address;
		if(!TextUtils.isEmpty(code)){
			address = "http://www.weather.com.cn/data/list3/city"+code+".xml";
			//服务器的地址
		}else{
			address = "http://www.weather.com.cn/data/list3/city.xml";
		}
		showProgressDialog();
		HttpUtil.sendHttpRequest(address, new HttpCallbackListener(){
			//发送服务器请求
			public void onFinish(String response){
				boolean result = false;
				if("province".equals(type)){
					result = Utility.handleProvincesResponse(coolWeatherDB,response);
					//解析是省的数据
				}else if("city".equals(type)){
					result = Utility.handleCitiesResponse(coolWeatherDB, response, selectedProvince.getId());
				}else if("county".equals(type)){
					result = Utility.handleCountiesResponse(coolWeatherDB, response, selectedCity.getId());
				}
				if(result){
					//解析完数据后，通过runonUiThread（）方法回到主线程处理逻辑
					runOnUiThread(new Runnable(){
						public void run(){
							closeProgressDialog();
							if("province".equals(type)){
								queryProvinces();
							}else if("city".equals(type)){
								queryCities();
							}else if("county".equals(type)){
								queryCounties();
							}
						}

					
					});
				}
			}
			
			public void onError(Exception e){
				//通过runOnUiThread()方法回到主线程处理逻辑
				runOnUiThread(new Runnable(){
					public void run(){
						closeProgressDialog();

						 Toast.makeText(ChooseAreaActivity.this,"加载失败", Toast.LENGTH_SHORT).show();
					}

				
				});
			}
		});
		
	}
/*
 * 显示进度对话框
 */
	private void showProgressDialog() {
		// TODO Auto-generated method stub
		if(progressDialog == null){
			progressDialog = new ProgressDialog(this);
			progressDialog.setMessage("正在加载...");
			progressDialog.setCanceledOnTouchOutside(false);
		}
		progressDialog.show();
	}
	/*
	 * 关闭进度对话框
	 */
	private void closeProgressDialog() {
		// TODO Auto-generated method stub
		if(progressDialog!=null){
			progressDialog.dismiss();
		}
	}
	/*
	 * 捕获Back按键，根据当前的级别来判断，此时应该返回市列表、省列表、还是直接退出
	 */
	public void onBackPressed(){
		if(currentLevel == LEVEL_COUNTY){
			queryCities();
		}else if (currentLevel == LEVEL_CITY){
			queryProvinces();
		}else{
			finish();
		}
	}
}
