package com.undp.wastemgmtapp.normalUser;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.drawerlayout.widget.DrawerLayout;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.apollographql.apollo.ApolloCall;
import com.apollographql.apollo.ApolloClient;
import com.apollographql.apollo.ApolloSubscriptionCall;
import com.apollographql.apollo.api.Error;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.exception.ApolloException;
import com.apollographql.apollo.subscription.WebSocketSubscriptionTransport;
import com.undp.wastemgmtapp.Common.GPSTracker;
import com.undp.wastemgmtapp.Common.LogInActivity;
import com.undp.wastemgmtapp.Common.SessionManager;
import com.undp.wastemgmtapp.GetCollectionNotifsQuery;
import com.undp.wastemgmtapp.GetSortedWasteNotifsQuery;
import com.undp.wastemgmtapp.MonitorService;
import com.undp.wastemgmtapp.NotifCollectionAddedSubscription;
import com.undp.wastemgmtapp.NotifSortedAddedSubscription;
import com.undp.wastemgmtapp.R;
import com.undp.wastemgmtapp.Common.SettingsActivity;
import com.undp.wastemgmtapp.TaskCollectionAddedSubscription;
import com.undp.wastemgmtapp.TaskSortedAddedSubscription;
import com.undp.wastemgmtapp.UserQuery;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.navigation.NavigationView;
import com.mapbox.mapboxsdk.Mapbox;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;

public class UserHomeActivity extends AppCompatActivity{

    private ActionBarDrawerToggle mToggle;
    private final String TAG = UserHomeActivity.class.getSimpleName();
    double userLat, userLong;
    ApolloClient apolloClient, subscriptionClient;
    TextView textUserName;
    LinearLayout linearCollect, linearSorted, gotoSettings, gotoRequests, gotoInstitutions;
    TextView collectNumber, sortedNumber;
    SessionManager session;
    FusedLocationProviderClient mFusedLocationClient;
    TextView sortNumber1, collNumber1;
    String userID;
    ProgressBar fetchLoading;
    ArrayList collectComplete = new ArrayList<>();
    ArrayList collectIncomplete = new ArrayList<>();
    ArrayList sortedComplete = new ArrayList<>();
    ArrayList sortedIncomplete = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        stopService(new Intent(this, MonitorService.class));
        Mapbox.getInstance(this, getString(R.string.mapbox_access_token));
        setContentView(R.layout.activity_user_home);

        Toolbar toolbar = findViewById(R.id.nav_action);
        DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
        CardView cardRequest = findViewById(R.id.cardRequest);
        CardView cardReview = findViewById(R.id.cardReview);
        CardView cardReport = findViewById(R.id.cardReport);
        CardView cardRecord = findViewById(R.id.cardRecord);
        linearCollect = findViewById(R.id.trash_collect);
        linearSorted = findViewById(R.id.sorted_waste);
        collectNumber = findViewById(R.id.collNumber);
        sortedNumber = findViewById(R.id.sortNumber);
        gotoSettings = findViewById(R.id.gotoSettings);
        gotoInstitutions = findViewById(R.id.gotoCompanies);
        gotoRequests = findViewById(R.id.gotoRequests);
        sortNumber1 = findViewById(R.id.sortNumber1);
        collNumber1 = findViewById(R.id.collNumber1);
        fetchLoading = findViewById(R.id.fetchLoading);
        fetchLoading.setVisibility(View.VISIBLE);

        session = new SessionManager(getApplicationContext());

        NavigationView navView = findViewById(R.id.user_navDrawer); // initiate a Navigation View

        View headerView = navView.getHeaderView(0);
        TextView text_support = headerView.findViewById(R.id.text_support);
        textUserName = headerView.findViewById(R.id.userName);
        text_support.setText("User");

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .build();
        apolloClient = ApolloClient.builder().okHttpClient(httpClient)
                .serverUrl("https://waste-mgmt-api.herokuapp.com/graphql")
                .build();
        subscriptionClient = ApolloClient.builder()
                .serverUrl("https://waste-mgmt-api.herokuapp.com/graphql")
                .subscriptionTransportFactory(
                        new WebSocketSubscriptionTransport.Factory("wss://waste-mgmt-api.herokuapp.com/subscriptions", httpClient))
                .okHttpClient(httpClient)
                .build();

