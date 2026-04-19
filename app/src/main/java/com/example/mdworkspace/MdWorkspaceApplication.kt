package com.example.mdworkspace

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.mdworkspace.core.database.AppDatabase
import com.example.mdworkspace.core.datastore.AppSessionStore
import com.example.mdworkspace.core.datastore.RepositoryConfigStore
import com.example.mdworkspace.core.network.GitHubApi
import com.example.mdworkspace.data.repo.MarkdownWorkspaceRepository
import okhttp3.OkHttpClient

class MdWorkspaceApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    private val database = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "markdown_workspace.db"
    )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .build()

    private val configStore = RepositoryConfigStore(application)
    val sessionStore = AppSessionStore(application)
    private val gitHubApi = GitHubApi(
        OkHttpClient.Builder()
            .build()
    )

    val repository = MarkdownWorkspaceRepository(
        configStore = configStore,
        gitHubApi = gitHubApi,
        repoEntryDao = database.repoEntryDao(),
        snapshotDao = database.documentSnapshotDao(),
        unsupportedMarkdownCacheDao = database.unsupportedMarkdownCacheDao(),
        noteDao = database.documentNoteDao(),
        textNoteDao = database.documentTextNoteDao(),
        favoriteDao = database.favoriteDao(),
        recentDocumentDao = database.recentDocumentDao()
    )

    private companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS document_text_notes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        projectCode TEXT NOT NULL,
                        docId TEXT NOT NULL,
                        docVersion TEXT NOT NULL,
                        selectedText TEXT NOT NULL,
                        note TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_document_text_notes_projectCode_docId_docVersion " +
                        "ON document_text_notes(projectCode, docId, docVersion)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_document_text_notes_updatedAt " +
                        "ON document_text_notes(updatedAt)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE document_text_notes ADD COLUMN bodyHash TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE document_text_notes ADD COLUMN startSourceOffset INTEGER")
                db.execSQL("ALTER TABLE document_text_notes ADD COLUMN endSourceOffset INTEGER")
                db.execSQL("ALTER TABLE document_text_notes ADD COLUMN prefix TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE document_text_notes ADD COLUMN suffix TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE document_text_notes ADD COLUMN headingPathJson TEXT")
                db.execSQL("ALTER TABLE document_text_notes ADD COLUMN blockId TEXT")
                db.execSQL("ALTER TABLE document_text_notes ADD COLUMN isLegacy INTEGER NOT NULL DEFAULT 1")
            }
        }
    }
}
