package com.misfinanzas.gestorgastos;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.messaging.FirebaseMessaging;
import com.misfinanzas.gestorgastos.adapter.TransactionAdapter;
import com.misfinanzas.gestorgastos.database.FirestoreManager;
import com.misfinanzas.gestorgastos.model.Transaction;
import com.misfinanzas.gestorgastos.model.Usuario;
import com.misfinanzas.gestorgastos.session.SessionManager;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements TransactionAdapter.OnItemClickListener {

    private static final String TAG = "MainActivity";

    private TextView tvTotalBalance, tvTotalIncome, tvTotalExpense;
    private TextView tvUserName, tvUserRole, tvListTitle;
    private EditText etSearch;
    private RecyclerView rvTransactions;
    private LinearLayout layoutEmptyState;
    private ProgressBar progressBarLoading;
    private FloatingActionButton fabAdd;

    private FirestoreManager firestoreManager;
    private ListenerRegistration firestoreListenerRegistration;
    private SessionManager session;

    private TransactionAdapter adapter;
    private final List<Transaction> fullTransactionList = new ArrayList<>();
    private NumberFormat currencyFormatter;

    // PILAR D: Solicitud del permiso POST_NOTIFICATIONS (Android 13+)
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    Toast.makeText(this, "Sin permiso de notificaciones no verás los avisos de tus transacciones.", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        session = new SessionManager(this);

        // Protección de ruta: sin sesión activa se vuelve al Login.
        if (!session.isLoggedIn() || FirebaseAuth.getInstance().getCurrentUser() == null) {
            goToLogin();
            return;
        }

        setContentView(R.layout.activity_main);

        // Inicializar Firestore Manager
        firestoreManager = new FirestoreManager();
        currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US);

        // Inicializar Vistas
        tvTotalBalance = findViewById(R.id.tv_total_balance);
        tvTotalIncome = findViewById(R.id.tv_total_income);
        tvTotalExpense = findViewById(R.id.tv_total_expense);
        tvUserName = findViewById(R.id.tv_user_name);
        tvUserRole = findViewById(R.id.tv_user_role);
        tvListTitle = findViewById(R.id.tv_list_title);
        etSearch = findViewById(R.id.et_search);
        rvTransactions = findViewById(R.id.rv_transactions);
        layoutEmptyState = findViewById(R.id.layout_empty_state);
        progressBarLoading = findViewById(R.id.progress_bar_loading);
        fabAdd = findViewById(R.id.fab_add);
        ImageButton btnMap = findViewById(R.id.btn_map);
        ImageButton btnLogout = findViewById(R.id.btn_logout);

        // Configurar RecyclerView
        rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TransactionAdapter(this, new ArrayList<>(), this);
        rvTransactions.setAdapter(adapter);

        // Configurar FAB
        fabAdd.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, TransactionFormActivity.class)));

        // PILAR C: Mapa interactivo
        btnMap.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, MapActivity.class)));

        // PILAR A: Cierre de sesión seguro
        btnLogout.setOnClickListener(v -> confirmLogout());

        // Configurar Filtro de Búsqueda
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterTransactions(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        renderUserHeader();
        requestNotificationPermissionIfNeeded();
        registerFcmToken();

        // Iniciar la escucha en tiempo real según el rol
        setupFirestoreRealtimeListener();
        refreshRoleFromCloud();
    }

    // =========================================================================
    // PILAR A: RBAC - La interfaz cambia según el rol del usuario
    // =========================================================================
    private void renderUserHeader() {
        tvUserName.setText(session.getName());
        tvUserRole.setText(Usuario.roleLabel(session.getRole()));
        tvListTitle.setText(session.isAdmin() ? R.string.title_all_transactions : R.string.title_recent_transactions);
        TextView tvBalanceTitle = findViewById(R.id.tv_balance_title);
        if (tvBalanceTitle != null) {
            tvBalanceTitle.setText(session.isAdmin() ? R.string.title_balance_admin : R.string.title_balance_mine);
        }
        adapter.setShowOwner(session.isAdmin());
    }

    /**
     * Si un administrador cambió el rol en Firestore, se actualiza la sesión local
     * y se vuelve a suscribir la lista con los permisos nuevos.
     */
    private void refreshRoleFromCloud() {
        firestoreManager.getUser(session.getUid(), doc -> {
            if (!doc.exists()) return;
            String cloudRole = doc.getString("rol");
            if (cloudRole != null && !cloudRole.equals(session.getRole())) {
                session.updateRole(cloudRole);
                renderUserHeader();
                setupFirestoreRealtimeListener();
                Toast.makeText(this, "Tu rol ahora es: " + Usuario.roleLabel(cloudRole), Toast.LENGTH_SHORT).show();
            }
        }, e -> Log.w(TAG, "No se pudo refrescar el rol", e));
    }

    // =========================================================================
    // PILAR B: Sincronización en Tiempo Real (addSnapshotListener) filtrada por rol
    // =========================================================================
    private void setupFirestoreRealtimeListener() {
        if (firestoreListenerRegistration != null) {
            firestoreListenerRegistration.remove();
        }
        if (progressBarLoading != null) {
            progressBarLoading.setVisibility(View.VISIBLE);
        }

        firestoreListenerRegistration = firestoreManager.listenToTransactions(session.isAdmin(), session.getUid(),
                (snapshots, error) -> {
                    if (progressBarLoading != null) {
                        progressBarLoading.setVisibility(View.GONE);
                    }

                    if (error != null) {
                        Log.e(TAG, "Error al escuchar cambios en Firestore", error);
                        String msg = error.getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED
                                ? "Sin permiso para leer los datos. Revisa las reglas de Firestore."
                                : "Error de sincronización: " + error.getMessage();
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                        return;
                    }

                    if (snapshots != null) {
                        fullTransactionList.clear();
                        double totalIncome = 0;
                        double totalExpense = 0;

                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            Transaction transaction = doc.toObject(Transaction.class);
                            if (transaction != null) {
                                // Garantizar que el Document ID esté asignado
                                if (transaction.getId() == null || transaction.getId().isEmpty()) {
                                    transaction.setId(doc.getId());
                                }
                                fullTransactionList.add(transaction);

                                // Acumular balances en tiempo real
                                if ("Ingreso".equalsIgnoreCase(transaction.getTipo())) {
                                    totalIncome += transaction.getMonto();
                                } else if ("Gasto".equalsIgnoreCase(transaction.getTipo())) {
                                    totalExpense += transaction.getMonto();
                                }
                            }
                        }
                        FirestoreManager.sortByDateDesc(fullTransactionList);

                        // Actualizar UI con datos recibidos en tiempo real
                        updateFinancialBalance(totalIncome, totalExpense);
                        filterTransactions(etSearch.getText().toString());
                    }
                });
    }

    private void updateFinancialBalance(double income, double expense) {
        double balance = income - expense;

        tvTotalIncome.setText(currencyFormatter.format(income));
        tvTotalExpense.setText(currencyFormatter.format(expense));
        tvTotalBalance.setText(currencyFormatter.format(balance));

        if (balance >= 0) {
            tvTotalBalance.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        } else {
            tvTotalBalance.setTextColor(ContextCompat.getColor(this, R.color.expense));
        }
    }

    private void filterTransactions(String query) {
        List<Transaction> filteredList = new ArrayList<>();
        String lowerQuery = query.toLowerCase(Locale.ROOT).trim();

        if (lowerQuery.isEmpty()) {
            filteredList.addAll(fullTransactionList);
        } else {
            for (Transaction t : fullTransactionList) {
                boolean matchesConcept = t.getConcepto() != null && t.getConcepto().toLowerCase(Locale.ROOT).contains(lowerQuery);
                boolean matchesCategory = t.getCategoria() != null && t.getCategoria().toLowerCase(Locale.ROOT).contains(lowerQuery);
                boolean matchesAddress = t.getDireccion() != null && t.getDireccion().toLowerCase(Locale.ROOT).contains(lowerQuery);
                if (matchesConcept || matchesCategory || matchesAddress) {
                    filteredList.add(t);
                }
            }
        }

        adapter.updateData(filteredList);

        if (filteredList.isEmpty()) {
            rvTransactions.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);
        } else {
            rvTransactions.setVisibility(View.VISIBLE);
            layoutEmptyState.setVisibility(View.GONE);
        }
    }

    @Override
    public void onItemClick(Transaction transaction) {
        // Abrir formulario enviando el Document ID de Firestore
        Intent intent = new Intent(MainActivity.this, TransactionFormActivity.class);
        intent.putExtra("DOCUMENT_ID", transaction.getId());
        startActivity(intent);
    }

    // =========================================================================
    // PILAR D: Permiso de notificaciones (Android 13+) y token FCM
    // =========================================================================
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void registerFcmToken() {
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> {
                    Log.d(TAG, "Token FCM del dispositivo: " + token);
                    firestoreManager.saveFcmToken(session.getUid(), token);
                })
                .addOnFailureListener(e -> Log.w(TAG, "No se pudo obtener el token FCM", e));
    }

    // =========================================================================
    // PILAR A: Cierre de sesión seguro
    // =========================================================================
    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_logout)
                .setMessage(R.string.confirm_logout)
                .setPositiveButton(R.string.action_logout, (d, w) -> logout())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void logout() {
        // 1. Dejar de escuchar Firestore antes de perder la autenticación
        if (firestoreListenerRegistration != null) {
            firestoreListenerRegistration.remove();
            firestoreListenerRegistration = null;
        }
        // 2. Invalidar el token push de este dispositivo
        FirebaseMessaging.getInstance().deleteToken();
        // 3. Destruir la sesión/token de Firebase Authentication
        FirebaseAuth.getInstance().signOut();
        // 4. Limpiar la caché local: SharedPreferences, imágenes de Glide y notificaciones
        session.clear();
        Glide.get(this).clearMemory();
        final android.content.Context appContext = getApplicationContext();
        new Thread(() -> Glide.get(appContext).clearDiskCache()).start();
        NotificationManagerCompat.from(this).cancelAll();

        Toast.makeText(this, "Sesión cerrada", Toast.LENGTH_SHORT).show();
        goToLogin();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // =========================================================================
    // Liberar memoria de Firebase al cerrar la pantalla (onDestroy)
    // =========================================================================
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (firestoreListenerRegistration != null) {
            firestoreListenerRegistration.remove();
            Log.d(TAG, "ListenerRegistration de Firestore desregistrado exitosamente en onDestroy().");
        }
    }
}
