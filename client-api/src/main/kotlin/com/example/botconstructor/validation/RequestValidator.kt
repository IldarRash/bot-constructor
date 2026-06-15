package com.example.botconstructor.validation

import com.example.botconstructor.exceptions.InvalidRequestException
import jakarta.validation.Validator
import org.springframework.stereotype.Component

/**
 * Runs bean validation on request DTOs coming through the functional WebFlux routes, which (unlike
 * annotated `@RequestBody` controllers) do not trigger `@field:Valid` automatically.
 *
 * On the first constraint violation it throws an [InvalidRequestException] whose subject is the
 * offending property path and whose violation is the constraint message, so the existing 400 error
 * envelope `{ "errors": { "<field>": ["<message>"] } }` is preserved without changes to the handlers.
 */
@Component
class RequestValidator(private val validator: Validator) {

    /**
     * Validates [target] and returns it unchanged if valid; throws [InvalidRequestException] on the
     * first violation otherwise.
     */
    fun <T : Any> validate(target: T): T {
        val violations = validator.validate(target)
        if (violations.isNotEmpty()) {
            val first = violations.first()
            val field = first.propertyPath.toString().ifBlank { "request" }
            throw InvalidRequestException(field, first.message)
        }
        return target
    }
}
