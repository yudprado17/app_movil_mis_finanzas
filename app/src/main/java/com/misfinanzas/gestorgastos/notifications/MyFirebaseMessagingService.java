package com.misfinanzas.gestorgastos.notifications;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.misfinanzas.gestorgastos.database.FirestoreManager;
import com.misfinanzas.gestorgastos.session.SessionManager;

import java.util.Map;

/**
 * PILAR D: Infraestructura FCM (Firebase Cloud Messaging).
 *
 * Recibe los mensajes push enviados desde la consola de Firebase
 * (Messaging → Nueva campaña → Notificaciones).
 *  - Con la app en segundo plano, Android muestra la notificación automáticamente
 *    usando el canal e icono declarados en el AndroidManifest.
 *  - Con la app abierta, llega a onMessageReceived() y la mostramos nosotros.
 */
public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);
        Log.d(TAG, "Mensaje push recibido de: " + message.getFrom());

        String title = "Mis Finanzas";
        String body = "";

        RemoteMessage.Notification notification = message.getNotification();
        if (notification != null) {
            if (notification.getTitle() != null) title = notification.getTitle();
            if (notification.getBody() != null) body = notification.getBody();
        }

        // Mensajes de datos (data payload) también se muestran
        Map<String, String> data = message.getData();
        if (data.containsKey("title")) title = data.get("title");
        if (data.containsKey("body")) body = data.get("body");

        if (!body.isEmpty() || notification != null) {
            NotificationHelper.show(this, title, body);
        }
    }

    /** Se llama cuando Firebase genera un token nuevo para este dispositivo. */
    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Nuevo token FCM: " + token);
        SessionManager session = new SessionManager(this);
        if (session.isLoggedIn() && session.getUid() != null) {
            new FirestoreManager().saveFcmToken(session.getUid(), token);
        }
    }
}
