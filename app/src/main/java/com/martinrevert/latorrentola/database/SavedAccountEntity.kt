package com.martinrevert.latorrentola.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persists an authenticated user's profile on this device for multi-account / OTT avatar profile switching.
 *
 * @property uid Unique Firebase user identifier.
 * @property displayName User display name.
 * @property email User email address.
 * @property photoUrl URL pointing to the user's avatar image.
 * @property lastActiveTimestamp Milliseconds epoch when this account was last active.
 */
@Entity(tableName = "saved_accounts")
data class SavedAccountEntity(
    @PrimaryKey val uid: String,
    val displayName: String,
    val email: String,
    val photoUrl: String? = null,
    val lastActiveTimestamp: Long = System.currentTimeMillis()
)
