package com.misfinanzas.gestorgastos;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.misfinanzas.gestorgastos.adapter.TransactionAdapter;
import com.misfinanzas.gestorgastos.database.DatabaseHelper;
import com.misfinanzas.gestorgastos.model.Transaction;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements TransactionAdapter.OnItemClickListener {

    private TextView tvTotalBalance, tvTotalIncome, tvTotalExpense;
    private EditText etSearch;
    private RecyclerView rvTransactions;
    private LinearLayout layoutEmptyState;
    private FloatingActionButton fabAdd;

    private DatabaseHelper dbHelper;
    private TransactionAdapter adapter;
    private List<Transaction> transactionList = new ArrayList<>();
    private NumberFormat currencyFormatter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializar BD
        dbHelper = new DatabaseHelper(this);
        currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US);

        // Inicializar Vistas
        tvTotalBalance = findViewById(R.id.tv_total_balance);
        tvTotalIncome = findViewById(R.id.tv_total_income);
        tvTotalExpense = findViewById(R.id.tv_total_expense);
        etSearch = findViewById(R.id.et_search);
        rvTransactions = findViewById(R.id.rv_transactions);
        layoutEmptyState = findViewById(R.id.layout_empty_state);
        fabAdd = findViewById(R.id.fab_add);

        // Configurar RecyclerView
        rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TransactionAdapter(this, transactionList, this);
        rvTransactions.setAdapter(adapter);

        // Configurar Evento del FAB (Agregar)
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Recargar datos siempre al volver a esta pantalla
        loadData();
    }

    private void loadData() {
        // Cargar lista completa de transacciones
        transactionList = dbHelper.getAllTransactions();
        adapter.updateData(transactionList);

        // Calcular y mostrar Balance General
        double income = dbHelper.getTotalIncome();
        double expense = dbHelper.getTotalExpense();
        double balance = income - expense;

        tvTotalIncome.setText(currencyFormatter.format(income));
        tvTotalExpense.setText(currencyFormatter.format(expense));
        
        tvTotalBalance.setText(currencyFormatter.format(balance));
        if (balance >= 0) {
            tvTotalBalance.setTextColor(getResources().getColor(R.color.text_primary));
        } else {
            tvTotalBalance.setTextColor(getResources().getColor(R.color.expense));
        }

        // Mostrar u ocultar estado vacío
        if (transactionList.isEmpty()) {
            rvTransactions.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);
        } else {
            rvTransactions.setVisibility(View.VISIBLE);
            layoutEmptyState.setVisibility(View.GONE);
        }
    }

    private void filterTransactions(String text) {
        if (text.isEmpty()) {
            loadData();
        } else {
            List<Transaction> filteredList = dbHelper.searchTransactions(text);
            adapter.updateData(filteredList);
            
            if (filteredList.isEmpty()) {
                rvTransactions.setVisibility(View.GONE);
                layoutEmptyState.setVisibility(View.VISIBLE);
            } else {
                rvTransactions.setVisibility(View.VISIBLE);
                layoutEmptyState.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onItemClick(Transaction transaction) {
        // Al hacer clic en un item, abrir formulario en modo edición
        Intent intent = new Intent(MainActivity.this, TransactionFormActivity.class);
        intent.putExtra("TRANSACTION_ID", transaction.getId());
        startActivity(intent);
    }
}
