package com.example.myai.commands

import CommandResult
import android.content.Context
import com.example.myai.ui.overlay.OverlayView
import com.example.myai.utils.normalize
import java.util.regex.Pattern
import android.util.Log

class CommandProcessor(private val context: Context) {
    private var overlayView: OverlayView? = null

    fun processCommand(text: String): CommandResult {
        val normalizedText = text.normalize().lowercase()
        
        // Verifica se o texto está vazio ou é muito curto
        if (normalizedText.isBlank() || normalizedText.length < 3) {
            return CommandResult.Unknown("Comando muito curto ou vazio")
        }

        return when {
            isStopCommand(normalizedText) -> CommandResult.Stop()
            isOpenWhatsAppChatCommand(normalizedText) -> {
                val contact = extractContactName(normalizedText)
                CommandResult.OpenWhatsAppChat(contact)
            }
            isSendMessageCommand(normalizedText) -> extractMessageCommand(normalizedText)
            isSetAlarmCommand(normalizedText) -> {
                val (hour, minute) = extractAlarmTime(text)
                if (hour != null && minute != null) {
                    CommandResult.SetAlarm(hour, minute)
                } else {
                    CommandResult.Unknown("Para definir um alarme, tente dizer: 'definir alarme para 8:30' ou 'acordar às 7:00'")
                }
            }
            isSearchCommand(normalizedText) -> {
                val query = extractSearchQuery(normalizedText)
                CommandResult.Search(query)
            }
            isOpenAppCommand(normalizedText) -> {
                val appName = extractAppName(normalizedText)
                CommandResult.OpenApp(AppCommand(appName))
            }
            else -> {
                // Tenta dar uma dica mais específica sobre o que pode ter dado errado
                when {
                    normalizedText.contains("alarme") || normalizedText.contains("despertador") ->
                        CommandResult.Unknown("Para definir um alarme, tente dizer: 'definir alarme para 8:30' ou 'acordar às 7:00'")
                    
                    normalizedText.contains("mensagem") || normalizedText.contains("whatsapp") ->
                        CommandResult.Unknown("Para enviar mensagem, tente: 'enviar mensagem para João dizendo olá' ou 'whatsapp com Maria'")
                    
                    normalizedText.contains("abrir") || normalizedText.contains("abra") ->
                        CommandResult.Unknown("Para abrir um aplicativo, tente: 'abrir calculadora' ou 'abrir câmera'")
                    
                    normalizedText.contains("pesquisa") || normalizedText.contains("procura") ->
                        CommandResult.Unknown("Para fazer uma pesquisa, tente: 'pesquisar sobre clima' ou 'procurar receita de bolo'")
                    
                    else -> CommandResult.Unknown("Não entendi o comando." )
                }
            }
        }
    }

    private fun isStopCommand(text: String): Boolean {
        val stopCommands = listOf("parar", "pare", "para", "desativar", "desativa", "desligar", "desliga")
        return stopCommands.any { command -> text.equals(command, ignoreCase = true) }
    }

    private fun isOpenAppCommand(text: String): Boolean {
        if (text.contains("whatsapp") && (text.contains("com") || text.contains("para"))) {
            return false
        }
        
        val openCommands = listOf("abrir", "abre", "abra", "iniciar", "inicia", "executar", "executa", "lançar", "lança")
        return openCommands.any { command -> text.contains(command) }
    }

    private fun extractAppName(text: String): String {
        val openCommands = listOf("abrir", "abre", "abra", "iniciar", "inicia", "executar", "executa", "lançar", "lança")
        var appName = text
        openCommands.forEach { command ->
            appName = appName.replace(command, "")
        }
        val wordsToRemove = listOf("o", "a", "os", "as", "um", "uma", "uns", "umas", "do", "da", "dos", "das")
        wordsToRemove.forEach { word ->
            appName = appName.replace(" $word ", " ")
                .replace("^$word ", "")
                .replace(" $word$", "")
        }
        return appName.trim()
    }

    private fun isSendMessageCommand(text: String): Boolean {
        val messageCommands = listOf(
            "enviar mensagem",
            "envia mensagem",
            "manda mensagem",
            "mandar mensagem",
            "envia uma mensagem",
            "manda uma mensagem"
        )
        return messageCommands.any { command -> text.contains(command) }
    }

