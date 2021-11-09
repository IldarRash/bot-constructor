package com.example.botconstructor.exceptions

class InvalidRequestException(val subject: String, val violation: String) : RuntimeException("$subject: $violation")
