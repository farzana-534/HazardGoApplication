package com.example.hazardalertapplication;

import com.android.volley.DefaultRetryPolicy;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import com.example.hazardalertapplication.databinding.ActivityMainBinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private ActivityMainBinding binding;
    private FusedLocationProviderClient fusedLocationClient;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    private final String HAZARDS_URL = "http://10.20.140.152/hazard_api/get_hazards.php";
    private RequestQueue requestQueue;
    private JSONArray cachedHazards;
    private final Map<Integer, BitmapDescriptor> iconCache = new HashMap<>();

    private final int[][] categoryIcons = {
            { // Road Hazards
                    R.drawable.ic_road, R.drawable.road2, R.drawable.road3,
                    R.drawable.road4, R.drawable.road5, R.drawable.road6, R.drawable.road7
            },
            { // Environmental Hazards
                    R.drawable.ic_env, R.drawable.env2, R.drawable.env3,
                    R.drawable.env4, R.drawable.env5, R.drawable.env6, R.drawable.env7
            },
            { // Building Hazards
                    R.drawable.ic_building, R.drawable.building2, R.drawable.building3,
                    R.drawable.building4, R.drawable.building5, R.drawable.building6, R.drawable.building7
            }
    };

    private final String[][] iconNames = {
            {
                    "Road Damaged",
                    "Pothole",
                    "Slippery Road",
                    "Construction",
                    "Road Construction",
                    "Traffic Jam",
                    "Missing Sign"
            },
            {
                    "Drainage Issues",
                    "Pollution",
                    "Garbage Dump",
                    "Bad Odor",
                    "Water Leakage",
                    "Flood",
                    "Street Light Out"
            },
            {
                    "Broken Window",
                    "Unsafe Structure",
                    "Ceiling Damage",
                    "Loose Roof",
                    "Door Damage",
                    "Electrical Hazard",
                    "Lift Out of Service"
            }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        requestQueue = Volley.newRequestQueue(this);

        if (savedInstanceState != null && savedInstanceState.containsKey("cached_hazards")) {
            try {
                String jsonString = savedInstanceState.getString("cached_hazards");
                if (jsonString != null) {
                    cachedHazards = new JSONArray(jsonString);
                }
            } catch (Exception e) {
                android.util.Log.e("MainActivity", "Failed to restore cached hazards", e);
            }
        }

        loadHazardsFromServer();

        setSupportActionBar(binding.toolbarHome);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        binding.fabReport.setOnClickListener(v -> showReportDialog());

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (cachedHazards != null) {
            outState.putString("cached_hazards", cachedHazards.toString());
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        applyMapStyle();
        mMap.getUiSettings().setZoomControlsEnabled(true);
        enableMyLocation();

        if (cachedHazards != null) {
            displayHazards(cachedHazards);
        }
    }

    private void applyMapStyle() {
        if (mMap == null) return;

        boolean isDark = ThemeManager.isDarkMode(this);
        if (isDark) {
            try {
                boolean success = mMap.setMapStyle(
                        MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_night));
                if (!success) {
                    android.util.Log.e("MapStyle", "Style parsing failed.");
                }
            } catch (android.content.res.Resources.NotFoundException e) {
                android.util.Log.e("MapStyle", "Can't find style. Error: ", e);
            }
        } else {
            mMap.setMapStyle(null);
        }
    }

    private void enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }
        mMap.setMyLocationEnabled(true);
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15));
            }
        });

    }

    private void loadHazardsFromServer() {
        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, HAZARDS_URL, null,
                response -> {
                    cachedHazards = response;
                    if (mMap != null) {
                        displayHazards(response);
                    }
                },
                error -> Toast.makeText(this, "Failed to load markers", Toast.LENGTH_SHORT).show());
        requestQueue.add(request);
    }

    private void displayHazards(JSONArray response) {
        if (mMap == null || response == null) return;
        mMap.clear();
        Map<String, Integer> markerCount = new HashMap<>();

        try {
            for (int i = 0; i < response.length(); i++) {
                JSONObject hazard = response.getJSONObject(i);
                double lat = hazard.getDouble("latitude");
                double lng = hazard.getDouble("longitude");
                String category = hazard.getString("category");
                String desc = hazard.getString("description");
                String dateTime = hazard.optString("report_time", "Date not available");
                String iconName = hazard.optString("icon_name", "");

                int markerIconRes;
                if (!iconName.isEmpty()) {
                    markerIconRes = getResources().getIdentifier(iconName, "drawable", getPackageName());
                    if (markerIconRes == 0) markerIconRes = getDefaultIcon(category);
                } else {
                    markerIconRes = getDefaultIcon(category);
                }

                BitmapDescriptor icon = iconCache.get(markerIconRes);
                if (icon == null) {
                    icon = BitmapDescriptorFactory.fromResource(markerIconRes);
                    iconCache.put(markerIconRes, icon);
                }

                String key = String.format("%.5f,%.5f", lat, lng);
                int count = markerCount.getOrDefault(key, 0);
                markerCount.put(key, count + 1);

                double radius = 0.00005;
                double angle = Math.toRadians(count * 45);
                LatLng position = new LatLng(lat + radius * Math.cos(angle), lng + radius * Math.sin(angle));

                String snippet = "Desc: " + desc + "\nTime: " + dateTime;
                mMap.addMarker(new MarkerOptions()
                        .position(position)
                        .title(category)
                        .snippet(snippet)
                        .icon(icon));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int getDefaultIcon(String category) {
        switch (category) {
            case "Road Hazards": return R.drawable.ic_road;
            case "Environmental Hazards": return R.drawable.ic_env;
            case "Building Hazards": return R.drawable.ic_building;
            default: return R.drawable.ic_road;
        }
    }

    private void showReportDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_report, null);

        Spinner spinnerCategory = dialogView.findViewById(R.id.spinner_category);
        Spinner spinnerHazardIcon = dialogView.findViewById(R.id.spinner_hazard_icon);
        EditText etDescription = dialogView.findViewById(R.id.et_description);
        Button btnSubmit = dialogView.findViewById(R.id.btn_submit);
        ImageButton btnClose = dialogView.findViewById(R.id.btn_close);

        AlertDialog dialog = builder.setView(dialogView).create();

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        String[] categories = {"Road Hazards", "Environmental Hazards", "Building Hazards"};
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, categories);
        spinnerCategory.setAdapter(catAdapter);

        final List<Integer> currentActiveIcons = new ArrayList<>();

        spinnerCategory.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                int[] selectedIcons = categoryIcons[position];
                String[] selectedNames = iconNames[position];

                currentActiveIcons.clear();
                for (int icon : selectedIcons) {
                    currentActiveIcons.add(icon);
                }

                ArrayAdapter<Integer> iconAdapter = new ArrayAdapter<Integer>(MainActivity.this, R.layout.item_spinner_hazard, R.id.tv_spinner_text, currentActiveIcons) {
                    @NonNull
                    @Override
                    public View getView(int pos, View convertView, @NonNull ViewGroup parentGroup) {
                        return createIconView(pos, convertView, parentGroup, selectedIcons, selectedNames);
                    }

                    @Override
                    public View getDropDownView(int pos, View convertView, @NonNull ViewGroup parentGroup) {
                        return createIconView(pos, convertView, parentGroup, selectedIcons, selectedNames);
                    }

                    private View createIconView(int pos, View convertView, ViewGroup parentGroup, int[] currentIcons, String[] names) {
                        if (convertView == null) {
                            convertView = getLayoutInflater().inflate(R.layout.item_spinner_hazard, parentGroup, false);
                        }
                        ImageView iv = convertView.findViewById(R.id.iv_spinner_icon);
                        TextView tv = convertView.findViewById(R.id.tv_spinner_text);
                        iv.setImageResource(currentIcons[pos]);

                        if (pos < names.length) {
                            tv.setText(names[pos]);
                        } else {
                            tv.setText("Style " + (pos + 1)); // Fallback
                        }

                        return convertView;
                    }
                };
                spinnerHazardIcon.setAdapter(iconAdapter);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        btnSubmit.setOnClickListener(v -> {
            String category = spinnerCategory.getSelectedItem().toString();
            String description = etDescription.getText().toString().trim();
            int selectedIconPos = spinnerHazardIcon.getSelectedItemPosition();

            if (selectedIconPos == Spinner.INVALID_POSITION || currentActiveIcons.isEmpty()) {
                Toast.makeText(this, "Select an icon", Toast.LENGTH_SHORT).show();
                return;
            }

            int selectedIconResId = currentActiveIcons.get(selectedIconPos);
            String iconName = getResources().getResourceEntryName(selectedIconResId);

            if (description.isEmpty()) {
                Toast.makeText(this, "Enter description", Toast.LENGTH_SHORT).show();
                return;
            }

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                    if (location != null) {
                        submitHazardToServer(location.getLatitude(), location.getLongitude(), category, description, iconName);
                        dialog.dismiss();
                    }
                });
            }
        });


        dialog.show();
    }

    private void submitHazardToServer(double lat, double lng, String category, String description, String iconName) {
        String url = "http://10.20.140.152/hazard_api/report_hazards.php";

        StringRequest request = new StringRequest(Request.Method.POST, url,
                response -> {
                    Toast.makeText(this, "Reported!", Toast.LENGTH_SHORT).show();
                    loadHazardsFromServer();
                },
                error -> {
                    String errorMsg = "Error submitting";
                    if (error.networkResponse != null) {
                        errorMsg += " Status: " + error.networkResponse.statusCode;
                    } else if (error.getMessage() != null) {
                        errorMsg += " - " + error.getMessage();
                    }
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                }) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("latitude", String.valueOf(lat));
                params.put("longitude", String.valueOf(lng));
                params.put("category", category);
                params.put("description", description);
                params.put("icon_name", iconName);
                return params;
            }
        };

        request.setRetryPolicy(new DefaultRetryPolicy(
                10000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));

        if (requestQueue != null) {
            requestQueue.add(request);
        } else {
            Volley.newRequestQueue(this).add(request);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        updateThemeMenuItem(menu);
        return true;
    }

    private void updateThemeMenuItem(Menu menu) {
        MenuItem themeItem = menu.findItem(R.id.action_theme);
        if (themeItem != null) {
            boolean isDark = ThemeManager.isDarkMode(this);
            themeItem.setTitle(isDark ? "☀️ Day" : "🌙 Night");
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            Intent intent = new Intent(MainActivity.this, HomeActivity.class);
            startActivity(intent);
            finish();
            return true;
        } else if (id == R.id.action_theme) {

            boolean isCurrentlyDark = ThemeManager.isDarkMode(this);
            ThemeManager.setDarkMode(this, !isCurrentlyDark);

            recreate();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}