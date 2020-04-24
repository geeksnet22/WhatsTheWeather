package com.example.android.whatstheweather.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.android.whatstheweather.R;
import com.example.android.whatstheweather.types.Coordinates;
import com.example.android.whatstheweather.types.OverallData;
import com.example.android.whatstheweather.utils.CommonUtilFunctions;
import com.example.android.whatstheweather.utils.DataLayoutSetter;
import com.example.android.whatstheweather.utils.DatabaseHandler;
import com.example.android.whatstheweather.utils.ExtractData;
import com.example.android.whatstheweather.utils.JSONFileReader;
import com.example.android.whatstheweather.utils.LocationDataProcessor;
import com.example.android.whatstheweather.utils.LocationServices;
import com.example.android.whatstheweather.utils.LocationsStorage;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {

    private Geocoder geocoder;

    private DrawerLayout drawer;
    private ActionBarDrawerToggle toggle;

    private List<String> locationSuggestionsList;
    private ArrayAdapter suggestedLocationsAdaptor;
    private ListView suggestedLocationsView;

    private List<String> favLocationsList;
    private ArrayAdapter favLocationsAdaptor;

    private ListView favLocationsView;

    private SearchView searchView;

    private Toolbar toolbar;

    private boolean removeLocation;

    private DatabaseHandler databaseHandler;

    private LocationServices locationServices;

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeContent(this);

        readJsonFile(this);

        if (locationServices.getLocation(this) != null) {
            try {
                fetchDataAndSetupLayout();
            } catch (InterruptedException | ExecutionException | JSONException e) {
                e.printStackTrace();
            }
        }
        else {
            locationServices.promptLocationPermission(this, this);
            findViewById(R.id.mainScroll).setVisibility(View.GONE);
        }

        setupRefreshListener();

        setupFavLocationsNavigationDrawer(this);

        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        suggestedLocationsView.setVisibility(View.INVISIBLE);
        Map<String, ?> favLocationsMap = getSharedPreferences("FAV_LOCS", MODE_PRIVATE).getAll();
        for (Map.Entry<String, ?> entry: favLocationsMap.entrySet()) {
            if (!favLocationsList.contains(entry.getValue().toString())) {
                favLocationsList.add(entry.getValue().toString());
            }
        }
        favLocationsAdaptor.notifyDataSetChanged();
        if (searchView != null) {
            searchView.setQuery("", false);
            searchView.setIconified(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        databaseHandler.close();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.search_menu, menu);
        MenuItem searchViewItem = menu.findItem(R.id.app_bar_search);
        searchView = (SearchView) searchViewItem.getActionView();

        setupLocationSearch(searchView, this);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (toggle.onOptionsItemSelected(item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        }
        else {
            super.onBackPressed();
        }
    }

    public void setupLocationSearch(final SearchView searchView, final Context context) {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                try {
                    String rawData = CommonUtilFunctions.getRawDataFromLocationName(query, new Geocoder(context),
                            LocationsStorage.locationsMap);
                    if (rawData == null) {
                        Toast.makeText(context, "Location not found. Please provide more information " +
                                "(eg. country name).", Toast.LENGTH_SHORT).show();
                        return true;
                    }

                    CommonUtilFunctions.addLocationToStorage(query, geocoder, databaseHandler);

                    Intent searchedLocationIntent = new Intent(context, SearchedLocationActivity.class);
                    searchedLocationIntent.putExtra("rawData", rawData);
                    searchedLocationIntent.putExtra("location", query);
                    startActivity(searchedLocationIntent);
                    return false;
                }
                catch (IOException | InterruptedException | ExecutionException | JSONException e) {
                    e.printStackTrace();
                    return true;
                }
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (newText.length() == 0) {
                    suggestedLocationsView.setVisibility(View.INVISIBLE);
                }
                else {
                    suggestedLocationsView.setVisibility(View.VISIBLE);
                }
                locationSuggestionsList.clear();
                suggestedLocationsAdaptor.notifyDataSetChanged();
                if (LocationsStorage.isSafeToRead) {
                    for (Map.Entry<String, Coordinates> e: LocationsStorage.locationsMap.entrySet()) {
                        if (e.getKey().startsWith(newText)) {
                            locationSuggestionsList.add(e.getKey());
                            suggestedLocationsAdaptor.notifyDataSetChanged();
                        }
                    }
                }
                return false;
            }
        });

        suggestedLocationsView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                searchView.setQuery(suggestedLocationsView.getItemAtPosition(position).toString(), false);
            }
        });
    }

    private void setupRefreshListener() {
        final SwipeRefreshLayout pullToRefresh = findViewById(R.id.pullToRefresh);
        pullToRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                try {
                    fetchDataAndSetupLayout();
                } catch (InterruptedException | ExecutionException | JSONException e) {
                    e.printStackTrace();
                }
                pullToRefresh.setRefreshing(false);
            }
        });
    }

    private void setupFavLocationsNavigationDrawer(final Context context) {
        removeLocation = false;
        findViewById(R.id.doneRemovingLocations).setVisibility(View.GONE);
        drawer = findViewById(R.id.drawer_layout);
        toggle = new ActionBarDrawerToggle(this, drawer, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        favLocationsList = new ArrayList<String>();
        favLocationsAdaptor = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, favLocationsList);
        favLocationsView = findViewById(R.id.favLocationsList);
        favLocationsView.setAdapter(favLocationsAdaptor);

        favLocationsView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (removeLocation) {
                    SharedPreferences.Editor editor = getSharedPreferences("FAV_LOCS", MODE_PRIVATE).edit();
                    editor.remove(favLocationsView.getItemAtPosition(position).toString());
                    editor.apply();
                    favLocationsList.remove(position);
                    favLocationsAdaptor.notifyDataSetChanged();
                }
                else {
                    findViewById(R.id.doneRemovingLocations).setVisibility(View.GONE);
                    Intent intent = new Intent(context, SearchedLocationActivity.class);
                    try {
                        String rawData = CommonUtilFunctions.getRawDataFromLocationName(favLocationsView.getItemAtPosition(position).toString(),
                                geocoder, LocationsStorage.locationsMap);
                        intent.putExtra("rawData", rawData);
                        intent.putExtra("location",favLocationsView.getItemAtPosition(position).toString());
                        startActivity(intent);
                    } catch (ExecutionException | InterruptedException | IOException |JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        findViewById(R.id.addLocation).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(context, AddFavLocationActivity.class);
                startActivity(intent);
            }
        });

        findViewById(R.id.removeLocation).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                removeLocation = true;
                findViewById(R.id.doneRemovingLocations).setVisibility(View.VISIBLE);
            }
        });

        findViewById(R.id.doneRemovingLocations).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                removeLocation = false;
                findViewById(R.id.doneRemovingLocations).setVisibility(View.GONE);
            }
        });
    }


    private void initializeContent(final Context context) {
        /* setup location services */
        locationServices = new LocationServices((LocationManager) this.getSystemService(Context.LOCATION_SERVICE));
        LocationsStorage.initializeLocationMap();
        databaseHandler = new DatabaseHandler(context);
        geocoder = new Geocoder(this);
        toolbar = findViewById(R.id.toolbar);
        locationSuggestionsList = new ArrayList<>();
        suggestedLocationsAdaptor = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1,
                locationSuggestionsList);
        suggestedLocationsView = findViewById(R.id.locationsList);
        suggestedLocationsView.setAdapter(suggestedLocationsAdaptor);
        suggestedLocationsView.setVisibility(View.INVISIBLE);
    }

    private void readJsonFile(Context context) {
        Intent  intent = new Intent(context, JSONFileReader.class);
        intent.putExtra("fileName", "citylist.json");
        startService(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (permissions.length == 0) {
            toolbar.setTitle("Home");
            findViewById(R.id.mainScroll).setVisibility(View.GONE);
        }

        try {
            findViewById(R.id.mainScroll).setVisibility(View.VISIBLE);
            fetchDataAndSetupLayout();
        }
        catch (InterruptedException | ExecutionException | JSONException e) {
            e.printStackTrace();
        }
    }

    private void fetchDataAndSetupLayout() throws InterruptedException, ExecutionException,
            JSONException {

        /* get user location */
        Location userLocation = locationServices.getLocation(this);
        String rawData = new ExtractData().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
                userLocation.getLatitude(), userLocation.getLongitude()).get();

        LocationDataProcessor locationDataProcessor = new LocationDataProcessor(new Pair<Context, String>(this, rawData));

        OverallData data = locationDataProcessor.fetchWeatherData(new Pair<Context, String>(this, rawData));

        toolbar.setTitle(data.currentData.locationName);

        DataLayoutSetter.setDataLayout(this, this, data.currentData, data.hourlyData,
                data.dailyData, data.detailsData);

    }


}
