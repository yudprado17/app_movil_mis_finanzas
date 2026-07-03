package com.misfinanzas.gestorgastos;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.misfinanzas.gestorgastos.database.DatabaseHelper;
import com.misfinanzas.gestorgastos.model.Transaction;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class TransactionFormActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private TextView tvFormTitle;
    private RadioGroup rgType;
    private RadioButton rbIncome, rbExpense;
    private TextInputLayout tilConcept, tilAmount, tilDate;
    private TextInputEditText tietConcept, tietAmount, tietDate;
    private Spinner spinnerCategory;
    private Button btnSave, btnDelete;

    private DatabaseHelper dbHelper;
    private boolean isEditMode = false;
    private int transactionId = -1;
    
    private final Calendar calendar = Calendar.getInstance();
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_form);

        dbHelper = new DatabaseHelper(this);

        // Inicializar Vistas
        btnBack = findViewById(R.id.btn_back);
        tvFormTitle = findViewById(R.id.tv_form_title);
        rgType = findViewById(R.id.rg_type);
        rbIncome = findViewById(R.id.rb_income);
        rbExpense = findViewById(R.id.rb_expense);
        tilConcept = findViewById(R.id.til_concept);
        tietConcept = findViewById(R.id.tiet_concept);
        tilAmount = findViewById(R.id.til_amount);
        tietAmount = findViewById(R.id.tiet_amount);
        tilDate = findViewById(R.id.til_date);
        tietDate = findViewById(R.id.tiet_date);
        spinnerCategory = findViewById(R.id.spinner_category);
        btnSave = findViewById(R.id.btn_save);
        btnDelete = findViewById(R.id.btn_delete);

        // Configurar Spinner de Categorías
        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(
                this, R.array.categories, android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(spinnerAdapter);

        // Configurar selector de fecha
        tietDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDatePicker();
            }
        });

        // Configurar Botón Atrás
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // Determinar si es Edición o Creación
        if (getIntent().hasExtra("TRANSACTION_ID")) {
            isEditMode = true;
            transactionId = getIntent().getIntExtra("TRANSACTION_ID", -1);
            loadTransactionData();
        } else {
            // Modo Creación: Establecer fecha actual por defecto
            tietDate.setText(dateFormatter.format(calendar.getTime()));
        }

        // Configurar Botón Guardar
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveTransaction();
            }
        });

        // Configurar Botón Eliminar
        btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDelete();
            }
        });
    }

    private void showDatePicker() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                        calendar.set(Calendar.YEAR, year);
                        calendar.set(Calendar.MONTH, month);
                        calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                        tietDate.setText(dateFormatter.format(calendar.getTime()));
                    }
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    private void loadTransactionData() {
        tvFormTitle.setText(R.string.title_edit_transaction);
        btnDelete.setVisibility(View.VISIBLE);

        Transaction transaction = dbHelper.getTransactionById(transactionId);
        if (transaction != null) {
            tietConcept.setText(transaction.getConcepto());
            tietAmount.setText(String.valueOf(transaction.getMonto()));
            tietDate.setText(transaction.getFecha());

            // Seleccionar tipo
            if ("Ingreso".equalsIgnoreCase(transaction.getTipo())) {
                rbIncome.setChecked(true);
            } else {
                rbExpense.setChecked(true);
            }

            // Seleccionar categoría
            ArrayAdapter<CharSequence> adapter = (ArrayAdapter<CharSequence>) spinnerCategory.getAdapter();
            if (adapter != null) {
                int position = adapter.getPosition(transaction.getCategoria());
                if (position >= 0) {
                    spinnerCategory.setSelection(position);
                }
            }

            // Actualizar calendario con la fecha cargada
            try {
                java.util.Date date = dateFormatter.parse(transaction.getFecha());
                if (date != null) {
                    calendar.setTime(date);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            Toast.makeText(this, "Error al cargar la transacción", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void saveTransaction() {
        String concept = tietConcept.getText().toString().trim();
        String amountStr = tietAmount.getText().toString().trim();
        String date = tietDate.getText().toString().trim();
        String type = rbIncome.isChecked() ? "Ingreso" : "Gasto";
        String category = spinnerCategory.getSelectedItem().toString();

        // Validaciones
        boolean isValid = true;

        if (concept.isEmpty()) {
            tilConcept.setError(getString(R.string.error_empty_concept));
            isValid = false;
        } else {
            tilConcept.setError(null);
        }

        double amount = 0;
        if (amountStr.isEmpty()) {
            tilAmount.setError(getString(R.string.error_invalid_amount));
            isValid = false;
        } else {
            try {
                amount = Double.parseDouble(amountStr);
                if (amount <= 0) {
                    tilAmount.setError(getString(R.string.error_invalid_amount));
                    isValid = false;
                } else {
                    tilAmount.setError(null);
                }
            } catch (NumberFormatException e) {
                tilAmount.setError(getString(R.string.error_invalid_amount));
                isValid = false;
            }
        }

        if (date.isEmpty()) {
            tilDate.setError(getString(R.string.error_empty_date));
            isValid = false;
        } else {
            tilDate.setError(null);
        }

        if (!isValid) return;

        if (isEditMode) {
            Transaction transaction = new Transaction(transactionId, concept, amount, type, date, category);
            int rowsAffected = dbHelper.updateTransaction(transaction);
            if (rowsAffected > 0) {
                Toast.makeText(this, "Transacción actualizada con éxito", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, "Error al actualizar la transacción", Toast.LENGTH_SHORT).show();
            }
        } else {
            Transaction transaction = new Transaction(concept, amount, type, date, category);
            long id = dbHelper.insertTransaction(transaction);
            if (id > 0) {
                Toast.makeText(this, "Transacción registrada con éxito", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, "Error al registrar la transacción", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Confirmar eliminación")
                .setMessage(R.string.confirm_delete)
                .setPositiveButton("Sí, eliminar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deleteTransaction();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void deleteTransaction() {
        int rowsDeleted = dbHelper.deleteTransaction(transactionId);
        if (rowsDeleted > 0) {
            Toast.makeText(this, "Transacción eliminada con éxito", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, "Error al eliminar la transacción", Toast.LENGTH_SHORT).show();
        }
    }
}
