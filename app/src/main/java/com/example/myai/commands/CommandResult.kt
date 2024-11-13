import com.example.myai.commands.AppCommand

sealed class CommandResult {
    data class Stop(val reason: String = "") : CommandResult()
    data class OpenApp(val appCommand: AppCommand) : CommandResult()
    data class SendMessage(val app: String, val contact: String, val message: String) : CommandResult()
    data class Unknown(val text: String) : CommandResult()
    data class SetAlarm(val hour: Int, val minute: Int) : CommandResult()
    data class OpenWhatsAppChat(val contact: String) : CommandResult()
    data class Search(val query: String) : CommandResult()
}