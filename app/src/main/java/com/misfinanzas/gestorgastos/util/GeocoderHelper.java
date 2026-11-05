package com.misfinanzas.gestorgastos.util;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PILAR C: Geocodificación inversa.
 * Convierte latitud/longitud en una dirección legible (calle, barrio, ciudad).
 */
public final class GeocoderHelper {

    public interface Callback {
        /** Siempre se llama en el hilo principal. address es null si no se encontró. */
        void onResult(@Nullable String address);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private GeocoderHelper() {
    }

    public static void reverseGeocode(Context context, double lat, double lng, Callback callback) {
        if (!Geocoder.isPresent()) {
            callback.onResult(null);
            return;
        }
        Geocoder geocoder = new Geocoder(context, new Locale("es", "CO"));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // API moderna (Android 13+): asíncrona, no bloquea el hilo principal
            geocoder.getFromLocation(lat, lng, 1, new Geocoder.GeocodeListener() {
                @Override
                public void onGeocode(List<Address> addresses) {
                    MAIN.post(() -> callback.onResult(format(addresses)));
                }

                @Override
                public void onError(@Nullable String errorMessage) {
                    MAIN.post(() -> callback.onResult(null));
                }
            });
        } else {
            // API anterior: es bloqueante, por eso se ejecuta en un hilo secundario
            EXECUTOR.execute(() -> {
                String result = null;
                try {
                    @SuppressWarnings("deprecation")
                    List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
                    result = format(addresses);
                } catch (Exception ignored) {
                    // Sin red o servicio no disponible
                }
                final String finalResult = result;
                MAIN.post(() -> callback.onResult(finalResult));
            });
        }
    }

    @Nullable
    private static String format(@Nullable List<Address> addresses) {
        if (addresses == null || addresses.isEmpty()) return null;
        Address a = addresses.get(0);

        // Formato corto: calle y número, barrio, ciudad
        List<String> parts = new ArrayList<>();
        String street = a.getThoroughfare();
        if (street != null) {
            parts.add(a.getSubThoroughfare() != null ? street + " #" + a.getSubThoroughfare() : street);
        }
        if (a.getSubLocality() != null) parts.add(a.getSubLocality());
        if (a.getLocality() != null) parts.add(a.getLocality());

        if (!parts.isEmpty()) return android.text.TextUtils.join(", ", parts);
        // Si no hay detalle, la línea completa que entrega el Geocoder
        return a.getMaxAddressLineIndex() >= 0 ? a.getAddressLine(0) : null;
    }
}
