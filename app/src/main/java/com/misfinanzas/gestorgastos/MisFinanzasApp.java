package com.misfinanzas.gestorgastos;

import android.app.Application;

import com.misfinanzas.gestorgastos.notifications.NotificationHelper;

/**
 * Clase Application: se ejecuta una sola vez al arrancar el proceso de la app.
 * Aquí se registra el canal de notificaciones (obligatorio en Android 8.0+).
 */
public class MisFinanzasApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.createNotificationChannel(this);
    }
}