    private fun extractMessageCommand(text: String): CommandResult {
        try {
            val app = when {
                text.contains("whatsapp") -> "whatsapp"
                text.contains("mensagem") && !text.contains("whatsapp") -> "sms"
                else -> "whatsapp" // default para WhatsApp
            }

            val wordsToRemove = listOf(
                "para", "pro", "pra", "ao", "a", "o", "as", "os", "pelo", "pela", "no", "na"
            )

            val contactRegex = "(?:para|pro|para o|para a) ([^,]*)(?:no|pelo|via|usando|dizendo|falando)".toRegex()
            val contactMatch = contactRegex.find(text)
            var contact = contactMatch?.groupValues?.get(1)?.trim() ?: return CommandResult.Unknown(text)

            wordsToRemove.forEach { word ->
                contact = contact.replace(" $word ", " ")
                    .replace("^$word ", "")
                    .replace(" $word$", "")
            }

            contact = contact.trim()

            val messageRegex = "(?:dizendo|falando|a dizer|a dizer que) (.*)".toRegex()
            val messageMatch = messageRegex.find(text)
            val message = messageMatch?.groupValues?.get(1)?.trim() ?: return CommandResult.Unknown(text)

            return CommandResult.SendMessage(app, contact, message)
        } catch (e: Exception) {
            return CommandResult.Unknown(text)
        }
    }

    private fun isOpenWhatsAppChatCommand(text: String): Boolean {
        val patterns = listOf(
            ".*(?:whatsapp|conversa|chat)\\s+(?:com|para|pro|pra)\\s+.*",
            ".*(?:abrir|abre|abra)\\s+(?:whatsapp|conversa|chat)\\s+(?:com|para|pro|pra)\\s+.*",
            ".*(?:conversar|falar)\\s+(?:com|para|pro|pra)\\s+.*\\s+(?:no|pelo)\\s+whatsapp.*"
        )
        return patterns.any { pattern -> text.matches(Regex(pattern, RegexOption.IGNORE_CASE)) }
    }

    private fun extractContactName(text: String): String {
        val patterns = listOf(
            "(?:abrir|abre|abra)\\s+(?:conversa|chat|whatsapp)\\s+(?:com|para|pro|pra)\\s+",
            "(?:conversar|falar|mandar mensagem)\\s+(?:com|para|pro|pra)\\s+",
            "(?:whatsapp)\\s+(?:com|para|pro|pra)\\s+",
            "\\s+no whatsapp$"
        )

        var contact = text
        patterns.forEach { pattern ->
            contact = contact.replace(Regex(pattern, RegexOption.IGNORE_CASE), "")
        }
        return contact.trim()
    }

    private fun isSetAlarmCommand(text: String): Boolean {
        val patterns = listOf(
            ".*(?:definir|define|defina|colocar|coloca|coloque|criar|cria|crie)\\s+(?:alarme|despertador)\\s+(?:para|às|as|para as|para às)\\s+.*",
            ".*(?:acorda-me|acordar-me|me\\s+acorda|me\\s+acorde)\\s+(?:às|as)\\s+.*",
            ".*(?:alarme|despertador)\\s+(?:para|às|as|para as|para às)\\s+.*"
        )
        return patterns.any { pattern -> text.normalize().matches(Regex(pattern)) }
    }

    private fun extractAlarmTime(text: String): Pair<Int?, Int?> {
        val timePattern = "(\\d{1,2})(?::|\\s+e\\s+|\\s+)(\\d{2})".toRegex()
        val match = timePattern.find(text)

        return if (match != null) {
            val hour = match.groupValues[1].toIntOrNull()
            val minute = match.groupValues[2].toIntOrNull()

            if (hour != null && minute != null && hour in 0..23 && minute in 0..59) {
                Pair(hour, minute)
            } else {
                Pair(null, null)
            }
        } else {
            Pair(null, null)
        }
    }

    private fun isSearchCommand(text: String): Boolean {
        val searchCommands = listOf(
            "pesquisar", "pesquisa", "procurar", "procura", 
            "buscar", "busca", "procure", "busque",
            "pesquise", "quero saber sobre", "me fala sobre",
            "o que é", "quem é", "onde fica"
        )
        return searchCommands.any { command -> text.startsWith(command) }
    }

    private fun extractSearchQuery(text: String): String {
        val searchCommands = listOf(
            "pesquisar", "pesquisa", "procurar", "procura", 
            "buscar", "busca", "procure", "busque",
            "pesquise", "quero saber sobre", "me fala sobre",
            "o que é", "quem é", "onde fica"
        )
        
        var query = text
        searchCommands.forEach { command ->
            if (text.startsWith(command)) {
                query = text.substring(command.length)
                return@forEach
            }
        }
        return query.trim()
    }
}
