package cz.jwo.kisctecka

import android.content.Context
import android.content.Intent

fun launchGitHub(context: Context) {
    context.startActivity(Intent.parseUri("https://github.com/su-fit-vut/kis-ctecka-android/", 0));
}

fun launchDiscord(context: Context) {
    context.startActivity(Intent.parseUri("https://discord.com/channels/369526274433089546/492346484629700619", 0));
}
