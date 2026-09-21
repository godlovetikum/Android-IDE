// KeystoreCredentialVault is the secure boundary for provider credentials. It exposes
// identifiers and redacted values without making secret material part of project state.
package dev.android.ide.security

import dev.android.ide.contracts.CredentialVaultAdapter
import dev.android.ide.contracts.ErrorCategory
import dev.android.ide.contracts.OperationOutcome
import dev.android.ide.contracts.OperationReport
import java.security.KeyStore

class KeystoreCredentialVault : CredentialVaultAdapter {
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    override suspend fun listCredentialIds(): List<String> = keyStore.aliases().toList()

    override suspend fun revoke(credentialId: String): OperationReport {
        if (!keyStore.containsAlias(credentialId)) {
            return OperationReport(
                outcome = OperationOutcome.BLOCKED,
                message = "Credential was not found",
                errorCategory = ErrorCategory.CREDENTIAL_FAILURE,
                recoveryHint = "Refresh the credential list and try again.",
            )
        }
        return runCatching {
            keyStore.deleteEntry(credentialId)
            OperationReport(OperationOutcome.COMPLETE, "Credential revoked")
        }.getOrElse {
            OperationReport(
                outcome = OperationOutcome.FAILED,
                message = "Credential could not be revoked",
                errorCategory = ErrorCategory.CREDENTIAL_FAILURE,
                recoveryHint = "Retry after the secure storage provider is available.",
            )
        }
    }

    override fun redact(value: String): String {
        if (value.isEmpty()) return ""
        val suffix = value.takeLast(4)
        return "••••$suffix"
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}
