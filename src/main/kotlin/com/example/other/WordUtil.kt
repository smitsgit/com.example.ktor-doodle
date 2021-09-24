package com.example.other

import java.io.File

val words = readWordList("resources/wordlist.txt")

fun readWordList(fileName: String): List<String> {
    val inputStream = File(fileName).inputStream()
    val wordsList = mutableListOf<String>()
    inputStream.bufferedReader().forEachLine {
        wordsList.add(it)
    }
    return wordsList
}


fun getRandomWords(howMany: Int): List<String> {
   var currentNoOfWords = 0
   val result = mutableListOf<String>()

   while(currentNoOfWords < howMany) {
       val word = words.random()
       if (word !in result) {
           result.add(word)
           currentNoOfWords++
       }
   }
   return result
}


fun String.transforToUnderscores() = toCharArray().map {
    if (it != ' ') '_' else ' '
}.joinToString(" ")