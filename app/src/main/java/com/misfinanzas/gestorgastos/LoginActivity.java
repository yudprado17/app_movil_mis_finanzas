package com.misfinanzas.gestorgastos;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.misfinanzas.gestorgastos.database.FirestoreManager;
import com.misfinanzas.gestorgastos.model.Usuario;
import com.misfinanzas.gestorgastos.session.SessionManager;
import com.misfinanzas.gestorgastos.util.AuthErrors;

/**
 * PILAR A: Autenticación en la nube con FirebaseAuth (correo y contraseña)
 * y Auto-Login con SharedPreferences.
 */
public class LoginActivity extends AppCompatActivity {

    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText tietEmail, tietPassword;
    private Button btnLogin, btnGoRegister;
    private ProgressBar progressLogin;

    private FirebaseAuth auth;
    private SessionManager session;
    private FirestoreManager firestoreManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();
        session = new SessionManager(this);
        firestoreManager = new FirestoreManager();

        // =====================================================================
        // AUTO-LOGIN: si hay sesión guardada en SharedPreferences y Firebase
        // conserva el usuario autenticado, se salta el login.
        // =====================================================================
        FirebaseUser current = auth.getCurrentUser();
        if (session.isLoggedIn() && current != null && current.getUid().equals(session.getUid())) {
            openMain();
            return;
        }
        // Sesión local inconsistente (por ejemplo, token revocado): se limpia.
        if (session.isLoggedIn() || current != null) {
            session.clear();
            auth.signOut();
        }

        setContentView(R.layout.activity_login);

        tilEmail = findViewById(R.id.til_email);
        tilPassword = findViewById(R.id.til_password);
        tietEmail = findViewById(R.id.tiet_email);
        tietPassword = findViewById(R.id.tiet_password);
        btnLogin = findViewById(R.id.btn_login);
        btnGoRegister = findViewById(R.id.btn_go_register);
        progressLogin = findViewById(R.id.progress_login);

        setupRealtimeValidations();

        btnLogin.setOnClickListener(v -> attemptLogin());
        tietPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin();
                return true;
            }
            return false;
        });
        btnGoRegister.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));
    }

    // =========================================================================
    // PILAR E: Validaciones en tiempo real con TextInputLayout.setError()
    // =========================================================================
    private void setupRealtimeValidations() {
        tietEmail.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validateEmail(s.toString().trim());
            }
        });
        tietPassword.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validatePassword(s.toString());
            }
        });
    }

    private boolean validateEmail(String email) {
        if (email.isEmpty()) {
            tilEmail.setError("El correo es obligatorio");
            return false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Formato de correo inválido");
            return false;
        }
        tilEmail.setError(null);
        return true;
    }

    private boolean validatePassword(String password) {
        if (password.isEmpty()) {
            tilPassword.setError("La contraseña es obligatoria");
            return false;
        }
        if (password.length() < 6) {
            tilPassword.setError("Mínimo 6 caracteres");
            return false;
        }
        tilPassword.setError(null);
        return true;
    }

    private void attemptLogin() {
        String email = textOf(tietEmail).trim();
        String password = textOf(tietPassword);

        boolean emailOk = validateEmail(email);
        boolean passwordOk = validatePassword(password);
        if (!emailOk || !passwordOk) return;

        setLoading(true);
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user != null) {
                        loadProfileAndEnter(user);
                    } else {
                        setLoading(false);
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, AuthErrors.toMessage(e), Toast.LENGTH_LONG).show();
                });
    }

    /**
     * Lee el perfil "usuarios/{uid}" para conocer el rol (RBAC). Si el usuario fue
     * creado desde la consola y aún no tiene perfil, se crea con rol Estándar.
     */
    private void loadProfileAndEnter(FirebaseUser user) {
        firestoreManager.getUser(user.getUid(), doc -> {
            Usuario usuario = doc.exists() ? doc.toObject(Usuario.class) : null;
            if (usuario != null) {
                usuario.setUid(user.getUid());
                if (usuario.getRol() == null) usuario.setRol(Usuario.ROL_ESTANDAR);
                enter(usuario);
            } else {
                String name = user.getDisplayName() != null ? user.getDisplayName() : user.getEmail();
                Usuario nuevo = new Usuario(user.getUid(), name, user.getEmail(), Usuario.ROL_ESTANDAR);
                firestoreManager.createUser(nuevo, unused -> enter(nuevo), e -> enter(nuevo));
            }
        }, e -> {
            // Sin acceso al perfil: se entra con permisos mínimos (Estándar).
            Toast.makeText(this, "No se pudo leer tu perfil; entrarás con rol Estándar.", Toast.LENGTH_LONG).show();
            enter(new Usuario(user.getUid(), user.getEmail(), user.getEmail(), Usuario.ROL_ESTANDAR));
        });
    }

    private void enter(Usuario usuario) {
        session.saveSession(usuario);
        Toast.makeText(this, "Bienvenido(a), " + usuario.getNombre(), Toast.LENGTH_SHORT).show();
        openMain();
    }

    private void openMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading) {
        progressLogin.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
        btnGoRegister.setEnabled(!loading);
    }

    private static String textOf(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString() : "";
    }

    /** TextWatcher con métodos vacíos para sobrescribir solo onTextChanged. */
    abstract static class SimpleWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    }
}
