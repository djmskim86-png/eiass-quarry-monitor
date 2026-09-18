package com.coupleledger.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 부부(가구) 단위. Firestore의 households/{householdId} 문서를 로컬에 캐시한 것.
 * 초대 코드로 배우자가 참여하면 memberUserIds에 uid가 추가된다.
 */
@Entity(tableName = "household")
data class HouseholdEntity(
    @PrimaryKey val id: String,
    val name: String,
    val inviteCode: String,
    val memberUserIds: List<String>,
    val syncIntervalMinutes: Long = 30,
    val updatedAt: Long = System.currentTimeMillis(),
)
