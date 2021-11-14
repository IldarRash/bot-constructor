package com.example.botconstructor.bot_template.support

import com.example.botconstructor.fbs.BotTemplate
import java.nio.ByteBuffer


internal object MessageMapper {

    @ExperimentalUnsignedTypes
    fun mapToBotTemplate(botTemplate: BotTemplate): ByteBuffer {

        return botTemplate.byteBuffer
    }
}
