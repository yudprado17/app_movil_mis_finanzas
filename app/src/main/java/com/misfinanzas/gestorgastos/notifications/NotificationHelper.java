package com.misfinanzas.gestorgastos.notifications;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.misfinanzas.gestorgastos.LoginActivity;
import com.misfinanzas.gestorgastos.R;

/**
 * PILAR D: Notificaciones del sistema.
 *  - Registro del NotificationChannel (obligatorio en Android 8.0+).
 *  - Notificaciones locales con PendingIntent para reabrir la app.
 */
public final class NotificationHelper {

    private NotificationHelper() {
    }

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    context.getString(R.string.notification_channel_id),
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(context.getString(R.string.notification_channel_desc));
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /** Notificación local al crear o actualizar una transacción con éxito. */
    public static void notifyTransactionSaved(Context context, boolean isUpdate, String concept, String formattedAmount) {
        String title = isUpdate ? "Transacción actualizada" : "Nueva transacción registrada";
        String text = concept + " · " + formattedAmount;
        show(context, title, text);
    }

    /**
     * Muestra una notificación en la barra de estado. Al tocarla se abre la app
     * (LoginActivity hace Auto-Login y lleva a la pantalla principal).
     */
    public static void show(Context context, String title, String text) {
        // Android 13+: sin el permiso POST_NOTIFICATIONS no se puede notificar.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent intent = new Intent(context, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, context.getString(R.string.notification_channel_id))
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(ContextCompat.getColor(context, R.color.primary))
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManagerCompat.from(context).notify((int) System.currentTimeMillis(), builder.build());
    }
}
