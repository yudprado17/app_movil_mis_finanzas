package com.misfinanzas.gestorgastos;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.misfinanzas.gestorgastos.database.FirestoreManager;
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
    private ProgressBar progressBarForm;
    private Button btnSave, btnDelete;

    private FirestoreManager firestoreManager;
    private boolean isEditMode = false;
    private String documentId = null;

    private final Calendar calendar = Calendar.getInstance();
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_form);

        // Inicializar Firestore Manager
        firestoreManager = new FirestoreManager();

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
        progressBarForm = findViewById(R.id.progress_bar_form);
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

        // =========================================================================
        // PILAR 4: Validaciones en Tiempo Real (TextWatcher)
        // =========================================================================
        setupRealtimeValidations();

        // Determinar si es Edición o Creación
        if (getIntent().hasExtra("DOCUMENT_ID")) {
            isEditMode = true;
            documentId = getIntent().getStringExtra("DOCUMENT_ID");
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

    private void setupRealtimeValidations() {
        // Validación en tiempo real del Concepto
        tietConcept.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String text = s.toString().trim();
                if (text.isEmpty()) {
                    tilConcept.setError("El concepto no puede estar vacío");
                } else if (text.length() < 3) {
                    tilConcept.setError("El concepto debe tener al menos 3 caracteres");
                } else {
                    tilConcept.setError(null);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Validación en tiempo real del Monto (Advirtiendo si es $0 o inválido)
        tietAmount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String text = s.toString().trim();
                if (text.isEmpty()) {
                    tilAmount.setError("El monto es requerido");
                } else {
                    try {
                        double val = Double.parseDouble(text);
                        if (val <= 0) {
                            tilAmount.setError("Advertencia: El monto debe ser mayor a $0");
                        } else {
                            tilAmount.setError(null);
                        }
                    } catch (NumberFormatException e) {
                        tilAmount.setError("Formato numérico inválido");
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
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
        setLoadingState(true);

        firestoreManager.getTransactionById(documentId, new OnSuccessListener<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot doc) {
                setLoadingState(false);
                if (doc.exists()) {
                    Transaction transaction = doc.toObject(Transaction.class);
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
                            if (transaction.getFecha() != null) {
                                java.util.Date date = dateFormatter.parse(transaction.getFecha());
                                if (date != null) {
                                    calendar.setTime(date);
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    Toast.makeText(TransactionFormActivity.this, "No se encontró el documento en Firestore", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        }, new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                setLoadingState(false);
                Toast.makeText(TransactionFormActivity.this, "Error al cargar documento: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void saveTransaction() {
        String concept = tietConcept.getText().toString().trim();
        String amountStr = tietAmount.getText().toString().trim();
        String date = tietDate.getText().toString().trim();
        String type = rbIncome.isChecked() ? "Ingreso" : "Gasto";
        String category = spinnerCategory.getSelectedItem().toString();

        // Validaciones estrictas previa inserción/modificación
        boolean isValid = true;

        if (concept.isEmpty() || concept.length() < 3) {
            tilConcept.setError("El concepto debe tener al menos 3 caracteres");
            isValid = false;
        } else {
            tilConcept.setError(null);
        }

        double amount = 0;
        if (amountStr.isEmpty()) {
            tilAmount.setError("El monto es requerido");
            isValid = false;
        } else {
            try {
                amount = Double.parseDouble(amountStr);
                if (amount <= 0) {
                    tilAmount.setError("El monto debe ser un número positivo mayor a $0");
                    isValid = false;
                } else {
                    tilAmount.setError(null);
                }
            } catch (NumberFormatException e) {
                tilAmount.setError("Formato numérico inválido");
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

        // =========================================================================
        // PILAR 4: Prevenir Doble Envío (Deshabilitar botones durante guardado)
        // =========================================================================
        setLoadingState(true);

        if (isEditMode) {
            Transaction transaction = new Transaction(documentId, concept, amount, type, date, category);
            firestoreManager.updateTransaction(documentId, transaction, new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void unused) {
                    Toast.makeText(TransactionFormActivity.this, "Transacción actualizada en la nube", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }, new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    setLoadingState(false);
                    Toast.makeText(TransactionFormActivity.this, "Error al actualizar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        } else {
            Transaction transaction = new Transaction(concept, amount, type, date, category);
            firestoreManager.addTransaction(transaction, new OnSuccessListener<DocumentReference>() {
                @Override
                public void onSuccess(DocumentReference documentReference) {
                    Toast.makeText(TransactionFormActivity.this, "Transacción registrada en tiempo real", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }, new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    setLoadingState(false);
                    Toast.makeText(TransactionFormActivity.this, "Error al guardar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
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
        setLoadingState(true);
        firestoreManager.deleteTransaction(documentId, new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void unused) {
                Toast.makeText(TransactionFormActivity.this, "Transacción eliminada de Firestore", Toast.LENGTH_SHORT).show();
                finish();
            }
        }, new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                setLoadingState(false);
                Toast.makeText(TransactionFormActivity.this, "Error al eliminar: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoadingState(boolean isLoading) {
        if (isLoading) {
            btnSave.setEnabled(false);
            btnDelete.setEnabled(false);
            if (progressBarForm != null) {
                progressBarForm.setVisibility(View.VISIBLE);
            }
        } else {
            btnSave.setEnabled(true);
            btnDelete.setEnabled(true);
            if (progressBarForm != null) {
                progressBarForm.setVisibility(View.GONE);
            }
        }
    }
}
