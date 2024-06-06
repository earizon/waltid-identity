package id.walt.webwallet.config

import id.walt.crypto.keys.KeyGenerationRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class RegistrationDefaultsConfig(
    val defaultKeyConfig: KeyGenerationRequest = KeyGenerationRequest(),
    /*
    : JsonObject = buildJsonObject {
        put("backend", JsonPrimitive("jwk"))
        put("keyType", JsonPrimitive("Ed25519"))
    },*/

    private val defaultDidConfig: JsonObject = buildJsonObject {
        put("method", JsonPrimitive("jwk"))
    }
) : WalletConfig() {
//    val keyGenerationRequest = Json.decodeFromJsonElement<KeyGenerationRequest>(defaultKeyConfig)

    val didMethod = defaultDidConfig["method"]!!.jsonPrimitive.content
    val didConfig: Map<String, JsonPrimitive> = defaultDidConfig["config"]?.jsonObject?.mapValues { it.value.jsonPrimitive } ?: emptyMap()
}
