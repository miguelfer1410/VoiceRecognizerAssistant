package com.example.myai.commands

data class AppCommand (
    val name: String,
    val packageName: String = name
)
