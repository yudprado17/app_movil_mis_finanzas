package com.misfinanzas.gestorgastos;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.misfinanzas.gestorgastos.adapter.TransactionAdapter;
import com.misfinanzas.gestorgastos.database.FirestoreManager;
import com.misfinanzas.gestorgastos.model.Transaction;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements TransactionAdapter.OnItemClickListener {

    private static final String TAG = "MainActivity";

    private TextView tvTotalBalance, tvTotalIncome, tvTotalExpense;
    private EditText etSearch;
    private RecyclerView rvTransactions;
    private LinearLayout layoutEmptyState;
    private ProgressBar progressBarLoading;
    private FloatingActionButton fabAdd;

    private FirestoreManager firestoreManager;
    private ListenerRegistration firestoreListenerRegistration;
    
    private TransactionAdapter adapter;
    private final List<Transaction> fullTransactionList = new ArrayList<>();
    private NumberFormat currencyFormatter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializar Firestore Manager
        firestoreManager = new FirestoreManager();
        currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US);

        // Inicializar Vistas
        tvTotalBalance = findViewById(R.id.tv_total_balance);
        tvTotalIncome = findViewById(R.id.tv_total_income);
        tvTotalExpense = findViewById(R.id.tv_total_expense);
        etSearch = findViewById(R.id.et_search);
        rvTransactions = findViewById(R.id.rv_transactions);
        layoutEmptyState = findViewById(R.id.layout_empty_state);
        progressBarLoading = findViewById(R.id.progress_bar_loading);
        fabAdd = findViewById(R.id.fab_add);

        // Configurar RecyclerView
        rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TransactionAdapter(this, new ArrayList<Transaction>(), this);
        rvTransactions.setAdapter(adapter);

        // Configurar FAB
        fabAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, TransactionFormActivity.class);
                startActivity(intent);
            }
        });

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

        // Iniciar la escucha en tiempo real
        setupFirestoreRealtimeListener();
    }

    // =========================================================================
    // PILAR 1 & 4: Sincronización en Tiempo Real y Liberación de Memoria
    // =========================================================================
    private void setupFirestoreRealtimeListener() {
        if (progressBarLoading != null) {
            progressBarLoading.setVisibility(View.VISIBLE);
        }

        firestoreListenerRegistration = firestoreManager.listenToTransactions(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(@Nullable QuerySnapshot snapshots, @Nullable FirebaseFirestoreException error) {
                if (progressBarLoading != null) {
                    progressBarLoading.setVisibility(View.GONE);
                }

                if (error != null) {
                    Log.e(TAG, "Error al escuchar cambios en Firestore", error);
                    Toast.makeText(MainActivity.this, "Error de sincronización: " + error.getMessage(), Toast.LENGTH_LONG).show();
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

                    // Actualizar UI con datos recibidos en tiempo real
                    updateFinancialBalance(totalIncome, totalExpense);
                    filterTransactions(etSearch.getText().toString());
                }
            }
        });
    }

    private void updateFinancialBalance(double income, double expense) {
        double balance = income - expense;

        tvTotalIncome.setText(currencyFormatter.format(income));
        tvTotalExpense.setText(currencyFormatter.format(expense));
        tvTotalBalance.setText(currencyFormatter.format(balance));

        if (balance >= 0) {
            tvTotalBalance.setTextColor(getResources().getColor(R.color.text_primary));
        } else {
            tvTotalBalance.setTextColor(getResources().getColor(R.color.expense));
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
                if (matchesConcept || matchesCategory) {
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
    // PILAR 4: Liberar memoria de Firebase al cerrar la App (onDestroy)
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