        HashMap<String, String> user = session.getUserDetails();
        userID = user.get(SessionManager.KEY_USERID);
        if(userID == null || TextUtils.isEmpty(userID)){
            Intent i = new Intent(UserHomeActivity.this, LogInActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        setSupportActionBar(toolbar);

        mToggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.open, R.string.close);
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        drawerLayout.addDrawerListener(mToggle);
        mToggle.syncState();

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        apolloClient.query(new UserQuery(userID)).enqueue(usersCallBack());
        //apolloClient.query(new ZonesQuery()).enqueue(zonesQuery());
        apolloClient.query(new GetCollectionNotifsQuery()).enqueue(collectCallback());
        apolloClient.query(new GetSortedWasteNotifsQuery()).enqueue(sortedCallback());
        subscriptionClient.subscribe(new NotifCollectionAddedSubscription()).execute(NotifCollectionAdded());
        subscriptionClient.subscribe(new NotifSortedAddedSubscription()).execute(NotifSortedAdded());


        GPSTracker gpsTracker = new GPSTracker(UserHomeActivity.this, UserHomeActivity.this);
        userLat = gpsTracker.getLatitude();
        userLong = gpsTracker.getLongitude();

        if(userLong == 0.0 && userLat == 0.0 ){
            Toast.makeText(UserHomeActivity.this,
                    "Could not obtain location! Enable the gps location or network on your phone and try again!", Toast.LENGTH_LONG).show();
        }
        Log.d(TAG, "Latitude: " + gpsTracker.getLatitude() +"-Longitude: "+ gpsTracker.getLongitude());

        cardRequest.setOnClickListener(v -> {
            Intent intent = new Intent(UserHomeActivity.this, RequestCollection.class);
            intent.putExtra("id", userID);
            intent.putExtra("lat", userLat);
            intent.putExtra("long", userLong);
            startActivity(intent);
        });

        cardReview.setOnClickListener(v -> {
            Intent intent = new Intent(UserHomeActivity.this, ReviewArea.class);
            intent.putExtra("id", userID);
            intent.putExtra("lat", userLat);
            intent.putExtra("long", userLong);
            startActivity(intent);
        });

        cardReport.setOnClickListener(v -> {
            Intent intent = new Intent(UserHomeActivity.this, ReportDumping.class);
            intent.putExtra("id", userID);
            intent.putExtra("lat", userLat);
            intent.putExtra("long", userLong);
            startActivity(intent);
        });

        cardRecord.setOnClickListener(v -> {
            Intent intent = new Intent(UserHomeActivity.this, RecordWaste.class);
            intent.putExtra("id", userID);
            intent.putExtra("lat", userLat);
            intent.putExtra("long", userLong);
            startActivity(intent);
        });

        gotoRequests.setOnClickListener(view -> {
            Intent intent = new Intent(UserHomeActivity.this, MyRequests.class);
            intent.putExtra("id", userID);
            startActivity(intent);
        });

        gotoInstitutions.setOnClickListener(view ->{
            Intent intent = new Intent(UserHomeActivity.this, InstitutionsActivity.class);
            intent.putExtra("id", userID);
            startActivity(intent);
        });

        gotoSettings.setOnClickListener(view -> {
            Intent intent = new Intent(UserHomeActivity.this, SettingsActivity.class);
            intent.putExtra("id", userID);
            startActivity(intent);
        });

        // implement setNavigationSelectedListener event
        navView.setNavigationItemSelectedListener(menuItem -> {
            Log.d(TAG, "onOptionsItemSelected: " + menuItem);
            if(TextUtils.equals(menuItem.toString(), "Logout")){
                AlertDialog.Builder builder = new AlertDialog.Builder(UserHomeActivity.this);
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

                //Intent intent = new Intent(UserHomeActivity.this, LogInActivity.class);
                //startActivity(intent);
            } else if((TextUtils.equals(menuItem.toString(), "Request Collection"))){
                Intent intent = new Intent(UserHomeActivity.this, RequestCollection.class);
                intent.putExtra("id", userID);
                intent.putExtra("lat", userLat);
                intent.putExtra("long", userLong);
                startActivity(intent);
            }else if((TextUtils.equals(menuItem.toString(), "Report Illegal Waste"))){
                Intent intent = new Intent(UserHomeActivity.this, ReportDumping.class);
                intent.putExtra("id", userID);intent.putExtra("lat", userLat);intent.putExtra("long", userLong);
                startActivity(intent);
            }else if((TextUtils.equals(menuItem.toString(), "Review Area"))){
                Intent intent = new Intent(UserHomeActivity.this, ReviewArea.class);
                intent.putExtra("id", userID);intent.putExtra("lat", userLat);intent.putExtra("long", userLong);
                startActivity(intent);
            }else if((TextUtils.equals(menuItem.toString(), "Record Sorted Waste"))){
                Intent intent = new Intent(UserHomeActivity.this, RecordWaste.class);
                intent.putExtra("id", userID);intent.putExtra("lat", userLat);intent.putExtra("long", userLong);
                startActivity(intent);
            }
            // add code here what you need on click of items.
            return false;
        });

    }

