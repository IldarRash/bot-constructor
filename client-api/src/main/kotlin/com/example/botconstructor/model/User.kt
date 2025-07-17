package com.example.botconstructor.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document
data class User(
        val username: String,
        val email: String,
        var encodedPassword: String,
        val bio: String?,
        val image: String?,
        @Id
        val id: String,
) {


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as User

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "User(id='$id', username='$username', encodedPassword='$encodedPassword', email='$email', bio='$bio', image='$image')"
    }
}
