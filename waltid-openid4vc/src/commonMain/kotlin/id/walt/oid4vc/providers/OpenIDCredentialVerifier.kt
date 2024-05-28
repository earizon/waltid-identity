package id.walt.oid4vc.providers

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType
import id.walt.oid4vc.data.*
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.data.dif.VCFormat
import id.walt.oid4vc.interfaces.ISessionCache
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.responses.TokenResponse
import id.walt.oid4vc.util.ShortIdUtils
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

abstract class OpenIDCredentialVerifier(val config: CredentialVerifierConfig) :
    ISessionCache<PresentationSession> {

    /**
     * Override this method to cache presentation definition and append it to authorization request by reference
     * @return URI by which presentation definition can be resolved, or null, if full presentation definition object should be appended to authorization request
     */
    protected open fun preparePresentationDefinitionUri(
        presentationDefinition: PresentationDefinition,
        sessionID: String
    ): String? = null

    protected open fun prepareResponseOrRedirectUri(sessionID: String, responseMode: ResponseMode): String =
        when (responseMode) {
            ResponseMode.query, ResponseMode.fragment, ResponseMode.form_post -> config.redirectUri
            else -> config.responseUrl ?: config.redirectUri
        }

    open fun initializeAuthorization(
        presentationDefinition: PresentationDefinition,
        responseMode: ResponseMode = ResponseMode.fragment,
        responseType: ResponseType? = ResponseType.VpToken,
        scope: Set<String> = setOf(),
        expiresIn: Duration = 60.seconds,
        sessionId: String? = null, // A calling party may provide a unique session Id
        ephemeralEncKey: Key? = null,
        clientIdScheme: ClientIdScheme = config.defaultClientIdScheme,
        openId4VPProfile: OpenId4VPProfile = OpenId4VPProfile.Default,
        stateParamAuthorizeReqEbsi: String? = null,
        useEbsiCTv3: Boolean? = false
    ): PresentationSession {
        val session = PresentationSession(
            id = sessionId ?: ShortIdUtils.randomSessionId(),
            authorizationRequest = null,
            expirationTimestamp = Clock.System.now().plus(expiresIn),
            presentationDefinition = presentationDefinition,
            stateParamAuthorizeReqEbsi = stateParamAuthorizeReqEbsi,
            ephemeralEncKey = ephemeralEncKey,
            openId4VPProfile = openId4VPProfile
        ).also {
            putSession(it.id, it)
        }
        val presentationDefinitionUri = when(openId4VPProfile) {
            OpenId4VPProfile.ISO_18013_7_MDOC -> null
            else -> preparePresentationDefinitionUri(presentationDefinition, session.id)
        }
        val authReq = AuthorizationRequest(
            // here add VpToken if response type is null
            responseType = setOf(responseType!!),
            clientId = when(clientIdScheme) {
                ClientIdScheme.RedirectUri -> config.redirectUri
                else -> config.clientIdMap[clientIdScheme] ?: config.defaultClientId
            }.let{
                when(useEbsiCTv3) {
                    true -> it.replace(it, config.defaultClientId.replace("/openid4vc/verify", ""))
                    else -> it
                }
            },
            responseMode = responseMode,
            redirectUri = when (responseMode) {
                ResponseMode.query, ResponseMode.fragment, ResponseMode.form_post -> prepareResponseOrRedirectUri(
                    session.id,
                    responseMode
                )
                else -> null
            }.let{
                when(useEbsiCTv3) {
                    true -> when (responseMode) {
                        ResponseMode.direct_post -> prepareResponseOrRedirectUri(session.id, responseMode)
                        else -> null
                    }
                    else -> it
                }
            },
            responseUri = when (responseMode) {
                ResponseMode.direct_post, ResponseMode.direct_post_jwt -> prepareResponseOrRedirectUri(session.id, responseMode)
                else -> null
            }.let{
                when(useEbsiCTv3) {
                    true -> null
                    else -> it
                }
            },
            presentationDefinitionUri = presentationDefinitionUri,
            presentationDefinition = when (presentationDefinitionUri) {
                null -> presentationDefinition
                else -> when (useEbsiCTv3) { // some wallets support presentation_definition only, even ebsiconformancetest wallet
                    true -> presentationDefinition
                    else -> null
                }
            },
            scope = when (useEbsiCTv3) {
                true -> setOf("openid")
                else -> scope
            },
            state = session.id,
            clientIdScheme = clientIdScheme,
            nonce = UUID.generateUUID().toString()
        )
        return session.copy(authorizationRequest = authReq).also {
            putSession(session.id, it)
        }
    }

    open fun verify(tokenResponse: TokenResponse, session: PresentationSession): PresentationSession {
        // https://json-schema.org/specification
        // https://github.com/OptimumCode/json-schema-validator
        return session.copy(
            tokenResponse = tokenResponse,
            verificationResult = doVerify(tokenResponse, session)
        ).also {
            putSession(it.id, it)
        }
    }

    protected abstract fun doVerify(tokenResponse: TokenResponse, session: PresentationSession): Boolean
}
