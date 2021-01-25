package de.westnordost.streetcomplete.data.osmnotes

import de.westnordost.streetcomplete.data.NotesApi
import de.westnordost.osmapi.common.Handler
import de.westnordost.osmapi.map.data.BoundingBox
import de.westnordost.osmapi.map.data.OsmLatLon
import de.westnordost.osmapi.notes.Note
import de.westnordost.osmapi.notes.NoteComment
import de.westnordost.osmapi.user.User
import de.westnordost.streetcomplete.data.user.UserStore
import de.westnordost.streetcomplete.mock
import de.westnordost.streetcomplete.on
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.verify
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class OsmNotesDownloaderTest {
    private lateinit var notesApi: NotesApi
    private lateinit var noteController: NoteController
    private lateinit var avatarsDownloader: OsmAvatarsDownloader
    private lateinit var userStore: UserStore

    @Before fun setUp() {
        notesApi = mock()
        noteController = mock()

        avatarsDownloader = mock()
        userStore = mock()
        on(userStore.userId).thenReturn(1L)
    }

    @Test fun `downloads avatars of all users involved in note discussions`() {
        val note1 = createANote(4L)
        note1.comments.addAll(listOf(
            NoteComment().apply {
                date = Date()
                action = NoteComment.Action.COMMENTED
                text = "abc"
                user = User(54, "Blibu")
            },
            NoteComment().apply {
                date = Date()
                action = NoteComment.Action.COMMENTED
                text = "abc"
                user = User(13, "Wilbur")
            }
        ))

        val noteApi = TestListBasedNotesApi(listOf(note1))
        val dl = OsmNotesDownloader(noteApi, avatarsDownloader, userStore, noteController)
        dl.download(BoundingBox(0.0, 0.0, 1.0, 1.0), AtomicBoolean(false))

        verify(avatarsDownloader).download(setOf(54, 13))
    }
}

private fun createANote(id: Long): Note {
    val note = Note()
    note.id = id
    note.position = OsmLatLon(6.0, 7.0)
    note.status = Note.Status.OPEN
    note.dateCreated = Date()
    val comment = NoteComment()
    comment.date = Date()
    comment.action = NoteComment.Action.OPENED
    comment.text = "hurp durp"
    note.comments.add(comment)
    return note
}

private class TestListBasedNotesApi(val notes: List<Note>) :  NotesApi(null) {
    override fun getAll(bounds: BoundingBox, handler: Handler<Note>, limit: Int, hideClosedNoteAfter: Int) {
        for (note in notes) {
            handler.handle(note)
        }
    }
}
