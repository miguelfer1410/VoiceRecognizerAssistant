package com.example.myai.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.myai.R
import com.example.myai.ui.overlay.OverlayView
import android.app.Notification
import android.content.ComponentName
import android.content.pm.ServiceInfo
import android.net.Uri
import android.provider.ContactsContract
import android.speech.tts.TextToSpeech
import com.example.myai.commands.CommandProcessor
import java.util.Locale
import android.speech.tts.UtteranceProgressListener
import android.content.pm.ResolveInfo
import android.provider.AlarmClock
import androidx.annotation.RequiresApi
import com.example.myai.utils.normalize
import java.lang.Math.abs
import java.text.Normalizer
import com.spotify.sdk.android.player.SpotifyPlayer
import com.spotify.sdk.android.player.Config
import com.spotify.sdk.android.player.Player

class VoiceRecognitionService : Service() {
    // Propriedades
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var recognizerIntent: Intent
    private var textToSpeech: TextToSpeech? = null
    private var overlayView: OverlayView? = null
    private val commandProcessor by lazy { CommandProcessor(this) }
    private lateinit var spotifyPlayer: SpotifyPlayer

    // Estados
    private var isListening = false
    private var isSpeaking = false
    private var isServiceActive = true

    // Adicionar estas propriedades no topo da classe
    private var pendingContacts: List<Pair<String, String>>? = null
    private var pendingMessage: String? = null
    private var isWaitingForContactSelection = false
    private var messageApp: String? = null  // "whatsapp" ou "sms"
    private var pendingApps: List<ResolveInfo>? = null
    private var isWaitingForAppSelection = false

