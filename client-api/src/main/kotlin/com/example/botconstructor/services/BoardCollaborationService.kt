package com.example.botconstructor.services

import com.example.botconstructor.dto.BoardEvent
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * A board participant, identified by user id and display name.
 */
data class BoardUser(val id: String, val name: String)

/**
 * In-memory fan-out hub for board collaboration. Each board has its own multicast sink that
 * replays nothing but broadcasts every published [BoardEvent] to all current subscribers, and a
 * presence registry used to build ROSTER snapshots.
 *
 * All state is kept in process; horizontal scaling would require an external broker, see notes.
 */
@Service
class BoardCollaborationService {

    private val sinks = ConcurrentHashMap<String, Sinks.Many<BoardEvent>>()
    private val presence = ConcurrentHashMap<String, MutableSet<BoardUser>>()

    /**
     * The shared event stream for [boardId]. Subscribers receive every event published after they
     * subscribe; the controller layer owns presence join/leave emission.
     */
    fun stream(boardId: String): Flux<BoardEvent> = sinkFor(boardId).asFlux()

    /**
     * Fans [event] out to every current subscriber of [boardId]. Edits arrive on different RSocket
     * connection threads, so emissions must be serialized: a busy-looping handler retries on
     * FAIL_NON_SERIALIZED/FAIL_OVERFLOW instead of silently dropping concurrent edits (which is
     * exactly the multi-editor case). FAIL_ZERO_SUBSCRIBER (no one listening yet) is a no-op.
     */
    fun publish(boardId: String, event: BoardEvent) {
        sinkFor(boardId).emitNext(event, Sinks.EmitFailureHandler.busyLooping(Duration.ofSeconds(1)))
    }

    /**
     * Registers [user] as present on [boardId] and returns the roster snapshot after the join.
     */
    fun join(boardId: String, user: BoardUser): Set<BoardUser> {
        val members = presence.computeIfAbsent(boardId) { ConcurrentHashMap.newKeySet() }
        members.add(user)
        return members.toSet()
    }

    /**
     * Removes [user] from the [boardId] roster. Cleans up empty boards.
     */
    fun leave(boardId: String, user: BoardUser) {
        presence.computeIfPresent(boardId) { _, members ->
            members.remove(user)
            if (members.isEmpty()) null else members
        }
    }

    /**
     * Current roster snapshot for [boardId].
     */
    fun roster(boardId: String): Set<BoardUser> = presence[boardId]?.toSet() ?: emptySet()

    private fun sinkFor(boardId: String): Sinks.Many<BoardEvent> =
            sinks.computeIfAbsent(boardId) {
                Sinks.many().multicast().onBackpressureBuffer()
            }
}
