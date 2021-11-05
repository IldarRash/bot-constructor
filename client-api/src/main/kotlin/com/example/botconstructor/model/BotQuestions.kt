package com.example.botconstructor.model

data class BotQuestions(val
                        id: Long, val questions: List<Questions>)

data class Questions(
        var question: String, val keyWords: List<String>
)
