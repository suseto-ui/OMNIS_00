package com.example.security

import android.content.Context
import android.util.Base64
import android.util.Log
import java.security.SecureRandom

/**
 * Poskytovatel šifrovacího klíče (passphrase) pro SQLCipher Room databázi.
 *
 * Klíč o délce 256 bitů (32 bajtů) je vygenerován kryptograficky bezpečným generátorem
 * a zašifrován pomocí Master Key v hardwarovém úložišti Android KeyStore (AES/GCM/NoPadding)
 * prostřednictvím [OmnisSecureStorage].
 *
 * Uživatel nemusí zadávat žádné manuální heslo; šifrování databáze na disku je plně transparentní
 * a svázané s hardwarovou identitou a úložištěm klíčů zařízení.
 */
object DatabasePassphraseProvider {
    private const val TAG = "DatabasePassphrase"
    private const val KEY_DB_PASSPHRASE = "omnis_sqlcipher_db_key"

    @Volatile
    private var cachedPassphrase: ByteArray? = null

    @Synchronized
    fun getDatabasePassphrase(context: Context): ByteArray {
        cachedPassphrase?.let { return it }

        try {
            // 1. Zkusíme načíst uložený dešifrovaný klíč z Android KeyStore
            val storedBase64 = OmnisSecureStorage.decryptAndRetrieve(context, KEY_DB_PASSPHRASE)
            if (!storedBase64.isNullOrBlank()) {
                val decoded = Base64.decode(storedBase64, Base64.DEFAULT)
                if (decoded.size == 32) {
                    cachedPassphrase = decoded
                    return decoded
                }
            }

            // 2. Pokud ještě neexistuje, vygenerujeme nový 256bitový (32 bajtů) náhodný klíč
            // Pokud na disku zbyl starý DB soubor bez platného klíče (např. po ztrátě KeyStore),
            // odstraníme jej, aby SQLCipher neselhal s 'file is not a database'.
            deleteOrphanedDatabaseFiles(context)

            val randomBytes = ByteArray(32)
            SecureRandom().nextBytes(randomBytes)
            val base64Key = Base64.encodeToString(randomBytes, Base64.DEFAULT)

            // 3. Uložíme zašifrovaný klíč do OmnisSecureStorage (Android KeyStore AES-GCM)
            OmnisSecureStorage.encryptAndStore(context, KEY_DB_PASSPHRASE, base64Key)

            cachedPassphrase = randomBytes
            Log.i(TAG, "Vygenerován a bezpečně uložen nový 256bitový klíč pro SQLCipher databázi.")
            return randomBytes
        } catch (e: Exception) {
            Log.w(TAG, "KeyStore není dostupný, generování dynamického 256bit klíče v paměti: ${e.message}")
            deleteOrphanedDatabaseFiles(context)
            val dynamicBytes = ByteArray(32)
            SecureRandom().nextBytes(dynamicBytes)
            cachedPassphrase = dynamicBytes
            return dynamicBytes
        }
    }

    private fun deleteOrphanedDatabaseFiles(context: Context) {
        try {
            val dbFile = context.getDatabasePath(com.example.data.OmnisDatabase.DATABASE_NAME)
            if (dbFile.exists()) {
                Log.w(TAG, "Odstraňuji osiřelou šifrovanou DB bez dostupného klíče: ${dbFile.name}")
                dbFile.delete()
                java.io.File(dbFile.absolutePath + "-wal").delete()
                java.io.File(dbFile.absolutePath + "-shm").delete()
                java.io.File(dbFile.absolutePath + "-journal").delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Nepodařilo se vyčistit osiřelou DB: ${e.message}")
        }
    }

    /**
     * Vyčištění cache pro testovací účely.
     */
    fun clearCache() {
        cachedPassphrase = null
    }
}
