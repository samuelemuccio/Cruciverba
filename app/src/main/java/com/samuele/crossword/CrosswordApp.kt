package com.samuele.crossword

import android.app.Application
import com.samuele.crossword.data.db.CrosswordDatabase
import com.samuele.crossword.data.repository.CrosswordRepository
import com.samuele.crossword.data.repository.WordListRepository

class CrosswordApp : Application() {
    val database by lazy { CrosswordDatabase.get(this) }
    val wordListRepository by lazy { WordListRepository(database.wordListDao()) }
    val crosswordRepository by lazy { CrosswordRepository(database.crosswordDao()) }
}
