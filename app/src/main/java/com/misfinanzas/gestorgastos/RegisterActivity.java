package com.misfinanzas.gestorgastos;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.misfinanzas.gestorgastos.database.FirestoreManager;
import com.misfinanzas.gestorgastos.model.Usuario;
import com.misfinanzas.gestorgastos.session.SessionManager;
import com.misfinanzas.gestorgastos.util.AuthErrors;

/**
 * PILAR A: Registro de usuarios con FirebaseAuth.
 * Además de la cuenta en Authentication, se crea el perfil "usuarios/{uid}"
 * en Firestore con rol "estandar". Solo un administrador puede cambiarlo.
 */
public class RegisterActivity extends AppCompatActivity {

    private TextInputLayout tilName, tilEmail, tilPassword, tilConfirm;
    private TextInputEditText tietName, tietEmail, tietPassword, tietConfirm;
    private Button btnRegister, btnGoLogin;
    private ProgressBar progressRegister;

    private FirebaseAuth auth;
    private FirestoreManager firestoreManager;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        auth = FirebaseAuth.getInstance();
        firestoreManager = new FirestoreManager();
        session = new SessionManager(this);

        ImageButton btnBack = findViewById(R.id.btn_back);
        tilName = findViewById(R.id.til_name);
        tilEmail = findViewById(R.id.til_email);
        tilPassword = findViewById(R.id.til_password);
        tilConfirm = findViewById(R.id.til_confirm_password);
        tietName = findViewById(R.id.tiet_name);
        tietEmail = findViewById(R.id.tiet_email);
        tietPassword = findViewById(R.id.tiet_password);
        tietConfirm = findViewById(R.id.tiet_confirm_password);
        btnRegister = findViewById(R.id.btn_register);
        btnGoLogin = findViewById(R.id.btn_go_login);
        progressRegister = findViewById(R.id.progress_register);

        btnBack.setOnClickListener(v -> finish());
        btnGoLogin.setOnClickListener(v -> finish());
        btnRegister.setOnClickListener(v -> attemptRegister());

        // PILAR E: Validaciones en tiempo real
        tietName.addTextChangedListener(new LoginActivity.SimpleWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validateName(s.toString().trim());
            }
        });
        tietEmail.addTextChangedListener(new LoginActivity.SimpleWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validateEmail(s.toString().trim());
            }
        });
        tietPassword.addTextChangedListener(new LoginActivity.SimpleWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validatePassword(s.toString());
                if (!textOf(tietConfirm).isEmpty()) validateConfirm(textOf(tietConfirm));
            }
        });
        tietConfirm.addTextChangedListener(new LoginActivity.SimpleWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validateConfirm(s.toString());
            }
        });
    }

    private boolean validateName(String name) {
        if (name.isEmpty()) {
            tilName.setError("El nombre es obligatorio");
            return false;
        }
        if (name.length() < 3) {
            tilName.setError("Mínimo 3 caracteres");
            return false;
        }
        tilName.setError(null);
        return true;
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
        if (password.length() < 6) {
            tilPassword.setError("Mínimo 6 caracteres");
            return false;
        }
        tilPassword.setError(null);
        return true;
    }

    private boolean validateConfirm(String confirm) {
        if (!confirm.equals(textOf(tietPassword))) {
            tilConfirm.setError("Las contraseñas no coinciden");
            return false;
        }
        tilConfirm.setError(null);
        return true;
    }

    private void attemptRegister() {
        String name = textOf(tietName).trim();
        String email = textOf(tietEmail).trim();
        String password = textOf(tietPassword);

        boolean ok = validateName(name);
        ok &= validateEmail(email);
        ok &= validatePassword(password);
        ok &= validateConfirm(textOf(tietConfirm));
        if (!ok) return;

        setLoading(true);
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) {
                        setLoading(false);
                        return;
                    }
                    // Nombre visible en el perfil de Authentication
                    user.updateProfile(new UserProfileChangeRequest.Builder().setDisplayName(name).build());

                    // Perfil con rol en Firestore (RBAC). Todo usuario nuevo es Estándar.
                    Usuario usuario = new Usuario(user.getUid(), name, email, Usuario.ROL_ESTANDAR);
                    firestoreManager.createUser(usuario,
                            unused -> enter(usuario),
                            e -> {
                                Toast.makeText(this, "Cuenta creada, pero no se pudo guardar el perfil: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                enter(usuario);
                            });
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, AuthErrors.toMessage(e), Toast.LENGTH_LONG).show();
                });
    }

    private void enter(Usuario usuario) {
        session.saveSession(usuario);
        Toast.makeText(this, "Cuenta creada. ¡Bienvenido(a), " + usuario.getNombre() + "!", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading) {
        progressRegister.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnRegister.setEnabled(!loading);
        btnGoLogin.setEnabled(!loading);
    }

    private static String textOf(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString() : "";
    }
}
