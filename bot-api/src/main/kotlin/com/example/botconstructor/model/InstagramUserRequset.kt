package com.example.botconstructor.model

data class InstagramUserRequest(val obj: String, val entry: List<Entry>)

data class Entry(val id: String, val time: Long, val messaging: List<Message>)

data class Message(val sender: Sender, val recipient: Recipient, val read: Read, val text: TextMessage)

data class Sender(val id: String)

data class Recipient(val id: String)

data class Read(val mid: String)

data class TextMessage(val mid: String, val text: String)
