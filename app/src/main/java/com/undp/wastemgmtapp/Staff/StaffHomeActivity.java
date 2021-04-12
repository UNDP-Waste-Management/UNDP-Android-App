package com.undp.wastemgmtapp.Staff;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.drawerlayout.widget.DrawerLayout;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.apollographql.apollo.ApolloCall;
import com.apollographql.apollo.ApolloClient;
import com.apollographql.apollo.api.Error;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.exception.ApolloException;
import com.undp.wastemgmtapp.Common.GPSTracker;
import com.undp.wastemgmtapp.Common.LogInActivity;
import com.undp.wastemgmtapp.Common.SessionManager;
import com.undp.wastemgmtapp.GetStaffQuery;
import com.undp.wastemgmtapp.GetTaskSortedWastesQuery;
import com.undp.wastemgmtapp.GetTaskTrashCollectionsQuery;
import com.undp.wastemgmtapp.GetZoneTrashcansQuery;
import com.undp.wastemgmtapp.R;
import com.undp.wastemgmtapp.Common.SettingsActivity;
import com.google.android.material.navigation.NavigationView;
import com.mapbox.mapboxsdk.Mapbox;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;

public class StaffHomeActivity extends AppCompatActivity {

    private ActionBarDrawerToggle mToggle;
    ApolloClient apolloClient;
    SessionManager session;
    double userLat, userLong;
    TextView textUserName, text_support, trashNumber, taskNumber;
    String TAG = StaffHomeActivity.class.getSimpleName();
    LinearLayout zoneTrashcans, assignedTasks;
    String companyID, userID;
    CardView cardSettings, cardWaste, cardTrashcans, cardTasks;
    TextView name, location, phoneNumber, createdAt, emailID;
    ProgressBar loading;
    AlertDialog dialog;
    String nameText, locationText, phoneNumberText, createdAtText, emailText;
    int sumTasks = 0;
    ArrayList<Object> tasks = new ArrayList<>();
    ProgressBar fetchLoading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Mapbox.getInstance(this, getString(R.string.mapbox_access_token));
        setContentView(R.layout.activity_staff_home);

        Toolbar toolbar = findViewById(R.id.nav_action);
        DrawerLayout drawerLayout = findViewById(R.id.drawer_layout2);
        zoneTrashcans = findViewById(R.id.zone_trash);
        assignedTasks = findViewById(R.id.assigned_tasks);
        trashNumber = findViewById(R.id.trashNumber);
        taskNumber = findViewById(R.id.taskNumber);
        cardSettings = findViewById(R.id.cardSettings);
        cardTasks = findViewById(R.id.cardTask);
        cardWaste = findViewById(R.id.cardWaste);
        fetchLoading = findViewById(R.id.fetchLoading);
        cardTrashcans = findViewById(R.id.cardZone);

        fetchLoading.setVisibility(View.VISIBLE);

        NavigationView navView = findViewById(R.id.staff_navDrawer); // initiate a Navigation View

        try{
            View headerView = navView.getHeaderView(0);
            text_support = headerView.findViewById(R.id.text_support);
            textUserName = headerView.findViewById(R.id.userName);
        }catch (Exception e){
            e.printStackTrace();
        }

        session = new SessionManager(getApplicationContext());

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .build();
        apolloClient = ApolloClient.builder().okHttpClient(httpClient)
                .serverUrl("https://waste-mgmt-api.herokuapp.com/graphql")
                .build();

        setSupportActionBar(toolbar);
        HashMap<String, String> user = session.getUserDetails();
        userID = user.get(SessionManager.KEY_USERID);

