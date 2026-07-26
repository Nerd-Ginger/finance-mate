package dev.financemate

import android.app.Application

class FinanceMateApplication : Application() {

    /**
     * Built eagerly, but nothing expensive happens until something is asked for:
     * the vault and the encrypted database are both lazy, so startup does not
     * block on unsealing a Keystore key.
     */
    val container: AppContainer by lazy { AppContainer(this) }
}
