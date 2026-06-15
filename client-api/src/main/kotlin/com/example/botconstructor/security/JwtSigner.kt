package com.example.botconstructor

import com.example.botconstructor.security.UserTokenProvider
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jws
import io.jsonwebtoken.Jwts
import org.springframework.stereotype.Component
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.*

@Component
class JwtSigner : UserTokenProvider {

    private val keyPair = Jwts.SIG.RS256.keyPair().build()
    private val jwtParser = Jwts.parser()
            .verifyWith(keyPair.public as RSAPublicKey)
            .build()

    fun validate(jwt: String): Jws<Claims> = jwtParser.parseSignedClaims(jwt)

    fun generateToken(userId: String): String = Jwts.builder()
            .signWith(keyPair.private as RSAPrivateKey)
            .subject(userId)
            .expiration(expirationDate())
            .issuer("identity")
            .compact()

    private fun expirationDate(): Date {
        val expirationDate = System.currentTimeMillis() + sessionTime()
        return Date(expirationDate)
    }

    private fun sessionTime(): Long = 86400 * 1000L

    override fun getToken(userId: String): String = generateToken(userId)
}
