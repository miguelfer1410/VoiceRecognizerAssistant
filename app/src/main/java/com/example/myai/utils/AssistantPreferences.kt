import android.content.Context

class AssistantPreferences(private val context: Context) {
    private val prefs = context.getSharedPreferences("assistant_prefs", Context.MODE_PRIVATE)

    var wakeWord: String
        get() = prefs.getString("wake_word", "assistente") ?: "assistente"
        set(value) = prefs.edit().putString("wake_word", value).apply()

    var voiceSpeed: Float
        get() = prefs.getFloat("voice_speed", 1.0f)
        set(value) = prefs.edit().putFloat("voice_speed", value).apply()

    var voicePitch: Float
        get() = prefs.getFloat("voice_pitch", 1.0f)
        set(value) = prefs.edit().putFloat("voice_pitch", value).apply()

    var isWakeWordEnabled: Boolean
        get() = prefs.getBoolean("wake_word_enabled", false)
        set(value) = prefs.edit().putBoolean("wake_word_enabled", value).apply()

    var isContinuousListeningEnabled: Boolean
        get() = prefs.getBoolean("continuous_listening", true)
        set(value) = prefs.edit().putBoolean("continuous_listening", value).apply()
} 