        Log.d(TAG, "userID: " + userID);
        if(userID == null || TextUtils.isEmpty(userID)){
            Intent i = new Intent(StaffHomeActivity.this, LogInActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        apolloClient.query(new GetStaffQuery(userID)).enqueue(staffCallback());

        mToggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.open, R.string.close);

        drawerLayout.addDrawerListener(mToggle);
        mToggle.syncState();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        GPSTracker gpsTracker = new GPSTracker(StaffHomeActivity.this, StaffHomeActivity.this);
        userLat = gpsTracker.getLatitude();
        userLong = gpsTracker.getLongitude();

        sumTasks = 0;
        apolloClient.query(new GetTaskSortedWastesQuery()).enqueue(taskSortedCallback());
        apolloClient.query(new GetTaskTrashCollectionsQuery()).enqueue(taskCollectCallback());
        apolloClient.query(new GetZoneTrashcansQuery()).enqueue(trashCallback());

        zoneTrashcans.setOnClickListener(view ->{
            Intent intent = new Intent(StaffHomeActivity.this, ZoneTrashcans.class);
            intent.putExtra("id", userID);intent.putExtra("lat", userLat);intent.putExtra("long", userLong);
            intent.putExtra("companyID", companyID);
            startActivity(intent);
        });

        assignedTasks.setOnClickListener(v -> {
            Intent intent = new Intent(StaffHomeActivity.this, CollectionRequests.class);
            intent.putExtra("id", userID);intent.putExtra("lat", userLat);intent.putExtra("long", userLong);
            startActivity(intent);
        });

        //NavigationView navView = findViewById(R.id.staff_navDrawer); // initiate a Navigation View
        // implement setNavigationSelectedListener event
        navView.setNavigationItemSelectedListener(menuItem -> {
            Log.d(TAG, "onOptionsItemSelected: " + menuItem);
            if(TextUtils.equals(menuItem.toString(), "Logout")){
                AlertDialog.Builder builder = new AlertDialog.Builder(StaffHomeActivity.this);
                builder.setTitle("Log Out").setMessage("Are you sure you want to log out?");

                builder.setPositiveButton("Log Out", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // User clicked OK button
                        session.logoutUser();
                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // User cancelled the dialog
                        dialog.cancel();
                    }
                });
                builder.show(); //show the alert dialog
            } else if((TextUtils.equals(menuItem.toString(), "Assigned Tasks"))){
                Intent intent = new Intent(StaffHomeActivity.this, CollectionRequests.class);
                intent.putExtra("id", userID);intent.putExtra("lat", userLat);intent.putExtra("long", userLong);
                startActivity(intent);

            } else if((TextUtils.equals(menuItem.toString(), "Zone Trash-cans"))){
                Intent intent = new Intent(StaffHomeActivity.this, ZoneTrashcans.class);
                intent.putExtra("id", userID);intent.putExtra("lat", userLat);intent.putExtra("long", userLong);
                startActivity(intent);
            }else if((TextUtils.equals(menuItem.toString(), "Settings"))){
                //going to the settings activity later on
                Intent intent = new Intent(StaffHomeActivity.this, SettingsActivity.class);
                intent.putExtra("id", userID);
                startActivity(intent);
            }
            // add code here what you need on click of items.
            return false;
        });

        cardSettings.setOnClickListener(view ->{
            Intent intent = new Intent(StaffHomeActivity.this, SettingsActivity.class);
            intent.putExtra("id", userID);
            startActivity(intent);
        });

        cardTasks.setOnClickListener(view -> {
            Intent intent = new Intent(StaffHomeActivity.this, CollectionRequests.class);
            intent.putExtra("id", userID);intent.putExtra("lat", userLat);intent.putExtra("long", userLong);
            startActivity(intent);
        });

        cardTrashcans.setOnClickListener(view -> {
            Intent intent = new Intent(StaffHomeActivity.this, ZoneTrashcans.class);
            intent.putExtra("id", userID);intent.putExtra("lat", userLat);intent.putExtra("long", userLong);
            intent.putExtra("companyID", companyID);
            startActivity(intent);
        });

        cardWaste.setOnClickListener(view -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(StaffHomeActivity.this);
            builder.setTitle("Institution Profile"); //set the title for the dialog
            LayoutInflater inflater = (LayoutInflater) StaffHomeActivity.this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            assert inflater != null;
            //build the dialog and set the view from the layout already created
            view = inflater.inflate(R.layout.waste_details, null);
            builder.setView(view);

            emailID = view.findViewById(R.id.email);
            name = view.findViewById(R.id.name);
            location = view.findViewById(R.id.locationText);
            phoneNumber = view.findViewById(R.id.phone);
            createdAt = view.findViewById(R.id.createdAt);
            loading = view.findViewById(R.id.nameLoad);

            //apolloClient.query(new GetStaffQuery(userID)).enqueue(staffCallback());
            emailID.setText("Email:  " + emailText); name.setText("Name:  " + nameText);
            location.setText("Location:  " + locationText);
            createdAt.setText("Date Created:  " + createdAtText);
            phoneNumber.setText("Phone Number: " + phoneNumberText);
            loading.setVisibility(View.GONE);

            builder.setPositiveButton("OK", new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.cancel();
                }
            });
            dialog = builder.create();
            dialog.show();
        });
    }

    @Override
    public void onRestart() {
        super.onRestart();
        //trashNumber.setText(String.valueOf(0));
        //taskNumber.setText(String.valueOf(0));
        tasks.clear();
        //When BACK BUTTON is pressed, the activity on the stack is restarted
        //Do what you want on the refresh procedure here
        apolloClient.query(new GetStaffQuery(userID)).enqueue(staffCallback());
        apolloClient.query(new GetTaskSortedWastesQuery()).enqueue(taskSortedCallback());
        apolloClient.query(new GetTaskTrashCollectionsQuery()).enqueue(taskCollectCallback());
        apolloClient.query(new GetZoneTrashcansQuery()).enqueue(trashCallback());
    }

    public ApolloCall.Callback<GetStaffQuery.Data> staffCallback(){
        return new ApolloCall.Callback<GetStaffQuery.Data>() {
            @Override
            public void onResponse(@NotNull Response<GetStaffQuery.Data> response) {
                GetStaffQuery.Data data = response.getData();

                try{

                    data.staff();
                    runOnUiThread(() -> {
                        fetchLoading.setVisibility(View.GONE);
                        Log.d(TAG, "staff fetched" + data.staff());
                        textUserName.setText(data.staff().fullName());
                        text_support.setText("Staff Member");

                        locationText = data.staff().creator().location();
                        emailText = data.staff().creator().email();
                        createdAtText = data.staff().creator().createdAt();
                        nameText = data.staff().creator().name();
                        phoneNumberText = data.staff().creator().phoneNumber();
                        companyID = data.staff().creator()._id();

                    });

                    if(response.getErrors() != null){
                        List<Error> error = response.getErrors();
                        String errorMessage = error.get(0).getMessage();
                        Log.e(TAG, "an Error in staff query : " + errorMessage );
                        runOnUiThread(() -> {
                            Toast.makeText(StaffHomeActivity.this,
                                    "an Error occurred : " + errorMessage, Toast.LENGTH_LONG).show();

                        });
                    }

                } catch (Exception e){
                    e.printStackTrace();
                }

            }

            @Override
            public void onFailure(@NotNull ApolloException e) {
                Log.e(TAG, "Error", e);
                runOnUiThread(() -> {
                    Toast.makeText(StaffHomeActivity.this,
                            "An error occurred : " + e.getMessage(), Toast.LENGTH_LONG).show();
                    fetchLoading.setVisibility(View.GONE);

                });
            }
        };
    }


    public ApolloCall.Callback<GetTaskTrashCollectionsQuery.Data> taskCollectCallback(){
        return new ApolloCall.Callback<GetTaskTrashCollectionsQuery.Data>() {
            @Override
            public void onResponse(@NotNull Response<GetTaskTrashCollectionsQuery.Data> response) {
                GetTaskTrashCollectionsQuery.Data data = response.getData();

                if(data.taskTrashCollections() == null){

                    if(response.getErrors() == null){
                        Log.e(TAG, "an unknown Error in tasks query : " );
                        runOnUiThread(() -> {
                            Toast.makeText(StaffHomeActivity.this,
                                    "an unknown Error occurred : " , Toast.LENGTH_LONG).show();

                        });
                    } else{
                        List<Error> error = response.getErrors();
                        String errorMessage = error.get(0).getMessage();
                        Log.e(TAG, "an Error in tasks collections query : " + errorMessage );
                        runOnUiThread(() -> {
                            Toast.makeText(StaffHomeActivity.this,
                                    "task collect1: error occurred : " + errorMessage, Toast.LENGTH_SHORT).show();

                        });
                    }
                }else{
                    runOnUiThread(() -> {
                        Log.d(TAG, "tasks collections fetched: " + data.taskTrashCollections());
                        if(!TextUtils.isEmpty(userID)){
                            for(int i=0; i < data.taskTrashCollections().size(); i++){
                                if(userID.equals(data.taskTrashCollections().get(i).staff()._id()) && (data.taskTrashCollections().get(i).completed() == false)){
                                    tasks.add(data.taskTrashCollections().get(i));
                                }
                            }
                        }

                        sumTasks = sumTasks + tasks.size();
                        Log.d(TAG, "sumTasks trash collect: " + sumTasks+ "-" +tasks.size());
                        taskNumber.setText(String.valueOf(tasks.size()));

                    });

                    if(response.getErrors() != null){
                        List<Error> error = response.getErrors();
                        String errorMessage = error.get(0).getMessage();
                        Log.e(TAG, "an Error in tasks collections query : " + errorMessage );
                        runOnUiThread(() -> {
                            Toast.makeText(StaffHomeActivity.this,
                                    "task collect2: error occurred : " + errorMessage, Toast.LENGTH_LONG).show();

                        });
                    }
                }
            }

            @Override
            public void onFailure(@NotNull ApolloException e) {
                Log.e(TAG, "Error", e);
                runOnUiThread(() -> {
                    Toast.makeText(StaffHomeActivity.this,
                            "task collect3: error occurred  : " + e.getMessage(), Toast.LENGTH_LONG).show();

                });
            }
        };
    }

    public ApolloCall.Callback<GetTaskSortedWastesQuery.Data> taskSortedCallback(){
        return new ApolloCall.Callback<GetTaskSortedWastesQuery.Data>() {
            @Override
            public void onResponse(@NotNull Response<GetTaskSortedWastesQuery.Data> response) {
                GetTaskSortedWastesQuery.Data data = response.getData();

                if(data.taskSortedWastes() == null){

                    if(response.getErrors() == null){
                        Log.e(TAG, "an unknown Error in tasks query : " );
                        runOnUiThread(() -> {
                            Toast.makeText(StaffHomeActivity.this,
                                    "task sorted: error occurred : " , Toast.LENGTH_LONG).show();

                        });
                    } else{
                        List<Error> error = response.getErrors();
                        String errorMessage = error.get(0).getMessage();
                        Log.e(TAG, "an Error in tasks Sorted query : " + errorMessage );
                        runOnUiThread(() -> {
                            Toast.makeText(StaffHomeActivity.this,
                                    "task sorted: error occurred  : " + errorMessage, Toast.LENGTH_SHORT).show();

                        });
                    }
                }else{
                    runOnUiThread(() -> {
                        Log.d(TAG, "tasks Sorted fetched: " + data.taskSortedWastes());
                        try{
                            if(!TextUtils.isEmpty(userID)){
                                for(int i=0; i < data.taskSortedWastes().size(); i++){
                                    if(userID.equals(data.taskSortedWastes().get(i).staff()._id()) && (data.taskSortedWastes().get(i).completed() == false)){
                                        tasks.add(data.taskSortedWastes().get(i));
                                    }
                                }
                            }

                            sumTasks = sumTasks + tasks.size();
                            Log.d(TAG, "sumTasks sorted: " + sumTasks+ "-" +tasks.size());
                            taskNumber.setText(String.valueOf(tasks.size()));
                        } catch (Exception e){
                            Toast.makeText(StaffHomeActivity.this,
                                    "task sorted: error occurred : " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }


                    });

                    if(response.getErrors() != null){
                        List<Error> error = response.getErrors();
                        String errorMessage = error.get(0).getMessage();
                        Log.e(TAG, "an Error in tasks Sorted query : " + errorMessage );
                        runOnUiThread(() -> {
                            Toast.makeText(StaffHomeActivity.this,
                                    "task sorted: error occurred : " + errorMessage, Toast.LENGTH_LONG).show();

                        });
                    }
                }
            }

            @Override
            public void onFailure(@NotNull ApolloException e) {
                Log.e(TAG, "Error Sorted", e);
                runOnUiThread(() -> {
                    Toast.makeText(StaffHomeActivity.this,
                            "task sorted: error occurred : " + e.getMessage(), Toast.LENGTH_LONG).show();

                });
            }
        };
    }

    public ApolloCall.Callback<GetZoneTrashcansQuery.Data> trashCallback(){
        return new ApolloCall.Callback<GetZoneTrashcansQuery.Data>() {
            @Override
            public void onResponse(@NotNull Response<GetZoneTrashcansQuery.Data> response) {
                GetZoneTrashcansQuery.Data data = response.getData();

                if(data.trashcans() == null){

                    if(response.getErrors() == null){
                        Log.e(TAG, "an Error in trashcans query : " );
                        runOnUiThread(() -> {
                            Toast.makeText(StaffHomeActivity.this,
                                    "zone: Error occurred : " , Toast.LENGTH_LONG).show();
                        });
                    } else{
                        List<Error> error = response.getErrors();
                        String errorMessage = error.get(0).getMessage();
                        Log.e(TAG, "an Error in trashcans query : " + errorMessage );
                        runOnUiThread(() -> {
                            Toast.makeText(StaffHomeActivity.this,
                                    "zone: error occurred : " + errorMessage, Toast.LENGTH_LONG).show();

                        });
                    }
                }else{
                    runOnUiThread(() -> {
                        Log.d(TAG, "trashcans fetched: " + data.trashcans());
                        try{
                            ArrayList<Object> cans = new ArrayList<>();
                            if(!TextUtils.isEmpty(companyID)){
                                for(int i=0; i < data.trashcans().size(); i++){
                                    if(companyID.equals(data.trashcans().get(i).zone().creator()._id())){
                                        Log.d(TAG, "onResponse: " + data.trashcans().get(i));
                                        cans.add(data.trashcans().get(i));
                                    }
                                }
                            }

                            trashNumber.setText(String.valueOf(cans.size()));
                        } catch (Exception e){
                            e.printStackTrace();
                        }


                    });

                    if(response.getErrors() != null){
                        List<Error> error = response.getErrors();
                        String errorMessage = error.get(0).getMessage();
                        Log.e(TAG, "an Error in staff query : " + errorMessage );
                        runOnUiThread(() -> {
                            Toast.makeText(StaffHomeActivity.this,
                                    "zone: error occurred : " + errorMessage, Toast.LENGTH_LONG).show();

                        });
                    }
                }
            }

            @Override
            public void onFailure(@NotNull ApolloException e) {
                Log.e(TAG, "Error", e);
                runOnUiThread(() -> {
                    Toast.makeText(StaffHomeActivity.this,
                            "zone: error : " + e.getMessage(), Toast.LENGTH_LONG).show();

                });
            }
        };
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mToggle.onOptionsItemSelected(item)){
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

}