    // Lifecycle do Serviço
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        startForeground()
        initializeTextToSpeech()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val accessToken = intent?.getStringExtra("SPOTIFY_ACCESS_TOKEN")
        if (accessToken != null) {
            initializeSpotifyPlayer(accessToken)
        }
        startListening()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
    }

    // Inicialização
    private fun startForeground() {
        val channelId = "voice_service_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Serviço de Voz",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Serviço de reconhecimento de voz"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Assistente de Voz")
            .setContentText("Ouvindo comandos...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(1, notification)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale("pt", "PT"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "Linguagem não suportada")
                }else{
                    Handler(Looper.getMainLooper()).post{
                        initializeSpeechRecognizer();
                    }
                }
            } else {
                Log.e("TTS", "Inicialização falhou")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun initializeSpeechRecognizer() {
        try {
            overlayView = OverlayView(this)
            overlayView?.show()
            overlayView?.updateText("Ouvindo...")

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onResults(results: Bundle?) {
                        try {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            matches?.get(0)?.let { textoReconhecido ->
                                Log.d("VoiceService", "Texto reconhecido: $textoReconhecido")
                                
                                when {
                                    isWaitingForAppSelection -> {
                                        handleAppSelection(textoReconhecido)
                                    }
                                    isWaitingForContactSelection -> {
                                        handleContactSelection(textoReconhecido)
                                    }
                                    else -> {
                                        processCommand(textoReconhecido)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("VoiceService", "Erro ao processar resultado: ${e.message}")
                            overlayView?.updateText("Ouvindo...")
                            if (isServiceActive && !isSpeaking) startListening()
                        }
                    }

                    override fun onError(error: Int) {
                        Log.e("VoiceService", "Erro no reconhecimento: $error")
                        if (isServiceActive && !isSpeaking) {
                            when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH,
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                    isListening = false
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        startListening()
                                    }, 1000)
                                }
                                else -> {
                                    isListening = false
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        startListening()
                                    }, 1000)
                                }
                            }
                        }
                    }

                    override fun onReadyForSpeech(params: Bundle?) {
                        if (isServiceActive) {
                            Log.d("VoiceService", "Pronto para ouvir")
                            isListening = true
                            if (!isWaitingForAppSelection && !isWaitingForContactSelection) {
                                overlayView?.updateText("Ouvindo...")
                            }
                        }
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d("VoiceService", "Começou a falar")
                    }

                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        Log.d("VoiceService", "Fim da fala")
                        isListening = false
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-PT")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            startListening()
        } catch (e: Exception) {
            Log.e("VoiceService", "Erro ao inicializar: ${e.message}")
        }
    }

    // Funções de Reconhecimento de Voz
    private fun startListening() {
        try {
            if (!isListening && !isSpeaking && speechRecognizer != null && isServiceActive) {
                Log.d("VoiceService", "Iniciando novo ciclo de reconhecimento")
                speechRecognizer?.cancel()
                Handler(Looper.getMainLooper()).postDelayed({
                    speechRecognizer?.startListening(recognizerIntent)
                    Log.d("VoiceService", "Reconhecimento iniciado com sucesso")
                }, 500)
            } else {
                Log.d("VoiceService", "Não iniciou reconhecimento: isListening=$isListening, isSpeaking=$isSpeaking, speechRecognizer=${speechRecognizer != null}, isServiceActive=$isServiceActive")
            }
        } catch (e: Exception) {
            Log.e("VoiceService", "Erro ao iniciar reconhecimento: ${e.message}")
            isListening = false
            if (isServiceActive && !isSpeaking) {
                Handler(Looper.getMainLooper()).postDelayed({
                    startListening()
                }, 1000)
            }
        }
    }

    private fun speak(text: String) {
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
            }

            override fun onDone(utteranceId: String?) {
                isSpeaking = false
                if (isServiceActive) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        startListening()
                    }, 500)
                }
            }

            override fun onError(utteranceId: String?) {
                isSpeaking = false
                if (isServiceActive) {
                    startListening()
                }
            }
        })

        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "messageId")
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "messageId")
    }

    // Processamento de Comandos
    private fun processCommand(text: String) {
        when (val result = commandProcessor.processCommand(text)) {
            is CommandResult.Unknown -> {
                val respostas = listOf(
                    "Desculpe, ${result.text}",
                    "Ops, ${result.text}",
                    "Não entendi bem. ${result.text}",
                    "Hmm, ${result.text}"
                )
                speak(respostas.random())
                overlayView?.updateText("Não entendi o comando")
                // Não precisa chamar startListening() aqui pois será chamado no onDone do speak
            }
            is CommandResult.Stop -> {
                isServiceActive = false
                val stopText = "Assistente desativado!"
                overlayView?.updateText(stopText)
                speak(stopText)
                
                Handler(Looper.getMainLooper()).postDelayed({
                    cleanup()
                    stopSelf()
                }, 2000)
            }
            is CommandResult.OpenApp -> {
                openApp(result.appCommand.packageName)
            }
            is CommandResult.SendMessage -> {
                when(result.app) {
                    "whatsapp" -> sendWhatsAppMessage(result.contact, result.message)
                    "sms" -> sendSMS(result.contact, result.message)
                    else -> speak("Não entendi qual aplicativo usar para a mensagem")
                }
            }
            is CommandResult.OpenWhatsAppChat -> {
                openWhatsAppChat(result.contact)
            }
            is CommandResult.SetAlarm -> {
                setAlarm(result.hour, result.minute)
            }
            is CommandResult.Search -> {
                performSearch(result.query)
            }

            else -> {}
        }
    }

    private fun openApp(appName: String) {
        try {
            val pm = packageManager
            val queryIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(queryIntent, 0)
            
            // Busca apps que correspondam ao nome fornecido
            val matchingApps = resolveInfos.filter { resolveInfo ->
                val appLabel = resolveInfo.loadLabel(pm).toString().lowercase()
                val searchName = appName.trim().lowercase()
                
                appLabel.contains(searchName) || 
                appLabel.replace(" ", "").contains(searchName) ||
                resolveInfo.activityInfo.packageName.contains(searchName)
            }

            when {
                matchingApps.isEmpty() -> {
                    speak("Não encontrei o aplicativo $appName")
                }
                matchingApps.size == 1 -> {
                    val intent = pm.getLaunchIntentForPackage(matchingApps[0].activityInfo.packageName)
                    intent?.let {
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(it)
                        speak("Abrindo ${matchingApps[0].loadLabel(pm)}")
                    }
                }
                else -> {
                    pendingApps = matchingApps.sortedBy { it.loadLabel(pm).toString().lowercase() }
                    isWaitingForAppSelection = true
                    val appList = pendingApps!!.mapIndexed { index, app -> 
                        "${index + 1} - ${app.loadLabel(pm)}"
                    }.joinToString("\n")
                    speak("Encontrei vários aplicativos. Qual você quer abrir?")
                    overlayView?.updateText("Escolha um número:\n$appList")
                }
            }
        } catch (e: Exception) {
            Log.e("VoiceService", "Erro ao abrir app: ${e.message}")
            speak("Não consegui abrir o aplicativo $appName")
        }
    }

    private fun handleAppSelection(text: String) {
        val numberSpoken = convertSpokenNumberToDigit(text.lowercase().trim())
        
        if (numberSpoken != null && pendingApps != null) {
            if (numberSpoken > 0 && numberSpoken <= pendingApps!!.size) {
                val selectedApp = pendingApps!![numberSpoken - 1]
                try {
                    val packageName = selectedApp.activityInfo?.packageName 
                        ?: selectedApp.resolvePackageName 
                        ?: throw Exception("Não foi possível encontrar o pacote do aplicativo")
                        
                    val intent = packageManager.getLaunchIntentForPackage(packageName)
                    intent?.let {
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(it)
                        speak("Abrindo ${selectedApp.loadLabel(packageManager)}")
                        
                        isWaitingForAppSelection = false
                        pendingApps = null
                        overlayView?.updateText("Ouvindo...")
                    } ?: throw Exception("Não foi possível criar o intent para o aplicativo")
                } catch (e: Exception) {
                    Log.e("VoiceService", "Erro ao abrir aplicativo: ${e.message}")
                    speak("Desculpe, não consegui abrir este aplicativo")
                    startListening()
                }
            } else {
                speak("Número inválido. Por favor, tente novamente.")
                startListening()
            }
        }
    }

    private fun sendWhatsAppMessage(contact: String, message: String) {
        try {
            Log.d("VoiceService", "Tentando enviar mensagem para: '$contact'")
            pendingMessage = message
            messageApp = "whatsapp"
            val phoneNumber = getPhoneNumberFromContactName(contact)

            if (phoneNumber != null && !isWaitingForContactSelection) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://api.whatsapp.com/send?phone=$phoneNumber&text=${Uri.encode(message)}")
                    `package` = "com.whatsapp"
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
                speak("Abrindo conversa no WhatsApp. Por favor, confirme o envio.")
            }
        } catch (e: Exception) {
            Log.e("VoiceService", "Erro ao abrir WhatsApp: ${e.message}")
            speak("Não consegui abrir o WhatsApp")
        }
    }

    private fun openWhatsAppChat(contact: String) {
        try {
            Log.d("VoiceService", "Tentando abrir conversa com: '$contact'")
            val phoneNumber = getPhoneNumberFromContactName(contact)

            if (phoneNumber != null && !isWaitingForContactSelection) {
                try {
                    // Formata o número removendo caracteres especiais e adiciona o código do país se necessário
                    var formattedNumber = phoneNumber.replace(Regex("[^0-9+]"), "")


                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://api.whatsapp.com/send?phone=$formattedNumber")
                        `package` = "com.whatsapp"
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                    speak("Abrindo conversa com $contact no WhatsApp")
                } catch (e: Exception) {
                    Log.e("VoiceService", "Erro ao abrir conversa: ${e.message}")
                    speak("Não consegui abrir a conversa no WhatsApp")
                }
            }
        } catch (e: Exception) {
            Log.e("VoiceService", "Erro ao processar contato: ${e.message}")
            speak("Não consegui processar o contato")
        }
    }

    private fun sendSMS(contact: String, message: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$contact")
                putExtra("sms_body", message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            speak("Enviando SMS para $contact")
        } catch (e: Exception) {
            Log.e("VoiceService", "Erro ao enviar SMS: ${e.message}")
            speak("Não consegui enviar o SMS")
        }
    }

    // Utilitários de Contatos
    private fun getPhoneNumberFromContactName(contactName: String): String? {
        try {
            val normalizedSearchName = contactName.normalize().lowercase()
            Log.d("VoiceService", "Procurando contato normalizado: $normalizedSearchName")

            val cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID
                ),
                null,
                null,
                null
            )

            cursor?.use {
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val contactIdIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                
                val exactMatches = mutableMapOf<Long, Pair<String, String>>()
                val partialMatches = mutableMapOf<Long, Pair<String, String>>()
                
                while (it.moveToNext()) {
                    val name = it.getString(nameIndex)
                    val number = it.getString(numberIndex)
                    val contactId = it.getLong(contactIdIndex)
                    val normalizedName = name.normalize().lowercase()
                    
                    when {
                        // Correspondência exata
                        normalizedName == normalizedSearchName -> {
                            if (!exactMatches.containsKey(contactId)) {
                                exactMatches[contactId] = Pair(name, number)
                            }
                        }
                        // Correspondência parcial
                        normalizedName.contains(normalizedSearchName) -> {
                            if (!partialMatches.containsKey(contactId)) {
                                partialMatches[contactId] = Pair(name, number)
                            }
                        }
                    }
                }

                // Primeiro verifica correspondências exatas
                if (exactMatches.isNotEmpty()) {
                    return if (exactMatches.size == 1) {
                        exactMatches.values.first().second
                    } else {
                        pendingContacts = exactMatches.values.toList()
                        isWaitingForContactSelection = true
                        val contactList = pendingContacts!!.mapIndexed { index, contact -> 
                            "${index + 1} - ${contact.first}"
                        }.joinToString("\n")
                        speak("Encontrei vários contatos exatos. Qual você quer usar?")
                        overlayView?.updateText("Escolha um número:\n$contactList")
                        null
                    }
                }

                // Se não houver correspondências exatas, usa as parciais
                val allMatches = partialMatches.values.toList()
                return when {
                    allMatches.isEmpty() -> {
                        speak("Não encontrei o contato $contactName")
                        null
                    }
                    allMatches.size == 1 -> {
                        allMatches[0].second
                    }
                    else -> {
                        pendingContacts = allMatches
                        isWaitingForContactSelection = true
                        val contactList = allMatches.mapIndexed { index, contact -> 
                            "${index + 1} - ${contact.first}"
                        }.joinToString("\n")
                        speak("Encontrei vários contatos. Qual você quer usar?")
                        overlayView?.updateText("Escolha um número:\n$contactList")
                        null
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("VoiceService", "Erro ao buscar contato: ${e.message}")
            speak("Erro ao buscar contato")
        }
        return null
    }

    // Adicionar esta nova função para tratar a seleção de contatos
    private fun handleContactSelection(text: String) {
        val numberSpoken = convertSpokenNumberToDigit(text.lowercase().trim())
        Log.d("VoiceService", "Número falado convertido: $numberSpoken")

        if (numberSpoken != null) {
            if (numberSpoken > 0 && numberSpoken <= (pendingContacts?.size ?: 0)) {
                val selectedContact = pendingContacts!![numberSpoken - 1]
                overlayView?.updateText("Selecionado: ${selectedContact.first}")

                val formattedNumber = selectedContact.second.replace(Regex("[^0-9+]"), "")
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://api.whatsapp.com/send?phone=$formattedNumber")
                    if (pendingMessage != null) {
                        data = Uri.parse("https://api.whatsapp.com/send?phone=$formattedNumber&text=${Uri.encode(pendingMessage)}")
                    }
                    `package` = "com.whatsapp"
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
                speak("Abrindo conversa com ${selectedContact.first} no WhatsApp")



            } else {
                speak("Número inválido. Por favor, tente novamente.")
            }
        } else {
            speak("Não entendi o número. Por favor, diga apenas o número do contato desejado.")
        }

        // Limpa o estado de seleção
        isWaitingForContactSelection = false
        pendingContacts = null
        pendingMessage = null
        messageApp = null
    }

    private fun convertSpokenNumberToDigit(text: String): Int? {
        // Mapa de números por extenso para dígitos
        val numberMap = mapOf(
            "um" to 1,
            "uma" to 1,
            "primeiro" to 1,
            "primeira" to 1,
            "dois" to 2,
            "duas" to 2,
            "segundo" to 2,
            "segunda" to 2,
            "três" to 3,
            "terceiro" to 3,
            "terceira" to 3,
            "quatro" to 4,
            "quarta" to 4,
            "quarto" to 4,
            "cinco" to 5,
            "quinta" to 5,
            "quinto" to 5,
            "seis" to 6,
            "sexta" to 6,
            "sexto" to 6,
            "sete" to 7,
            "sétima" to 7,
            "sétimo" to 7,
            "oito" to 8,
            "oitava" to 8,
            "oitavo" to 8,
            "nove" to 9,
            "nona" to 9,
            "nono" to 9,
            "dez" to 10,
            "décima" to 10,
            "décimo" to 10
        )

        // Primeiro tenta encontrar um número escrito por extenso
        for ((word, number) in numberMap) {
            if (text.contains(word)) {
                return number
            }
        }

        // Se não encontrar, tenta extrair um número digital
        val numberPattern = "\\d+".toRegex()
        val matchResult = numberPattern.find(text)
        return matchResult?.value?.toIntOrNull()
    }


    private fun setAlarm(hour: Int, minute: Int) {
        overlayView?.updateText(hour.toString())
        try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            speak("Alarme definido para $hour:${minute.toString().padStart(2, '0')}")
        } catch (e: Exception) {
            Log.e("VoiceService", "Erro ao definir alarme: ${e.message}")
            speak("Não consegui definir o alarme")
        }
    }

    private fun performSearch(query: String) {
        try {
            // Cria a URI de pesquisa do Google
            val searchUri = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
            
            // Cria e configura o intent para abrir o navegador
            val searchIntent = Intent(Intent.ACTION_VIEW, searchUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            // Inicia a atividade
            startActivity(searchIntent)
            
            // Feedback ao usuário
            speak("Pesquisando por $query")
            overlayView?.updateText("Pesquisando: $query")
            
        } catch (e: Exception) {
            Log.e("VoiceService", "Erro ao realizar pesquisa: ${e.message}")
            speak("Desculpe, não consegui realizar a pesquisa")
            overlayView?.updateText("Erro na pesquisa")
        }
    }

    // Funções de Limpeza
    private fun cleanup() {
        isServiceActive = false
        isListening = false

        speechRecognizer?.destroy()
        speechRecognizer = null

        textToSpeech?.stop()
        textToSpeech?.shutdown()

        overlayView?.hide()
        overlayView = null
    }

    // Companion Object para Constantes
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "voice_service_channel"
        private const val SPEECH_DELAY = 500L
        private const val ERROR_RETRY_DELAY = 1000L
        private const val STOP_SERVICE_DELAY = 2000L
    }

    private fun initializeSpotifyPlayer(accessToken: String) {
        val config = Config(this, accessToken, "2a222dc5c8af4df391fb1259567711d7")
        spotifyPlayer = SpotifyPlayer(config, object : Player.InitializationObserver {
            override fun onInitialized(player: Player) {
                Log.d("Spotify", "Player inicializado com sucesso")
            }

            override fun onError(throwable: Throwable) {
                Log.e("Spotify", "Erro ao inicializar o player: ${throwable.message}")
            }
        })
    }

    private fun playMusic(trackUri: String) {
        spotifyPlayer.playUri(null, trackUri, 0, 0)
        speak("Tocando música")
    }
}