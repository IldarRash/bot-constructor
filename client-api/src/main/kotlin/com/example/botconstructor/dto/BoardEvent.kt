package com.example.botconstructor.dto

/**
 * A single collaboration event on a board, fanned out to every subscriber of that board.
 *
 * @property type One of PRESENCE_JOIN, PRESENCE_LEAVE, ROSTER, NODES_CHANGE, EDGES_CHANGE,
 *   NODE_ADD, NODE_REMOVE, EDGE_ADD, EDGE_REMOVE, CURSOR.
 * @property senderId Id of the user that produced the event. Clients ignore events whose
 *   senderId equals their own.
 * @property senderName Display name of the sender, used for presence and rosters.
 * @property payload Free-form JSON payload: an @xyflow node/edge object, a cursor {x,y},
 *   or for ROSTER a list of roster members. Serialized/deserialized by Jackson as-is.
 */
data class BoardEvent(
        val type: String,
        val senderId: String,
        val senderName: String,
        val payload: Any? = null,
)
