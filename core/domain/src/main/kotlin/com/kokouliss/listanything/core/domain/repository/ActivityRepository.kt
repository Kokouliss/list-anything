package com.kokouliss.listanything.core.domain.repository

import com.kokouliss.listanything.core.domain.model.Activity
import kotlinx.coroutines.flow.Flow

/**
 * The history of a space: who added, checked, changed or deleted what. The item and list
 * repositories write it as things happen; entries go by themselves after `ACTIVITY_KEEP_DAYS`.
 */
interface ActivityRepository {
    /** The newest entries first, at most [ACTIVITY_SHOWN] of them. */
    fun observeActivity(spaceId: String): Flow<List<Activity>>

    companion object {
        /** Each visit reads at most this many entries. */
        const val ACTIVITY_SHOWN = 100L
    }
}