    @Override
    public void onRestart() {
        super.onRestart();
        //When BACK BUTTON is pressed, the activity on the stack is restarted
        //Do what you want on the refresh procedure here
        clearLists();
        apolloClient.query(new UserQuery(userID)).enqueue(usersCallBack());
        apolloClient.query(new GetCollectionNotifsQuery()).enqueue(collectCallback());
        apolloClient.query(new GetSortedWasteNotifsQuery()).enqueue(sortedCallback());
    }

    public void clearLists(){
        collectComplete.clear();
        collectIncomplete.clear();
        sortedComplete.clear();
        sortedIncomplete.clear();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mToggle.onOptionsItemSelected(item)){

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public ApolloCall.Callback<UserQuery.Data> usersCallBack(){
        return new ApolloCall.Callback<UserQuery.Data>() {
            @Override
            public void onResponse(@NotNull Response<UserQuery.Data> response) {
                UserQuery.Data data = response.getData();

                if(data.user() == null){

                    if(response.getErrors() == null){
                        Log.e("Apollo", "an Error occurred : " );
                        runOnUiThread(() -> {
                            // Stuff that updates the UI
                            Toast.makeText(UserHomeActivity.this,
                                    "an Error occurred : " , Toast.LENGTH_LONG).show();
                            //errorText.setText();
                            fetchLoading.setVisibility(View.GONE);
                        });

                    } else{
                        List<Error> error = response.getErrors();
                        String errorMessage = error.get(0).getMessage();
                        Log.e("Apollo", "an Error occurred : " + errorMessage );
                        runOnUiThread(() -> {
                            Toast.makeText(UserHomeActivity.this,
                                    "an Error occurred : " + errorMessage, Toast.LENGTH_LONG).show();
                            fetchLoading.setVisibility(View.GONE);

                        });
                    }
                }else{
                    runOnUiThread(() -> {
                        Log.d(TAG, "user fetched" + data.user());
                        textUserName.setText(data.user().fullName());
                        fetchLoading.setVisibility(View.GONE);

                    });

                    if(response.getErrors() != null){
                        List<Error> error = response.getErrors();
                        String errorMessage = error.get(0).getMessage();
                        Log.e("Apollo", "an Error occurred : " + errorMessage );
                        runOnUiThread(() -> {
                            Toast.makeText(UserHomeActivity.this,
                                    "an Error occurred : " + errorMessage, Toast.LENGTH_LONG).show();

                        });
                    }
                }

            }

            @Override
            public void onFailure(@NotNull ApolloException e) {
                Log.e("Apollo", "Error", e);
                runOnUiThread(() -> {
                    Toast.makeText(UserHomeActivity.this,
                            "An error occurred : " + e.getMessage(), Toast.LENGTH_LONG).show();
                    fetchLoading.setVisibility(View.GONE);

                });
            }
        };
    }

    public ApolloCall.Callback<GetSortedWasteNotifsQuery.Data> sortedCallback(){
        return new ApolloCall.Callback<GetSortedWasteNotifsQuery.Data>() {
            @Override
            public void onResponse(@NotNull Response<GetSortedWasteNotifsQuery.Data> response) {
                GetSortedWasteNotifsQuery.Data data = response.getData();



                    if(data.sortedWasteNotications() == null){
                        if(response.getErrors() == null){
                            Log.e("Apollo", "an Error occurred : " );
                            runOnUiThread(() -> {
                                // Stuff that updates the UI
                                Toast.makeText(UserHomeActivity.this,
                                        "an Error occurred : " , Toast.LENGTH_LONG).show();
                            });
                        } else{
                            List<Error> error = response.getErrors();
                            String errorMessage = error.get(0).getMessage();
                            Log.e("Apollo", "an Error occurred : " + errorMessage );
                            runOnUiThread(() -> {
                                Toast.makeText(UserHomeActivity.this,
                                        "an Error occurred : " + errorMessage, Toast.LENGTH_LONG).show();

                            });
                        }
                    }else{
                        runOnUiThread(() -> {

                            for(int i =0; i < data.sortedWasteNotications().size(); i++){
                                if(!data.sortedWasteNotications().get(i).completed() && userID.equals(data.sortedWasteNotications().get(i).creator()._id())){
                                    sortedIncomplete.add(data.sortedWasteNotications().get(i));
                                } else if (data.sortedWasteNotications().get(i).completed() && userID.equals(data.sortedWasteNotications().get(i).creator()._id())) {
                                    sortedComplete.add(data.sortedWasteNotications().get(i));
                                }
                            }
                            int completeSize = sortedComplete.size();
                            int pendingSize = sortedIncomplete.size();
                            Log.d(TAG, "onResponse: " + completeSize +"-"+ pendingSize);
                            sortedNumber.setText(String.valueOf(completeSize));
                            sortNumber1.setText(String.valueOf(pendingSize));

                        });
                        if(response.getErrors() != null){
                            List<Error> error = response.getErrors();
                            String errorMessage = error.get(0).getMessage();
                            Log.e("Apollo", "an Error occurred : " + errorMessage );
                            runOnUiThread(() -> {
                                Toast.makeText(UserHomeActivity.this,
                                        "an Error occurred : " + errorMessage, Toast.LENGTH_LONG).show();

                            });
                        }
                    }


            }

            @Override
            public void onFailure(@NotNull ApolloException e) {
                Log.e("Apollo", "Error", e);
                runOnUiThread(() -> {
                    Toast.makeText(UserHomeActivity.this,
                            "An error occurred : " + e.getMessage(), Toast.LENGTH_LONG).show();

                });
            }
        };
    }

    public ApolloCall.Callback<GetCollectionNotifsQuery.Data> collectCallback(){
        return new ApolloCall.Callback<GetCollectionNotifsQuery.Data>() {
            @Override
            public void onResponse(@NotNull Response<GetCollectionNotifsQuery.Data> response) {
                GetCollectionNotifsQuery.Data data = response.getData();


                    if(data.trashCollectionNotications() == null){

                        if(response.getErrors() == null){
                            Log.e("Apollo", "an Error occurred : " );
                            runOnUiThread(() -> {
                                // Stuff that updates the UI
                                Toast.makeText(UserHomeActivity.this,
                                        "an Error occurred : " , Toast.LENGTH_LONG).show();
                            });
                        } else{
                            List<Error> error = response.getErrors();
                            String errorMessage = error.get(0).getMessage();
                            Log.e("Apollo", "an Error occurred : " + errorMessage );
                            runOnUiThread(() -> {
                                Toast.makeText(UserHomeActivity.this,
                                        "an Error occurred : " + errorMessage, Toast.LENGTH_LONG).show();

                            });
                        }
                    }else{
                        runOnUiThread(() -> {
                            Log.d(TAG, "notifs fetched" + data.trashCollectionNotications());


                            for(int i =0; i < data.trashCollectionNotications().size(); i++){
                                if(!data.trashCollectionNotications().get(i).completed() &&
                                        userID.equals(data.trashCollectionNotications().get(i).creator()._id())){
                                    Log.d(TAG, "it's equal..." + "-" + data.trashCollectionNotications().get(i).creator()._id()
                                    +"-"+userID);
                                    collectIncomplete.add(data.trashCollectionNotications().get(i));
                                } else if(data.trashCollectionNotications().get(i).completed() &&
                                        userID.equals(data.trashCollectionNotications().get(i).creator()._id())) {
                                    collectComplete.add(data.trashCollectionNotications().get(i));
                                }
                            }
                            int completeSize = collectComplete.size();
                            int pendingSize = collectIncomplete.size();
                            Log.d(TAG, "onResponse: " + completeSize +"-"+ pendingSize);
                            collNumber1.setText(String.valueOf(completeSize));
                            collectNumber.setText(String.valueOf(pendingSize));

                        });

                        if(response.getErrors() != null){
                            List<Error> error = response.getErrors();
                            String errorMessage = error.get(0).getMessage();
                            Log.e("Apollo", "an Error occurred : " + errorMessage );
                            runOnUiThread(() -> {
                                Toast.makeText(UserHomeActivity.this,
                                        "an Error occurred : " + errorMessage, Toast.LENGTH_LONG).show();

                            });
                        }
                    }



            }

            @Override
            public void onFailure(@NotNull ApolloException e) {
                Log.e("Apollo", "Error", e);
                runOnUiThread(() -> {
                    Toast.makeText(UserHomeActivity.this,
                            "An error occurred : " + e.getMessage(), Toast.LENGTH_LONG).show();

                });
            }
        };


    }

    public ApolloSubscriptionCall.Callback<NotifSortedAddedSubscription.Data> NotifSortedAdded(){
        return new ApolloSubscriptionCall.Callback<NotifSortedAddedSubscription.Data>() {

            @Override
            public void onResponse(@NotNull Response<NotifSortedAddedSubscription.Data> response) {
                NotifSortedAddedSubscription.Data data = response.getData();
                if(!data.noficationtSortedWasteAdded().completed() && userID.equals(data.noficationtSortedWasteAdded().creator()._id())){
                    sortedIncomplete.add(data.noficationtSortedWasteAdded());
                } else if(data.noficationtSortedWasteAdded().completed() && userID.equals(data.noficationtSortedWasteAdded().creator()._id())) {
                    sortedComplete.add(data.noficationtSortedWasteAdded());
                }

                int completeSize = sortedComplete.size();
                int pendingSize = sortedIncomplete.size();
                sortedNumber.setText(String.valueOf(completeSize));
                sortNumber1.setText(String.valueOf(pendingSize));
            }

            @Override
            public void onFailure(@NotNull ApolloException e) {

            }

            @Override
            public void onCompleted() {

            }

            @Override
            public void onTerminated() {

            }

            @Override
            public void onConnected() {

            }
        };
    }

    public ApolloSubscriptionCall.Callback<NotifCollectionAddedSubscription.Data> NotifCollectionAdded(){
        return new ApolloSubscriptionCall.Callback<NotifCollectionAddedSubscription.Data>() {
            @Override
            public void onResponse(@NotNull Response<NotifCollectionAddedSubscription.Data> response) {

                NotifCollectionAddedSubscription.Data data = response.getData();
                if(!data.noficationtTrashCollectionAdded().completed() && userID.equals(data.noficationtTrashCollectionAdded().creator()._id())){
                    collectIncomplete.add(data.noficationtTrashCollectionAdded());
                } else if(data.noficationtTrashCollectionAdded().completed() && userID.equals(data.noficationtTrashCollectionAdded().creator()._id())) {
                    collectComplete.add(data.noficationtTrashCollectionAdded());
                }

                int completeSize = collectComplete.size();
                int pendingSize = collectIncomplete.size();
                collNumber1.setText(String.valueOf(completeSize));
                collectNumber.setText(String.valueOf(pendingSize));

            }



            @Override
            public void onFailure(@NotNull ApolloException e) {

            }

            @Override
            public void onCompleted() {

            }

            @Override
            public void onTerminated() {

            }

            @Override
            public void onConnected() {

            }
        };
    }

}