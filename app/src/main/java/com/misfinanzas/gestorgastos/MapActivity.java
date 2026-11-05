package com.misfinanzas.gestorgastos;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
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
 *  - Pines personalizados (verde = ingreso, rojo = gasto) donde se crearon las transacciones,
 *    con una etiqueta siempre visible: concepto, valor y (para el Administrador) el dueño.
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
                        .icon(createLabeledPin(t))
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

    /**
     * Marcador personalizado: una etiqueta con el concepto y el valor (y el dueño si
     * el usuario es Administrador) sobre el icono de ubicación pintado con el color
     * del tipo (verde = ingreso, rojo = gasto). Así se lee sin tener que tocar el pin.
     */
    private BitmapDescriptor createLabeledPin(Transaction t) {
        int typeColor = ContextCompat.getColor(this, t.isIncome() ? R.color.income : R.color.expense);
        Drawable drawable = ContextCompat.getDrawable(this, R.drawable.ic_location);
        if (drawable == null) return BitmapDescriptorFactory.defaultMarker();
        drawable = DrawableCompat.wrap(drawable.mutate());
        DrawableCompat.setTint(drawable, typeColor);

        // Textos de la etiqueta
        String concept = ellipsize(t.getConcepto(), 22);
        String amount = (t.isIncome() ? "+ " : "- ") + currencyFormatter.format(t.getMonto());
        String owner = null;
        if (session.isAdmin() && t.getUserEmail() != null) {
            String email = t.getUserEmail();
            int at = email.indexOf('@');
            owner = ellipsize(at > 0 ? email.substring(0, at) : email, 22);
        }

        Paint conceptPaint = textPaint(12, ContextCompat.getColor(this, R.color.text_primary), true);
        Paint amountPaint = textPaint(12, typeColor, true);
        Paint ownerPaint = textPaint(10, ContextCompat.getColor(this, R.color.text_secondary), false);

        int pad = dp(6);
        int lineGap = dp(2);
        float textWidth = Math.max(conceptPaint.measureText(concept), amountPaint.measureText(amount));
        if (owner != null) textWidth = Math.max(textWidth, ownerPaint.measureText(owner));

        float h1 = lineHeight(conceptPaint), h2 = lineHeight(amountPaint), h3 = owner != null ? lineHeight(ownerPaint) : 0;
        int bubbleW = (int) Math.ceil(textWidth) + pad * 2;
        int bubbleH = (int) Math.ceil(h1 + h2 + h3 + lineGap * (owner != null ? 2 : 1)) + pad * 2;
        int arrow = dp(6);
        int pinSize = dp(36);
        int stroke = dp(2);

        int width = Math.max(bubbleW, pinSize) + stroke * 2;
        int height = bubbleH + arrow + pinSize + stroke * 2;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        float cx = width / 2f;

        // Burbuja blanca con borde del color del tipo y flecha hacia el pin
        RectF bubble = new RectF(cx - bubbleW / 2f, stroke, cx + bubbleW / 2f, stroke + bubbleH);
        Path arrowPath = new Path();
        arrowPath.moveTo(cx - arrow, bubble.bottom - 1);
        arrowPath.lineTo(cx + arrow, bubble.bottom - 1);
        arrowPath.lineTo(cx, bubble.bottom + arrow);
        arrowPath.close();

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(0xFFFFFFFF);
        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setColor(typeColor);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(stroke);

        canvas.drawRoundRect(bubble, dp(8), dp(8), border);
        canvas.drawRoundRect(bubble, dp(8), dp(8), fill);
        canvas.drawPath(arrowPath, fill);

        float y = bubble.top + pad - conceptPaint.ascent();
        canvas.drawText(concept, cx, y, conceptPaint);
        y += conceptPaint.descent() + lineGap - amountPaint.ascent();
        canvas.drawText(amount, cx, y, amountPaint);
        if (owner != null) {
            y += amountPaint.descent() + lineGap - ownerPaint.ascent();
            canvas.drawText(owner, cx, y, ownerPaint);
        }

        // Pin con círculo blanco detrás del agujero para que resalte
        int pinTop = (int) bubble.bottom + arrow;
        int pinLeft = (int) (cx - pinSize / 2f);
        canvas.drawCircle(cx, pinTop + pinSize * 9f / 24f, pinSize * 3.2f / 24f, fill);
        drawable.setBounds(pinLeft, pinTop, pinLeft + pinSize, pinTop + pinSize);
        drawable.draw(canvas);
        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    private Paint textPaint(int sp, int color, boolean bold) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(sp * getResources().getDisplayMetrics().scaledDensity);
        if (bold) paint.setTypeface(Typeface.DEFAULT_BOLD);
        return paint;
    }

    private static float lineHeight(Paint paint) {
        return paint.descent() - paint.ascent();
    }

    private static String ellipsize(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
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
