package id.walt.authkit.sessions

interface TokenStore {

    /**
     * Return session id
     */
    fun mapToken(token: String, sessionId: String)

    fun removeToken(token: String)

}
