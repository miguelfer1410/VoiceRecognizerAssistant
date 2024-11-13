import java.text.Normalizer

fun String.normalize(): String {
    val normalized = Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace("[^\\p{ASCII}]".toRegex(), "")
        .lowercase()
        .trim()
    
    return normalized
} 