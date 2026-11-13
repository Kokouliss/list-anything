package com.kokouliss.listanything.core.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import com.kokouliss.listanything.core.domain.model.Drawing
import com.kokouliss.listanything.core.domain.model.DrawingRef
import com.kokouliss.listanything.core.domain.model.Stroke
import com.kokouliss.listanything.core.domain.repository.DrawingRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

internal fun FirebaseFirestore.drawings(spaceId: String): CollectionReference =
    collection(Collections.SPACES).document(spaceId).collection(Collections.DRAWINGS)

internal fun FirebaseFirestore.drawing(ref: DrawingRef): DocumentReference = drawings(ref.spaceId).document(ref.drawingId)

internal class FirestoreDrawingRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) : DrawingRepository {

    override fun observeDrawings(spaceId: String): Flow<List<Drawing>> =
        db.drawings(spaceId).orderBy(Fields.CREATED_AT).snapshots().map { snapshot ->
            snapshot.documents.mapNotNull { it.toDrawing(spaceId) }
        }

    override fun observeDrawing(ref: DrawingRef): Flow<Drawing?> =
        db.drawing(ref).snapshots()
            .map { it.toDrawing(ref.spaceId) }
            .catch { e -> if (e is FirebaseFirestoreException) emit(null) else throw e }

    override suspend fun createDrawing(spaceId: String, title: String): String {
        val drawing = db.drawings(spaceId).document()
        drawing
            .set(
                mapOf(
                    Fields.TITLE to title,
                    Fields.STROKES to emptyList<Any>(),
                    Fields.CREATED_BY_NAME to auth.requireUser().memberName,
                    Fields.CREATED_AT to FieldValue.serverTimestamp(),
                ),
            )
            .logFailure("create drawing")
        return drawing.id
    }

    // Strokes are appended and removed one at a time, so two people drawing at once keep both lines.
    override suspend fun addStroke(ref: DrawingRef, stroke: Stroke) {
        db.drawing(ref).update(Fields.STROKES, FieldValue.arrayUnion(stroke.toMap())).logFailure("draw")
    }

    override suspend fun removeStroke(ref: DrawingRef, stroke: Stroke) {
        db.drawing(ref).update(Fields.STROKES, FieldValue.arrayRemove(stroke.toMap())).logFailure("undo stroke")
    }

    override suspend fun clearStrokes(ref: DrawingRef) {
        db.drawing(ref).update(Fields.STROKES, emptyList<Any>()).logFailure("clear drawing")
    }

    override suspend fun deleteDrawing(ref: DrawingRef) {
        db.drawing(ref).delete().logFailure("delete drawing")
    }
}
