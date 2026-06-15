package com.example.botconstructor.rsocket

import com.example.botconstructor.JwtSigner
import com.example.botconstructor.dto.BoardEvent
import com.example.botconstructor.repos.UserRepository
import com.example.botconstructor.services.BoardCollaborationService
import com.example.botconstructor.services.BoardUser
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.annotation.ConnectMapping
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap

/**
 * Setup payload sent by a client in the RSocket SETUP frame: a JSON object carrying the JWT.
 */
data class ConnectionSetup(val token: String? = null, val clientId: String? = null)

/**
 * RSocket endpoints for real-time board collaboration over the websocket transport mapped at
 * /rsocket. This boundary authenticates itself from the SETUP frame (it does NOT go through the
 * WebFlux HTTP security filter chain) and reuses [JwtSigner] for identity.
 */
@Controller
class BoardController(
        private val collaboration: BoardCollaborationService,
        private val jwtSigner: JwtSigner,
        private val userRepository: UserRepository,
) {

    /**
     * Identity resolved at connection time, keyed by the connection's requester so per-message
     * handlers can attribute events without re-validating the token on every frame.
     */
    private val connections = ConcurrentHashMap<RSocketRequester, BoardUser>()

    /**
     * Authenticates a new connection from its SETUP payload. The payload is JSON `{ "token": "..." }`.
     * An invalid or missing token errors the connection (RSocket rejects the SETUP). On success the
     * resolved [BoardUser] is cached for the lifetime of the connection and evicted on close.
     */
    @ConnectMapping
    fun connect(requester: RSocketRequester, @Payload setup: ConnectionSetup): Mono<Void> {
        return resolveUser(setup.token, setup.clientId)
                .doOnNext { user ->
                    connections[requester] = user
                    requester.rsocket()
                            ?.onClose()
                            ?.doFinally { connections.remove(requester) }
                            ?.subscribe()
                }
                .then()
    }

    /**
     * Subscribes the caller to [boardId]'s event stream. On subscribe it registers presence and
     * emits a PRESENCE_JOIN followed by a ROSTER snapshot; on cancel/termination it deregisters
     * presence and broadcasts a PRESENCE_LEAVE to the remaining subscribers.
     */
    @MessageMapping("board.{boardId}")
    fun board(requester: RSocketRequester, @DestinationVariable boardId: String): Flux<BoardEvent> {
        val user = userFor(requester)
        return Flux.defer {
            collaboration.join(boardId, user)
            collaboration.publish(boardId, BoardEvent("PRESENCE_JOIN", user.id, user.name))
            val roster = BoardEvent(
                    type = "ROSTER",
                    senderId = user.id,
                    senderName = user.name,
                    payload = collaboration.roster(boardId).map { mapOf("id" to it.id, "name" to it.name) },
            )
            Flux.concat(Flux.just(roster), collaboration.stream(boardId))
        }.doFinally {
            collaboration.leave(boardId, user)
            collaboration.publish(boardId, BoardEvent("PRESENCE_LEAVE", user.id, user.name))
        }
    }

    /**
     * Receives an edit event from a client and fans it out to all subscribers of [boardId]. The
     * sender id/name are taken from the authenticated connection, not trusted from the payload.
     */
    @MessageMapping("board.{boardId}.edit")
    fun edit(
            requester: RSocketRequester,
            @DestinationVariable boardId: String,
            @Payload event: BoardEvent,
    ): Mono<Void> {
        val user = userFor(requester)
        collaboration.publish(
                boardId,
                event.copy(senderId = user.id, senderName = user.name),
        )
        return Mono.empty()
    }

    private fun userFor(requester: RSocketRequester): BoardUser =
            connections[requester]
                    ?: throw IllegalStateException("Unauthenticated RSocket connection")

    private fun resolveUser(token: String?, clientId: String?): Mono<BoardUser> {
        if (token.isNullOrBlank()) {
            return Mono.error(IllegalArgumentException("Missing RSocket setup token"))
        }
        val userId = try {
            jwtSigner.validate(token).payload.subject
        } catch (ex: Exception) {
            return Mono.error(IllegalArgumentException("Invalid RSocket setup token", ex))
        }
        // The collaborator identity is the per-tab clientId (so two tabs of the same user are two
        // distinct collaborators and each tab can suppress its own echoes); the display name comes
        // from the authenticated user. Fall back to userId when no clientId was supplied.
        val collaboratorId = clientId?.takeIf { it.isNotBlank() } ?: userId
        return userRepository.findById(userId)
                .map { BoardUser(collaboratorId, it.username) }
                .defaultIfEmpty(BoardUser(collaboratorId, userId))
    }
}
