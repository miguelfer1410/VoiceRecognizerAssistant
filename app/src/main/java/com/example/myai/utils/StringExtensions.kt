package com.example.myai.utils

import java.text.Normalizer
import java.util.Locale

fun String.normalize(): String {
    return Normalizer
        .normalize(this.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "") // Remove diacríticos
        .replace(Regex("[^a-z0-9\\s]"), "") // Remove caracteres especiais exceto letras, números e espaços
        .trim()
} 