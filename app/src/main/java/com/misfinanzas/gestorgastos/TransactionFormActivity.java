package com.misfinanzas.gestorgastos;

import android.Manifest;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.location.LocationManagerCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.misfinanzas.gestorgastos.database.FirestoreManager;
import com.misfinanzas.gestorgastos.database.ReceiptImageStore;
import com.misfinanzas.gestorgastos.model.Transaction;
import com.misfinanzas.gestorgastos.notifications.NotificationHelper;
import com.misfinanzas.gestorgastos.session.SessionManager;
import com.misfinanzas.gestorgastos.util.GeocoderHelper;
import com.misfinanzas.gestorgastos.util.ImageUtils;

import java.io.File;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TransactionFormActivity extends AppCompatActivity {

    private static final String TAG = "TransactionForm";

    // Claves para conservar el estado al girar la pantalla
    private static final String STATE_IMAGE_URI = "state_image_uri";
    private static final String STATE_CAMERA_URI = "state_camera_uri";
    private static final String STATE_REMOVE_IMAGE = "state_remove_image";
    private static final String STATE_LAT = "state_lat";
    private static final String STATE_LNG = "state_lng";
    private static final String STATE_ADDRESS = "state_address";

    private ImageButton btnBack;
    private TextView tvFormTitle;
    private RadioGroup rgType;
    private RadioButton rbIncome, rbExpense;
    private TextInputLayout tilConcept, tilAmount, tilDate;
    private TextInputEditText tietConcept, tietAmount, tietDate;
    private Spinner spinnerCategory;
    private ProgressBar progressBarForm;
    private Button btnSave, btnDelete, btnCamera, btnGallery, btnGetLocation, btnViewOnMap;
    private TextView tvDeleteRestricted;

    // Comprobante
    private FrameLayout layoutReceiptPreview;
    private ImageView ivReceiptPreview;
    private LinearLayout layoutUploadProgress;
    private ProgressBar progressUpload;
    private TextView tvUploadProgress;

    // Ubicación
    private TextView tvLocationAddress, tvLocationCoords;
    private ProgressBar progressLocation;

    private FirestoreManager firestoreManager;
    private SessionManager session;
    private FusedLocationProviderClient fusedLocationClient;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean isEditMode = false;
    private String documentId = null;
    private Transaction originalTransaction = null;

    // Estado del comprobante
    private Uri selectedImageUri = null;   // Foto nueva pendiente de subir
    private Uri pendingCameraUri = null;   // Archivo donde la cámara guardará la foto
    private boolean removeImage = false;   // El usuario quitó la foto existente

    // Estado de la ubicación
    private Double currentLat = null;
    private Double currentLng = null;
    private String currentAddress = null;

    private final Calendar calendar = Calendar.getInstance();
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    // =========================================================================
    // PILAR B: API moderna ActivityResultLauncher (Cámara, Galería y permisos)
    // =========================================================================
    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    launchCamera();
                } else {
                    showPermissionDeniedDialog("cámara", "tomar la foto del comprobante");
                }
            });

    private final ActivityResultLauncher<Uri> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (Boolean.TRUE.equals(success) && pendingCameraUri != null) {
                    onImageSelected(pendingCameraUri);
                }
            });

    private final ActivityResultLauncher<PickVisualMediaRequest> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    onImageSelected(uri);
                }
            });

    // PILAR C: permisos de ubicación precisa y aproximada
    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean fine = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));
                boolean coarse = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                if (fine || coarse) {
                    if (!fine) {
                        Toast.makeText(this, "Usando ubicación aproximada", Toast.LENGTH_SHORT).show();
                    }
                    fetchCurrentLocation(fine);
                } else {
                    showPermissionDeniedDialog("ubicación", "registrar dónde se hizo la transacción");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_form);

        // Inicializar servicios
        firestoreManager = new FirestoreManager();
        session = new SessionManager(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

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
        tvDeleteRestricted = findViewById(R.id.tv_delete_restricted);
        btnCamera = findViewById(R.id.btn_camera);
        btnGallery = findViewById(R.id.btn_gallery);
        layoutReceiptPreview = findViewById(R.id.layout_receipt_preview);
        ivReceiptPreview = findViewById(R.id.iv_receipt_preview);
        layoutUploadProgress = findViewById(R.id.layout_upload_progress);
        progressUpload = findViewById(R.id.progress_upload);
        tvUploadProgress = findViewById(R.id.tv_upload_progress);
        tvLocationAddress = findViewById(R.id.tv_location_address);
        tvLocationCoords = findViewById(R.id.tv_location_coords);
        progressLocation = findViewById(R.id.progress_location);
        btnGetLocation = findViewById(R.id.btn_get_location);
        btnViewOnMap = findViewById(R.id.btn_view_on_map);
        ImageButton btnRemovePhoto = findViewById(R.id.btn_remove_photo);

        // Configurar Spinner de Categorías
        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(
                this, R.array.categories, android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(spinnerAdapter);

        // Configurar selector de fecha
        tietDate.setOnClickListener(v -> showDatePicker());
        btnBack.setOnClickListener(v -> finish());

        // Comprobante
        btnCamera.setOnClickListener(v -> onCameraClicked());
        btnGallery.setOnClickListener(v -> pickImageLauncher.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));
        btnRemovePhoto.setOnClickListener(v -> {
            selectedImageUri = null;
            removeImage = true;
            renderReceiptPreview();
        });

        // Ubicación
        btnGetLocation.setOnClickListener(v -> onGetLocationClicked());
        btnViewOnMap.setOnClickListener(v -> {
            Intent intent = new Intent(this, MapActivity.class);
            if (currentLat == null || currentLng == null) return;
            intent.putExtra(MapActivity.EXTRA_LAT, currentLat.doubleValue());
            intent.putExtra(MapActivity.EXTRA_LNG, currentLng.doubleValue());
            startActivity(intent);
        });

        // PILAR E: Validaciones en Tiempo Real (TextWatcher)
        setupRealtimeValidations();

        // Restaurar el estado si la pantalla se giró
        if (savedInstanceState != null) {
            selectedImageUri = savedInstanceState.getParcelable(STATE_IMAGE_URI);
            pendingCameraUri = savedInstanceState.getParcelable(STATE_CAMERA_URI);
            removeImage = savedInstanceState.getBoolean(STATE_REMOVE_IMAGE, false);
            if (savedInstanceState.containsKey(STATE_LAT)) {
                currentLat = savedInstanceState.getDouble(STATE_LAT);
                currentLng = savedInstanceState.getDouble(STATE_LNG);
                currentAddress = savedInstanceState.getString(STATE_ADDRESS);
            }
        }

        // Determinar si es Edición o Creación
        if (getIntent().hasExtra("DOCUMENT_ID")) {
            isEditMode = true;
            documentId = getIntent().getStringExtra("DOCUMENT_ID");
            applyRolePermissions();
            loadTransactionData(savedInstanceState == null);
        } else if (savedInstanceState == null) {
            // Modo Creación: Establecer fecha actual por defecto
            tietDate.setText(dateFormatter.format(calendar.getTime()));
        }

        renderReceiptPreview();
        renderLocation();

        btnSave.setOnClickListener(v -> saveTransaction());
        btnDelete.setOnClickListener(v -> confirmDelete());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(STATE_IMAGE_URI, selectedImageUri);
        outState.putParcelable(STATE_CAMERA_URI, pendingCameraUri);
        outState.putBoolean(STATE_REMOVE_IMAGE, removeImage);
        if (currentLat != null && currentLng != null) {
            outState.putDouble(STATE_LAT, currentLat);
            outState.putDouble(STATE_LNG, currentLng);
            outState.putString(STATE_ADDRESS, currentAddress);
        }
    }

    // =========================================================================
    // PILAR A: RBAC - Solo el Administrador puede eliminar
    // =========================================================================
    private void applyRolePermissions() {
        if (session.isAdmin()) {
            btnDelete.setVisibility(View.VISIBLE);
            tvDeleteRestricted.setVisibility(View.GONE);
        } else {
            btnDelete.setVisibility(View.GONE);
            tvDeleteRestricted.setVisibility(View.VISIBLE);
        }
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
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    tietDate.setText(dateFormatter.format(calendar.getTime()));
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    /**
     * @param fillFields true en la primera carga; false si la pantalla se recreó
     *                   (al girar), para no pisar lo que el usuario ya cambió.
     */
    private void loadTransactionData(boolean fillFields) {
        tvFormTitle.setText(R.string.title_edit_transaction);
        setLoadingState(true);

        firestoreManager.getTransactionById(documentId, doc -> {
            setLoadingState(false);
            if (!doc.exists()) {
                Toast.makeText(TransactionFormActivity.this, "No se encontró el documento en Firestore", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            Transaction transaction = doc.toObject(Transaction.class);
            if (transaction == null) return;
            originalTransaction = transaction;

            if (fillFields) {
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
                @SuppressWarnings("unchecked")
                ArrayAdapter<CharSequence> adapter = (ArrayAdapter<CharSequence>) spinnerCategory.getAdapter();
                if (adapter != null) {
                    int position = adapter.getPosition(transaction.getCategoria());
                    if (position >= 0) {
                        spinnerCategory.setSelection(position);
                    }
                }

                // Ubicación guardada
                if (transaction.hasLocation()) {
                    currentLat = transaction.getLatitud();
                    currentLng = transaction.getLongitud();
                    currentAddress = transaction.getDireccion();
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
                Log.w(TAG, "Fecha con formato inesperado", e);
            }

            renderReceiptPreview();
            renderLocation();
        }, e -> {
            setLoadingState(false);
            Toast.makeText(TransactionFormActivity.this, "Error al cargar documento: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    // =========================================================================
    // PILAR B: Captura multimedia (Cámara con permiso en tiempo de ejecución)
    // =========================================================================
    private void onCameraClicked() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            new AlertDialog.Builder(this)
                    .setTitle("Permiso de cámara")
                    .setMessage("Mis Finanzas necesita la cámara para fotografiar el comprobante de la transacción.")
                    .setPositiveButton("Continuar", (d, w) -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA))
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchCamera() {
        try {
            File dir = new File(getCacheDir(), "camera");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IllegalStateException("No se pudo crear la carpeta temporal");
            }
            File photo = new File(dir, "comprobante_" + System.currentTimeMillis() + ".jpg");
            pendingCameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photo);
            takePictureLauncher.launch(pendingCameraUri);
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo abrir la cámara: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void onImageSelected(Uri uri) {
        selectedImageUri = uri;
        removeImage = false;
        renderReceiptPreview();
    }

    /** Muestra la foto nueva (local) o la existente (guardada en Firestore) con Glide. */
    private void renderReceiptPreview() {
        Object source = null;
        if (selectedImageUri != null) {
            source = selectedImageUri;
        } else if (!removeImage && originalTransaction != null && originalTransaction.hasImage()) {
            source = originalTransaction.getImageUrl();
        }

        if (source == null) {
            Glide.with(this).clear(ivReceiptPreview);
            layoutReceiptPreview.setVisibility(View.GONE);
            return;
        }
        layoutReceiptPreview.setVisibility(View.VISIBLE);
        Glide.with(this)
                .load(source)
                .placeholder(R.drawable.ic_receipt)
                .error(R.drawable.ic_broken_image)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .into(ivReceiptPreview);
    }

    // =========================================================================
    // PILAR C: Captura de coordenadas con FusedLocationProviderClient
    // =========================================================================
    private void onGetLocationClicked() {
        boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (fine || coarse) {
            fetchCurrentLocation(fine);
        } else {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void fetchCurrentLocation(boolean precise) {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm != null && !LocationManagerCompat.isLocationEnabled(lm)) {
            new AlertDialog.Builder(this)
                    .setTitle("Ubicación desactivada")
                    .setMessage("Activa la ubicación (GPS) del dispositivo para registrar dónde se hizo la transacción.")
                    .setPositiveButton("Activar", (d, w) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
            return;
        }

        progressLocation.setVisibility(View.VISIBLE);
        btnGetLocation.setEnabled(false);
        int priority = precise ? Priority.PRIORITY_HIGH_ACCURACY : Priority.PRIORITY_BALANCED_POWER_ACCURACY;

        try {
            CancellationTokenSource cts = new CancellationTokenSource();
            fusedLocationClient.getCurrentLocation(priority, cts.getToken())
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            onLocationObtained(location);
                        } else {
                            // Respaldo: última ubicación conocida
                            fallbackToLastLocation();
                        }
                    })
                    .addOnFailureListener(e -> fallbackToLastLocation());
        } catch (SecurityException e) {
            onLocationFailed("Permiso de ubicación no concedido");
        }
    }

    private void fallbackToLastLocation() {
        try {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            onLocationObtained(location);
                        } else {
                            onLocationFailed("No se pudo obtener la ubicación. Intenta de nuevo al aire libre.");
                        }
                    })
                    .addOnFailureListener(e -> onLocationFailed("Error de ubicación: " + e.getMessage()));
        } catch (SecurityException e) {
            onLocationFailed("Permiso de ubicación no concedido");
        }
    }

    private void onLocationObtained(Location location) {
        currentLat = location.getLatitude();
        currentLng = location.getLongitude();
        currentAddress = null;
        renderLocation();

        // Geocodificación inversa: coordenadas -> dirección legible
        tvLocationAddress.setText("Buscando dirección...");
        GeocoderHelper.reverseGeocode(this, currentLat, currentLng, address -> {
            progressLocation.setVisibility(View.GONE);
            btnGetLocation.setEnabled(true);
            currentAddress = address != null ? address : "Dirección no disponible";
            renderLocation();
        });
    }

    private void onLocationFailed(String message) {
        progressLocation.setVisibility(View.GONE);
        btnGetLocation.setEnabled(true);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void renderLocation() {
        if (currentLat == null || currentLng == null) {
            tvLocationAddress.setText(R.string.location_empty);
            tvLocationCoords.setVisibility(View.GONE);
            btnViewOnMap.setVisibility(View.GONE);
            return;
        }
        tvLocationAddress.setText(currentAddress != null ? currentAddress : "Ubicación capturada");
        tvLocationCoords.setText(String.format(Locale.US, "Lat %.5f, Lng %.5f", currentLat, currentLng));
        tvLocationCoords.setVisibility(View.VISIBLE);
        btnViewOnMap.setVisibility(View.VISIBLE);
    }

    private void showPermissionDeniedDialog(String permissionName, String reason) {
        new AlertDialog.Builder(this)
                .setTitle("Permiso de " + permissionName + " denegado")
                .setMessage("Sin este permiso no es posible " + reason + ". Puedes activarlo en los ajustes de la aplicación.")
                .setPositiveButton("Abrir ajustes", (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", getPackageName(), null));
                    startActivity(intent);
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    // =========================================================================
    // GUARDAR: validar -> (preparar foto) -> guardar en Firestore -> notificar
    // =========================================================================
    private void saveTransaction() {
        String concept = textOf(tietConcept).trim();
        String amountStr = textOf(tietAmount).trim();
        String date = textOf(tietDate).trim();
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
        if (isEditMode && originalTransaction == null) {
            Toast.makeText(this, "Espera a que cargue la transacción", Toast.LENGTH_SHORT).show();
            return;
        }

        // Prevenir doble envío (deshabilitar botones durante el guardado)
        setLoadingState(true);

        Transaction transaction = isEditMode
                ? new Transaction(documentId, concept, amount, type, date, category)
                : new Transaction(concept, amount, type, date, category);

        // Dueño del registro: se conserva el original al editar (el Admin puede editar registros ajenos)
        if (isEditMode) {
            transaction.setUserId(originalTransaction.getUserId());
            transaction.setUserEmail(originalTransaction.getUserEmail());
        }
        if (transaction.getUserId() == null) {
            transaction.setUserId(session.getUid());
            transaction.setUserEmail(session.getEmail());
        }

        // Ubicación
        transaction.setLatitud(currentLat);
        transaction.setLongitud(currentLng);
        transaction.setDireccion(currentLat != null ? currentAddress : null);

        // Comprobante: se conserva el existente salvo que se quite o se reemplace
        if (isEditMode && !removeImage) {
            transaction.setImageUrl(originalTransaction.getImageUrl());
        }

        if (selectedImageUri != null) {
            prepareImageThenPersist(transaction);
        } else {
            persist(transaction);
        }
    }

    /**
     * PILAR B: prepara la foto (compresión + Base64) mostrando el progreso y la
     * guarda en el documento de Firestore (plan gratuito, sin Firebase Storage).
     */
    private void prepareImageThenPersist(Transaction transaction) {
        layoutUploadProgress.setVisibility(View.VISIBLE);
        progressUpload.setProgress(0);
        tvUploadProgress.setText("Preparando foto...");

        ReceiptImageStore.prepare(this, selectedImageUri, ioExecutor, mainHandler, new ReceiptImageStore.Callback() {
            @Override
            public void onProgress(int percent, @NonNull String stage) {
                progressUpload.setProgress(percent);
                tvUploadProgress.setText(stage);
            }

            @Override
            public void onReady(@NonNull String dataUri, int jpegBytes) {
                progressUpload.setProgress(85);
                tvUploadProgress.setText("Subiendo a la nube... (" + (jpegBytes / 1024) + " KB)");
                transaction.setImageUrl(dataUri);
                persist(transaction);
            }

            @Override
            public void onError(@NonNull Exception e) {
                onImageFailed(transaction, e);
            }
        });
    }

    /**
     * Tolerancia a fallos: si la foto no se puede procesar, la transacción se
     * guarda igual (sin la foto nueva) y se avisa al usuario.
     */
    private void onImageFailed(Transaction transaction, Exception e) {
        Log.e(TAG, "No se pudo preparar la foto", e);
        layoutUploadProgress.setVisibility(View.GONE);
        Toast.makeText(this, "No se pudo procesar la foto (" + e.getMessage() + "). La transacción se guardará sin la foto nueva.", Toast.LENGTH_LONG).show();
        persist(transaction);
    }

    /** Guarda en Firestore y, si hubo éxito, notifica. */
    private void persist(Transaction transaction) {
        final String formattedAmount = NumberFormat.getCurrencyInstance(Locale.US).format(transaction.getMonto());

        if (isEditMode) {
            firestoreManager.updateTransaction(documentId, transaction, unused -> {
                onSavedToCloud();
                NotificationHelper.notifyTransactionSaved(this, true, transaction.getConcepto(), formattedAmount);
                Toast.makeText(TransactionFormActivity.this, "Transacción actualizada en la nube", Toast.LENGTH_SHORT).show();
                finish();
            }, this::onSaveFailed);
        } else {
            firestoreManager.addTransaction(transaction, ref -> {
                onSavedToCloud();
                NotificationHelper.notifyTransactionSaved(this, false, transaction.getConcepto(), formattedAmount);
                Toast.makeText(TransactionFormActivity.this, "Transacción registrada en tiempo real", Toast.LENGTH_SHORT).show();
                finish();
            }, this::onSaveFailed);
        }
    }

    private void onSavedToCloud() {
        if (layoutUploadProgress.getVisibility() == View.VISIBLE) {
            progressUpload.setProgress(100);
            tvUploadProgress.setText("Foto guardada en la nube ✔");
        }
    }

    private void onSaveFailed(Exception e) {
        setLoadingState(false);
        layoutUploadProgress.setVisibility(View.GONE);
        Toast.makeText(TransactionFormActivity.this, "Error al guardar: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }

    private void confirmDelete() {
        // Doble verificación del rol (además de ocultar el botón y de las reglas de Firestore)
        if (!session.isAdmin()) {
            Toast.makeText(this, R.string.delete_admin_only, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Confirmar eliminación")
                .setMessage(R.string.confirm_delete)
                .setPositiveButton("Sí, eliminar", (dialog, which) -> deleteTransaction())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void deleteTransaction() {
        setLoadingState(true);
        // La foto vive dentro del documento, así que se elimina junto con él
        firestoreManager.deleteTransaction(documentId, unused -> {
            Toast.makeText(TransactionFormActivity.this, "Transacción eliminada de Firestore", Toast.LENGTH_SHORT).show();
            finish();
        }, e -> {
            setLoadingState(false);
            Toast.makeText(TransactionFormActivity.this, "Error al eliminar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    private void setLoadingState(boolean isLoading) {
        btnSave.setEnabled(!isLoading);
        btnDelete.setEnabled(!isLoading);
        btnCamera.setEnabled(!isLoading);
        btnGallery.setEnabled(!isLoading);
        if (progressBarForm != null) {
            progressBarForm.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
    }

    private static String textOf(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString() : "";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdown();
    }
}
