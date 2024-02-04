package id.walt.crypto.keys

import android.util.Base64
import id.walt.crypto.keys.AndroidLocalKeyGenerator.KEY_ALIAS
import kotlinx.serialization.json.JsonObject
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.Certificate

actual class LocalKey actual constructor(jwk: String?) : Key() {

    override val keyType: KeyType
        get() = KeyType.RSA

    actual override val hasPrivateKey: Boolean
        get() {
            return (keyStore.getKey(KEY_ALIAS, null) as PrivateKey?) != null
        }

    private val keyStore = KeyStore.getInstance(AndroidLocalKeyGenerator.ANDROID_KEYSTORE).apply {
        load(null)
    }

    actual override suspend fun getKeyId(): String {
        TODO("Not yet implemented")
    }

    actual override suspend fun getThumbprint(): String {
        TODO("Not yet implemented")
    }

    actual override suspend fun exportJWK(): String {
        TODO("Not yet implemented")
    }

    actual override suspend fun exportJWKObject(): JsonObject {
        TODO("Not yet implemented")
    }

    actual override suspend fun exportPEM(): String {
        TODO("Not yet implemented")
    }

    /**
     * Signs as a JWS: Signs a message using this private key (with the algorithm this key is based on)
     * @exception IllegalArgumentException when this is not a private key
     * @param plaintext data to be signed
     * @return signed (JWS)
     */
    actual override suspend fun signRaw(plaintext: ByteArray): ByteArray {
        check(hasPrivateKey) { "No private key is attached to this key!" }

        //Retrieves the private key from the keystore
        val privateKey: PrivateKey = keyStore.getKey(KEY_ALIAS, null) as PrivateKey

        val signature: ByteArray? = getSignature().run {
            initSign(privateKey)
            update(plaintext)
            sign()
        }

        println("signed with Sig - ${Base64.encodeToString(signature, Base64.DEFAULT)}")
        println("signed plaintext - ${plaintext.decodeToString()}")
        return Base64.encodeToString(signature, Base64.DEFAULT).toByteArray()
    }

    actual override suspend fun signJws(plaintext: ByteArray, headers: Map<String, String>): String {
        TODO("Not yet implemented")
    }

    /**
     * Verifies JWS: Verifies a signed message using this public key
     * @param signed signed
     * @return Result wrapping the plaintext; Result failure when the signature fails
     */
    actual override suspend fun verifyRaw(signed: ByteArray, detachedPlaintext: ByteArray?): Result<ByteArray> {
        //We get the certificate from the keystore
        val certificate: Certificate? = keyStore.getCertificate(KEY_ALIAS)

        return if (certificate != null) {
            //We decode the signature value
            val signature: ByteArray = Base64.decode(signed.decodeToString(), Base64.DEFAULT)

            println("signature to verify- ${signed.decodeToString()}")
            println("plaintext - ${detachedPlaintext!!.decodeToString()}")

            //We check if the signature is valid
            val isValid: Boolean = getSignature().run {
                initVerify(certificate)
                update(detachedPlaintext)
                verify(signature)
            }

            return if (isValid) {
                Result.success(detachedPlaintext)
            } else {
                Result.failure(Exception("Signature is not valid"))
            }

        } else {
            Result.failure(Exception("Certificate not found in KeyStore"))
        }
    }

    actual override suspend fun verifyJws(signedJws: String): Result<JsonObject> {
        TODO("Not yet implemented")
    }

    actual override suspend fun getPublicKey(): LocalKey {
        TODO("Not yet implemented")
    }

    actual override suspend fun getPublicKeyRepresentation(): ByteArray {
        TODO("Not yet implemented")
    }

    private fun getSignature(): Signature = when (keyType) {
        KeyType.secp256k1 -> Signature.getInstance("SHA256withECDSA", "BC")//Legacy SunEC curve disabled
        KeyType.secp256r1 -> Signature.getInstance("SHA256withECDSA")
        KeyType.Ed25519 -> Signature.getInstance("Ed25519")
        KeyType.RSA -> Signature.getInstance("SHA256withRSA")
    }

    actual companion object : LocalKeyCreator {
        actual override suspend fun generate(
            type: KeyType,
            metadata: LocalKeyMetadata
        ): LocalKey = AndroidLocalKeyGenerator.generate(type, metadata)

        actual override suspend fun importRawPublicKey(
            type: KeyType,
            rawPublicKey: ByteArray,
            metadata: LocalKeyMetadata
        ): Key {
            TODO("Not yet implemented")
        }

        actual override suspend fun importJWK(jwk: String): Result<LocalKey> {
            TODO("Not yet implemented")
        }

        actual override suspend fun importPEM(pem: String): Result<LocalKey> {
            TODO("Not yet implemented")
        }

    }


}