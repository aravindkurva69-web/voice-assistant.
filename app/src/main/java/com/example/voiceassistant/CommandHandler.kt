package com.example.voiceassistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CommandHandler(
    private val context: Context,
    private val speak: (String) -> Unit
) {

    fun handle(raw: String) {
        val cmd = raw.lowercase(Locale.getDefault()).trim()
        when {
            cmd.contains("time") -> {
                val t = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
                speak("It is $t")
            }
            cmd.contains("date") || cmd.contains("today") -> {
                val d = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date())
                speak("Today is $d")
            }
            cmd.startsWith("call ") -> callContact(cmd.removePrefix("call ").trim())
            cmd.contains("open youtube") -> openApp("com.google.android.youtube", "YouTube")
            cmd.contains("open instagram") -> openApp("com.instagram.android", "Instagram")
            cmd.contains("open whatsapp") -> openApp("com.whatsapp", "WhatsApp")
            else -> speak("Sorry, I did not understand")
        }
    }

    private fun openApp(pkg: String, name: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            speak("Opening $name")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            speak("$name is not installed")
        }
    }

    private fun callContact(name: String) {
        val hasContacts = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        val hasCall = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasContacts || !hasCall) {
            speak("I need contacts and phone permission")
            return
        }

        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val number = it.getString(0)
                speak("Calling $name")
                val call = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                call.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(call)
                return
            }
        }
        speak("I could not find $name in contacts")
    }
}
