package com.samuele.crossword.data.db

import androidx.room.*

@Entity(tableName = "word_lists")
data class WordListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "words",
    foreignKeys = [ForeignKey(
        entity = WordListEntity::class,
        parentColumns = ["id"], childColumns = ["listId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("listId")]
)
data class WordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val word: String,
    val clue: String
)

@Entity(tableName = "crosswords")
data class CrosswordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val name: String,
    val rows: Int,
    val cols: Int,
    val maskJson: String,
    val lettersJson: String,
    val slotsJson: String,
    val createdAt: Long = System.currentTimeMillis()
)
