package nz.co.doer.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureStorageManager @Inject constructor(
    context: Context
) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "doer_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var isLoggedIn: Boolean
        get() = securePrefs.getBoolean(KEY_IS_LOGGED_IN, false)
        set(value) = securePrefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()

    fun clear() {
        securePrefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_IS_LOGGED_IN = "is_logged_in"
    }
}
