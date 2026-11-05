package com.misfinanzas.gestorgastos;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.misfinanzas.gestorgastos.database.FirestoreManager;
import com.misfinanzas.gestorgastos.model.Transaction;
import com.misfinanzas.gestorgastos.session.SessionManager;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * PILAR C: Mapa interactivo con Google Maps SDK.
 *  - Pines personalizados (verde = ingreso, rojo = gasto) donde se crearon las transacciones.
 *  - Controles de zoom y alternancia de capas Normal / Satélite.
 *  - Ventana de información (InfoWindow) al tocar cada pin.
 *  - Los pines se actualizan en tiempo real (addSnapshotListener) y respetan el rol.
 */
public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    public static final String EXTRA_LAT = "extra_lat";
    public static final String EXTRA_LNG = "extra_lng";

    private static final String TAG = "MapActivity";
    private static final LatLng DEFAULT_CENTER = new LatLng(4.6097, -74.0817); // Bogotá

    private GoogleMap map;
    private TextView tvMapMessage;
    private SessionManager session;
    private FirestoreManager firestoreManager;
    private ListenerRegistration listenerRegistration;

    private final Map<String, Transaction> markerData = new HashMap<>();
    private final NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US);
    private BitmapDescriptor incomePin, expensePin;
    private boolean cameraPositioned = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        session = new SessionManager(this);
        firestoreManager = new FirestoreManager();
        tvMapMessage = findViewById(R.id.tv_map_message);
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        if (!BuildConfig.MAPS_KEY_CONFIGURED) {
            showMessage(getString(R.string.map_key_missing));
        }

        // Alternancia de capas
        MaterialButtonToggleGroup toggle = findViewById(R.id.toggle_map_type);
        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || map == null) return;
            map.setMapType(checkedId == R.id.btn_layer_satellite
                    ? GoogleMap.MAP_TYPE_HYBRID   // satélite con nombres de calles
                    : GoogleMap.MAP_TYPE_NORMAL);
        });

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_fragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        map = googleMap;
        incomePin = createPin(R.color.income);
        expensePin = createPin(R.color.expense);

        // Controles de zoom, brújula y barra de herramientas
        map.getUiSettings().setZoomControlsEnabled(true);
        map.getUiSettings().setCompassEnabled(true);
        map.getUiSettings().setMapToolbarEnabled(true);
        map.setPadding(0, dp(110), 0, 0);

        // Punto azul "Mi ubicación" si el permiso ya fue concedido
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                map.setMyLocationEnabled(true);
            } catch (SecurityException ignored) {
            }
        }

        map.setInfoWindowAdapter(new TransactionInfoWindowAdapter());
        map.setOnInfoWindowClickListener(marker -> {
            Transaction t = markerData.get(marker.getId());
            if (t != null && t.getId() != null) {
                Intent intent = new Intent(this, TransactionFormActivity.class);
                intent.putExtra("DOCUMENT_ID", t.getId());
                startActivity(intent);
            }
        });

        // Si se abrió desde el formulario, centrar en esa ubicación
        if (getIntent().hasExtra(EXTRA_LAT) && getIntent().hasExtra(EXTRA_LNG)) {
            LatLng focus = new LatLng(getIntent().getDoubleExtra(EXTRA_LAT, 0), getIntent().getDoubleExtra(EXTRA_LNG, 0));
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(focus, 16f));
            cameraPositioned = true;
        } else {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_CENTER, 11f));
        }

        listenToTransactions();
    }

    /** Pines en tiempo real: si alguien crea una transacción con ubicación, aparece al instante. */
    private void listenToTransactions() {
        listenerRegistration = firestoreManager.listenToTransactions(session.isAdmin(), session.getUid(), (snapshots, error) -> {
            if (error != null) {
                Log.e(TAG, "Error al escuchar transacciones", error);
                Toast.makeText(this, "Error al cargar los pines: " + error.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }
            if (snapshots == null || map == null) return;

            map.clear();
            markerData.clear();
            LatLngBounds.Builder bounds = new LatLngBounds.Builder();
            int count = 0;

            for (DocumentSnapshot doc : snapshots.getDocuments()) {
                Transaction t = doc.toObject(Transaction.class);
                if (t == null || !t.hasLocation()) continue;
                if (t.getId() == null) t.setId(doc.getId());

                LatLng position = new LatLng(t.getLatitud(), t.getLongitud());
                Marker marker = map.addMarker(new MarkerOptions()
                        .position(position)
                        .title(t.getConcepto())
                        .icon(t.isIncome() ? incomePin : expensePin)
                        .anchor(0.5f, 1f));
                if (marker != null) {
                    markerData.put(marker.getId(), t);
                }
                bounds.include(position);
                count++;
            }

            if (count == 0) {
                if (BuildConfig.MAPS_KEY_CONFIGURED) showMessage(getString(R.string.map_no_locations));
                return;
            }
            if (BuildConfig.MAPS_KEY_CONFIGURED) tvMapMessage.setVisibility(View.GONE);

            // Encuadrar todos los pines la primera vez
            if (!cameraPositioned) {
                cameraPositioned = true;
                if (count == 1) {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(bounds.build().getCenter(), 15f));
                } else {
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), dp(64)));
                }
            }
        });
    }

    private void showMessage(String message) {
        tvMapMessage.setText(message);
        tvMapMessage.setVisibility(View.VISIBLE);
    }

    /** Marcador personalizado: el icono de ubicación de la app pintado con el color del tipo. */
    private BitmapDescriptor createPin(int colorRes) {
        Drawable drawable = ContextCompat.getDrawable(this, R.drawable.ic_location);
        if (drawable == null) return BitmapDescriptorFactory.defaultMarker();
        drawable = DrawableCompat.wrap(drawable.mutate());
        DrawableCompat.setTint(drawable, ContextCompat.getColor(this, colorRes));

        int size = dp(44);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // Círculo blanco detrás del agujero del pin para que resalte sobre el mapa
        Paint white = new Paint(Paint.ANTI_ALIAS_FLAG);
        white.setColor(0xFFFFFFFF);
        canvas.drawCircle(size / 2f, size * 9f / 24f, size * 3.2f / 24f, white);

        drawable.setBounds(0, 0, size, size);
        drawable.draw(canvas);
        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    /** Ventana de información contextual con los datos de la transacción. */
    private class TransactionInfoWindowAdapter implements GoogleMap.InfoWindowAdapter {
        private final View view = getLayoutInflater().inflate(R.layout.view_info_window, null);

        @Nullable
        @Override
        public View getInfoWindow(@NonNull Marker marker) {
            Transaction t = markerData.get(marker.getId());
            if (t == null) return null;

            ((TextView) view.findViewById(R.id.tv_info_concept)).setText(t.getConcepto());

            TextView amount = view.findViewById(R.id.tv_info_amount);
            String formatted = currencyFormatter.format(t.getMonto());
            amount.setText((t.isIncome() ? "+ " : "- ") + formatted);
            amount.setTextColor(ContextCompat.getColor(MapActivity.this, t.isIncome() ? R.color.income : R.color.expense));

            ((TextView) view.findViewById(R.id.tv_info_details)).setText(t.getCategoria() + " • " + t.getFecha());

            TextView address = view.findViewById(R.id.tv_info_address);
            if (t.getDireccion() != null) {
                address.setText(t.getDireccion());
                address.setVisibility(View.VISIBLE);
            } else {
                address.setVisibility(View.GONE);
            }

            TextView owner = view.findViewById(R.id.tv_info_owner);
            if (session.isAdmin() && t.getUserEmail() != null) {
                owner.setText(t.getUserEmail());
                owner.setVisibility(View.VISIBLE);
            } else {
                owner.setVisibility(View.GONE);
            }
            return view;
        }

        @Nullable
        @Override
        public View getInfoContents(@NonNull Marker marker) {
            return null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listenerRegistration != null) {
            listenerRegistration.remove();
        }
    }
}
